package com.aria.reply;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class AriaStore {
    private final SharedPreferences p;
    public AriaStore(Context c) { p = c.getSharedPreferences("aria_v28", Context.MODE_PRIVATE); }

    public void put(String k, String v) { p.edit().putString(k, v).apply(); }
    public String get(String k, String d) { return p.getString(k, d); }
    public void bool(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }
    public boolean bool(String k, boolean d) { return p.getBoolean(k, d); }

    public synchronized void addMessage(String contact, String role, String text) {
        try {
            JSONArray a = new JSONArray(get("messages", "[]"));
            JSONObject o = new JSONObject();
            o.put("contact", contact);
            o.put("role", role);
            o.put("text", text);
            o.put("time", System.currentTimeMillis());
            a.put(o);
            while (a.length() > 300) a.remove(0);
            put("messages", a.toString());
        } catch (Exception ignored) {}
    }

    public synchronized String context(String contact, int max) {
        try {
            JSONArray a = new JSONArray(get("messages", "[]"));
            StringBuilder b = new StringBuilder();
            int count = 0;
            for (int i=a.length()-1; i>=0 && count<max; i--) {
                JSONObject o=a.getJSONObject(i);
                if (contact.equals(o.optString("contact"))) {
                    b.insert(0, o.optString("role")+": "+o.optString("text")+"\n");
                    count++;
                }
            }
            return b.toString().trim();
        } catch(Exception e) { return ""; }
    }

    public synchronized void addAction(String contact, String title, String detail) {
        try {
            JSONArray a = new JSONArray(get("actions", "[]"));
            JSONObject o=new JSONObject();
            o.put("id", System.currentTimeMillis());
            o.put("contact", contact);
            o.put("title", title);
            o.put("detail", detail);
            o.put("done", false);
            a.put(o);
            put("actions", a.toString());
        } catch(Exception ignored) {}
    }

    public synchronized String actions() { return get("actions","[]"); }

    public synchronized void completeAction(long id) {
        try {
            JSONArray a=new JSONArray(get("actions","[]"));
            for(int i=0;i<a.length();i++) {
                JSONObject o=a.getJSONObject(i);
                if(o.optLong("id")==id) o.put("done",true);
            }
            put("actions",a.toString());
        } catch(Exception ignored) {}
    }
}
