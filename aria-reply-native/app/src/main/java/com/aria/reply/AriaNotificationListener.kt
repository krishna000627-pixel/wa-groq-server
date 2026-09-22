package com.aria.reply

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class AriaNotificationListener : NotificationListenerService() {
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val seen = LinkedHashMap<String, Long>()
    private val pendingReplies = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private lateinit var store: AriaStore

    override fun onCreate() { super.onCreate(); store = AriaStore(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val synthetic = pkg == packageName && sbn.notification.extras?.getBoolean("aria_synthetic_test", false) == true
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b" && !synthetic) return

        val notification = sbn.notification ?: return
        val extras = notification.extras
        val title = extras.getString("android.title")?.trim().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
        if (title.isBlank() || text.isBlank()) return

        // WhatsApp also posts our own sent messages. Never treat those as new user input.
        // Without this guard Aria can reply to its own reply and create a reply loop.
        if (title.equals("You", ignoreCase = true)) {
            store.logEvent("Ignored WhatsApp outgoing message from self", null)
            return
        }

        val key = "$pkg|$title|$text"
        val now = System.currentTimeMillis()
        synchronized(seen) {
            if (seen[key]?.let { now - it < 12_000 } == true) return
            seen[key] = now
            if (seen.size > 100) seen.remove(seen.keys.first())
        }

        store.lastCapture = "$title: $text"
        store.addConversationMessage(title, "user", text)
        store.logEvent("Captured ${if (synthetic) "synthetic" else "WhatsApp"} notification from $title", null)

        val action = notification.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        if (action == null) {
            store.lastTargetReady = false
            store.lastTargetDescription = "Notification captured, but no RemoteInput reply action was exposed."
            store.lastTargetPackage = pkg
            store.lastTargetAt = now
            if (!store.autoReply && !synthetic) return
            return
        }

        val remoteInput = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return
        store.lastTargetReady = true
        store.lastTargetDescription = "$pkg • ${action.title ?: "Reply"} • ${remoteInput.resultKey}"
        store.lastTargetPackage = pkg
        store.lastTargetAt = now
        lastReplyAction = action
        lastRemoteInput = remoteInput
        store.logEvent("RemoteInput target detected", true)

        if (!store.autoReply && !synthetic) return

        // Burst gate: several messages arriving inside the configured delay window
        // become ONE reply job. All messages are already stored in conversation history,
        // so the eventual generation sees the complete burst as context.
        val conversationKey = title
        if (pendingReplies.containsKey(conversationKey) || inFlight.contains(conversationKey)) {
            store.logEvent("Coalesced message into pending reply for $title", null)
            return
        }

        val delay = if (synthetic) 0L else store.minDelay.coerceAtLeast(0).toLong()
        val future = executor.schedule({
            pendingReplies.remove(conversationKey)
            if (!store.autoReply && !synthetic) return@schedule
            if (!inFlight.add(conversationKey)) return@schedule

            try {
                // Read the latest conversation state at execution time so messages that
                // arrived during the delay are included in the same reply context.
                val history = store.recentContext(title)
                val latest = history.lastOrNull { it.role == "user" } ?: return@schedule
                val context = history.dropLast(1)
                val result = AriaApi.generate(store, title, latest.text, context)
                if (!result.ok || result.reply.isBlank()) {
                    store.lastError = "${result.error} (HTTP ${result.code})"
                    store.logEvent("AI generation failed: HTTP ${result.code}", false)
                    return@schedule
                }

                val reply = store.marker + result.reply
                val results = Bundle().apply { putCharSequence(remoteInput.resultKey, reply) }
                val fillIn = Intent().apply { RemoteInput.addResultsToIntent(action.remoteInputs, this, results) }
                val sent = runCatching { action.actionIntent.send(this, 0, fillIn); true }.getOrDefault(false)
                if (sent) {
                    store.lastReply = reply
                    store.addConversationMessage(title, "assistant", reply)
                    store.lastError = ""
                    store.logEvent("Reply delivered through RemoteInput", true)
                } else {
                    store.lastError = "RemoteInput PendingIntent could not be sent"
                    store.logEvent("RemoteInput reply send failed", false)
                }
            } finally {
                inFlight.remove(conversationKey)
            }
        }, delay, TimeUnit.SECONDS)
        pendingReplies[conversationKey] = future
    }

    companion object {
        @Volatile private var lastReplyAction: android.app.Notification.Action? = null
        @Volatile private var lastRemoteInput: RemoteInput? = null

        fun sendDirectReply(context: android.content.Context, reply: String): Boolean {
            val action = lastReplyAction ?: return false
            val remoteInput = lastRemoteInput ?: return false
            return runCatching {
                val results = Bundle().apply { putCharSequence(remoteInput.resultKey, reply) }
                val fillIn = Intent().apply { RemoteInput.addResultsToIntent(action.remoteInputs, this, results) }
                action.actionIntent.send(context, 0, fillIn)
                AriaStore(context).logEvent("Direct RemoteInput action invoked", true)
                true
            }.getOrElse {
                AriaStore(context).logEvent("Direct RemoteInput action failed: ${it.message}", false)
                false
            }
        }
    }

    override fun onDestroy() { pendingReplies.values.forEach { it.cancel(false) }; pendingReplies.clear(); inFlight.clear(); executor.shutdownNow(); super.onDestroy() }
}
