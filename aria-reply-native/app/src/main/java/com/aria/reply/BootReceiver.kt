package com.aria.reply

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores the follow-up notification after device reboot, and — on both boot and
 * the periodic watchdog alarm scheduled from [MainActivity] — nudges Android to
 * rebind the notification listener. This is the "die catcher": some OEM ROMs
 * silently unbind listener services under aggressive battery management, which
 * otherwise causes Aria to stop capturing messages with no visible error.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = AriaStore(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (store.pendingFollowUps().isNotEmpty()) {
                    store.logEvent("Boot: restoring follow-up notification", null)
                }
                AriaNotificationListener.rebind(context)
            }
            ACTION_WATCHDOG -> AriaNotificationListener.rebind(context)
        }
    }

    companion object {
        const val ACTION_WATCHDOG = "com.aria.reply.WATCHDOG"
    }
}
