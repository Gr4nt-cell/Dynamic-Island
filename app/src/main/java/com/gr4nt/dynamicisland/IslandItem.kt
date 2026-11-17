package com.gr4nt.dynamicisland

import android.app.*
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.*
import android.os.SystemClock

data class IslandAction(val title: String, val intent: PendingIntent, val icon: Icon?, val inputs: List<RemoteInput> = emptyList()) {
    val reply get() = inputs.any { it.allowFreeFormInput }
    val kind get() = NotificationRules.actionKind(title)
}
data class MediaInfo(val controller: MediaController?, val duration: Long, val position: Long, val updatedAt: Long, val speed: Float, val playing: Boolean) {
    fun elapsed(): Long = (position + if(playing) ((SystemClock.elapsedRealtime()-updatedAt)*speed).toLong() else 0).coerceIn(0,duration.coerceAtLeast(0))
}
/** System activity shown like the iPhone's ring/silent, charging, low-battery and accessory pills. */
data class StatusInfo(val kind: String, val label: String, val value: String = "", val color: Int = 0xFFFFFFFF.toInt(), val level: Int = -1, val action: String? = null,
    /** Live activities: chronometer base on the elapsed-realtime clock; countdown when [countdown]. */
    val chrono: Boolean = false, val baseElapsed: Long = 0, val countdown: Boolean = false)
data class IslandItem(
    val key: String, val pkg: String, val app: String, val title: String, val text: String,
    val appIcon: Bitmap? = null, val largeIcon: Bitmap? = null, val intent: PendingIntent? = null,
    val actions: List<IslandAction> = emptyList(), val call: Boolean = false, val incoming: Boolean = false,
    val avatar: Bitmap? = null, val media: MediaInfo? = null, val code: String? = null,
    val accent: Int = 0xffa4d8b4.toInt(), val callStartedAtElapsed: Long? = null, val status: StatusInfo? = null, val dialing: Boolean = false
)
