package com.aria.reply;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;

public class Contacts {
    public static boolean granted(Context c) {
        return android.os.Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public static String resolve(Context c, String name) {
        if (TextUtils.isEmpty(name) || !granted(c)) return name;
        String normalized = name.trim();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME},
                ContactsContract.Contacts.DISPLAY_NAME + " = ?",
                new String[]{normalized}, null);
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {} finally { if (cur != null) cur.close(); }
        return normalized;
    }
}
