package com.aria.reply

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.drawable.GradientDrawable
import androidx.core.app.ActivityCompat

class MainActivity : Activity() {
    private lateinit var page: FrameLayout
    private lateinit var store: AriaStore
    private val bg = Color.rgb(7, 17, 13)
    private val surface = Color.rgb(17, 26, 22)
    private val accent = Color.rgb(32, 232, 137)
    private val white = Color.rgb(244, 255, 248)
    private val muted = Color.rgb(168, 184, 175)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AriaStore(this)
        setContentView(R.layout.activity_main)
        page = findViewById(R.id.page)
        findViewById<Button>(R.id.navHome).setOnClickListener { home() }
        findViewById<Button>(R.id.navTest).setOnClickListener { test() }
        findViewById<Button>(R.id.navSettings).setOnClickListener { settings() }
        findViewById<Button>(R.id.navAbout).setOnClickListener { about() }
        home()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float = 16f, color: Int = white): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = true
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun title(value: String) = text(value, 30f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(4), 0, dp(12)) }

    private fun card(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        setBackgroundColor(surface)
        addView(text(label.uppercase(), 11f, muted))
        addView(text(value, 17f))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(8)) }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        setTextColor(white)
        isAllCaps = false
        gravity = Gravity.CENTER
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = GradientDrawable().apply {
            setColor(surface)
            setStroke(dp(1), accent)
            cornerRadius = dp(10).toFloat()
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, dp(7), 0, dp(7)) }
    }

    private fun input(hint: String, value: String, password: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint; setText(value); setTextColor(white); setHintTextColor(muted); setSingleLine(false); setPadding(dp(8), dp(8), dp(8), dp(8))
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(10)) }
    }

    private fun base(screen: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(12)); setBackgroundColor(bg)
        addView(text("ARIA", 13f, accent)); addView(title(screen))
    }

    private fun show(content: LinearLayout) {
        page.removeAllViews()
        page.addView(ScrollView(this).apply { isFillViewport = true; addView(content) })
    }

    private fun home() {
        val l = base("Control Center")
        l.addView(card("Automation", if (store.autoReply) "ACTIVE — background reply engine enabled" else "OFF — enable Auto Reply in Settings"))
        l.addView(card("Notification access", if (hasNotificationAccess()) "CONNECTED" else "NOT CONNECTED"))
        l.addView(card("Battery", if (batteryIgnored()) "OPTIMIZATION EXEMPT" else "OPTIMIZATION ACTIVE"))
        l.addView(card("API", store.endpoint))
        l.addView(button("NOTIFICATION ACCESS") { openNotificationAccess() })
        if (android.os.Build.VERSION.SDK_INT >= 33) l.addView(button("APP NOTIFICATION PERMISSION") { requestNotificationPermission() })
        l.addView(button("BATTERY OPTIMIZATION") { requestBatteryExemption() })
        l.addView(button("APP / AUTO-LAUNCH SETTINGS") { openAppSettings() })
        l.addView(button("OPEN SETTINGS") { settings() })
        show(l)
    }

    private fun test() {
        val l = base("System Test")
        l.addView(text("Run each layer independently. Tests never enable automation automatically.", 15f, muted))
        l.addView(button("TEST NOTIFICATION ACCESS") { openNotificationAccess() })
        l.addView(button("TEST BATTERY OPTIMIZATION") { requestBatteryExemption() })
        val sender = input("Sender", "TestUser"); l.addView(sender)
        val message = input("Incoming message", "Hi Aria, test notification"); l.addView(message)
        l.addView(button("SIMULATE CAPTURE") {
            store.lastCapture = "${sender.text}: ${message.text}"
            toast("Capture stored")
            test()
        })
        val result = text("Last captured:\n${store.lastCapture}\n\nLast reply:\n${store.lastReply}", 14f, muted)
        l.addView(result)
        l.addView(button("TEST DIRECT API CALL") {
            l.addView(text("Calling API...", 14f, accent))
            Thread {
                val r = AriaApi.generate(store, sender.text.toString(), message.text.toString())
                runOnUiThread {
                    l.addView(text(if (r.ok) "HTTP ${r.code}\n${r.reply}" else "API ERROR\n${r.error}\n${r.raw.take(1200)}", 14f, if (r.ok) accent else white))
                }
            }.start()
        })
        show(l)
    }

    private fun settings() {
        val l = base("Settings")
        val toggle = Switch(this).apply { text = "AUTO REPLY"; setTextColor(white); isChecked = store.autoReply }
        l.addView(toggle)
        l.addView(text("API endpoint", 12f, muted))
        val endpoint = input("Groq/OpenAI-compatible or JSON webhook URL", store.endpoint); l.addView(endpoint)
        l.addView(text("Model", 12f, muted))
        val model = input("Model name", store.model); l.addView(model)
        l.addView(text("API key — stored using Android Keystore", 12f, muted))
        val key = input("API key", store.apiKey(), true); l.addView(key)
        l.addView(text("System prompt", 12f, muted))
        val prompt = input("Reply policy", store.systemPrompt); l.addView(prompt)
        l.addView(text("WhatsApp marker", 12f, muted))
        val marker = input("Prefix, e.g. *Automated Response*\\n", store.marker); l.addView(marker)
        l.addView(text("Delay window (seconds)", 12f, muted))
        val min = input("Minimum", store.minDelay.toString()); min.inputType = InputType.TYPE_CLASS_NUMBER; l.addView(min)
        val max = input("Maximum", store.maxDelay.toString()); max.inputType = InputType.TYPE_CLASS_NUMBER; l.addView(max)
        l.addView(button("SAVE CONFIGURATION") {
            store.autoReply = toggle.isChecked
            store.endpoint = endpoint.text.toString().trim()
            store.model = model.text.toString().trim()
            store.setApiKey(key.text.toString())
            store.systemPrompt = prompt.text.toString().trim()
            store.marker = marker.text.toString()
            store.minDelay = min.text.toString().toIntOrNull() ?: 2
            store.maxDelay = max.text.toString().toIntOrNull() ?: 5
            toast("Configuration saved")
        })
        l.addView(button("BATTERY OPTIMIZATION") { requestBatteryExemption() })
        l.addView(button("APP / AUTO-LAUNCH SETTINGS") { openAppSettings() })
        show(l)
    }

    private fun about() {
        val l = base("About")
        val logo = ImageView(this).apply {
            setImageResource(com.aria.reply.R.drawable.aria_logo)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(-1, 180).apply { setMargins(0, 0, 0, 10) }
        }
        l.addView(logo)
        l.addView(card("Aria Reply", "Native notification-to-reply engine"))
        l.addView(text("Pipeline", 13f, muted)); l.addView(text("WhatsApp notification → capture → direct API → guarded reply → RemoteInput reply action"))
        l.addView(text("Security model", 13f, muted)); l.addView(text("Incoming notification text is treated as untrusted data. It cannot override the system prompt or request secrets."))
        l.addView(text("WhatsApp limitation", 13f, muted)); l.addView(text("The app can prepend a text marker such as *Automated Response*. WhatsApp controls how its own UI renders messages; Aria cannot create a native badge inside WhatsApp."))
        l.addView(text("Compatibility", 13f, muted)); l.addView(text("Android 8+; WhatsApp and WhatsApp Business notification reply actions when exposed by the installed version."))
        show(l)
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
