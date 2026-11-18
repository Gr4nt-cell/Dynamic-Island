package com.gr4nt.dynamicisland

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract

/** Local, permission-gated lookup. Ambiguous names never borrow another contact's picture. */
internal object ContactPhoto {
    fun find(context: Context, personUri: String?, name: String): Bitmap? {
        if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val resolver=context.contentResolver
            fun photo(uri: Uri): Bitmap? = ContactsContract.Contacts.openContactPhotoInputStream(resolver,uri,true)?.use {
                BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=2 })
            }
            val person=personUri?.let(Uri::parse)
            if(person?.scheme=="content" && person.authority==ContactsContract.AUTHORITY) photo(person)?.let { return it }
            val number=person?.takeIf { it.scheme=="tel" }?.schemeSpecificPart
                ?: name.takeIf { value -> value.count(Char::isDigit)>=3 && value.all { it.isDigit() || it in "+- ()" } }
            val ids=linkedSetOf<Long>()
            if(number!=null) {
                val uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number))
                resolver.query(uri,arrayOf(ContactsContract.PhoneLookup._ID),null,null,null)?.use { cursor ->
                    while(cursor.moveToNext()) ids+=cursor.getLong(0)
                }
            } else if(name.isNotBlank()) {
                resolver.query(ContactsContract.Contacts.CONTENT_URI,arrayOf(ContactsContract.Contacts._ID),
                    "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ? COLLATE NOCASE",arrayOf(name.trim()),null)?.use { cursor ->
                    while(cursor.moveToNext()) ids+=cursor.getLong(0)
                }
            }
            ids.singleOrNull()?.let { photo(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,it)) }
        }.getOrNull()
    }
}
