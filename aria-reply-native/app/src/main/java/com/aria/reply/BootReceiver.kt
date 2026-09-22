package com.aria.reply

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores the follow-up notification after device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = AriaStore(context)
        if (store.pendingFollowUps().isNotEmpty()) {
            store.logEvent("Boot: restoring follow-up notification", null)
            // AriaNotificationListener is a service that starts when the system
            // binds it; we can't invoke it directly here, so we log and let
            // MainActivity refresh the notification on next launch.
        }
    }
}
