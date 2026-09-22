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

class MainActivity : Activity() {
    private lateinit var page: FrameLayout
    private lateinit var store: AriaStore
    private var screen = 0

    // Warm Clay palette: no white background, no elevation/shadow, no bento grid.
    private val bg = Color.rgb(232, 215, 197)          // warm brown
    private val surface = Color.rgb(246, 235, 221)     // oat
    private val cream = Color.rgb(251, 243, 232)       // cream
    private val sage = Color.rgb(216, 226, 209)        // sage
    private val clay = Color.rgb(231, 182, 160)        // terracotta
    private val olive = Color.rgb(202, 204, 165)       // muted olive
    private val sky = Color.rgb(202, 218, 214)         // dusty aqua
    private val ink = Color.rgb(58, 42, 34)            // dark cocoa
    private val muted = Color.rgb(119, 101, 90)
    private val accent = Color.rgb(91, 119, 94)        // eucalyptus
    private val danger = Color.rgb(160, 79, 70)
    private val border = Color.rgb(207, 185, 163)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AriaStore(this)
        setContentView(R.layout.activity_main)
        page = findViewById(R.id.page)
        findViewById<View>(R.id.navHome).setOnClickListener { screen = 0; render() }
        findViewById<View>(R.id.navTest).setOnClickListener { screen = 1; render() }
        findViewById<View>(R.id.navSettings).setOnClickListener { screen = 2; render() }
        findViewById<View>(R.id.navAbout).setOnClickListener { screen = 3; render() }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::page.isInitialized) render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 15f, color: Int = ink) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        includeFontPadding = true
    }

    private fun bg(color: Int, radius: Int = 18, stroke: Int = border, strokeWidth: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
        }

    private fun shell(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(24))
        setBackgroundColor(bg)
        addView(tv("ARIA / REPLY", 12f, accent).apply {
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        })
        addView(tv(title, 29f, ink).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(5), 0, 0)
        })
        addView(tv(subtitle, 14f, muted).apply { setPadding(0, dp(2), 0, dp(14)) })
    }

    private fun icon(resource: Int, tint: Int = ink, size: Int = 24): ImageView = ImageView(this).apply {
        setImageResource(resource)
        setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun card(
        title: String,
        value: String,
        color: Int,
        iconRes: Int,
        action: (() -> Unit)? = null
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(15), dp(17), dp(15))
        background = bg(color, 20)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(5), 0, dp(5)) }
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(icon(iconRes, accent, 25).apply {
                layoutParams = LinearLayout.LayoutParams(dp(25), dp(25)).apply { setMargins(0, 0, dp(11), 0) }
            })
            addView(tv(title.uppercase(), 11f, muted).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
            if (action != null) addView(tv("OPEN", 10f, accent).apply { typeface = Typeface.DEFAULT_BOLD })
        })
        addView(tv(value, 16f, ink).apply { setPadding(0, dp(8), 0, 0) })
        if (action != null) setOnClickListener { action() }
    }

    private fun actionButton(label: String, iconRes: Int, color: Int, action: () -> Unit) =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = bg(color, 17)
            layoutParams = LinearLayout.LayoutParams(-1, dp(55)).apply { setMargins(0, dp(4), 0, dp(4)) }
            addView(icon(iconRes, accent, 23).apply {
                layoutParams = LinearLayout.LayoutParams(dp(23), dp(23)).apply { setMargins(0, 0, dp(12), 0) }
            })
            addView(tv(label, 14f, ink).apply { typeface = Typeface.DEFAULT_BOLD })
            setOnClickListener { action() }
        }

    private fun section(title: String, detail: String = "") = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(15), 0, dp(4))
        addView(tv(title.uppercase(), 11f, accent).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f })
        if (detail.isNotBlank()) addView(tv(detail, 12f, muted).apply { setPadding(0, dp(2), 0, 0) })
    }

    private fun input(label: String, value: String, password: Boolean = false, multiline: Boolean = false) =
        EditText(this).apply {
            hint = label
            setText(value)
            setTextColor(ink)
            setHintTextColor(muted)
            textSize = 14f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = bg(cream, 15)
            isSingleLine = !multiline
            if (multiline) minLines = 4
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(-1, if (multiline) dp(126) else dp(52)).apply {
                setMargins(0, dp(4), 0, dp(7))
            }
        }

    private fun show(content: LinearLayout) {
        page.removeAllViews()
        page.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
    }

    private fun ready(ok: Boolean) = if (ok) "READY" else "ACTION REQUIRED"

    private fun render() {
        when (screen) {
            0 -> home()
            1 -> test()
            2 -> settings()
            else -> about()
        }
    }

    private fun home() {
        val l = shell("Control Center", "Notification → AI → RemoteInput reply, with one clear system state.")

        val automationReady = store.autoReply && store.hasApiKey() && hasNotificationAccess()
        l.addView(card(
            "Automation",
            if (store.autoReply) "ON  •  ${if (automationReady) "ready to process WhatsApp" else "needs one or more prerequisites"}" else "OFF  •  engine is idle",
            if (automationReady) sage else clay,
            R.drawable.ic_bolt
        ) { screen = 2; render() })

        l.addView(card(
            "AI engine",
            "${store.provider.uppercase()}  •  ${activeModel()}  •  ${ready(store.hasApiKey())}",
            olive,
            R.drawable.ic_key
        ) { screen = 2; render() })

        l.addView(card(
            "Notification reader",
            if (hasNotificationAccess()) "CONNECTED  •  notification listener is active" else "NOT CONNECTED  •  permission required",
            if (hasNotificationAccess()) sky else clay,
            R.drawable.ic_bell
        ) { openNotificationAccess() })

        l.addView(section("System pipeline", "Each layer is independently visible; no hidden automation."))
        pipeline(l)

        l.addView(section("Latest activity"))
        l.addView(card("Last capture", store.lastCapture, cream, R.drawable.ic_bell))
        l.addView(card("Last reply", store.lastReply, sage, R.drawable.ic_play))
        l.addView(card(
            "RemoteInput",
            if (store.lastTargetReady) "READY  •  ${store.lastTargetDescription}" else "WAITING  •  no reply action captured yet",
            if (store.lastTargetReady) sage else clay,
            R.drawable.ic_reply
        ))
        if (store.lastError.isNotBlank()) l.addView(card("Latest error", store.lastError, clay, R.drawable.ic_warning) { screen = 1; render() })

        l.addView(section("Quick operations"))
        l.addView(actionButton("RUN SYNTHETIC CAPTURE + AI REPLY", R.drawable.ic_play, sage) { runSyntheticTest() })
        l.addView(actionButton("TEST ACTIVE API", R.drawable.ic_key, olive) { apiOnlyTest() })
        l.addView(actionButton("OPEN DIAGNOSTICS", R.drawable.ic_test, sky) { screen = 1; render() })
        l.addView(actionButton("OPEN SETTINGS", R.drawable.ic_settings, cream) { screen = 2; render() })
        show(l)
    }

    private fun pipeline(l: LinearLayout) {
        val steps = listOf(
            "Capture notification" to hasNotificationAccess(),
            "Detect RemoteInput" to store.lastTargetReady,
            "Generate with ${store.provider.uppercase()}" to store.hasApiKey(),
            "Send reply" to (store.lastReply != "No reply sent yet.")
        )
        steps.forEachIndexed { index, pair ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(11), dp(14), dp(11))
                background = bg(if (pair.second) sage else cream, 14)
                layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(3), 0, dp(3)) }
            }
            row.addView(tv(if (pair.second) "✓" else "○", 18f, if (pair.second) accent else muted).apply {
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(dp(27), -2)
            })
            row.addView(tv("0${index + 1}  ${pair.first}", 13f, ink))
            l.addView(row)
        }
    }

    private fun test() {
        val l = shell("Diagnostics", "Real notification capture, RemoteInput detection, AI generation and reply delivery.")
        l.addView(card("Notification access", if (hasNotificationAccess()) "CONNECTED" else "ACTION REQUIRED", if (hasNotificationAccess()) sage else clay, R.drawable.ic_bell) { openNotificationAccess() })
        l.addView(card("Battery policy", if (batteryIgnored()) "OPTIMIZATION EXEMPTION ACTIVE" else "OPTIMIZATION MAY RESTRICT BACKGROUND WORK", if (batteryIgnored()) sage else clay, R.drawable.ic_bolt) { requestBatteryExemption() })
        l.addView(card("RemoteInput target", if (store.lastTargetReady) "READY  •  ${store.lastTargetDescription}" else "NOT DETECTED  •  post a test notification or receive a WhatsApp message", if (store.lastTargetReady) sage else sky, R.drawable.ic_reply))

        l.addView(section("Capture test"))
        l.addView(actionButton("POST ARIA SYNTHETIC MESSAGE", R.drawable.ic_bell, clay) { runSyntheticTest() })
        l.addView(actionButton("SEND DIRECT REMOTEINPUT TEST", R.drawable.ic_reply, sky) { directReplyTest() })
        l.addView(card("Captured message", store.lastCapture, cream, R.drawable.ic_test))
        l.addView(card("Last generated reply", store.lastReply, sage, R.drawable.ic_play))

        l.addView(section("API diagnostics"))
        l.addView(actionButton("TEST ACTIVE PROVIDER", R.drawable.ic_key, olive) { apiOnlyTest() })
        if (store.lastError.isNotBlank()) l.addView(card("API / runtime error", store.lastError, clay, R.drawable.ic_warning))

        l.addView(section("Activity log", "Latest events are stored locally."))
        store.events().take(12).forEach { raw ->
            val parts = raw.split('|', limit = 3)
            val tag = parts.getOrElse(1) { "INFO" }
            val msg = parts.getOrElse(2) { raw }
            l.addView(card(tag, msg, if (tag == "FAIL") clay else cream, if (tag == "FAIL") R.drawable.ic_warning else R.drawable.ic_activity))
        }
        l.addView(actionButton("CLEAR ACTIVITY LOG", R.drawable.ic_trash, cream) { store.clearEvents(); toast("Activity log cleared"); render() })
        show(l)
    }

    private fun settings() {
        val l = shell("Settings", "Everything that affects capture, generation and reply is explicit and saved.")

        l.addView(section("Automation"))
        val sw = Switch(this).apply {
            text = if (store.autoReply) "Auto Reply enabled" else "Auto Reply disabled"
            setTextColor(ink)
            textSize = 15f
            isChecked = store.autoReply
            setOnCheckedChangeListener { _, checked -> text = if (checked) "Auto Reply enabled" else "Auto Reply disabled" }
        }
        l.addView(sw)
        l.addView(card("Mode", if (store.autoReply) "BACKGROUND ENGINE ARMED" else "MANUAL / TEST MODE", if (store.autoReply) sage else clay, R.drawable.ic_bolt))

        l.addView(section("AI provider"))
        val provider = Spinner(this).apply {
            background = bg(cream, 15)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("GROQ", "GEMINI"))
            setSelection(if (store.provider.equals("GEMINI", true)) 1 else 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, dp(3), 0, dp(8)) }
        }
        l.addView(provider)

        l.addView(section("Groq"))
        val endpoint = input("Groq endpoint", store.endpoint)
        val model = input("Groq model", store.model)
        val groqKey = input("Groq API key • encrypted", store.apiKey(), true)
        l.addView(endpoint); l.addView(model); l.addView(groqKey)

        l.addView(section("Gemini"))
        val gemModel = input("Gemini model", store.geminiModel)
        val gemKey = input("Gemini API key • encrypted", store.geminiApiKey(), true)
        l.addView(gemModel); l.addView(gemKey)

        l.addView(section("Reply behaviour"))
        val prompt = input("System prompt", store.systemPrompt, false, true)
        val marker = input("WhatsApp response marker", store.marker)
        val min = input("Minimum delay (seconds)", store.minDelay.toString()); min.inputType = InputType.TYPE_CLASS_NUMBER
        val max = input("Maximum delay (seconds)", store.maxDelay.toString()); max.inputType = InputType.TYPE_CLASS_NUMBER
        l.addView(prompt); l.addView(marker); l.addView(min); l.addView(max)

        l.addView(section("Operations"))
        l.addView(actionButton("SAVE ALL CONFIGURATION", R.drawable.ic_save, sage) {
            store.autoReply = sw.isChecked
            store.provider = provider.selectedItem.toString()
            store.endpoint = endpoint.text.toString().trim().ifBlank { AriaStore.DEFAULT_ENDPOINT }
            store.model = model.text.toString().trim().ifBlank { "llama-3.3-70b-versatile" }
            store.setApiKey(groqKey.text.toString())
            store.geminiModel = gemModel.text.toString().trim().ifBlank { "gemini-2.5-flash" }
            store.setGeminiApiKey(gemKey.text.toString())
            store.systemPrompt = prompt.text.toString().trim().ifBlank { AriaStore.DEFAULT_PROMPT }
            store.marker = marker.text.toString()
            store.minDelay = (min.text.toString().toIntOrNull() ?: 2).coerceAtLeast(0)
            store.maxDelay = (max.text.toString().toIntOrNull() ?: 5).coerceAtLeast(store.minDelay)
            store.logEvent("Configuration saved", true)
            toast("Configuration saved")
            render()
        })
        l.addView(actionButton("TEST ACTIVE PROVIDER", R.drawable.ic_play, olive) { apiOnlyTest() })
        l.addView(actionButton("RESET SYSTEM PROMPT", R.drawable.ic_reset, cream) { store.systemPrompt = AriaStore.DEFAULT_PROMPT; toast("System prompt reset"); render() })
        l.addView(actionButton("CLEAR API KEYS", R.drawable.ic_trash, clay) { store.clearApiKey(); store.clearGeminiApiKey(); toast("API keys cleared"); render() })
        l.addView(actionButton("NOTIFICATION ACCESS", R.drawable.ic_bell, sky) { openNotificationAccess() })
        l.addView(actionButton("BATTERY OPTIMIZATION", R.drawable.ic_bolt, cream) { requestBatteryExemption() })
        show(l)
    }

    private fun about() {
        val l = shell("Aria System", "V25 Warm Clay Core — native Android notification automation.")
        l.addView(card("Design system", "Warm brown base • sage • oat • terracotta • olive • dusty aqua. No white canvas. No elevation shadows. No bento grid.", cream, R.drawable.ic_palette))
        l.addView(card("Automation", "WhatsApp notification capture → RemoteInput detection → Groq/Gemini generation → guarded reply action.", sage, R.drawable.ic_bolt))
        l.addView(card("Diagnostics", "Synthetic Aria notification contains a real RemoteInput action. The listener captures it, calls the active AI provider, submits the generated text and the test receiver records the result.", sky, R.drawable.ic_test))
        l.addView(card("Security", "API keys are encrypted with Android Keystore. Incoming notification text is treated as untrusted data and cannot replace the system prompt.", olive, R.drawable.ic_key))
        l.addView(card("Compatibility", "Android 8+; direct reply requires the installed messaging app to expose a RemoteInput reply action.", clay, R.drawable.ic_info))
        l.addView(section("Feature set"))
        val features = listOf(
            "01  Warm Clay visual system", "02  Icon-led navigation", "03  Auto Reply state", "04  Provider switch", "05  Groq endpoint + model", "06  Gemini endpoint + model",
            "07  Keystore API-key storage", "08  Notification access status", "09  Battery optimization status", "10  RemoteInput target state", "11  Synthetic capture notification", "12  Synthetic AI reply loop",
            "13  Direct RemoteInput test", "14  API-only test", "15  Activity log", "16  Clear activity", "17  Config save", "18  Prompt reset", "19  Delay controls", "20  Runtime error surface"
        )
        features.forEach { l.addView(tv(it, 13f, ink).apply { setPadding(dp(4), dp(5), 0, dp(5)) }) }
        show(l)
    }

    private fun activeModel() = if (store.provider.equals("GEMINI", true)) store.geminiModel else store.model

    private fun runSyntheticTest() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission(); toast("Allow notifications, then run the test again"); return
        }
        if (!hasNotificationAccess()) { openNotificationAccess(); toast("Grant Notification Access, then run the test again"); return }
        if (!store.hasApiKey()) { screen = 2; render(); toast("Configure the active AI key first"); return }
        store.lastError = ""
        store.lastTargetReady = false
        store.logEvent("Synthetic capture test posted", null)
        AriaTestNotification.post(this, "Aria Test Contact", "Hello Aria, this is a synthetic capture test.")
        toast("Synthetic message posted")
        screen = 1
        render()
    }

    private fun directReplyTest() {
        if (!store.lastTargetReady) { toast("No RemoteInput target captured yet"); return }
        Thread {
            val ok = AriaNotificationListener.sendDirectReply(this, "Aria direct responder test")
            runOnUiThread {
                if (ok) { store.lastReply = "Aria direct responder test"; store.lastError = ""; store.logEvent("Direct RemoteInput test reply sent", true); toast("Direct reply sent") }
                else { store.lastError = "No active RemoteInput target"; store.logEvent("Direct RemoteInput test failed", false); toast("No active target") }
                render()
            }
        }.start()
    }

    private fun apiOnlyTest() {
        if (!store.hasApiKey()) { toast("Configure the active AI key first"); return }
        Thread {
            val r = AriaApi.generate(store, "Aria Test", "Reply with a short test confirmation.")
            runOnUiThread {
                if (r.ok) {
                    store.lastReply = r.reply
                    store.lastError = ""
                    store.logEvent("${store.provider} API test passed", true)
                    toast("${store.provider} API passed")
                } else {
                    val body = r.raw.take(260).replace("\n", " ")
                    store.lastError = "${r.error} (HTTP ${r.code})${if (body.isNotBlank()) " • $body" else ""}"
                    store.logEvent("${store.provider} API test failed: HTTP ${r.code}", false)
                    toast("API test failed")
                }
                render()
            }
        }.start()
    }

    private fun hasNotificationAccess() =
        (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName)

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private fun openNotificationAccess() = startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))

    private fun batteryIgnored() =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun requestBatteryExemption() {
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }.onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
