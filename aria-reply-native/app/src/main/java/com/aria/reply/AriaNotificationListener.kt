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
        if (!store.autoReply) return

        val action = notification.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.allowFreeFormInput } == true
        } ?: return
        val remoteInput = action.remoteInputs?.firstOrNull() ?: return

        executor.execute {
            val result = AriaApi.generate(store, title, text)
            if (!result.ok || result.reply.isBlank()) return@execute
            val min = store.minDelay
            val max = maxOf(store.maxDelay, min)
            val delay = if (max == min) min else Random.nextInt(min, max + 1)
            if (delay > 0) Thread.sleep(delay * 1000L)
            val reply = store.marker + result.reply
            val results = Bundle().apply { putCharSequence(remoteInput.resultKey, reply) }
            val fillIn = Intent().apply { RemoteInput.addResultsToIntent(action.remoteInputs, this, results) }
            runCatching { action.actionIntent.send(this, 0, fillIn) }
            store.lastReply = reply
        }
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
