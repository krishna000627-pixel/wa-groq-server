package com.aria.reply

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.app.RemoteInput

class AriaTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val results: Bundle? = RemoteInput.getResultsFromIntent(intent)
        val reply = results?.getCharSequence(AriaTestNotification.RESULT_KEY)?.toString().orEmpty()
        val store = AriaStore(context)
        if (reply.isNotBlank()) {
            store.lastReply = reply
            store.lastError = ""
            store.logEvent("Synthetic RemoteInput reply received by Aria test receiver", true)
        } else {
            store.lastError = "Synthetic RemoteInput returned no reply"
            store.logEvent("Synthetic RemoteInput receiver got no text", false)
        }
        context.getSystemService(NotificationManager::class.java)?.cancel(AriaTestNotification.NOTIFICATION_ID)
    }
}
