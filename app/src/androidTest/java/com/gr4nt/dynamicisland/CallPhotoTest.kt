package com.gr4nt.dynamicisland

import android.app.Notification
import android.app.Person
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class CallPhotoTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun photo(color: Int)=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    @Test fun legacyDialerLargePhotoIsUsed() {
        val notification=Notification.Builder(context,"test").setCategory(Notification.CATEGORY_CALL)
            .setLargeIcon(photo(Color.RED)).build()
        assertEquals(Color.RED,CallPhoto.resolve(context,notification)!!.getPixel(64,64))
    }
    @Test fun callerPhotoTakesPriorityOverLargeIcon() {
        val notification=Notification.Builder(context,"test").setLargeIcon(photo(Color.RED)).build()
        notification.extras.putParcelable("android.callPerson",Person.Builder().setName("Gloria")
            .setIcon(Icon.createWithBitmap(photo(Color.BLUE))).build())
        assertEquals(Color.BLUE,CallPhoto.resolve(context,notification)!!.getPixel(64,64))
    }
    @Test fun missingPhotoLeavesInitialFallbackAvailable() {
        assertNull(CallPhoto.resolve(context,Notification.Builder(context,"test").build()))
    }
    @Test fun unreadableCallerIconFallsBackToLargePhoto() {
        val notification=Notification.Builder(context,"test").setLargeIcon(photo(Color.GREEN)).build()
        notification.extras.putParcelable("android.callPerson",Person.Builder().setName("Gloria")
            .setIcon(Icon.createWithContentUri("content://unavailable/photo")).build())
        assertEquals(Color.GREEN,CallPhoto.resolve(context,notification)!!.getPixel(64,64))
    }
}
