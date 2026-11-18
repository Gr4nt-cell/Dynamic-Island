package com.gr4nt.dynamicisland

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Called only for an explicit tap, granting the dialer the user's activity-launch privilege. */
internal object NotificationActionSender {
    fun send(context: Context, intent: PendingIntent, fillIn: Intent? = null): Boolean = try {
        val options=if(Build.VERSION.SDK_INT>=34) ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle() else null
        intent.send(context,0,fillIn,null,null,null,options)
        true
    } catch (_: PendingIntent.CanceledException) { false }
      catch (_: SecurityException) { false }
}
