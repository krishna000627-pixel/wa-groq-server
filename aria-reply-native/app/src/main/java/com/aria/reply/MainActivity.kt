package com.aria.reply

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var page: FrameLayout
    private lateinit var store: AriaStore
    private var screen = 0
    private var selectedChat = ""

    // Dark pastel clay system: cocoa base + sage/teal/terracotta accents.
    private val bg = Color.rgb(42, 29, 24)
    private val surface = Color.rgb(52, 37, 31)
    private val surface2 = Color.rgb(61, 43, 36)
    private val cream = Color.rgb(243, 228, 208)
    private val muted = Color.rgb(185, 162, 147)
    private val sage = Color.rgb(143, 175, 143)
    private val teal = Color.rgb(110, 169, 160)
    private val terracotta = Color.rgb(196, 126, 99)
    private val gold = Color.rgb(200, 165, 106)
    private val ink = Color.rgb(249, 237, 220)
    private val danger = Color.rgb(208, 106, 91)
    private val border = Color.rgb(89, 65, 56)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AriaStore(this)
        setContentView(R.layout.activity_main)
        page = findViewById(R.id.page)
        findViewById<View>(R.id.navHome).setOnClickListener { screen = 0; render() }
        findViewById<View>(R.id.navTest).setOnClickListener { screen = 1; render() }
        findViewById<View>(R.id.navChats).setOnClickListener { screen = 2; render() }
        findViewById<View>(R.id.navSettings).setOnClickListener { screen = 3; render() }
        findViewById<View>(R.id.navSystem).setOnClickListener { screen = 4; render() }
        render()
    }

    override fun onResume() { super.onResume(); if (::page.isInitialized) render() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 15f, color: Int = ink) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); includeFontPadding = true
    }

    private fun shape(color: Int, radius: Int = 16, stroke: Int = border, width: Int = 1) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (width > 0) setStroke(dp(width), stroke)
    }

    private fun icon(res: Int, tint: Int = cream, size: Int = 22) = ImageView(this).apply {
        setImageResource(res); setColorFilter(tint); layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun shell(title: String, subtitle: String, back: (() -> Unit)? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(17), dp(18), dp(24)); setBackgroundColor(bg)
        val top = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        if (back != null) {
            top.addView(iconButton(R.drawable.ic_back, "Back") { back() }, LinearLayout.LayoutParams(dp(44), dp(44)))
            top.addView(Space(this@MainActivity), LinearLayout.LayoutParams(dp(8), 1))
        }
        top.addView(tv("ARIA / REPLY", 11f, sage).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = .16f }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(top)
        addView(tv(title, 30f, ink).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(5), 0, 0) })
        addView(tv(subtitle, 14f, muted).apply { setPadding(0, dp(3), 0, dp(12)) })
    }

    private fun iconButton(res: Int, content: String, action: () -> Unit) = ImageButton(this).apply {
        contentDescription = content; setImageResource(res); setColorFilter(ink); background = shape(surface2, 14); setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { action() }
    }

    private fun statusPill(text: String, ok: Boolean): TextView = tv(text.uppercase(), 10f, if (ok) sage else terracotta).apply {
        typeface = Typeface.DEFAULT_BOLD; setPadding(dp(10), dp(5), dp(10), dp(5)); background = shape(if (ok) Color.rgb(48, 66, 50) else Color.rgb(73, 44, 37), 12, if (ok) Color.rgb(72, 103, 73) else Color.rgb(118, 64, 52))
    }

    private fun panel(title: String, value: String, color: Int, res: Int, action: (() -> Unit)? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(14), dp(15), dp(14)); background = shape(color, 18)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(5), 0, dp(5)) }
        val head = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(icon(res, sage, 23), LinearLayout.LayoutParams(dp(23), dp(23)).apply { setMargins(0, 0, dp(10), 0) })
        head.addView(tv(title.uppercase(), 10f, muted).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = .08f }, LinearLayout.LayoutParams(0, -2, 1f))
        if (action != null) head.addView(tv("OPEN", 9f, sage).apply { typeface = Typeface.DEFAULT_BOLD })
        addView(head)
        addView(tv(value, 15f, ink).apply { setPadding(0, dp(8), 0, 0) })
        if (action != null) setOnClickListener { action() }
    }

    private fun action(label: String, res: Int, color: Int, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(11), dp(14), dp(11)); background = shape(color, 14)
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(4), 0, dp(4)) }
        addView(icon(res, sage, 21), LinearLayout.LayoutParams(dp(21), dp(21)).apply { setMargins(0, 0, dp(12), 0) })
        addView(tv(label, 13f, ink).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(icon(R.drawable.ic_chevron, muted, 18)); setOnClickListener { action() }
    }

    private fun section(title: String, subtitle: String = "") = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, dp(15), 0, dp(4))
        addView(tv(title.uppercase(), 10f, sage).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = .1f })
        if (subtitle.isNotBlank()) addView(tv(subtitle, 12f, muted).apply { setPadding(0, dp(3), 0, 0) })
    }

    private fun field(label: String, value: String, password: Boolean = false, multiline: Boolean = false): EditText = EditText(this).apply {
        hint = label; setText(value); setTextColor(ink); setHintTextColor(muted); textSize = 14f; setPadding(dp(14), dp(8), dp(14), dp(8)); background = shape(surface2, 14)
        isSingleLine = !multiline; if (multiline) minLines = 5
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(-1, if (multiline) dp(140) else dp(52)).apply { setMargins(0, dp(4), 0, dp(7)) }
    }

    private fun show(content: LinearLayout) { page.removeAllViews(); page.addView(ScrollView(this).apply { isFillViewport = true; addView(content) }) }

    private fun render() = when (screen) { 0 -> home(); 1 -> diagnostics(); 2 -> chats(); 3 -> settings(); 4 -> system(); 5 -> chatDetail(); 6 -> apiTest() ; else -> home() }

    private fun home() {
        val l = shell("Command Center", "Aria's live notification → context → AI → reply pipeline.")
        val ready = store.autoReply && store.hasApiKey() && hasNotificationAccess()
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(17), dp(17), dp(17)); background = shape(if (ready) Color.rgb(49, 67, 51) else Color.rgb(67, 46, 39), 20) }
        val h = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        h.addView(tv("AUTOMATION ENGINE", 11f, muted).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = .08f }, LinearLayout.LayoutParams(0, -2, 1f))
        h.addView(statusPill(if (store.autoReply) "Armed" else "Manual", ready))
        hero.addView(h)
        hero.addView(tv(if (ready) "Ready to process WhatsApp replies." else "Configure the active provider and Notification Access.", 18f, ink).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(12), 0, dp(4)) })
        hero.addView(tv("${store.provider.uppercase()}  •  ${activeModel()}", 12f, muted))
        l.addView(hero)

        l.addView(section("Pipeline"))
        val steps = listOf("Notification capture" to hasNotificationAccess(), "Conversation context" to store.conversations().isNotEmpty(), "${store.provider.uppercase()} generation" to store.hasApiKey(), "RemoteInput reply" to store.lastTargetReady)
        steps.forEachIndexed { i, pair ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(13), dp(10), dp(13), dp(10)); background = shape(surface, 13); layoutParams = LinearLayout.LayoutParams(-1, dp(47)).apply { setMargins(0, dp(3), 0, dp(3)) } }
            row.addView(tv(if (pair.second) "●" else "○", 16f, if (pair.second) sage else muted).apply { layoutParams = LinearLayout.LayoutParams(dp(28), -2) })
            row.addView(tv("0${i+1}  ${pair.first}", 13f, ink), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(statusPill(if (pair.second) "Ready" else "Wait", pair.second))
            l.addView(row)
        }

        l.addView(section("Latest signal"))
        l.addView(panel("Captured", store.lastCapture, surface, R.drawable.ic_bell) { screen = 2; render() })
        l.addView(panel("Last reply", store.lastReply, Color.rgb(48, 66, 50), R.drawable.ic_reply))
        if (store.lastError.isNotBlank()) l.addView(panel("Latest error", store.lastError, Color.rgb(73, 44, 37), R.drawable.ic_warning) { screen = 6; render() })

        l.addView(section("Operations", "Actions are explicit. Configuration changes are persisted immediately."))
        l.addView(action("RUN CAPTURE + AI TEST", R.drawable.ic_play, surface2) { runSyntheticTest() })
        l.addView(action("OPEN API TEST LAB", R.drawable.ic_api, Color.rgb(52, 67, 63)) { screen = 6; render() })
        l.addView(action("OPEN CHAT CONTEXT", R.drawable.ic_chat, surface2) { screen = 2; render() })
        show(l)
    }

    private fun diagnostics() {
        val l = shell("Diagnostics", "Verify each layer without silently enabling automation.")
        l.addView(panel("Notification Access", if (hasNotificationAccess()) "Connected" else "Permission required", if (hasNotificationAccess()) Color.rgb(48, 66, 50) else Color.rgb(73, 44, 37), R.drawable.ic_bell) { openNotificationAccess() })
        l.addView(panel("Battery", if (batteryIgnored()) "Background exemption active" else "Optimization may restrict background work", if (batteryIgnored()) Color.rgb(48, 66, 50) else Color.rgb(67, 46, 39), R.drawable.ic_bolt) { requestBatteryExemption() })
        l.addView(panel("RemoteInput", if (store.lastTargetReady) store.lastTargetDescription else "No live reply action captured", if (store.lastTargetReady) Color.rgb(48, 66, 50) else surface, R.drawable.ic_reply))
        l.addView(section("Capture and responder"))
        l.addView(action("POST SYNTHETIC ARIA MESSAGE", R.drawable.ic_bell, terracotta) { runSyntheticTest() })
        l.addView(action("SEND DIRECT REMOTEINPUT TEST", R.drawable.ic_reply, Color.rgb(52, 67, 63)) { directReplyTest() })
        l.addView(panel("Captured message", store.lastCapture, surface, R.drawable.ic_test))
        l.addView(panel("Last generated reply", store.lastReply, Color.rgb(48, 66, 50), R.drawable.ic_reply))
        l.addView(section("API"))
        l.addView(action("OPEN API TEST LAB", R.drawable.ic_api, Color.rgb(52, 67, 63)) { screen = 6; render() })
        if (store.lastError.isNotBlank()) l.addView(panel("Runtime error", store.lastError, Color.rgb(73, 44, 37), R.drawable.ic_warning))
        l.addView(section("Event log"))
        store.events().take(10).forEach { raw ->
            val p = raw.split('|', limit = 3); val tag = p.getOrElse(1){"INFO"}; val msg = p.getOrElse(2){raw}
            l.addView(panel(tag, msg, if (tag == "FAIL") Color.rgb(73, 44, 37) else surface, if (tag == "FAIL") R.drawable.ic_warning else R.drawable.ic_activity))
        }
        l.addView(action("CLEAR EVENT LOG", R.drawable.ic_trash, surface2) { store.clearEvents(); toast("Event log cleared"); render() })
        show(l)
    }

    private fun chats() {
        val l = shell("Chat Context", "Captured conversations are stored locally and can be injected into AI tests.")
        val names = store.conversations()
        if (names.isEmpty()) {
            l.addView(panel("No chats yet", "Receive a WhatsApp notification or run the synthetic capture test.", surface, R.drawable.ic_chat))
        } else {
            names.forEach { name ->
                val history = store.history(name); val last = history.lastOrNull()?.text ?: "No messages"
                l.addView(panel(name, "${history.size} messages  •  $last", surface, R.drawable.ic_chat) { selectedChat = name; screen = 5; render() })
            }
        }
        l.addView(section("Context policy"))
        l.addView(panel("Context window", "Up to 10 recent messages from the selected conversation are included in an AI test/reply. Older messages remain visible here but are not sent.", Color.rgb(52, 67, 63), R.drawable.ic_info))
        l.addView(action("RUN CONTEXT-AWARE API TEST", R.drawable.ic_api, Color.rgb(52, 67, 63)) { if (names.isNotEmpty()) { selectedChat = names.first(); screen = 6; render() } else { screen = 6; render() } })
        show(l)
    }

    private fun chatDetail() {
        val l = shell(selectedChat.ifBlank { "Conversation" }, "Local capture history", { screen = 2; render() })
        val history = store.history(selectedChat)
        history.forEach { m ->
            val mine = m.role == "assistant"
            val bubble = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(10), dp(13), dp(10)); background = shape(if (mine) Color.rgb(48, 66, 50) else surface2, 15); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(if (mine) dp(35) else 0, dp(4), if (mine) 0 else dp(35), dp(4)) } }
            bubble.addView(tv(if (mine) "ARIA" else m.sender, 10f, if (mine) sage else muted).apply { typeface = Typeface.DEFAULT_BOLD })
            bubble.addView(tv(m.text, 14f, ink).apply { setPadding(0, dp(5), 0, 0) })
            bubble.addView(tv(formatTime(m.timestamp), 9f, muted).apply { gravity = Gravity.END; setPadding(0, dp(5), 0, 0) })
            l.addView(bubble)
        }
        l.addView(section("Actions"))
        l.addView(action("USE THIS CHAT IN API TEST", R.drawable.ic_api, Color.rgb(52, 67, 63)) { screen = 6; render() })
        l.addView(action("CLEAR THIS CHAT", R.drawable.ic_trash, Color.rgb(73, 44, 37)) { store.clearConversation(selectedChat); toast("Conversation cleared"); screen = 2; render() })
        show(l)
    }

    private fun settings() {
        val l = shell("Settings", "One source of truth for automation, providers, context and reply behavior.")
        l.addView(section("Automation"))
        val sw = Switch(this).apply { text = if (store.autoReply) "Auto Reply enabled" else "Auto Reply disabled"; setTextColor(ink); textSize = 15f; isChecked = store.autoReply; setOnCheckedChangeListener { _, checked -> store.autoReply = checked; text = if (checked) "Auto Reply enabled" else "Auto Reply disabled"; store.logEvent("Auto Reply ${if (checked) "enabled" else "disabled"}", null) } }
        l.addView(sw)
        l.addView(panel("Engine", if (store.autoReply) "BACKGROUND ENGINE ARMED" else "MANUAL / TEST MODE", if (store.autoReply) Color.rgb(48, 66, 50) else Color.rgb(67, 46, 39), R.drawable.ic_bolt))

        l.addView(section("AI provider"))
        val providerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = shape(surface, 15); setPadding(dp(5), dp(5), dp(5), dp(5)) }
        val groq = TextView(this).apply { text = "GROQ"; textSize = 13f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); setTextColor(ink); setPadding(0, dp(12), 0, dp(12)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val gem = TextView(this).apply { text = "GEMINI"; textSize = 13f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); setTextColor(muted); setPadding(0, dp(12), 0, dp(12)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        fun refreshProvider() { val isGroq = store.provider.equals("GROQ", true); groq.background = shape(if (isGroq) Color.rgb(48, 66, 50) else surface, 12); gem.background = shape(if (!isGroq) Color.rgb(52, 67, 63) else surface, 12); groq.setTextColor(if (isGroq) ink else muted); gem.setTextColor(if (!isGroq) ink else muted) }
        groq.setOnClickListener { store.provider = "GROQ"; refreshProvider(); toast("Groq selected") }
        gem.setOnClickListener { store.provider = "GEMINI"; refreshProvider(); toast("Gemini selected") }
        providerRow.addView(groq); providerRow.addView(gem); refreshProvider(); l.addView(providerRow)

        l.addView(section("Groq"))
        val endpoint = field("Groq endpoint", store.endpoint); val model = field("Groq model", store.model); val gKey = field("Groq API key • encrypted", if (store.apiKey().isBlank()) "" else "••••••••••••••••", true)
        l.addView(endpoint); l.addView(model); l.addView(gKey)
        gKey.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus && !gKey.text.toString().startsWith("••")) store.setApiKey(gKey.text.toString()) }
        l.addView(action("TEST GROQ IN API LAB", R.drawable.ic_api, Color.rgb(52, 67, 63)) { screen = 6; render() })

        l.addView(section("Gemini"))
        val gm = field("Gemini model", store.geminiModel); val gmKey = field("Gemini API key • encrypted", if (store.geminiApiKey().isBlank()) "" else "••••••••••••••••", true)
        l.addView(gm); l.addView(gmKey)
        gmKey.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus && !gmKey.text.toString().startsWith("••")) store.setGeminiApiKey(gmKey.text.toString()) }

        l.addView(section("Conversation context"))
        l.addView(panel("Context window", "The reply engine can use the latest 10 messages from the same sender. Full captured history remains local and viewable under Chats.", Color.rgb(52, 67, 63), R.drawable.ic_chat))
        l.addView(section("Reply behavior"))
        val prompt = field("System prompt", store.systemPrompt, false, true); val marker = field("WhatsApp marker", store.marker); val min = field("Minimum delay seconds", store.minDelay.toString()); min.inputType = InputType.TYPE_CLASS_NUMBER; val max = field("Maximum delay seconds", store.maxDelay.toString()); max.inputType = InputType.TYPE_CLASS_NUMBER
        l.addView(prompt); l.addView(marker); l.addView(min); l.addView(max)

        l.addView(section("Save and security"))
        l.addView(action("SAVE CONFIGURATION", R.drawable.ic_save, Color.rgb(48, 66, 50)) {
            store.autoReply = sw.isChecked; store.provider = if (store.provider.equals("GEMINI", true)) "GEMINI" else "GROQ"; store.endpoint = endpoint.text.toString().trim().ifBlank { AriaStore.DEFAULT_ENDPOINT }; store.model = model.text.toString().trim().ifBlank { "openai/gpt-oss-120b" }
            if (!gKey.text.toString().startsWith("••")) store.setApiKey(gKey.text.toString()); store.geminiModel = gm.text.toString().trim().ifBlank { "gemini-2.5-flash" }; if (!gmKey.text.toString().startsWith("••")) store.setGeminiApiKey(gmKey.text.toString()); store.systemPrompt = prompt.text.toString().trim().ifBlank { AriaStore.DEFAULT_PROMPT }; store.marker = marker.text.toString(); store.minDelay = (min.text.toString().toIntOrNull() ?: 2).coerceAtLeast(0); store.maxDelay = (max.text.toString().toIntOrNull() ?: 5).coerceAtLeast(store.minDelay); store.logEvent("Configuration saved", true); toast("Saved"); render()
        })
        l.addView(action("RESET SYSTEM PROMPT", R.drawable.ic_reset, surface2) { store.systemPrompt = AriaStore.DEFAULT_PROMPT; toast("Prompt reset"); render() })
        l.addView(action("CLEAR API KEYS", R.drawable.ic_trash, Color.rgb(73, 44, 37)) { store.clearApiKey(); store.clearGeminiApiKey(); toast("API keys cleared"); render() })
        l.addView(action("NOTIFICATION ACCESS", R.drawable.ic_bell, surface2) { openNotificationAccess() })
        l.addView(action("BATTERY OPTIMIZATION", R.drawable.ic_bolt, surface2) { requestBatteryExemption() })
        show(l)
    }

    private fun apiTest() {
        val l = shell("API Test Lab", "A dedicated manual test surface. Nothing is sent until you press Run.", { screen = 0; render() })
        l.addView(panel("Active provider", "${store.provider.uppercase()}  •  ${activeModel()}", Color.rgb(52, 67, 63), R.drawable.ic_api))
        l.addView(section("Test message"))
        val sender = field("Sender name", selectedChat.ifBlank { "TestUser" })
        val message = field("Write a message to test", "", false, true)
        l.addView(sender); l.addView(message)
        l.addView(section("Context"))
        val useContext = Switch(this).apply { text = if (selectedChat.isBlank()) "Use conversation context" else "Use context from $selectedChat"; setTextColor(ink); isChecked = selectedChat.isNotBlank() && store.history(selectedChat).isNotEmpty() }
        l.addView(useContext)
        l.addView(panel("Context preview", if (useContext.isChecked && selectedChat.isNotBlank()) store.recentContext(selectedChat).takeLast(5).joinToString("\n") { "${if (it.role == "assistant") "Aria" else it.sender}: ${it.text}" } else "No context selected.", surface, R.drawable.ic_chat))
        l.addView(section("Execution"))
        l.addView(action("RUN API REQUEST", R.drawable.ic_play, Color.rgb(48, 66, 50)) {
            val who = sender.text.toString().trim().ifBlank { "TestUser" }; val msg = message.text.toString().trim(); if (msg.isBlank()) { toast("Write a test message first"); return@action }
            val ctx = if (useContext.isChecked && selectedChat.isNotBlank()) store.recentContext(selectedChat) else store.recentContext(who)
            runManualApiTest(who, msg, ctx)
        })
        l.addView(action("OPEN SETTINGS", R.drawable.ic_settings, surface2) { screen = 3; render() })
        l.addView(section("Result"))
        l.addView(panel("Last capture", store.lastCapture, surface, R.drawable.ic_test))
        l.addView(panel("Last response", store.lastReply, Color.rgb(48, 66, 50), R.drawable.ic_reply))
        if (store.lastError.isNotBlank()) l.addView(panel("API error", store.lastError, Color.rgb(73, 44, 37), R.drawable.ic_warning))
        show(l)
    }

    private fun system() {
        val l = shell("System", "Aria runtime state, security and build information.")
        l.addView(panel("Version", "V27 • Dark Clay Core", Color.rgb(52, 67, 63), R.drawable.ic_info))
        l.addView(panel("Security", "API keys use Android Keystore encryption. Notification text is treated as untrusted input.", surface, R.drawable.ic_key))
        l.addView(panel("Automation", "Notification capture → conversation context → provider generation → RemoteInput delivery.", Color.rgb(48, 66, 50), R.drawable.ic_bolt))
        l.addView(panel("Compatibility", "Android 8+; direct replies depend on WhatsApp exposing a RemoteInput action.", surface, R.drawable.ic_info))
        l.addView(section("Design system"))
        l.addView(panel("Dark pastel clay", "Cocoa background • mocha surfaces • sage active • dusty teal secondary • terracotta warnings • warm cream text. No white canvas. No elevation shadows. No bento grid.", surface2, R.drawable.ic_palette))
        l.addView(section("Core modules"))
        val features = listOf("Dashboard state machine", "Dedicated API Test Lab", "Groq + Gemini providers", "Encrypted API keys", "Conversation history", "Context-aware generation", "Synthetic notification pipeline", "RemoteInput target diagnostics", "Direct responder test", "Notification permission state", "Battery optimization state", "Event log", "Persistent automation toggle", "Model configuration", "System prompt editor", "Reply marker", "Delay controls", "Error body diagnostics", "Chat detail viewer", "Conversation clear control")
        features.forEachIndexed { i, f -> l.addView(tv("${String.format("%02d", i + 1)}  $f", 13f, ink).apply { setPadding(dp(3), dp(5), 0, dp(5)) }) }
        show(l)
    }

    private fun runManualApiTest(sender: String, message: String, context: List<AriaStore.ChatMessage>) {
        if (!store.hasApiKey()) { toast("Configure the active provider API key first"); screen = 3; render(); return }
        toast("Calling ${store.provider.uppercase()}…")
        Thread {
            val r = AriaApi.generate(store, sender, message, context)
            runOnUiThread {
                if (r.ok) { store.lastReply = r.reply; store.lastError = ""; store.logEvent("${store.provider} manual API test passed", true); toast("API test passed") }
                else { val body = r.raw.take(500).replace("\n", " "); store.lastError = "${r.error} (HTTP ${r.code})${if (body.isNotBlank()) " • $body" else ""}"; store.logEvent("${store.provider} manual API test failed: HTTP ${r.code}", false); toast("API test failed") }
                render()
            }
        }.start()
    }

    private fun runSyntheticTest() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { requestNotificationPermission(); toast("Allow notifications, then run again"); return }
        if (!hasNotificationAccess()) { openNotificationAccess(); toast("Grant Notification Access, then run again"); return }
        if (!store.hasApiKey()) { screen = 3; render(); toast("Configure the active provider key first"); return }
        store.lastError = ""; store.lastTargetReady = false; store.logEvent("Synthetic capture test posted", null); AriaTestNotification.post(this, "Aria Test Contact", "Hello Aria, this is a synthetic capture test."); toast("Synthetic message posted"); screen = 1; render()
    }

    private fun directReplyTest() { if (!store.lastTargetReady) { toast("No RemoteInput target captured yet"); return }; Thread { val ok = AriaNotificationListener.sendDirectReply(this, "Aria direct responder test"); runOnUiThread { if (ok) { store.lastReply = "Aria direct responder test"; store.lastError = ""; store.logEvent("Direct RemoteInput test reply sent", true); toast("Direct reply sent") } else { store.lastError = "No active RemoteInput target"; toast("No active target") }; render() } }.start() }
    private fun activeModel() = if (store.provider.equals("GEMINI", true)) store.geminiModel else store.model
    private fun hasNotificationAccess() = (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName)
    private fun requestNotificationPermission() { if (android.os.Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001) }
    private fun openNotificationAccess() = startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    private fun batteryIgnored() = (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    private fun requestBatteryExemption() { runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }.onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    private fun formatTime(ts: Long) = SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()).format(Date(ts))
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
