package com.gr4nt.dynamicisland

import android.app.RemoteInput
import android.content.*
import android.os.*
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class IslandState(val item: IslandItem? = null, val expanded: Boolean = true, val replying: Boolean = false, val generation: Long = 0, val secondary: IslandItem? = null, val swapGeneration: Long = 0, val details: Boolean = false)
object IslandController {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var context: Context
    lateinit var settings: LayoutStore
    lateinit var root: RootExtras
    val state = MutableStateFlow(IslandState())
    val connected = MutableStateFlow(false)
    val listenerConnected = MutableStateFlow(false)
    private val live=LiveIslandRegistry()
    lateinit var systemEvents: SystemIslandEvents
    private var swapGeneration=0L
    private val calls = LinkedHashMap<String, IslandItem>()
    private var timer: Job? = null
    private val gate=NotificationGate()
    var setFocusable: (Boolean) -> Unit = {}
    fun init(c: Context) {
        context = c.applicationContext; settings = LayoutStore(context,scope); root = RootExtras(context);systemEvents=SystemIslandEvents(context,scope)
    }
    fun post(item: IslandItem, silent: Boolean = false) {
        val layout = settings.flow.value
        if (item.event==IslandEvent.NOTIFICATION && !layout.allApps && item.pkg !in layout.allowList) return
        val now = SystemClock.elapsedRealtime()
        if(gate.blocked(item.key,item.pkg,now)) return
        if(item.activity==null && item.media==null && !item.call && live.get(item.key)?.activity!=null) {
            live.remove(item.key)
            if(state.value.item?.key==item.key)fallback() else refreshSecondary()
        }
        if(item.event!=IslandEvent.NOTIFICATION) {
            if(state.value.replying || state.value.item?.let { it.call && it.incoming }==true) return
            if(gate.duplicate(item.pkg,item.title,item.text,now)) return
            display(item,false);return
        }
        if(item.activity!=null || item.media!=null && !item.call) {
            live.put(item)
            val visible=state.value
            when {
                visible.item?.key==item.key -> {
                    state.value=visible.copy(item=item)
                    if(item.media?.playing==false && !visible.expanded) fallback() else refreshSecondary()
                }
                visible.item==null && live.active().any { it.key==item.key } -> display(item,false)
                item.activity!=null && visible.item?.media!=null && !visible.expanded && live.active().any { it.key==item.key } -> display(item,false)
                else -> refreshSecondary()
            }
            return
        }
        if(item.call) {
            val previous=state.value.item
            val existing=calls[item.key]
            val known=existing?.takeIf { !it.incoming }
                ?: if(existing?.incoming==true) null else calls.values.lastOrNull {
                    it.pkg==item.pkg && it.title==item.title && !it.incoming
                }
            val observedWaiting=existing?.callStartedAtElapsed==null && existing!=null || calls.values.any {
                it.pkg==item.pkg && it.title==item.title && it.callStartedAtElapsed==null
            }
            val start=CallTiming.start(item.incoming,item.callConnected || item.callStartedAtElapsed!=null,
                known?.callStartedAtElapsed,observedWaiting,item.callStartedAtElapsed,now)
            val callItem=item.copy(media=null,callStartedAtElapsed=start,callConnected=start!=null)
            // Some dialers replace the incoming notification with a new ongoing key.
            if(!item.incoming && (known==null || previous?.key==item.key)) calls.entries.removeAll { it.value.pkg==item.pkg && it.value.incoming }
            calls[item.key]=callItem
            val sameVisibleCall=previous?.call==true && previous.pkg==item.pkg &&
                (previous.key==item.key || (!item.incoming && (!previous.incoming || known==null)))
            when {
                sameVisibleCall && previous?.incoming==item.incoming -> state.value=state.value.copy(item=callItem)
                sameVisibleCall -> display(callItem,item.incoming)
                item.incoming -> display(callItem,true)
                previous==null || previous.media!=null || previous.activity!=null -> display(callItem,false)
                // An ongoing-call refresh must not replace a message or steal reply focus.
                else -> Unit
            }
            refreshSecondary()
            return
        }
        if(silent) return
        // Keep the focused reply intact when unrelated notifications arrive.
        if(state.value.replying || state.value.item?.let { it.call && it.incoming }==true) return
        if(gate.duplicate(item.pkg,item.title,item.text,now)) return
        display(item,true)
    }
    fun updateSystemMonitoring() {
        runCatching { if(connected.value || listenerConnected.value) systemEvents.start() else systemEvents.stop() }
            .onFailure { android.util.Log.w("DynamicIsland","System monitor unavailable",it) }
    }
    private fun candidates(): List<IslandItem> = calls.values.filter { it.incoming }.asReversed() + calls.values.filter { !it.incoming }.asReversed() + live.active()
    private fun secondaryFor(item: IslandItem?, preferred: IslandItem?=state.value.secondary): IslandItem? = item?.let { primary ->
        val available=candidates().filter { it.key!=primary.key && it.pkg!=primary.pkg }
        available.firstOrNull { it.key==preferred?.key } ?: available.firstOrNull()
    }
    private fun refreshSecondary() {
        val s=state.value
        val secondary=if(s.item!=null && !s.expanded && !s.replying && IslandPresentation.tier(s.item,false)==IslandTier.PILL) secondaryFor(s.item) else null
        state.value=s.copy(secondary=secondary)
    }
    fun swap() {
        val s=state.value
        if(s.expanded || s.replying || s.item?.incoming==true)return
        s.secondary?.let { swapGeneration++;display(it,false) }
    }
    private fun display(item: IslandItem, expanded: Boolean) {
        timer?.cancel(); setFocusable(false)
        state.value=IslandState(item,expanded,false,state.value.generation+1,if(!expanded && IslandPresentation.tier(item,false)==IslandTier.PILL) secondaryFor(item,state.value.item) else null,swapGeneration)
        schedule()
    }
    fun schedule() {
        timer?.cancel()
        val s=state.value
        val item=s.item ?: return
        if(item.call && item.incoming || s.replying)return
        if(item.activity==null && item.media==null && !item.call && live.get(item.key)?.activity!=null) {
            live.remove(item.key)
            if(state.value.item?.key==item.key)fallback() else refreshSecondary()
        }
        if(item.event!=IslandEvent.NOTIFICATION) {
            timer=scope.launch { delay(item.statusDurationMillis);if(state.value.item?.key==item.key)fallback() };return
        }
        if(!s.expanded)return
        timer=scope.launch { delay((settings.flow.value.seconds*1000).toLong()); collapse() }
    }
    fun collapse() {
        setFocusable(false)
        val item=state.value.item ?: return fallback()
        if(item.call && !item.incoming) display(item,false)
        else if(item.activity!=null || item.media?.playing==true && live.active().any { it.key==item.key }) display(item,false)
        else fallback()
    }
    private fun fallback() {
        timer?.cancel();setFocusable(false)
        val item=candidates().firstOrNull()
        val expanded=item?.call==true && item.incoming
        state.value=IslandState(item,expanded,false,state.value.generation+1,if(!expanded) secondaryFor(item) else null,swapGeneration)
    }

