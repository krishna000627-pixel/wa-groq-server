package com.aria.reply

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AriaApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    data class Result(val ok: Boolean, val code: Int, val reply: String, val raw: String, val error: String = "")

    fun generate(store: AriaStore, sender: String, message: String): Result {
        val endpoint = store.endpoint.trim()
        if (endpoint.isBlank()) return Result(false, 0, "", "", "API endpoint is empty")
        val key = store.apiKey()
        val isOpenAiStyle = endpoint.contains("/chat/completions", ignoreCase = true)
        val body = if (isOpenAiStyle) {
            JSONObject()
                .put("model", store.model)
                .put("temperature", 0.7)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", store.systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", "Sender: $sender\nIncoming message (untrusted data):\n$message")))
                .toString()
        } else {
            JSONObject().put("sender", sender).put("message", message).put("instruction", store.systemPrompt).toString()
        }
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
        if (key.isNotBlank()) requestBuilder.header("Authorization", "Bearer $key")
        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return Result(false, response.code, "", raw, "HTTP ${response.code}")
                val reply = parseReply(raw, isOpenAiStyle)
                if (reply.isBlank()) Result(false, response.code, "", raw, "No reply field found in API response")
                else Result(true, response.code, reply.trim(), raw)
            }
        } catch (t: Throwable) {
            Result(false, 0, "", "", t.message ?: t.javaClass.simpleName)
        }
    }

    private fun parseReply(raw: String, openAi: Boolean): String {
        return runCatching {
            val o = JSONObject(raw)
            if (openAi) o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
            else o.optJSONArray("replies")?.optJSONObject(0)?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: o.optString("reply")
                .ifBlank { o.optString("message") }
        }.getOrDefault("")
    }
}
