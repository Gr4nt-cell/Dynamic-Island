package com.gr4nt.dynamicisland

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class ContactLookupTest {
    @Test fun findsSavedPhotoWithoutNotificationArtwork() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS)
        val resolver=context.contentResolver
        var raw: android.net.Uri?=null
        try {
            raw=resolver.insert(ContactsContract.RawContacts.CONTENT_URI,ContentValues().apply {
                putNull(ContactsContract.RawContacts.ACCOUNT_TYPE);putNull(ContactsContract.RawContacts.ACCOUNT_NAME)
            })!!
            val id=ContentUris.parseId(raw)
            fun data(mime: String, values: ContentValues) {
                values.put(ContactsContract.Data.RAW_CONTACT_ID,id)
                values.put(ContactsContract.Data.MIMETYPE,mime)
                resolver.insert(ContactsContract.Data.CONTENT_URI,values)
            }
            val name="Island Photo Test 982456"
            data(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,ContentValues().apply {
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,name)
            })
            val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val bytes=ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
            data(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,ContentValues().apply {
                put(ContactsContract.CommonDataKinds.Photo.PHOTO,bytes)
            })
            val photo=ContactPhoto.find(context,null,name)
            assertNotNull("Saved contact photo should be found without notification artwork",photo)
            val pixel=photo!!.getPixel(photo.width/2,photo.height/2)
            assertTrue(Color.blue(pixel)>200 && Color.red(pixel)<40 && Color.green(pixel)<40)
        } finally {
            raw?.let { resolver.delete(it,null,null) }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
