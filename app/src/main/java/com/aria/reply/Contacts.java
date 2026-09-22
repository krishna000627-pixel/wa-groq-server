package com.aria.reply;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;

public class Contacts {

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Resolves a WhatsApp notification sender name against the device contacts.
     * Falls back to the raw name if no match or no permission.
     * Result is cached in AriaStore to avoid repeated cursor queries.
     */
    public static String resolve(Context c, AriaStore store, String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        // Check store cache first
        String cached = store.cachedContact(raw);
        if (!cached.equals(raw)) return cached;

        if (!hasPermission(c)) return raw;
        try {
            // Try exact display-name match
            Cursor cur = c.getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    new String[]{ContactsContract.Contacts.DISPLAY_NAME},
                    ContactsContract.Contacts.DISPLAY_NAME + " = ?",
                    new String[]{raw}, null);
            if (cur != null) {
                try {
                    if (cur.moveToFirst()) {
                        String name = cur.getString(0);
                        if (name != null && !name.isEmpty()) {
                            store.cacheContact(raw, name);
                            return name;
                        }
                    }
                } finally { cur.close(); }
            }
        } catch (Exception ignored) {}
        return raw;
    }
}
