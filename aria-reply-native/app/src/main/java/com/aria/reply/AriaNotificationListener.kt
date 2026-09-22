package com.aria.reply

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors
import kotlin.random.Random

class AriaNotificationListener : NotificationListenerService() {
    private val executor = Executors.newSingleThreadExecutor()
    private val seen = LinkedHashMap<String, Long>()
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

        val key = "${sbn.key}|$title|$text"
        val now = System.currentTimeMillis()
        synchronized(seen) {
            if (seen[key]?.let { now - it < 12_000 } == true) return
            seen[key] = now
            if (seen.size > 100) seen.remove(seen.keys.first())
        }

        store.lastCapture = "$title: $text"
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

        executor.execute {
            val result = AriaApi.generate(store, title, text)
            if (!result.ok || result.reply.isBlank()) {
                store.lastError = "${result.error} (HTTP ${result.code})"
                store.logEvent("AI generation failed: HTTP ${result.code}", false)
                return@execute
            }
            val min = store.minDelay
            val max = maxOf(store.maxDelay, min)
            val delay = if (max == min) min else Random.nextInt(min, max + 1)
            if (delay > 0) Thread.sleep(delay * 1000L)
            val reply = store.marker + result.reply
            val results = Bundle().apply { putCharSequence(remoteInput.resultKey, reply) }
            val fillIn = Intent().apply { RemoteInput.addResultsToIntent(action.remoteInputs, this, results) }
            val sent = runCatching { action.actionIntent.send(this, 0, fillIn); true }.getOrDefault(false)
            if (sent) {
                store.lastReply = reply
                store.lastError = ""
                store.logEvent("Reply delivered through RemoteInput", true)
            } else {
                store.lastError = "RemoteInput PendingIntent could not be sent"
                store.logEvent("RemoteInput reply send failed", false)
            }
        }
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

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
