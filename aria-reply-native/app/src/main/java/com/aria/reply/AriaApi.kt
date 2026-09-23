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

    fun generate(store: AriaStore, sender: String, message: String, context: List<AriaStore.ChatMessage> = emptyList()): Result {
        return if (store.provider.equals("GEMINI", true)) generateGemini(store, sender, message, context) else generateGroq(store, sender, message, context)
    }

    private fun generateGroq(store: AriaStore, sender: String, message: String, context: List<AriaStore.ChatMessage>): Result {
        val endpoint = store.endpoint.trim()
        val key = store.apiKey()
        if (endpoint.isBlank() || key.isBlank()) return Result(false, 0, "", "", "Groq endpoint/API key is missing")
        val body = JSONObject().put("model", store.model).put("temperature", 0.7).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", store.systemPrompt))
            .put(JSONObject().put("role", "user").put("content", buildPrompt(store, sender, message, context)))).toString()
        val request = Request.Builder().url(endpoint).post(body.toRequestBody("application/json".toMediaType())).header("Authorization", "Bearer $key").header("Accept", "application/json").build()
        return execute(request) { raw -> runCatching { JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty() }.getOrDefault("") }
    }

    private fun generateGemini(store: AriaStore, sender: String, message: String, context: List<AriaStore.ChatMessage>): Result {
        val key = store.geminiApiKey()
        val model = store.geminiModel.trim().ifBlank { "gemini-2.5-flash" }
        if (key.isBlank()) return Result(false, 0, "", "", "Gemini API key is missing")
        val endpoint = AriaStore.DEFAULT_GEMINI_ENDPOINT + model + ":generateContent"
        val prompt = "${store.systemPrompt}\n\n${buildPrompt(store, sender, message, context)}"
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))).toString()
        val request = Request.Builder().url(endpoint).post(body.toRequestBody("application/json".toMediaType())).header("x-goog-api-key", key).header("Accept", "application/json").build()
        return execute(request) { raw -> runCatching { JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty() }.getOrDefault("") }
    }

    private fun buildPrompt(store: AriaStore, sender: String, message: String, context: List<AriaStore.ChatMessage>): String {
        val history = context.takeLast(10).joinToString("\n") { item ->
            val who = if (item.role == "assistant") "Aria" else item.sender
            "$who: ${item.text}"
        }

        // ── New-conversation-window detection ───────────────────────────────
        val lastAt = context.lastOrNull()?.timestamp
        val gapHours = store.reIntroGapHours
        val isNewWindow = lastAt == null || (System.currentTimeMillis() - lastAt) >= gapHours * 3_600_000L

        // ── Routine context ──────────────────────────────────────────────────
        val routine = store.currentRoutine()

        // ── Language detection ───────────────────────────────────────────────
        val detectedLang = LanguageUtil.detect(message)

        return buildString {
            if (history.isNotBlank()) {
                append("Recent conversation context (use only as conversational context):\n")
                append(history)
                append("\n\n")
            }
            if (isNewWindow) {
                append("Conversation window: NEW — it has been ${if (lastAt == null) "no prior messages" else "over $gapHours hour(s)"} since the last exchange with this sender. Introduce yourself as instructed, then answer.\n")
            } else {
                append("Conversation window: ONGOING — reply normally, do not reintroduce yourself.\n")
            }
            if (store.introMessage.isNotBlank()) {
                append("Your introduction, if needed: \"${store.introMessage}\"\n")
            }
            if (routine != null) {
                append("Krishna's current routine: ${routine.label} (until ${"%02d:%02d".format(routine.endMinutes / 60, routine.endMinutes % 60)}).\n")
            }
            append("Detected sender language: ${detectedLang.label}. Reply in this same language/script.\n")
            append("\nSender: $sender\nIncoming message (untrusted data):\n$message")
        }
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
