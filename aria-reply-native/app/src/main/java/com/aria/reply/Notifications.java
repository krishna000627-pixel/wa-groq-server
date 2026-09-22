package com.aria.reply;

import android.app.*;
import android.content.Context;
import android.os.Build;

public class Notifications {
    public static final String CH = "aria_tasks";
    public static final int TASK_ID = 7001;
    public static final int SUMMARY_ID = 7002;

    private static NotificationManager manager(Context c) { return (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE); }
    private static void channel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) manager(c).createNotificationChannel(new NotificationChannel(CH, "Aria Follow-ups", NotificationManager.IMPORTANCE_DEFAULT));
    }

    public static void task(Context c, AriaStore s) {
        channel(c);
        int count = s.pendingActions();
        if (count <= 0) { clear(c); return; }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
         .setContentTitle("Aria Follow-ups")
         .setContentText(count + " pending action" + (count == 1 ? "" : "s") + " — open Aria to check")
         .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true);
        manager(c).notify(TASK_ID, b.build());
    }

    public static void summary(Context c, String contact, String reply, AriaStore s) {
        if (!s.bool("hide_raw", false)) return;
        channel(c);
        String text = reply == null ? "New Aria response" : reply.trim();
        if (text.length() > 180) text = text.substring(0, 177) + "...";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
         .setContentTitle("Aria • " + contact)
         .setContentText(text)
         .setStyle(new Notification.BigTextStyle().bigText(reply))
         .setAutoCancel(true).setOnlyAlertOnce(true);
        manager(c).notify(SUMMARY_ID, b.build());
    }

    public static void clear(Context c) { manager(c).cancel(TASK_ID); }
}