    fun dismiss() {
        val item=state.value.item ?: return
        if(item.media!=null || item.activity!=null) live.hide(item.key)
        // Swipe hides only the presentation; never cancels a call or notification.
        if(item.call) calls.remove(item.key)
        fallback()
    }
    fun removed(key: String) {
        calls.remove(key);live.remove(key)
        if(state.value.item?.key == key) fallback() else refreshSecondary()
    }
    fun tap(revealActions: Boolean=false) {
        val s=state.value;val item=s.item ?: return
        if(revealActions && !s.replying) { state.value=s.copy(details=true,expanded=true);schedule();return }
        if(item.event==IslandEvent.LOW_BATTERY) { openBatterySaver();return }
        if((item.media != null || item.activity!=null || (item.call && !item.incoming)) && !s.expanded) display(item,true)
        else if(!s.replying) { sendIntent { item.intent?.send() }; collapse() }
    }
    fun reply() { timer?.cancel();state.value=state.value.copy(replying=true,secondary=null);setFocusable(true) }
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
    fun openBatterySaver() {
        sendIntent { context.startActivity(Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) };collapse()
    }
    fun openAudioOutput() {
        // Android owns the list of actual connected output devices.
        runCatching { context.startActivity(Intent(android.provider.Settings.Panel.ACTION_VOLUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast("Open Sound settings to choose audio output") }
    }
    fun toast(text: String) { Toast.makeText(context,text,Toast.LENGTH_SHORT).show() }
    fun preview(kind: String = "message") {
        val base=IslandItem("preview","preview","Messages","Ama","See you in a few minutes. I’m bringing the camera.")
        if(kind in setOf("timer","stopwatch","recording","navigation")) {
            val activityKind=when(kind) { "timer" -> ActivityKind.TIMER;"stopwatch" -> ActivityKind.STOPWATCH;"recording" -> ActivityKind.RECORDING;else -> ActivityKind.NAVIGATION }
            val now=SystemClock.elapsedRealtime()
            val item=base.copy(key="preview-$kind",pkg="preview.$kind",app=kind.replaceFirstChar { it.uppercase() },title=when(kind) { "timer" -> "Timer";"stopwatch" -> "Stopwatch";"recording" -> "Voice recording";else -> "Head south" },text=if(kind=="navigation")"Continue 300 m" else "",
                activity=LiveActivity(activityKind,if(kind=="navigation")null else if(kind=="timer")now+331000 else now-19000,kind=="timer",if(kind=="navigation")"300 m" else ""))
            post(item);scope.launch { delay(12000);removed(item.key) };return
        }
        if(kind=="simultaneous") {
            val now=SystemClock.elapsedRealtime()
            val music=base.copy(key="preview-music-dual",pkg="preview.music",app="Music",title="A little closer",text="Evening Sessions",media=MediaInfo(null,222000,84000,now,1f,true))
            val nav=base.copy(key="preview-nav-dual",pkg="preview.maps",app="Maps",title="Head south",text="Continue 300 m",activity=LiveActivity(ActivityKind.NAVIGATION,value="300 m"))
            post(music);post(nav);scope.launch { delay(15000);removed(music.key);removed(nav.key) };return
        }
        if(kind in setOf("unlock","ring","vibrate","headphones","hotspot","dnd")) {
            val event=when(kind) { "unlock" -> IslandEvent.UNLOCK;"ring","vibrate" -> IslandEvent.RINGER;"headphones" -> IslandEvent.HEADPHONES;"hotspot" -> IslandEvent.HOTSPOT;else -> IslandEvent.DND }
            post(base.copy(key="preview-system-$kind",event=event,title=when(kind) { "unlock" -> "Unlocked";"ring" -> "Ring";"vibrate" -> "Silent";"headphones" -> "Headphones";"hotspot" -> "Hotspot";else -> "Do Not Disturb" },text=if(kind=="unlock")"" else if(kind=="vibrate")"Vibrate" else "On"));return
        }
        display(when(kind){"call"->base.copy(app="Phone",call=true,incoming=true);"ongoing"->base.copy(app="Phone",call=true,incoming=false,callStartedAtElapsed=SystemClock.elapsedRealtime()-42000);"media"->base.copy(app="Music",title="A little closer",text="Evening Sessions",media=MediaInfo(null,222000,84000,SystemClock.elapsedRealtime(),1f,true),accent=0xffbf5af2.toInt());"music-pill"->base.copy(app="Music",title="A little closer",text="Evening Sessions",media=MediaInfo(null,222000,84000,SystemClock.elapsedRealtime(),1f,true),accent=0xffbf5af2.toInt());"charging"->base.copy(title="Charging",text="82%",event=IslandEvent.CHARGING);"battery"->base.copy(title="Low Battery",text="20%",event=IslandEvent.LOW_BATTERY);"large"->base.copy(text="See you in a few minutes. I am bringing the camera and everything we need for the photoshoot this afternoon.");"code"->base.copy(app="Verification",title="Verification code",text="Your code is 482916",code="482916");else->base},kind!="ongoing" && kind!="music-pill")
        if(kind in setOf("call","ongoing","media","music-pill")) timer=scope.launch { delay(6000);removed("preview") }
    }
}
