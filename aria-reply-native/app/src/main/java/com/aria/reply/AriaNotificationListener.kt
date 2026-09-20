package com.aria.reply

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors

class AriaNotificationListener : NotificationListenerService() {
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    private val prefs by lazy { getSharedPreferences("aria", MODE_PRIVATE) }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp" || !prefs.getBoolean("auto", false)) return
        val n = sbn.notification ?: return
        val sender = n.extras.getString(Notification.EXTRA_TITLE) ?: return
        val message = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val action = n.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true && it.actionIntent != null } ?: return
        val input = action.remoteInputs!!.first()
        val backend = prefs.getString("backend", "https://wa-groq-server.onrender.com/webhook")?.trim().orEmpty()
        if (backend.isBlank()) return

        executor.execute {
            val replyData = requestReply(backend, sender, message) ?: return@execute
            val reply = replyData.first
            val backendDelay = replyData.second
            val configuredDelay = prefs.getInt("delay", 2).coerceIn(0, 60)
            val delayMs = maxOf(configuredDelay * 1000L, backendDelay * 1000L)
            try {
                Thread.sleep(delayMs)
                val results = Bundle().apply { putCharSequence(input.resultKey, reply) }
                val intent = Intent()
                RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
                action.actionIntent.send(this, 0, intent)
            } catch (_: Exception) { }
        }
    }

    private fun requestReply(url: String, sender: String, message: String): Pair<String, Int>? {
        return try {
            val body = JSONObject().put("sender", sender).put("message", message).toString()
                .toRequestBody(jsonType)
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val root = JSONObject(response.body?.string().orEmpty())
                val replies = root.optJSONArray("replies") ?: return null
                if (replies.length() == 0) return null
                val item = replies.optJSONObject(0) ?: return null
                val text = item.optString("message").trim()
                if (text.isBlank()) return null
                Pair(text, item.optInt("delay", 0).coerceIn(0, 60))
            }
        } catch (_: Exception) { null }
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
