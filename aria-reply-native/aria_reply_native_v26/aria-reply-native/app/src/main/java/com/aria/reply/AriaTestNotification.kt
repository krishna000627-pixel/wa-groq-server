package com.aria.reply

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent

object AriaTestNotification {
    const val CHANNEL_ID = "aria_test"
    const val RESULT_KEY = "aria_test_reply"
    const val NOTIFICATION_ID = 2301

    fun post(context: Context, sender: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Aria Test", NotificationManager.IMPORTANCE_HIGH))
        val replyIntent = Intent(context, AriaTestReceiver::class.java).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(context, 2301, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val remoteInput = RemoteInput.Builder(RESULT_KEY).setLabel("Aria reply").setAllowFreeFormInput(true).build()
        val action = Notification.Action.Builder(android.R.drawable.ic_menu_send, "Reply", pending).addRemoteInput(remoteInput).build()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.aria.reply.R.drawable.ic_aria)
            .setContentTitle(sender)
            .setContentText(message)
            .setAutoCancel(true)
            .addExtras(android.os.Bundle().apply { putBoolean("aria_synthetic_test", true) })
            .addAction(action)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
