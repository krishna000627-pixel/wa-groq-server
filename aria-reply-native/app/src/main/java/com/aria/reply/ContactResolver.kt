package com.aria.reply

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

/** Resolves a WhatsApp sender display name against device contacts. */
object ContactResolver {

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Looks up [rawName] in device contacts and returns the best match display name,
     * or [rawName] unchanged if nothing matches.
     */
    fun resolve(ctx: Context, rawName: String): String {
        if (!hasPermission(ctx)) return rawName
        return try {
            val uri = ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            // Fuzzy: strip common WA suffixes like " ~ Business" and do a LIKE match
            val clean = rawName.substringBefore(" ~ ").trim()
            ctx.contentResolver.query(
                uri,
                projection,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
                arrayOf("%$clean%"),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                if (cursor.moveToFirst())
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                else rawName
            } ?: rawName
        } catch (_: Exception) { rawName }
    }
}
