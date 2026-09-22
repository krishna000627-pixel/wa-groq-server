package com.aria.reply;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;

public class Contacts {
    public static String resolve(Context c,String name){
        try{
            Cursor cur=c.getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME},
                ContactsContract.Contacts.DISPLAY_NAME+" = ?",
                new String[]{name},null);
            if(cur!=null){try{if(cur.moveToFirst())return cur.getString(0);}finally{cur.close();}}
        }catch(Exception ignored){}
        return name;
    }
}
