package com.aria.reply;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    // ── permission request codes ─────────────────────────────────────────────
    static final int REQ_CONTACTS = 22;
    static final int REQ_NOTIFS   = 23;

    // ── dark cocoa palette ───────────────────────────────────────────────────
    final int cocoa    = Color.rgb(36, 23, 20);
    final int surface  = Color.rgb(73, 48, 38);
    final int surface2 = Color.rgb(90, 58, 47);
    final int cream    = Color.rgb(240, 226, 208);
    final int muted    = Color.rgb(189, 170, 152);
    final int sage     = Color.rgb(168, 181, 138);  // active state
    final int terr     = Color.rgb(201, 122, 90);   // error / off
    final int teal     = Color.rgb(134, 170, 160);  // secondary

    // ── state ────────────────────────────────────────────────────────────────
    LinearLayout root, body;
    AriaStore    store;
    SecurePrefs  secure;
    int page = 0;          // 0=Home 1=Chats 2=Actions 3=Tests 4=Settings
    int prevPage = 0;      // for back-stack (API Lab / settings subflows)

    final String[] tabs = {"⌂  Home", "◉  Chats", "✓  Actions", "⌁  Tests", "⚙  Settings"};

    // ── lifecycle ────────────────────────────────────────────────────────────
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store  = new AriaStore(this);
        secure = new SecurePrefs(this);
        renderHome();
    }

    // ── widget helpers ───────────────────────────────────────────────────────
    TextView tv(String s, float z) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextColor(cream); v.setTextSize(z);
        return v;
    }

    GradientDrawable bg(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(r);
        return g;
    }

    Button btn(String label, String icon, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(icon + "  " + label);
        b.setTextColor(cream); b.setTextSize(14);
        b.setAllCaps(false); b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(bg(surface2, 24));
        b.setPadding(24, 0, 24, 0);
        b.setOnClickListener(l);
        b.setMinHeight(54);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(14, 6, 14, 6);
        b.setLayoutParams(lp);
        return b;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(20, 18, 20, 18);
        c.setBackground(bg(surface, 28));
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(14, 8, 14, 8);
        c.setLayoutParams(p);
        return c;
    }

    /** Build root + scroll + nav bar; body is the scroll target. */
    void base(String title, String sub) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cocoa);

        // header
        LinearLayout top = new LinearLayout(this);
        top.setPadding(22, 28, 22, 14);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView a = tv(title, 25); a.setTypeface(null, 1);
        t.addView(a); t.addView(tv(sub, 12));
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(top);

        // scroll body
        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 4, 0, 12);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        // bottom nav
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(8, 8, 8, 8);
        nav.setBackgroundColor(surface);
        for (int i = 0; i < tabs.length; i++) {
            final int n = i;
            Button b = new Button(this);
            b.setText(tabs[i]);
            b.setTextColor(i == page ? sage : muted);
            b.setAllCaps(false); b.setTextSize(11);
            // selected indicator: subtle sage underline via bg
            if (i == page) {
                GradientDrawable sel = new GradientDrawable();
                sel.setColor(Color.TRANSPARENT);
                sel.setStroke(2, sage);
                sel.setCornerRadius(8);
                b.setBackground(sel);
            } else {
                b.setBackgroundColor(Color.TRANSPARENT);
            }
            b.setOnClickListener(v -> { prevPage = page; page = n; navigate(n); });
            nav.addView(b, new LinearLayout.LayoutParams(0, 58, 1));
        }
        root.addView(nav);
        setContentView(root);
    }

    void navigate(int n) {
        switch (n) {
            case 0: renderHome();     break;
            case 1: renderChats();    break;
            case 2: renderActions();  break;
            case 3: apiLab();         break;
            case 4: renderSettings(); break;
        }
    }

    // ── HOME ─────────────────────────────────────────────────────────────────
    void renderHome() {
        page = 0;
        base("ARIA", "WhatsApp response system • V30");

        // status hero
        LinearLayout hero = card();
        hero.addView(tv("SYSTEM STATUS", 11));
        boolean on = store.bool("auto_reply", false);
        TextView st = tv(on ? "● AUTO REPLY ACTIVE" : "○ AUTO REPLY OFF", 22);
        st.setTextColor(on ? sage : terr);
        hero.addView(st);
        hero.addView(tv("Capture → Burst → Context → AI → Reply", 13));
        body.addView(hero);

        // pending actions badge
        int pending = store.pendingActionCount();
        if (pending > 0) {
            LinearLayout ac = card();
            TextView at = tv("⚑  " + pending + " pending follow-up" + (pending == 1 ? "" : "s"), 16);
            at.setTextColor(terr);
            ac.addView(at);
            ac.setOnClickListener(v -> renderActions());
            body.addView(ac);
        }

        body.addView(btn("Auto Reply", "↯", v -> {
            store.bool("auto_reply", !store.bool("auto_reply", false));
            renderHome();
        }));
        body.addView(btn("Chats & Context", "◉", v -> renderChats()));
        body.addView(btn("Pending Actions", "✓", v -> renderActions()));
        body.addView(btn("API Test Lab", "◇", v -> { prevPage = 0; apiLab(); }));
        body.addView(btn("Run Diagnostics", "⊕", v -> diagnostics()));
        body.addView(btn("Synthetic Capture", "⚡", v -> {
            String c = "Synthetic Contact";
            String m = "Bhai mujhe bhej de";
            store.put("last_capture", c + ": " + m);
            store.addMessage(c, "user", m);
            Toast.makeText(this, "Synthetic message captured", Toast.LENGTH_SHORT).show();
        }));
    }

    // ── CHATS ────────────────────────────────────────────────────────────────
    void renderChats() {
        page = 1;
        base("CHATS", "Contact conversations");
        java.util.List<String> contacts = store.contacts();
        if (contacts.isEmpty()) {
            body.addView(tv("No conversations yet.", 16));
        } else {
            for (String name : contacts) {
                LinearLayout c = card();
                c.addView(tv("◉  " + name, 17));
                String preview = store.lastMessage(name);
                if (preview.length() > 80) preview = preview.substring(0, 80) + "…";
                c.addView(tv(preview, 12));
                final String n = name;
                c.setOnClickListener(v -> chatDetail(n));
                body.addView(c);
            }
        }
        body.addView(btn("API Test Lab", "◇", v -> { prevPage = 1; apiLab(); }));
    }

    /** Chat detail → back goes to Chats (not Home). */
    void chatDetail(String contact) {
        page = 1;
        base(contact, "Conversation context");
        TextView cx = tv(store.context(contact, 30), 13);
        cx.setPadding(18, 18, 18, 18);
        body.addView(cx);
        body.addView(btn("Back to Chats", "‹", v -> renderChats()));
    }

    // ── ACTIONS ──────────────────────────────────────────────────────────────
    void renderActions() {
        page = 2;
        base("ACTIONS", "Persistent Aria commitments");
        try {
            JSONArray a = new JSONArray(store.actions());
            boolean any = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optBoolean("done")) continue;
                any = true;
                long id = o.optLong("id");

                LinearLayout c = card();
                CheckBox cb = new CheckBox(this);
                cb.setText(o.optString("contact") + " • " + o.optString("title"));
                cb.setTextColor(cream); cb.setTextSize(15);
                c.addView(cb);

                String detail = o.optString("detail");
                if (detail.length() > 120) detail = detail.substring(0, 120) + "…";
                c.addView(tv(detail, 12));

                cb.setOnCheckedChangeListener((x, checked) -> {
                    if (checked) {
                        store.completeAction(id);
                        // Clear persistent notification if no more pending
                        if (store.pendingActionCount() == 0)
                            Notifications.clearTasks(this);
                        else
                            Notifications.task(this, store);
                        renderActions();
                    }
                });
                body.addView(c);
            }
            if (!any) body.addView(tv("No pending actions.", 16));
        } catch (Exception ignored) {}
    }

    // ── SETTINGS ─────────────────────────────────────────────────────────────
    void renderSettings() {
        page = 4;
        base("SETTINGS", "Provider • permissions • behaviour");

        // Provider highlight
        String provider = store.get("provider", "Groq");
        LinearLayout ph = card();
        ph.addView(tv("ACTIVE PROVIDER", 11));
        TextView pvt = tv("● " + provider, 20);
        pvt.setTextColor(sage);
        ph.addView(pvt);
        body.addView(ph);

        body.addView(btn("Use Groq", "◈", v -> {
            store.put("provider", "Groq");
            renderSettings();
        }));
        body.addView(btn("Use Gemini", "◈", v -> {
            store.put("provider", "Gemini");
            renderSettings();
        }));
        body.addView(btn("Groq API Key", "◇", v -> keyDialog("Groq")));
        body.addView(btn("Gemini API Key", "◇", v -> keyDialog("Gemini")));
        body.addView(btn("API Test Lab", "▶", v -> { prevPage = 4; apiLab(); }));
        body.addView(btn("Notification Access", "●", v ->
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))));
        body.addView(btn("Contacts Access", "◎", v -> {
            if (Build.VERSION.SDK_INT >= 23)
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        }));
        body.addView(btn("Battery Optimization", "⚡", v -> {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        }));

        boolean hide = store.bool("hide_raw", false);
        body.addView(btn(hide ? "Show Raw WA Notifications (on)" : "Hide Raw WA Notifications",
                "◈", v -> { store.bool("hide_raw", !hide); renderSettings(); }));

        body.addView(btn("Clear API Error Log", "×", v -> {
            store.put("last_error", "");
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
        }));

        String err = store.get("last_error", "");
        if (!err.isEmpty()) {
            LinearLayout ec = card();
            ec.addView(tv("LAST API ERROR", 11));
            TextView et = tv(err, 12); et.setTextColor(terr);
            ec.addView(et);
            body.addView(ec);
        }
    }

    void keyDialog(String prov) {
        EditText e = new EditText(this);
        e.setHint(prov + " API key");
        e.setTextColor(cream); e.setHintTextColor(muted);
        new AlertDialog.Builder(this)
            .setTitle(prov + " API Key")
            .setView(e)
            .setPositiveButton("SAVE", (d, w) -> {
                secure.put(prov.equals("Groq") ? "groq_key" : "gemini_key",
                        e.getText().toString().trim());
                Toast.makeText(this, "Saved securely", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    // ── API TEST LAB ─────────────────────────────────────────────────────────
    /** API Lab → back returns to prevPage (Home, Chats, or Settings). */
    void apiLab() {
        page = 3;
        base("API TEST LAB", "Groq / Gemini diagnostics");

        // Provider selector
        String[] providers = {"Groq", "Gemini"};
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, providers));
        String cur = store.get("provider", "Groq");
        sp.setSelection(cur.equals("Gemini") ? 1 : 0);
        body.addView(sp);

        // Custom sender
        EditText sender = new EditText(this);
        sender.setHint("Sender / contact name");
        sender.setTextColor(cream); sender.setHintTextColor(muted);
        sender.setBackground(bg(surface, 18));
        sender.setPadding(18, 12, 18, 12);
        body.addView(sender);

        // Message
        EditText msg = new EditText(this);
        msg.setHint("Test message");
        msg.setTextColor(cream); msg.setHintTextColor(muted);
        msg.setMinHeight(130); msg.setGravity(Gravity.TOP);
        msg.setBackground(bg(surface, 18));
        msg.setPadding(18, 12, 18, 12);
        body.addView(msg);

        // Context toggle + preview
        CheckBox useCtx = new CheckBox(this);
        useCtx.setText("Include stored context");
        useCtx.setTextColor(cream);
        body.addView(useCtx);

        TextView ctxPreview = tv("Context: (none)", 12);
        ctxPreview.setPadding(18, 6, 18, 6);
        body.addView(ctxPreview);

        useCtx.setOnCheckedChangeListener((x, on) -> {
            String s = sender.getText().toString().trim();
            if (on && !s.isEmpty()) {
                String cx = store.context(s, 8);
                ctxPreview.setText(cx.isEmpty() ? "Context: (no history for this sender)" : cx);
            } else {
                ctxPreview.setText("Context: (none)");
            }
        });

        // Result area
        LinearLayout resultCard = card();
        TextView status = tv("HTTP —", 13);
        status.setTextColor(teal);
        TextView response = tv("Ready", 13);
        resultCard.addView(status);
        resultCard.addView(response);
        body.addView(resultCard);

        body.addView(btn("SEND TEST", "▶", v -> {
            String prov   = sp.getSelectedItem().toString();
            String key    = secure.get(prov.equals("Groq") ? "groq_key" : "gemini_key");
            String model  = store.get(prov.equals("Gemini") ? "gemini_model" : "groq_model",
                    prov.equals("Gemini") ? "gemini-2.0-flash" : "llama-3.1-8b-instant");
            String snd    = sender.getText().toString().trim();
            String cx     = (useCtx.isChecked() && !snd.isEmpty())
                            ? store.context(snd, 8) : "";
            String system = "You are Aria. Reply concisely."
                    + (cx.isEmpty() ? "" : "\nCONTEXT:\n" + cx);

            status.setText("Sending…"); response.setText("");
            AriaApi.call(prov, key, model, system, msg.getText().toString(),
                    (ok, r, e) -> {
                        status.setText(ok ? "HTTP 200 OK [" + prov + "]" : "FAILED [" + prov + "]");
                        status.setTextColor(ok ? sage : terr);
                        response.setText(ok ? r : e);
                        if (!ok) store.put("last_error", e);
                    });
        }));

        body.addView(btn("BACK", "‹", v -> navigate(prevPage)));
    }

    // ── DIAGNOSTICS ──────────────────────────────────────────────────────────
    void diagnostics() {
        page = 3;
        base("DIAGNOSTICS", "Subsystem status");

        // Notification access
        addDiag("Notification listener",
                AriaNotificationListener.live() ? "RUNNING" : "NOT RUNNING — grant access",
                AriaNotificationListener.live());

        // Contacts
        boolean hasCon = Contacts.hasPermission(this);
        addDiag("Contacts permission", hasCon ? "GRANTED" : "DENIED", hasCon);

        // Battery optimization
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        addDiag("Battery optimization ignored", ignoring ? "YES" : "NO — may kill service", ignoring);

        // Provider
        String prov = store.get("provider", "Groq");
        String key  = secure.get(prov.equals("Gemini") ? "gemini_key" : "groq_key");
        addDiag("API provider", prov + " — key " + (key.isEmpty() ? "MISSING" : "set (" + key.length() + " chars)"),
                !key.isEmpty());

        // RemoteInput
        addDiag("RemoteInput", "Uses latest active WA notification", true);

        // Capture state
        String cap = store.get("last_capture", "None");
        addDiag("Last capture", cap, !cap.equals("None"));

        // Context state
        int msgs = 0;
        try { msgs = new org.json.JSONArray(store.get("messages","[]")).length(); }
        catch (Exception ignored) {}
        addDiag("Context messages stored", msgs + " entries", msgs > 0);

        // Follow-up state
        int pending = store.pendingActionCount();
        addDiag("Pending follow-ups", pending + " action" + (pending == 1 ? "" : "s"), pending == 0);

        // Auto reply
        boolean ar = store.bool("auto_reply", false);
        addDiag("Auto reply", ar ? "ENABLED" : "DISABLED", ar);

        // Raw suppression
        boolean hide = store.bool("hide_raw", false);
        addDiag("Raw WA suppression", hide ? "ON (Aria summary replaces)" : "OFF", true);

        // Last API error
        String err = store.get("last_error", "");
        LinearLayout ec = card();
        ec.addView(tv("Last API error", 11));
        TextView et = tv(err.isEmpty() ? "None" : err, 13);
        et.setTextColor(err.isEmpty() ? sage : terr);
        ec.addView(et);
        body.addView(ec);

        body.addView(btn("Synthetic capture", "⚡", v -> {
            String c = "Synthetic Contact";
            String m = "Bhai mujhe bhej de";
            store.put("last_capture", c + ": " + m);
            store.addMessage(c, "user", m);
            diagnostics();
        }));
        body.addView(btn("BACK", "‹", v -> renderHome()));
    }

    private void addDiag(String label, String value, boolean ok) {
        LinearLayout c = card();
        c.addView(tv(label, 11));
        TextView v = tv(value, 14);
        v.setTextColor(ok ? sage : terr);
        c.addView(v);
        body.addView(c);
    }

    // ── permission result ────────────────────────────────────────────────────
    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_CONTACTS) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this,
                    granted ? "Contacts access granted" : "Contacts access denied",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
