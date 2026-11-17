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
data class IslandItem(
    val key: String, val pkg: String, val app: String, val title: String, val text: String,
    val appIcon: Bitmap? = null, val largeIcon: Bitmap? = null, val intent: PendingIntent? = null,
    val actions: List<IslandAction> = emptyList(), val call: Boolean = false, val incoming: Boolean = false,
    val avatar: Bitmap? = null, val media: MediaInfo? = null, val code: String? = null,
    val accent: Int = 0xffa4d8b4.toInt(), val callStartedAtElapsed: Long? = null
)
