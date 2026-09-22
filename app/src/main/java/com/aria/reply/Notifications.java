package com.aria.reply;

import android.app.*;
import android.content.*;
import android.os.Build;

public class Notifications {
    public static final String CH_TASKS   = "aria_tasks";
    public static final String CH_SUMMARY = "aria_summary";
    public static final int    ID_TASKS   = 7001;
    public static final int    ID_SUMMARY = 7002;

    private static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = nm(c);
        nm.createNotificationChannel(new NotificationChannel(
                CH_TASKS, "Aria Follow-ups", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(
                CH_SUMMARY, "Aria Summary", NotificationManager.IMPORTANCE_LOW));
    }

    private static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /** Post/update persistent follow-up notification. */
    public static void task(Context c, AriaStore store) {
        ensureChannels(c);
        int n = store.pendingActionCount();
        if (n == 0) { clearTasks(c); return; }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_TASKS)
                : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
         .setContentTitle("Aria Follow-ups")
         .setContentText(n + " pending action" + (n == 1 ? "" : "s") + " — open Aria to review.")
         .setOngoing(true)
         .setAutoCancel(false);
        nm(c).notify(ID_TASKS, b.build());
    }

    public static void clearTasks(Context c) {
        nm(c).cancel(ID_TASKS);
    }

    /** Replace raw WhatsApp notification stream with an Aria summary. */
    public static void summary(Context c, String sender, String reply) {
        ensureChannels(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_SUMMARY)
                : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_email)
         .setContentTitle("Aria → " + sender)
         .setContentText(reply)
         .setAutoCancel(true);
        nm(c).notify(ID_SUMMARY, b.build());
    }
}
