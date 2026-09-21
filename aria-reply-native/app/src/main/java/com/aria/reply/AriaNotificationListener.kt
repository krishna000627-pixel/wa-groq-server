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

    override fun onCreate() {
        super.onCreate()
        store = AriaStore(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
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
        store.lastError = ""
        store.logEvent("Notification captured from WhatsApp: $title", null)

        val action = notification.actions?.firstOrNull { it.remoteInputs?.any { input -> input.allowFreeFormInput } == true }
        val remoteInput = action?.remoteInputs?.firstOrNull { it.allowFreeFormInput }
        if (action != null && remoteInput != null) {
            lastReplyTarget = ReplyTarget(action.actionIntent, action.remoteInputs, remoteInput.resultKey)
            lastReplyTargetDescription = title
            store.logEvent("Reply action detected for $title", null)
        } else {
            lastReplyTarget = null
            lastReplyTargetDescription = ""
            store.logEvent("Notification captured without a reply action", false)
        }

        if (!store.autoReply) return
        if (action == null || remoteInput == null) return
        if (!store.hasApiKey()) {
            store.lastError = "API key is not configured"
            store.logEvent("Auto Reply blocked: API key not configured", false)
            return
        }

        executor.execute {
            val result = AriaApi.generate(store, title, text)
            if (!result.ok || result.reply.isBlank()) {
                store.lastError = "${result.error.ifBlank { "API request failed" }}${if (result.code > 0) " (HTTP ${result.code})" else ""}"
                store.logEvent("API reply generation failed: ${store.lastError}", false)
                return@execute
            }
            val min = store.minDelay
            val max = maxOf(store.maxDelay, min)
            val delay = if (max == min) min else Random.nextInt(min, max + 1)
            if (delay > 0) Thread.sleep(delay * 1000L)
            val reply = store.marker + result.reply
            val results = Bundle().apply { putCharSequence(remoteInput.resultKey, reply) }
            val fillIn = Intent().apply { RemoteInput.addResultsToIntent(action.remoteInputs, this, results) }
            runCatching { action.actionIntent.send(this, 0, fillIn) }
                .onSuccess {
                    store.lastReply = reply
                    store.lastError = ""
                    store.logEvent("Automatic reply sent to $title", true)
                }
                .onFailure {
                    store.lastError = it.message ?: "RemoteInput reply failed"
                    store.logEvent("RemoteInput reply failed: ${store.lastError}", false)
                }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        lastReplyTarget = null
        super.onDestroy()
    }

    companion object {
        data class ReplyTarget(val intent: android.app.PendingIntent, val inputs: Array<RemoteInput>, val resultKey: String)
        @Volatile var lastReplyTarget: ReplyTarget? = null
        @Volatile var lastReplyTargetDescription: String = ""

        fun sendDirectReply(context: android.content.Context, text: String): Boolean {
            val target = lastReplyTarget ?: return false
            return runCatching {
                val results = Bundle().apply { putCharSequence(target.resultKey, text) }
                val fillIn = Intent().apply { RemoteInput.addResultsToIntent(target.inputs, this, results) }
                target.intent.send(context, 0, fillIn)
                true
            }.getOrDefault(false)
        }
    }
}
