package com.gr4nt.dynamicisland

import android.app.*
import android.graphics.*
import android.graphics.drawable.*
import android.media.MediaMetadata
import android.media.session.*
import android.os.*
import android.service.notification.*
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.*

class IslandNotificationService : NotificationListenerService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val systemEvents by lazy { SystemIslandEvents(this) }
    private val controllers=mutableMapOf<String,Pair<MediaController,MediaController.Callback>>()
    private val notifications=mutableMapOf<String,StatusBarNotification>()
    private val revisions=mutableMapOf<String,Long>()
    override fun onListenerConnected() {
        IslandController.listenerConnected.value=true
        systemEvents.start()
        // Restore persistent call/media state without replaying all old messages.
        activeNotifications?.forEach { receive(it,true) }
    }
    override fun onListenerDisconnected() { IslandController.listenerConnected.value=false;systemEvents.stop();clear() }
    override fun onNotificationPosted(sbn: StatusBarNotification) { receive(sbn,false) }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        notifications.remove(sbn.key);revisions.remove(sbn.key)
        controllers.remove(sbn.key)?.let { it.first.unregisterCallback(it.second) }
        IslandController.removed(sbn.key)
    }
    private fun receive(sbn: StatusBarNotification, silent: Boolean) {
        val n=sbn.notification
        if(sbn.packageName == packageName || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val call=n.category == Notification.CATEGORY_CALL || n.extras.containsKey("android.callType")
        val media=n.category == Notification.CATEGORY_TRANSPORT || n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        if(n.flags and Notification.FLAG_ONGOING_EVENT != 0 && !call && !media) return
        if(n.category in listOf(Notification.CATEGORY_PROGRESS,Notification.CATEGORY_SERVICE) && !call && !media) return
        notifications[sbn.key]=sbn
        val revision=(revisions[sbn.key] ?: 0)+1;revisions[sbn.key]=revision
        scope.launch {
            val item=withContext(Dispatchers.Default) { runCatching { parse(sbn) }.getOrNull() } ?: return@launch
            var result=item
            if(item.text == "Content hidden by Android" && IslandController.settings.flow.value.rootRecovery) {
                val recovered=IslandController.root.recoverText(sbn.key)
                if(!recovered.isNullOrBlank()) result=item.copy(text=recovered,code=NotificationRules.code(recovered))
            }
            if(revisions[sbn.key] != revision) return@launch
            if(result.call) controllers.remove(sbn.key)?.let { it.first.unregisterCallback(it.second) }
            else attachMedia(sbn)
            IslandController.post(result,silent)
        }
    }
    @Suppress("DEPRECATION")
    private fun token(n: Notification): MediaSession.Token? = n.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
    private fun attachMedia(sbn: StatusBarNotification) {
        val token=token(sbn.notification) ?: return
        val existing=controllers[sbn.key]
        if(existing?.first?.sessionToken == token) return
        existing?.let { it.first.unregisterCallback(it.second) }
        val controller=runCatching { MediaController(this,token) }.getOrNull() ?: return
        val callback=object: MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { notifications[sbn.key]?.let { receive(it,true) } }
            override fun onMetadataChanged(metadata: MediaMetadata?) { notifications[sbn.key]?.let { receive(it,true) } }
            override fun onSessionDestroyed() { notifications.remove(sbn.key);revisions.remove(sbn.key);controllers.remove(sbn.key)?.let { it.first.unregisterCallback(it.second) };IslandController.removed(sbn.key) }
        }
        controller.registerCallback(callback,Handler(mainLooper));controllers[sbn.key]=controller to callback
    }
    @Suppress("DEPRECATION")
    private fun parse(sbn: StatusBarNotification): IslandItem {
        val n=sbn.notification;val e=n.extras;val pkg=sbn.packageName
        val app=runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg,0)).toString() }.getOrDefault(pkg)
        val icon=runCatching { packageManager.getApplicationIcon(pkg).softwareBitmap() }.getOrNull()
        val large=runCatching { n.getLargeIcon()?.loadDrawable(this)?.softwareBitmap() }.getOrNull()
        val actions=n.actions.orEmpty().mapNotNull { a -> a.actionIntent?.let { IslandAction(a.title?.toString().orEmpty(),it,a.getIcon(),a.remoteInputs?.toList().orEmpty()) } }
        var title=e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text=(e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if(text.isBlank()) text=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        val call=n.category == Notification.CATEGORY_CALL || e.containsKey("android.callType")
        val caller=e.getParcelable<Person>("android.callPerson")
        val avatar=runCatching { caller?.icon?.loadDrawable(this)?.softwareBitmap() }.getOrNull()
        if(call && !caller?.name.isNullOrBlank()) title=caller?.name.toString()
        val controller=if(call) null else token(n)?.let { runCatching { MediaController(this,it) }.getOrNull() }
        val metadata=controller?.metadata;val playback=controller?.playbackState
        val isMedia = n.category == Notification.CATEGORY_TRANSPORT || token(n) != null
        val media=if(isMedia && !call) MediaInfo(controller,metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,playback?.position?.coerceAtLeast(0) ?: 0,playback?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),playback?.playbackSpeed ?: 1f,(playback?.state == PlaybackState.STATE_PLAYING || (playback == null && n.flags and Notification.FLAG_ONGOING_EVENT != 0))) else null
        if(media != null) {
            title=metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: title
            text=metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: text
        }
        val cover=runCatching { (metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART))?.copy(Bitmap.Config.ARGB_8888,false) }.getOrNull() ?: large
        if(title.isBlank()) title=app
        if(text.isBlank() && !call && media == null) text="Content hidden by Android"
        val source=if(media != null) cover else icon
        val accent=dominant(source)
        val callType=e.getInt("android.callType",0)
        val incoming=call && (callType == 1 || (callType == 0 && (actions.any { it.kind == "accept" } || actions.size >= 2)))
        val callStart=NotificationRules.callStart(n.`when`,e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),incoming,System.currentTimeMillis(),SystemClock.elapsedRealtime())
        val callIcon=if(call) runCatching {
            getSystemService(android.telecom.TelecomManager::class.java).defaultDialerPackage
                ?.let { packageManager.getApplicationIcon(it).softwareBitmap() }
        }.getOrNull() ?: icon else icon
        return IslandItem(sbn.key,pkg,app,title,text,callIcon,cover,n.contentIntent,actions,call,incoming,avatar,media,NotificationRules.code("$title $text"),accent,callStart)
    }
    private fun Drawable.softwareBitmap(): Bitmap = toBitmap(128,128,Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888,false)
    private fun dominant(bitmap: Bitmap?): Int {
        if(bitmap==null) return 0xffa4d8b4.toInt()
        // Software copy is mandatory before getPixel (notification art can be hardware-backed).
        val b=bitmap.copy(Bitmap.Config.ARGB_8888,false) ?: return 0xffa4d8b4.toInt()
        var red=0L;var green=0L;var blue=0L;var count=0
        for(x in 0 until b.width step 8) for(y in 0 until b.height step 8) {
            val p=b.getPixel(x,y)
            if(Color.alpha(p)>128 && maxOf(Color.red(p),Color.green(p),Color.blue(p))>40) { red+=Color.red(p);green+=Color.green(p);blue+=Color.blue(p);count++ }
        }
        b.recycle()
        return if(count==0) 0xffa4d8b4.toInt() else Color.rgb((red/count).toInt(),(green/count).toInt(),(blue/count).toInt())
    }
    private fun clear() { controllers.values.forEach { it.first.unregisterCallback(it.second) };notifications.keys.toList().forEach { IslandController.removed(it) };controllers.clear();notifications.clear();revisions.clear() }
    override fun onDestroy() { systemEvents.stop();clear();scope.cancel();IslandController.listenerConnected.value=false;super.onDestroy() }
}
