package com.gr4nt.dynamicisland

import android.app.Notification
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap

/** Dialers expose caller pictures through either CallStyle or the large notification icon. */
internal object CallPhoto {
    @Suppress("DEPRECATION")
    fun resolve(context: Context, notification: Notification, name: String = ""): Bitmap? {
        fun load(icon: Icon?): Bitmap? = runCatching {
            icon?.loadDrawable(context)?.toBitmap(128,128,Bitmap.Config.ARGB_8888)
                ?.copy(Bitmap.Config.ARGB_8888,false)
        }.getOrNull()
        val caller=notification.extras.getParcelable<Person>("android.callPerson")
        ContactPhoto.find(context,caller?.uri,name)?.let { return it }
        load(caller?.icon)?.let { return it }
        notification.extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)
            ?.firstOrNull { it.icon!=null }?.let { load(it.icon)?.let { photo -> return photo } }
        load(notification.getLargeIcon())?.let { return it }
        return runCatching {
            notification.extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)
                ?.copy(Bitmap.Config.ARGB_8888,false)
        }.getOrNull()
    }
}
