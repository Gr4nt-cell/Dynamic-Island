package com.gr4nt.dynamicisland

import android.app.Notification
import android.app.PendingIntent

internal object CallActions {
    @Suppress("DEPRECATION")
    fun from(notification: Notification, actions: List<IslandAction>): List<IslandAction> {
        val canonical=listOf("android.answerIntent" to "accept", "android.declineIntent" to "decline", "android.hangUpIntent" to "decline")
            .mapNotNull { (key,kind) ->
                val intent=notification.extras.getParcelable<PendingIntent>(key) ?: return@mapNotNull null
                IslandAction(if(kind=="accept")"Answer" else "End call",intent,null,explicitKind=kind)
            }.distinctBy { it.kind }
        return canonical + actions.filterNot { action -> canonical.any { it.kind==action.kind } }
    }
}
