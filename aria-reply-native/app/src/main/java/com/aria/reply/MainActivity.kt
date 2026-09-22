package com.aria.reply

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var page: FrameLayout
    private lateinit var store: AriaStore
    private var selected = Nav.HOME

    private enum class Nav { HOME, TEST, ACTIVITY, SETTINGS }

    private val bg = Color.rgb(5, 13, 10)
    private val surface = Color.rgb(13, 24, 19)
    private val surface2 = Color.rgb(18, 31, 25)
    private val border = Color.rgb(37, 63, 50)
    private val accent = Color.rgb(37, 235, 139)
    private val accentSoft = Color.rgb(18, 64, 45)
    private val white = Color.rgb(241, 249, 245)
    private val muted = Color.rgb(157, 177, 166)
    private val danger = Color.rgb(255, 105, 105)
    private val warning = Color.rgb(255, 197, 92)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AriaStore(this)
        setContentView(R.layout.activity_main)
        page = findViewById(R.id.page)
        findViewById<View>(R.id.navHome).setOnClickListener { navigate(Nav.HOME) }
        findViewById<View>(R.id.navTest).setOnClickListener { navigate(Nav.TEST) }
        findViewById<View>(R.id.navActivity).setOnClickListener { navigate(Nav.ACTIVITY) }
        findViewById<View>(R.id.navSettings).setOnClickListener { navigate(Nav.SETTINGS) }
        navigate(Nav.HOME)
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && ::page.isInitialized) render()
    }

    private fun navigate(nav: Nav) {
        selected = nav
        render()
    }

    private fun render() {
        when (selected) {
            Nav.HOME -> home()
            Nav.TEST -> test()
            Nav.ACTIVITY -> activityLog()
            Nav.SETTINGS -> settings()
        }
        updateNav()
    }

    private fun updateNav() {
        val ids = listOf(R.id.navHome, R.id.navTest, R.id.navActivity, R.id.navSettings)
        val navs = Nav.values()
        ids.forEachIndexed { i, id ->
            val v = findViewById<TextView>(id)
            val active = navs[i] == selected
            v.setTextColor(if (active) accent else muted)
            v.setTypeface(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
            v.alpha = if (active) 1f else .72f
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float = 16f, color: Int = white): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = true
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun heading(kicker: String, title: String, subtitle: String? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(8))
        addView(text(kicker.uppercase(), 12f, accent))
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 31f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, dp(4))
        })
        subtitle?.let { addView(text(it, 14f, muted)) }
    }

    private fun scroll(content: LinearLayout): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(bg)
        addView(content)
    }

    private fun shell(header: LinearLayout): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        addView(header)
    }

    private fun show(content: LinearLayout) {
        page.removeAllViews()
        page.addView(scroll(content))
    }

    private fun card(title: String, value: String, status: Status = Status.NEUTRAL, action: (() -> Unit)? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rounded(surface, border, 16)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(20), dp(7), dp(20), dp(7)) }
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(title.uppercase(), 11f, muted), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(status.label, 11f, status.color))
        })
        addView(text(value, 17f, white).apply { setPadding(0, dp(8), 0, dp(1)) })
        action?.let { addView(outlineButton("OPEN", it, small = true)) }
    }

    private fun section(label: String): TextView = text(label.uppercase(), 11f, muted).apply {
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(20), dp(18), dp(20), dp(5))
    }

    private fun primaryButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 14f
        setTextColor(bg)
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(50)
        background = rounded(accent, accent, 13)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(dp(20), dp(7), dp(20), dp(7)) }
    }

    private fun outlineButton(
    label: String,
    action: () -> Unit
): Button = outlineButton(label, action, false)

