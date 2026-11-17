package com.gr4nt.dynamicisland

import android.app.RemoteInput
import android.content.*
import android.os.*
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class IslandState(val item: IslandItem? = null, val expanded: Boolean = true, val replying: Boolean = false, val generation: Long = 0)
object IslandController {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var context: Context
    lateinit var settings: LayoutStore
    lateinit var root: RootExtras
    val state = MutableStateFlow(IslandState())
    val connected = MutableStateFlow(false)
    val listenerConnected = MutableStateFlow(false)
    private var media: IslandItem? = null
    private val calls = LinkedHashMap<String, IslandItem>()
    private val hiddenMedia = mutableSetOf<String>()
    private var timer: Job? = null
    private val gate=NotificationGate()
    var setFocusable: (Boolean) -> Unit = {}
    fun init(c: Context) {
        context = c.applicationContext; settings = LayoutStore(context,scope); root = RootExtras(context)
    }
    fun post(item: IslandItem, silent: Boolean = false) {
        val layout = settings.flow.value
        if (!layout.allApps && item.pkg !in layout.allowList) return
        val now = SystemClock.elapsedRealtime()
        if(gate.blocked(item.key,item.pkg,now)) return
        if (item.media != null && !item.call) {
            val previous = media
            if(item.media.playing && (previous?.key != item.key || previous.media?.playing != true)) hiddenMedia.remove(item.key)
            media = item
            if(state.value.item?.key == item.key) {
                state.value = state.value.copy(item=item) // No generation change: no popup/timeout reset.
                if(!item.media.playing && !state.value.expanded) fallback()
            } else if(state.value.item == null && item.media.playing && item.key !in hiddenMedia) display(item,false)
            return
        }
        if(item.call) {
            val previous=state.value.item
            val existing=calls[item.key]
            val known=existing?.takeIf { !it.incoming }
                ?: if(existing?.incoming==true) null else calls.values.lastOrNull {
                    it.pkg==item.pkg && it.title==item.title && !it.incoming
                }
            val callItem=if(item.incoming) item.copy(media=null) else item.copy(media=null,callStartedAtElapsed=
                known?.callStartedAtElapsed ?: item.callStartedAtElapsed ?: now)
            // Some dialers replace the incoming notification with a new ongoing key.
            if(!item.incoming && (known==null || previous?.key==item.key)) calls.entries.removeAll { it.value.pkg==item.pkg && it.value.incoming }
            calls[item.key]=callItem
            val sameVisibleCall=previous?.call==true && previous.pkg==item.pkg &&
                (previous.key==item.key || (!item.incoming && (!previous.incoming || known==null)))
            when {
                sameVisibleCall && previous?.incoming==item.incoming -> state.value=state.value.copy(item=callItem)
                sameVisibleCall -> display(callItem,item.incoming)
                item.incoming -> display(callItem,true)
                previous==null || previous.media!=null -> display(callItem,false)
                // An ongoing-call refresh must not replace a message or steal reply focus.
                else -> Unit
            }
            return
        }
        if(silent) return
        // Keep the focused reply intact when unrelated notifications arrive.
        if(state.value.replying || state.value.item?.let { it.call && it.incoming }==true) return
        if(gate.duplicate(item.pkg,item.title,item.text,now)) return
        display(item,true)
    }
    private fun display(item: IslandItem, expanded: Boolean) {
        timer?.cancel(); setFocusable(false)
        state.value=IslandState(item,expanded,false,state.value.generation+1)
        schedule()
    }
    fun schedule() {
        timer?.cancel()
        val s=state.value
        if(s.item == null || (s.item.call && s.item.incoming) || s.replying || !s.expanded) return
        timer=scope.launch { delay((settings.flow.value.seconds*1000).toLong()); collapse() }
    }
    fun collapse() {
        setFocusable(false)
        val item=state.value.item
        if(item?.call == true && !item.incoming) display(item,false)
        else if(item?.media != null && item.media.playing && item.key !in hiddenMedia) display(item,false) else fallback()
    }
    private fun fallback() {
        timer?.cancel();setFocusable(false)
        val c=calls.values.lastOrNull { it.incoming } ?: calls.values.lastOrNull()
        val m=media?.takeIf { it.media?.playing == true && it.key !in hiddenMedia }
        val item=c ?: m
        state.value=IslandState(item,item?.call == true && item.incoming,false,state.value.generation+1)
    }
    fun dismiss() {
        val item=state.value.item ?: return
        if(item.media != null) hiddenMedia.add(item.key)
        // Swipe hides only the presentation; never cancels a call or notification.
        if(item.call) calls.remove(item.key)
        fallback()
    }
    fun removed(key: String) {
        calls.remove(key);if(media?.key == key) media=null
        hiddenMedia.remove(key);if(state.value.item?.key == key) fallback()
    }
    fun tap() {
        val s=state.value;val item=s.item ?: return
        if((item.media != null || (item.call && !item.incoming)) && !s.expanded) display(item,true)
        else if(!s.replying) { sendIntent { item.intent?.send() }; collapse() }
    }
    fun reply() { timer?.cancel();state.value=state.value.copy(replying=true);setFocusable(true) }
    fun cancelReply() { setFocusable(false);state.value=state.value.copy(replying=false);schedule() }
    fun sendReply(text: String) {
        if(text.isBlank()) return
        val item=state.value.item ?: return
        val action=item.actions.firstOrNull { it.reply } ?: return
        val intent=Intent();val result=Bundle()
        action.inputs.filter { it.allowFreeFormInput }.forEach { result.putCharSequence(it.resultKey,text) }
        RemoteInput.addResultsToIntent(action.inputs.toTypedArray(),intent,result)
        RemoteInput.setResultsSource(intent,RemoteInput.SOURCE_FREE_FORM_INPUT)
        // Install suppression before the app can publish its own reply echo.
        val now=SystemClock.elapsedRealtime();gate.suppressReply(item.key,item.pkg,now)
        try { action.intent.send(context,0,intent); collapse() }
        catch(_: android.app.PendingIntent.CanceledException) {
            gate.undoReply(item.key,item.pkg);toast("Reply action is no longer available")
        }
    }
    fun action(action: IslandAction) { if(action.reply) reply() else { sendIntent { action.intent.send() }; if(state.value.item?.media == null || state.value.item?.call==true) collapse() } }
    private fun sendIntent(block: ()->Unit) { runCatching(block).onFailure { toast("This notification action is no longer available") } }
    fun copyCode() { val code=state.value.item?.code ?: return;(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Verification code",code));toast("Code copied");collapse() }
    fun toast(text: String) { Toast.makeText(context,text,Toast.LENGTH_SHORT).show() }
    fun preview(kind: String = "message") {
        val base=IslandItem("preview","preview","Messages","Ama","See you in a few minutes. I’m bringing the camera.")
        display(when(kind){"call"->base.copy(app="Phone",call=true,incoming=true);"ongoing"->base.copy(app="Phone",call=true,incoming=false,callStartedAtElapsed=SystemClock.elapsedRealtime()-42000);"code"->base.copy(app="Verification",title="Verification code",text="Your code is 482916",code="482916");else->base},kind!="ongoing")
        if(kind=="call" || kind=="ongoing") timer=scope.launch { delay(6000);removed("preview") }
    }
}
