package com.aria.reply;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class AriaStore {
    private final SharedPreferences p;
    public AriaStore(Context c) { p = c.getSharedPreferences("aria_v30", Context.MODE_PRIVATE); }

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
            while (a.length() > 1000) a.remove(0);
            put("messages", a.toString());
            put("last_capture", contact + ": " + text);
        } catch (Exception ignored) {}
    }

    public synchronized String context(String contact, int max) {
        try {
            JSONArray a = new JSONArray(get("messages", "[]"));
            StringBuilder b = new StringBuilder();
            int count = 0;
            for (int i = a.length() - 1; i >= 0 && count < max; i--) {
                JSONObject o = a.getJSONObject(i);
                if (contact.equals(o.optString("contact"))) {
                    if (b.length() > 0) b.insert(0, "\n");
                    b.insert(0, o.optString("role") + ": " + o.optString("text"));
                    count++;
                }
            }
            return b.toString().trim();
        } catch (Exception e) { return ""; }
    }

    public synchronized JSONArray contacts() {
        JSONArray out = new JSONArray();
        try {
            JSONArray a = new JSONArray(get("messages", "[]"));
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < a.length(); i++) {
                String c = a.getJSONObject(i).optString("contact");
                if (!c.isEmpty() && seen.add(c)) out.put(c);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public synchronized int messageCount(String contact) {
        int n = 0;
        try {
            JSONArray a = new JSONArray(get("messages", "[]"));
            for (int i = 0; i < a.length(); i++) if (contact.equals(a.getJSONObject(i).optString("contact"))) n++;
        } catch (Exception ignored) {}
        return n;
    }

    public synchronized void rememberOutgoing(String contact, String text) {
        try {
            JSONArray a = new JSONArray(get("outgoing", "[]"));
            JSONObject o = new JSONObject(); o.put("contact", contact); o.put("text", text); o.put("time", System.currentTimeMillis());
            a.put(o);
            while (a.length() > 40) a.remove(0);
            put("outgoing", a.toString());
        } catch (Exception ignored) {}
    }

    public synchronized boolean isRecentOutgoing(String contact, String text) {
        try {
            JSONArray a = new JSONArray(get("outgoing", "[]"));
            long now = System.currentTimeMillis();
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject o = a.getJSONObject(i);
                if (now - o.optLong("time") > 120000) continue;
                if (contact.equals(o.optString("contact")) && text.equals(o.optString("text"))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public synchronized void addAction(String contact, String title, String detail) {
        try {
            JSONArray a = new JSONArray(get("actions", "[]"));
            JSONObject o = new JSONObject();
            o.put("id", System.currentTimeMillis());
            o.put("contact", contact);
            o.put("title", title);
            o.put("detail", detail);
            o.put("done", false);
            o.put("time", System.currentTimeMillis());
            a.put(o);
            put("actions", a.toString());
        } catch (Exception ignored) {}
    }

    public synchronized String actions() { return get("actions", "[]"); }

    public synchronized int pendingActions() {
        int n = 0;
        try {
            JSONArray a = new JSONArray(actions());
            for (int i = 0; i < a.length(); i++) if (!a.getJSONObject(i).optBoolean("done")) n++;
        } catch (Exception ignored) {}
        return n;
    }

    public synchronized void completeAction(long id) {
        try {
            JSONArray a = new JSONArray(actions());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optLong("id") == id) o.put("done", true);
            }
            put("actions", a.toString());
        } catch (Exception ignored) {}
    }
}
