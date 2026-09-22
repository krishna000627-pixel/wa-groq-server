package com.aria.reply;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.*;
import android.app.RemoteInput;
import java.util.*;
import org.json.JSONObject;

public class AriaNotificationListener extends NotificationListenerService {
    public static final String WA = "com.whatsapp";
    private static final long  BURST_DELAY_MS = 1800;

    private static AriaNotificationListener instance;
    private AriaStore store;
    private Handler   h;

    // burst accumulation: contact → list of message texts
    private final Map<String, ArrayList<String>> burst    = new HashMap<>();
    // dedup: contact → last notification key+text combo
    private final Map<String, String>            lastKeys = new HashMap<>();

    public static boolean live() { return instance != null; }

    // ── lifecycle ────────────────────────────────────────────────────────────
    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        store = new AriaStore(this);
        h = new Handler(Looper.getMainLooper());
    }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }

    // ── incoming notification ────────────────────────────────────────────────
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!WA.equals(sbn.getPackageName())) return;

        Bundle extras = sbn.getNotification().extras;
        String sender = strExtra(extras, Notification.EXTRA_TITLE);
        String text   = strExtra(extras, Notification.EXTRA_TEXT);

        if (sender.isEmpty() || text.isEmpty()) return;

        // ① Never capture Aria's own outbound reply
        if (isAriaSelf(sender)) return;

        // ② Skip WhatsApp group/summary notifications
        if (isSummary(text)) return;

        // ③ Skip our own Aria notification channel IDs so we never loop
        if (isAriaNotification(sbn)) return;

        // ④ Deduplicate by notification key + text
        String dedupeKey = sbn.getKey() + "|" + text;
        if (dedupeKey.equals(lastKeys.get(sender))) return;
        lastKeys.put(sender, dedupeKey);

        // ⑤ Resolve contact identity
        String contact = Contacts.resolve(this, store, sender);

        // ⑥ Store user message (role = "user")
        store.addMessage(contact, "user", text);
        store.put("last_capture", contact + ": " + text);

        // ⑦ Accumulate burst
        burst.computeIfAbsent(contact, k -> new ArrayList<>()).add(text);

        // ⑧ (Re)schedule burst timer — reset on each new message
        h.removeCallbacksAndMessages(contact);
        h.postAtTime(() -> generate(contact),
                contact, SystemClock.uptimeMillis() + BURST_DELAY_MS);

        // ⑨ Optional raw suppression — BEFORE generate so contact is already resolved
        if (store.bool("hide_raw", false)) {
            try { cancelNotification(sbn.getKey()); } catch (Exception ignored) {}
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private String strExtra(Bundle e, String key) {
        CharSequence cs = e.getCharSequence(key);
        return cs == null ? "" : cs.toString().trim();
    }

    private boolean isAriaSelf(String sender) {
        String s = sender.toLowerCase(Locale.US);
        return s.equals("you") || s.equals("aria") || s.equals("aria reply");
    }

    private boolean isSummary(String text) {
        String x = text.toLowerCase(Locale.US);
        return x.matches(".*\\b\\d+\\s+(new\\s+)?messages?\\b.*")
            || x.contains("notifications from")
            || x.equals("checking for new messages");
    }

    private boolean isAriaNotification(StatusBarNotification sbn) {
        // Our own notifications use IDs 7001/7002 posted by Notifications.java
        return sbn.getId() == Notifications.ID_TASKS
            || sbn.getId() == Notifications.ID_SUMMARY;
    }

    // ── burst generation ─────────────────────────────────────────────────────
    private void generate(String contact) {
        ArrayList<String> q = burst.remove(contact);
        if (q == null || q.isEmpty()) return;

        String provider = store.get("provider", "Groq");
        String key      = new SecurePrefs(this).get(
                provider.equals("Gemini") ? "gemini_key" : "groq_key");
        String model    = store.get(
                provider.equals("Gemini") ? "gemini_model" : "groq_model",
                provider.equals("Gemini") ? "gemini-2.0-flash" : "llama-3.1-8b-instant");

        String context = store.context(contact, 16);
        String userMsg = String.join("\n", q);

        String system = "You are Aria, a concise WhatsApp assistant.\n"
            + "Contact: " + contact + ".\n"
            + "IMPORTANT:\n"
            + "- Never claim to have sent something unless instructed to actually send.\n"
            + "- If you promise a future action, phrase the promise clearly.\n"
            + "- Reply only to the user's messages, not to your own prior replies.\n"
            + "CONTEXT (last turns, newest last):\n" + context;

        AriaApi.call(provider, key, model, system, userMsg, (ok, reply, error) -> {
            if (!ok) { store.put("last_error", error); return; }

            // Store assistant reply separately — never re-captured as incoming
            store.addMessage(contact, "assistant", reply);

            // Extract follow-up commitments
            extractAction(contact, reply);

            // Post Aria summary notification if suppression is on
            if (store.bool("hide_raw", false))
                Notifications.summary(this, contact, reply);

            // Send via RemoteInput if auto-reply is on
            if (store.bool("auto_reply", false))
                sendToLatest(contact, reply);
        });
    }

    // ── follow-up commitment detection ───────────────────────────────────────
    private void extractAction(String contact, String reply) {
        String x = reply.toLowerCase(Locale.US);
        boolean hasCommitment =
               x.contains("i'll tell")     || x.contains("i will tell")
            || x.contains("i'll inform")   || x.contains("i will inform")
            || x.contains("i'll send")     || x.contains("i will send")
            || x.contains("i'll remind")   || x.contains("i will remind")
            || x.contains("i'll let")      || x.contains("i will let")
            || x.contains("bata dunga")    || x.contains("inform kar dunga")
            || x.contains("bhej dunga")    || x.contains("remind kar")
            || x.contains("forward kar")   || x.contains("forward karunga")
            || x.contains("bata deta")     || x.contains("bata dunga");
        if (hasCommitment) {
            store.addAction(contact, "Aria follow-up", reply);
            Notifications.task(this, store);
        }
    }

    // ── send via RemoteInput ─────────────────────────────────────────────────
    private void sendToLatest(String contact, String reply) {
        try {
            for (StatusBarNotification s : getActiveNotifications()) {
                if (!WA.equals(s.getPackageName())) continue;
                // Skip our own Aria notifications
                if (isAriaNotification(s)) continue;
                Bundle e = s.getNotification().extras;
                String title = strExtra(e, Notification.EXTRA_TITLE);
                if (!Contacts.resolve(this, store, title).equals(contact)) continue;
                Notification.Action[] actions = s.getNotification().actions;
                if (actions == null) continue;
                for (Notification.Action a : actions) {
                    if (a.getRemoteInputs() == null || a.getRemoteInputs().length == 0) continue;
                    Intent i = new Intent();
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    RemoteInput.addResultsToIntent(a.getRemoteInputs(), i, new Bundle() {{
                        putCharSequence(a.getRemoteInputs()[0].getResultKey(), reply);
                    }});
                    a.actionIntent.send(this, 0, i);
                    return;
                }
            }
        } catch (Exception e) {
            store.put("last_error", "RemoteInput: " + e.getMessage());
        }
    }
}