private fun outlineButton(label: String, action: () -> Unit, small: Boolean = false): Button = Button(this).apply {
        text = label
        textSize = if (small) 12f else 14f
        setTextColor(accent)
        isAllCaps = false
        minHeight = dp(if (small) 42 else 48)
        background = rounded(surface2, border, 12)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(if (small) 44 else 50)).apply { setMargins(dp(20), dp(6), dp(20), dp(6)) }
    }

    private fun input(label: String, value: String, password: Boolean = false, single: Boolean = true): EditText = EditText(this).apply {
        hint = label
        setText(value)
        setTextColor(white)
        setHintTextColor(muted)
        textSize = 15f
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(surface2, border, 12)
        isSingleLine = single
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(-1, if (single) dp(52) else dp(130)).apply { setMargins(dp(20), dp(4), dp(20), dp(10)) }
    }

    private fun statusBlock(label: String, value: String, status: Status): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(15), dp(18), dp(15))
        background = rounded(surface, border, 14)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(20), dp(5), dp(20), dp(5)) }
        val dot = TextView(this@MainActivity).apply {
            text = "●"
            textSize = 14f
            setTextColor(status.color)
            setPadding(0, 0, dp(12), 0)
        }
        addView(dot)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(label, 12f, muted))
            addView(text(value, 15f, white))
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int): android.graphics.drawable.GradientDrawable = android.graphics.drawable.GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radius).toFloat()
    }

    private enum class Status(val label: String, val color: Int) {
        OK("READY", Color.rgb(70, 240, 151)),
        WARN("ACTION", Color.rgb(255, 197, 92)),
        FAIL("ERROR", Color.rgb(255, 105, 105)),
        NEUTRAL("INFO", Color.rgb(157, 177, 166))
    }

    private fun home() {
        val l = shell(heading("ARIA", "Control Center", "Notification-to-reply automation at a glance"))

        val automation = if (store.autoReply) Status.OK else Status.WARN
        l.addView(card("Automation", if (store.autoReply) "Auto Reply is enabled and the background engine can process WhatsApp notifications." else "Auto Reply is disabled. Enable it in Settings when the API and notification access are ready.", automation))

        val apiStatus = when {
            !store.hasApiKey() -> Status.WARN
            store.lastError.contains("HTTP", true) || store.lastError.isNotBlank() -> Status.FAIL
            else -> Status.OK
        }
        l.addView(card("API connection", if (store.hasApiKey()) "Credential is stored securely in Android Keystore.\nModel: ${store.model}" else "No API key is configured. Add a key in Settings.", apiStatus) { navigate(Nav.SETTINGS) })
        l.addView(statusBlock("Notification access", if (hasNotificationAccess()) "Connected to Android Notification Listener" else "Not connected", if (hasNotificationAccess()) Status.OK else Status.WARN))
        l.addView(statusBlock("Battery", if (batteryIgnored()) "Optimization exemption active" else "System optimization is active", if (batteryIgnored()) Status.OK else Status.WARN))

        l.addView(section("Pipeline"))
        val pipeline = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(10))
            background = rounded(surface, border, 16)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(20), dp(4), dp(20), dp(10)) }
        }
        listOf(
            "01  WHATSAPP NOTIFICATION" to if (hasNotificationAccess()) Status.OK else Status.WARN,
            "02  CAPTURE" to if (store.lastCapture != "No notification captured yet.") Status.OK else Status.NEUTRAL,
            "03  GROQ API" to if (store.hasApiKey()) Status.OK else Status.WARN,
            "04  GENERATE REPLY" to if (store.lastReply != "No reply sent yet.") Status.OK else Status.NEUTRAL,
            "05  REMOTE INPUT" to if (AriaNotificationListener.lastReplyTarget != null) Status.OK else Status.NEUTRAL
        ).forEach { (label, status) ->
            pipeline.addView(text("${status.label}   $label", 13f, status.color).apply { setPadding(0, dp(9), 0, dp(9)) })
        }
        l.addView(pipeline)

        l.addView(section("Latest activity"))
        l.addView(card("Last capture", store.lastCapture, if (store.lastCapture == "No notification captured yet.") Status.NEUTRAL else Status.OK))
        l.addView(card("Last reply", store.lastReply, if (store.lastReply == "No reply sent yet.") Status.NEUTRAL else Status.OK))
        if (store.lastError.isNotBlank()) l.addView(card("Latest error", store.lastError, Status.FAIL) { navigate(Nav.TEST) })

        l.addView(section("Quick actions"))
        l.addView(primaryButton("RUN DIAGNOSTICS") { navigate(Nav.TEST) })
        l.addView(outlineButton("NOTIFICATION ACCESS") { openNotificationAccess() })
        if (android.os.Build.VERSION.SDK_INT >= 33) l.addView(outlineButton("APP NOTIFICATION PERMISSION") { requestNotificationPermission() })
        l.addView(outlineButton("BATTERY OPTIMIZATION") { requestBatteryExemption() })
        show(l)
    }

    private fun test() {
        val l = shell(heading("DIAGNOSTICS", "System Test", "Test each layer independently; tests never enable Auto Reply."))

        val listenerStatus = if (hasNotificationAccess()) Status.OK else Status.WARN
        l.addView(card("Notification Reader", if (hasNotificationAccess()) "Android notification listener access is connected." else "Grant Notification Access before testing WhatsApp capture.", listenerStatus))
        l.addView(outlineButton("CHECK NOTIFICATION ACCESS") { openNotificationAccess() })
        l.addView(outlineButton("SIMULATE CAPTURE TEST") {
            store.lastCapture = "TestUser: Hi Aria, test notification"
            store.lastError = ""
            store.logEvent("Synthetic notification capture test completed", true)
            toast("Synthetic capture stored")
            render()
        })

        l.addView(section("Captured message"))
        val sender = input("Sender", "TestUser")
        val message = input("Incoming message", "Hi Aria, test notification", single = false)
        l.addView(sender)
        l.addView(message)

        l.addView(section("API connection"))
        l.addView(card("API", if (store.hasApiKey()) "Credential available • ${store.model}" else "Credential missing • configure API key in Settings", if (store.hasApiKey()) Status.OK else Status.WARN))
        l.addView(primaryButton("TEST API CONNECTION") {
            runApiTest(sender.text.toString(), message.text.toString())
        })

        l.addView(section("Direct responder"))
        val directReady = AriaNotificationListener.lastReplyTarget != null
        l.addView(card("RemoteInput target", if (directReady) "Ready from ${AriaNotificationListener.lastReplyTargetDescription}" else "No live WhatsApp reply action captured yet. Send a WhatsApp message to this device first.", if (directReady) Status.OK else Status.WARN))
        l.addView(outlineButton("SEND DIRECT TEST REPLY") {
            val ok = AriaNotificationListener.sendDirectReply(this, "Aria direct responder test")
            if (ok) {
                store.lastReply = "Aria direct responder test"
                store.logEvent("Direct RemoteInput test reply sent", true)
                toast("Direct reply sent")
            } else {
                store.lastError = "No active WhatsApp RemoteInput target"
                store.logEvent("Direct RemoteInput test failed: no target", false)
                toast("No reply target available")
            }
            render()
        })

        l.addView(section("Full pipeline"))
        l.addView(text("Capture → API → generate → RemoteInput", 14f, muted).apply { setPadding(dp(20), dp(3), dp(20), dp(8)) })
        l.addView(primaryButton("RUN FULL PIPELINE") {
            if (!hasNotificationAccess()) {
                toast("Grant Notification Access first")
            } else if (!store.hasApiKey()) {
                toast("Configure API key first")
                navigate(Nav.SETTINGS)
            } else if (AriaNotificationListener.lastReplyTarget == null) {
                toast("Capture a WhatsApp reply action first")
            } else {
                runApiTest(sender.text.toString(), message.text.toString(), true)
            }
        })

        l.addView(section("Last result"))
        l.addView(card("Captured", store.lastCapture, Status.NEUTRAL))
        l.addView(card("Reply", store.lastReply, if (store.lastReply == "No reply sent yet.") Status.NEUTRAL else Status.OK))
        if (store.lastError.isNotBlank()) l.addView(card("Error", store.lastError, Status.FAIL))
        show(l)
    }

    private fun runApiTest(sender: String, message: String, sendReply: Boolean = false) {
        if (!store.hasApiKey()) {
            store.lastError = "API key is not configured"
            store.logEvent("API test blocked: API key missing", false)
            toast("Configure API key first")
            navigate(Nav.SETTINGS)
            return
        }
        store.logEvent("API test started", null)
        Toast.makeText(this, "Calling API…", Toast.LENGTH_SHORT).show()
        Thread {
            val r = AriaApi.generate(store, sender.ifBlank { "TestUser" }, message.ifBlank { "Hi Aria, test notification" })
            runOnUiThread {
                if (r.ok) {
                    store.lastError = ""
                    store.lastReply = r.reply
                    store.logEvent("API test succeeded (HTTP ${r.code})", true)
                    if (sendReply) {
                        val target = AriaNotificationListener.lastReplyTarget
                        val sent = if (target != null) AriaNotificationListener.sendDirectReply(this, store.marker + r.reply) else false
                        if (sent) {
                            store.lastReply = store.marker + r.reply
                            store.logEvent("Full pipeline RemoteInput reply sent", true)
                            toast("Full pipeline passed")
                        } else {
                            store.lastError = "API succeeded, but RemoteInput reply failed"
                            store.logEvent("Full pipeline failed at RemoteInput", false)
                            toast("API passed; reply failed")
                        }
                    } else toast("API test passed")
                } else {
                    store.lastError = "${r.error.ifBlank { "API request failed" }}${if (r.code > 0) " (HTTP ${r.code})" else ""}"
                    store.logEvent("API test failed: ${store.lastError}", false)
                    toast("API test failed")
                }
                render()
            }
        }.start()
    }

    private fun activityLog() {
        val l = shell(heading("SYSTEM", "Activity", "Recent ARIA events and diagnostic outcomes"))
        if (store.events().isEmpty()) {
            l.addView(card("No activity", "ARIA has not recorded any events yet. Run a diagnostic test to begin.", Status.NEUTRAL))
        } else {
            store.events().forEach { raw ->
                val parts = raw.split("|", limit = 3)
                val time = parts.getOrNull(0)?.toLongOrNull()?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "--:--:--"
                val result = parts.getOrNull(1) ?: "INFO"
                val msg = parts.getOrNull(2) ?: raw
                val status = when (result) { "OK" -> Status.OK; "FAIL" -> Status.FAIL; else -> Status.NEUTRAL }
                l.addView(card(time, msg, status))
            }
        }
        l.addView(outlineButton("CLEAR ACTIVITY") {
            getSharedPreferences("aria", Context.MODE_PRIVATE).edit().remove("events").apply()
            toast("Activity cleared")
            render()
        })
        show(l)
    }

    private fun settings() {
        val l = shell(heading("CONFIGURATION", "Settings", "Persisted controls for automation, API and response behavior"))

        l.addView(section("Automation"))
        val toggle = SwitchMaterial(this).apply {
            text = if (store.autoReply) "Auto Reply enabled" else "Auto Reply disabled"
            setTextColor(white)
            textSize = 16f
            isChecked = store.autoReply
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setOnCheckedChangeListener { _, checked ->
                store.autoReply = checked
                store.logEvent(if (checked) "Auto Reply enabled" else "Auto Reply disabled", null)
                text = if (checked) "Auto Reply enabled" else "Auto Reply disabled"
                toast(if (checked) "Auto Reply enabled" else "Auto Reply disabled")
                homeRefreshIfNeeded()
            }
        }
        l.addView(toggle)
        l.addView(card("Automation state", if (store.autoReply) "ACTIVE • persisted" else "INACTIVE • persisted", if (store.autoReply) Status.OK else Status.WARN))

        l.addView(section("API configuration"))
        val endpoint = input("API endpoint", store.endpoint)
        val model = input("Model", store.model)
        l.addView(endpoint)
        l.addView(model)
        l.addView(card("API credential", if (store.hasApiKey()) "SAVED • encrypted with Android Keystore" else "NOT CONFIGURED • no credential stored", if (store.hasApiKey()) Status.OK else Status.WARN))
        val key = input("Enter API key to save or replace", "", password = true)
        key.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && key.text.toString().trim().isNotBlank()) {
                store.setApiKey(key.text.toString())
                store.logEvent("API key auto-saved securely", true)
            }
        }
        l.addView(key)
        l.addView(primaryButton("SAVE API CONFIGURATION") {
            store.endpoint = endpoint.text.toString().trim()
            store.model = model.text.toString().trim()
            if (key.text.toString().trim().isNotBlank()) store.setApiKey(key.text.toString())
            store.logEvent("API configuration saved", true)
            toast(if (store.hasApiKey()) "API configuration saved" else "API endpoint/model saved")
            render()
        })
        l.addView(outlineButton("TEST API CONNECTION") { runApiTest("TestUser", "Hi Aria, API connection test") })
        if (store.hasApiKey()) l.addView(outlineButton("CLEAR API KEY") {
            store.clearApiKey()
            store.logEvent("API key cleared", null)
            toast("API key removed")
            render()
        })

        l.addView(section("Response behavior"))
        val prompt = input("System prompt", store.systemPrompt, single = false)
        val marker = input("WhatsApp response marker", store.marker, single = false)
        val min = input("Minimum delay (seconds)", store.minDelay.toString())
        min.inputType = InputType.TYPE_CLASS_NUMBER
        val max = input("Maximum delay (seconds)", store.maxDelay.toString())
        max.inputType = InputType.TYPE_CLASS_NUMBER
        l.addView(prompt)
        l.addView(marker)
        l.addView(min)
        l.addView(max)
        l.addView(primaryButton("SAVE RESPONSE SETTINGS") {
            store.systemPrompt = prompt.text.toString().trim()
            store.marker = marker.text.toString()
            store.minDelay = min.text.toString().toIntOrNull() ?: 2
            store.maxDelay = max.text.toString().toIntOrNull() ?: 5
            store.logEvent("Response settings saved", true)
            toast("Response settings saved")
            render()
        })

        l.addView(section("Permissions & device"))
        l.addView(outlineButton("NOTIFICATION ACCESS") { openNotificationAccess() })
        if (android.os.Build.VERSION.SDK_INT >= 33) l.addView(outlineButton("APP NOTIFICATION PERMISSION") { requestNotificationPermission() })
        l.addView(outlineButton("BATTERY OPTIMIZATION") { requestBatteryExemption() })
        l.addView(outlineButton("APP / AUTO-LAUNCH SETTINGS") { openAppSettings() })

        l.addView(section("About"))
        l.addView(card("Aria Reply V22", "Native notification-to-reply engine\nAndroid 8+ • WhatsApp notification RemoteInput when exposed by the installed version.", Status.OK))
        l.addView(card("Security", "API credentials are encrypted with Android Keystore. Incoming notification text is treated as untrusted data.", Status.OK))
        show(l)
    }

    private fun homeRefreshIfNeeded() {
        if (selected == Nav.HOME) render()
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun batteryIgnored(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openNotificationAccess() = startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun requestBatteryExemption() {
        runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    private fun openAppSettings() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
