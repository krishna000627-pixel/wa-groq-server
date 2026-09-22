package com.aria.reply;
import android.app.*;
import android.content.*;
import android.os.Build;

public class Notifications {
    public static final String CH="aria_tasks";
    public static void task(Context c,AriaStore s){
        NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)n.createNotificationChannel(new NotificationChannel(CH,"Aria Follow-ups",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CH):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Aria Follow-ups")
         .setContentText("You have pending actions. Open Aria to check them.")
         .setOngoing(true).setAutoCancel(false);
        n.notify(7001,b.build());
    }
    public static void clear(Context c){
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(7001);
    }
}
