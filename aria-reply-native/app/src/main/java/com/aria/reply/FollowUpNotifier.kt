package com.aria.reply

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Manages the persistent "Aria follow-up(s) pending" reminder notification.
 *
 * This used to live only inside [AriaNotificationListener], which meant marking a task
 * done or clearing completed tasks from the Follow-ups screen (in [MainActivity], not the
 * service) never touched the notification — it stuck around showing stale commitments
 * even after they were completed. Both places now call [refresh] with just a [Context],
 * so the reminder always reflects the current pending list, no matter which side changed it.
 */
object FollowUpNotifier {
    private const val CHANNEL_FOLLOWUP = "aria_followup"
    private const val NOTIF_ID_FOLLOWUP = 9911

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_FOLLOWUP) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_FOLLOWUP, "Aria Follow-ups", NotificationManager.IMPORTANCE_LOW))
        }
    }

    /** Rebuilds the reminder from the current pending list. Cancels it entirely once
     * nothing is pending. Safe to call from anywhere, anytime the pending list changes. */
    fun refresh(context: Context) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val pending = AriaStore(context).pendingFollowUps()
        if (pending.isEmpty()) { nm.cancel(NOTIF_ID_FOLLOWUP); return }

        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { putExtra("openScreen", 7) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = pending.joinToString("; ") { "${it.sender}: ${it.commitment}" }.take(200)
        val n = Notification.Builder(context, CHANNEL_FOLLOWUP)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${pending.size} Aria follow-up${if (pending.size > 1) "s" else ""} pending")
            .setContentText(summary)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        nm.notify(NOTIF_ID_FOLLOWUP, n)
    }
}
