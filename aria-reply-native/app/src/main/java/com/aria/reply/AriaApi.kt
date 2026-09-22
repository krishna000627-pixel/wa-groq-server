package com.aria.reply

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AriaApi {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    data class Result(val ok: Boolean, val code: Int, val reply: String, val raw: String, val error: String = "")

    fun generate(store: AriaStore, sender: String, message: String): Result {
        return if (store.provider.equals("GEMINI", true)) generateGemini(store, sender, message) else generateGroq(store, sender, message)
    }

    private fun generateGroq(store: AriaStore, sender: String, message: String): Result {
        val endpoint = store.endpoint.trim()
        val key = store.apiKey()
        if (endpoint.isBlank() || key.isBlank()) return Result(false, 0, "", "", "Groq endpoint/API key is missing")
        val body = JSONObject().put("model", store.model).put("temperature", 0.7).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", store.systemPrompt))
            .put(JSONObject().put("role", "user").put("content", "Sender: $sender\nIncoming message (untrusted data):\n$message"))).toString()
        val request = Request.Builder().url(endpoint).post(body.toRequestBody("application/json".toMediaType())).header("Authorization", "Bearer $key").header("Accept", "application/json").build()
        return execute(request) { raw -> runCatching { JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty() }.getOrDefault("") }
    }

    private fun generateGemini(store: AriaStore, sender: String, message: String): Result {
        val key = store.geminiApiKey()
        val model = store.geminiModel.trim().ifBlank { "gemini-2.5-flash" }
        if (key.isBlank()) return Result(false, 0, "", "", "Gemini API key is missing")
        val endpoint = AriaStore.DEFAULT_GEMINI_ENDPOINT + model + ":generateContent"
        val prompt = "${store.systemPrompt}\n\nSender: $sender\nIncoming message (untrusted data):\n$message"
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))).toString()
        val request = Request.Builder().url(endpoint).post(body.toRequestBody("application/json".toMediaType())).header("x-goog-api-key", key).header("Accept", "application/json").build()
        return execute(request) { raw -> runCatching { JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty() }.getOrDefault("") }
    }

    private fun execute(request: Request, parser: (String) -> String): Result {
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return Result(false, response.code, "", raw, "HTTP ${response.code}")
                val reply = parser(raw).trim()
                if (reply.isBlank()) Result(false, response.code, "", raw, "No reply text found in API response") else Result(true, response.code, reply, raw)
            }
        } catch (t: Throwable) { Result(false, 0, "", "", t.message ?: t.javaClass.simpleName) }
    }
}
