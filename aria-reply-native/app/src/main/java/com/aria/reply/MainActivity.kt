package com.aria.reply

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
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

    // screen ids: 0=Home 1=Chats 2=Lab 3=Settings 4=System  sub: 5=ChatDetail 6=Diagnostics 7=FollowUps 8=ProviderSettings
    private var screen = 0
    private var selectedChat = ""
    private var lastApiResult: String = ""   // live result shown in Lab
    private var lastApiFollowUpPreview: String = ""
    private var lastApiError:  String = ""
    private var lastApiRaw:    String = ""

    // ── Clay palette ──────────────────────────────────────────────────────────
    private val bg          = Color.rgb(42,  29,  24)
    private val surface     = Color.rgb(52,  37,  31)
    private val surface2    = Color.rgb(61,  43,  36)
    private val surfaceGreen= Color.rgb(48,  66,  50)
    private val surfaceTeal = Color.rgb(44,  60,  60)
    private val surfaceRed  = Color.rgb(73,  44,  37)
    private val surfaceGold = Color.rgb(67,  50,  33)
    private val muted       = Color.rgb(185, 162, 147)
    private val sage        = Color.rgb(143, 175, 143)
    private val teal        = Color.rgb(110, 169, 160)
    private val terracotta  = Color.rgb(196, 126,  99)
    private val gold        = Color.rgb(200, 165, 106)
    private val ink         = Color.rgb(249, 237, 220)
    private val border      = Color.rgb(89,   65,  56)

    // nav-tab screen indices that correspond to bottom tabs
    private val tabScreens = listOf(0, 1, 2, 3, 4)
    private val backStack = ArrayDeque<Int>()

    companion object { private const val REQ_CONTACTS = 2001; private const val REQ_PICK_CONTACT = 2002 }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AriaStore(this)
        setContentView(R.layout.activity_main)
        page = findViewById(R.id.page)
        val openScreen = intent?.getIntExtra("openScreen", -1) ?: -1
        if (openScreen >= 0) screen = openScreen
        bindNav()
        render()
        scheduleWatchdog()
    }

    /** Schedules the periodic "die catcher" alarm that re-requests listener rebind. */
    private fun scheduleWatchdog() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, BootReceiver::class.java).setAction(BootReceiver.ACTION_WATCHDOG),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 15 * 60_000L, 15 * 60_000L, pi)
        }
    }

    override fun onResume() {
        super.onResume()
        store.contactsPermissionGranted = ContactResolver.hasPermission(this)
        if (::page.isInitialized) render()
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<String>, grants: IntArray) {
        if (req == REQ_CONTACTS) {
            val ok = grants.firstOrNull() == PackageManager.PERMISSION_GRANTED
            store.contactsPermissionGranted = ok
            store.logEvent("Contacts permission ${if (ok) "granted" else "denied"}", ok)
            toast(if (ok) "Contacts access granted" else "Contacts access denied")
            render()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_CONTACT || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)?.trim().orEmpty()
                if (name.isNotBlank()) {
                    store.addContactException(name)
                    toast("Added $name"); render()
                }
            }
        }
    }

    private fun pickContact() {
        if (!store.contactsPermissionGranted) { reqContacts(); toast("Grant Contacts access first"); return }
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI), REQ_PICK_CONTACT)
        }.onFailure { toast("Couldn't open Contacts") }
    }

    // ── Nav binding ───────────────────────────────────────────────────────────
    private fun bindNav() {
        findViewById<View>(R.id.navHome).setOnClickListener     { navigate(0) }
        findViewById<View>(R.id.navChats).setOnClickListener    { navigate(1) }
        findViewById<View>(R.id.navTest).setOnClickListener     { navigate(2) }
        findViewById<View>(R.id.navSettings).setOnClickListener { navigate(3) }
        findViewById<View>(R.id.navSystem).setOnClickListener   { navigate(4) }
    }

    private fun navigate(s: Int) {
        if (s != screen) {
            backStack.addLast(screen)
            if (backStack.size > 20) backStack.removeFirst()
        }
        screen = s; render()
    }

    /** Back button navigates within the app's own history instead of exiting straight to the launcher. */
    override fun onBackPressed() {
        if (backStack.isNotEmpty()) { screen = backStack.removeLast(); render() }
        else super.onBackPressed()
    }

    /** Tint the active tab sage, others muted. */
    private fun refreshNav() {
        val activeTab = if (screen in tabScreens) screen else -1
        val tabs = listOf(
            Triple(R.id.navHomeIcon,     R.id.navHomeLabel,     0),
            Triple(R.id.navChatsIcon,    R.id.navChatsLabel,    1),
            Triple(R.id.navTestIcon,     R.id.navTestLabel,     2),
            Triple(R.id.navSettingsIcon, R.id.navSettingsLabel, 3),
            Triple(R.id.navSystemIcon,   R.id.navSystemLabel,   4)
        )
        tabs.forEach { (iconId, labelId, idx) ->
            val active = idx == activeTab
            val color  = if (active) sage else muted
            findViewById<ImageView>(iconId)?.setColorFilter(color)
            findViewById<TextView>(labelId)?.setTextColor(color)
        }
    }

    // ── Primitive helpers ─────────────────────────────────────────────────────
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 14f, color: Int = ink) =
        TextView(this).apply { this.text = text; textSize = size; setTextColor(color); includeFontPadding = false }

    private fun shape(color: Int, radius: Int = 16, stroke: Int = border, sw: Int = 1) =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (sw > 0) setStroke(dp(sw), stroke)
        }

    private fun icon(res: Int, tint: Int = ink, size: Int = 20) = ImageView(this).apply {
        setImageResource(res); setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private var renderedScreen = -1
    private var savedScroll = 0

    private fun show(v: LinearLayout) {
        val prevSv = page.getChildAt(0) as? ScrollView
        val samePage = renderedScreen == screen
        if (prevSv != null && samePage) savedScroll = prevSv.scrollY
        page.removeAllViews()
        val sv = ScrollView(this).apply { isFillViewport = true; addView(v) }
        page.addView(sv)
        if (samePage) sv.post { sv.scrollTo(0, savedScroll) }
        renderedScreen = screen
        refreshNav()
    }

    // ── Shell: page header ────────────────────────────────────────────────────
    private fun shell(title: String, sub: String = "", back: (() -> Unit)? = null): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(28))
            setBackgroundColor(bg)
            val top = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            if (back != null) {
                val btn = ImageButton(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_back); setColorFilter(ink)
                    background = shape(surface2, 12); setPadding(dp(9), dp(9), dp(9), dp(9))
                    setOnClickListener { back() }
                }
                top.addView(btn, LinearLayout.LayoutParams(dp(40), dp(40)))
                top.addView(Space(this@MainActivity), LinearLayout.LayoutParams(dp(10), 1))
            }
            top.addView(tv("ARIA / REPLY", 10f, sage).apply {
                typeface = Typeface.DEFAULT_BOLD; letterSpacing = .18f
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(top)
            addView(tv(title, 28f, ink).apply {
                typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(6), 0, 0)
            })
            if (sub.isNotBlank()) addView(tv(sub, 13f, muted).apply { setPadding(0, dp(2), 0, dp(10)) })
        }

    // ── Section label ─────────────────────────────────────────────────────────
    private fun sec(label: String) = tv(label.uppercase(), 10f, sage).apply {
        typeface = Typeface.DEFAULT_BOLD; letterSpacing = .12f
        setPadding(0, dp(18), 0, dp(6))
    }

    // ── Clay card ─────────────────────────────────────────────────────────────
    private fun card(bg: Int = surface, radius: Int = 16, block: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.background = shape(bg, radius)
            setPadding(dp(15), dp(14), dp(15), dp(14))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }
            block()
        }

    // ── Info row inside a card ────────────────────────────────────────────────
    private fun infoCard(title: String, body: String, bg: Int = surface, iconRes: Int = R.drawable.ic_info, tap: (() -> Unit)? = null): LinearLayout =
        card(bg) {
            val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(icon(iconRes, sage, 19), LinearLayout.LayoutParams(dp(19), dp(19)).apply { setMargins(0, 0, dp(10), 0) })
            row.addView(tv(title.uppercase(), 9f, muted).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = .08f }, LinearLayout.LayoutParams(0, -2, 1f))
            if (tap != null) row.addView(tv("›", 16f, sage))
            addView(row)
            addView(tv(body, 14f, ink).apply { setPadding(0, dp(7), 0, 0) })
            if (tap != null) setOnClickListener { tap() }
        }

    // ── Pill badge ────────────────────────────────────────────────────────────
    private fun pill(text: String, ok: Boolean) = tv(text.uppercase(), 9f, if (ok) sage else terracotta).apply {
        typeface = Typeface.DEFAULT_BOLD; setPadding(dp(9), dp(4), dp(9), dp(4))
        background = shape(if (ok) Color.rgb(48, 66, 50) else Color.rgb(73, 44, 37), 10,
            if (ok) Color.rgb(72, 103, 73) else Color.rgb(118, 64, 52))
    }

    // ── Tappable action row ───────────────────────────────────────────────────
    private fun actionRow(label: String, iconRes: Int, bg: Int = surface2, tap: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            this.background = shape(bg, 14)
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, dp(3), 0, dp(3)) }
            addView(icon(iconRes, sage, 20), LinearLayout.LayoutParams(dp(20), dp(20)).apply { setMargins(0, 0, dp(12), 0) })
            addView(tv(label, 13f, ink).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(tv("›", 18f, muted))
            setOnClickListener { tap() }
        }

    // ── Divider ───────────────────────────────────────────────────────────────
    private fun divider() = View(this).apply {
        setBackgroundColor(border); alpha = 0.4f
        layoutParams = LinearLayout.LayoutParams(-1, 1).apply { setMargins(0, dp(8), 0, dp(8)) }
    }

    // ── EditText field ────────────────────────────────────────────────────────
    private fun field(hint: String, value: String = "", password: Boolean = false, multi: Boolean = false) =
        EditText(this).apply {
            this.hint = hint; setText(value); setTextColor(ink); setHintTextColor(muted)
            textSize = 14f; setPadding(dp(14), dp(10), dp(14), dp(10)); background = shape(surface2, 13)
            isSingleLine = !multi
            if (multi) { minLines = 4; maxLines = 8 }
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(-1, if (multi) -2 else dp(50)).apply { setMargins(0, dp(4), 0, dp(6)) }
        }

    // ── Router ────────────────────────────────────────────────────────────────
    private fun render() = when (screen) {
        0 -> home()
        1 -> chats()
        2 -> lab()
        3 -> settings()
        4 -> systemPage()
        5 -> chatDetail()
        6 -> diagnostics()
        7 -> followUps()
        else -> home()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 0 — HOME
    // ══════════════════════════════════════════════════════════════════════════
    private fun home() {
        val l = shell("Command Center")
        val ready = store.autoReply && store.hasApiKey() && hasNotifAccess()

        // ── Status hero ───────────────────────────────────────────────────────
        val hero = card(if (ready) surfaceGreen else surfaceRed, 20) {
            val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(tv(if (ready) "ARMED" else "STANDBY", 11f, if (ready) sage else terracotta).apply {
                typeface = Typeface.DEFAULT_BOLD; letterSpacing = .12f
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(pill(if (ready) "Live" else "Off", ready))
            addView(row)
            addView(tv(
                if (ready) "Aria is watching for WhatsApp messages."
                else buildString {
                    val issues = mutableListOf<String>()
                    if (!hasNotifAccess()) issues.add("notification access")
                    if (!store.hasApiKey()) issues.add("API key")
                    if (!store.autoReply)  issues.add("auto reply toggle")
                    append("Needs: ${issues.joinToString(", ")}")
                },
                15f, ink
            ).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(10), 0, dp(4)) })
            addView(tv("${store.provider.uppercase()} • ${activeModel()}", 12f, muted))
        }
        l.addView(hero)

        // ── Global status ─────────────────────────────────────────────────────
        l.addView(infoCard("Global replies",
            if (store.autoReply) "ACTIVE — Aria will auto-reply on WhatsApp" else "OFF — nothing will be sent automatically",
            if (store.autoReply) surfaceGreen else surface, R.drawable.ic_bolt) { navigate(3) })

        val totalMsgs = store.history().size
        val totalConvos = store.conversations().size
        l.addView(infoCard("Total capture",
            "$totalMsgs message${if (totalMsgs == 1) "" else "s"} captured across $totalConvos conversation${if (totalConvos == 1) "" else "s"}",
            surface, R.drawable.ic_chat) { navigate(1) })

        val allFollowUps = store.pendingFollowUps()
        l.addView(infoCard("Follow-up tasks",
            if (allFollowUps.isEmpty()) "None pending" else "${allFollowUps.size} pending — tap to view",
            if (allFollowUps.isEmpty()) surface else surfaceGold, R.drawable.ic_bell) { navigate(7) })

        // ── Quick actions ─────────────────────────────────────────────────────
        l.addView(sec("Quick actions"))
        l.addView(actionRow("Test AI pipeline", R.drawable.ic_play, surface2) { runSyntheticTest() })
        l.addView(actionRow("Open API Lab", R.drawable.ic_api, surfaceTeal) { navigate(2) })
        l.addView(actionRow("Edit Routine / Contact Filter", R.drawable.ic_bell, surface2) { navigate(3) })

        // ── Pending follow-ups ────────────────────────────────────────────────
        val pending = store.pendingFollowUps()
        if (pending.isNotEmpty()) {
            l.addView(sec("Follow-ups (${pending.size} pending)"))
            pending.take(3).forEach { task ->
                val c = card(surfaceGold) {
                    val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                    row.addView(tv("□  ", 17f, gold))
                    val col = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    }
                    col.addView(tv(task.sender, 10f, muted).apply { typeface = Typeface.DEFAULT_BOLD })
                    col.addView(tv(task.commitment, 13f, ink))
                    row.addView(col)
                    addView(row)
                }
                c.setOnClickListener { navigate(7) }
                l.addView(c)
            }
            if (pending.size > 3)
                l.addView(actionRow("All follow-ups (${pending.size})", R.drawable.ic_bell, surfaceGold) { navigate(7) })
        }

        // ── Last signal ───────────────────────────────────────────────────────
        if (store.lastCapture != "No notification captured yet." || store.lastReply != "No reply sent yet.") {
            l.addView(sec("Last signal"))
            if (store.lastCapture != "No notification captured yet.")
                l.addView(infoCard("Captured", store.lastCapture.take(120), surface, R.drawable.ic_bell) { navigate(1) })
            if (store.lastReply != "No reply sent yet.")
                l.addView(infoCard("Last reply", store.lastReply.take(120), surfaceGreen, R.drawable.ic_reply))
            if (store.lastError.isNotBlank())
                l.addView(infoCard("Error", store.lastError.take(200), surfaceRed, R.drawable.ic_warning) { navigate(2) })
        }

        show(l)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 1 — CHATS
    // ══════════════════════════════════════════════════════════════════════════
    private fun chats() {
        val l = shell("Chats")
        val names = store.conversations()
        if (names.isEmpty()) {
            l.addView(infoCard("No conversations yet", "Receive a WhatsApp message or run the pipeline test from Home.", surface, R.drawable.ic_chat))
        } else {
            names.forEach { name ->
                val history  = store.history(name)
                val last     = history.lastOrNull()?.text?.take(60) ?: ""
                val resolved = store.resolvedName(name)
                val display  = if (resolved != name) "$resolved" else name
                val tasks    = store.pendingFollowUps().count { it.sender == name }
                val c = card(surface) {
                    val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                    val col = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    }
                    col.addView(tv(display, 15f, ink).apply { typeface = Typeface.DEFAULT_BOLD })
                    col.addView(tv("${history.size} msgs${if (tasks > 0) " • $tasks follow-up" else ""}", 11f, muted).apply { setPadding(0, dp(2), 0, 0) })
                    if (last.isNotBlank()) col.addView(tv(last, 13f, muted).apply { setPadding(0, dp(3), 0, 0) })
                    row.addView(col)
                    row.addView(tv("›", 18f, muted))
                    addView(row)
                }
                c.setOnClickListener { selectedChat = name; navigate(5) }
                l.addView(c)
            }
        }
        show(l)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 5 — CHAT DETAIL
    // ══════════════════════════════════════════════════════════════════════════
    private fun chatDetail() {
        val resolved = store.resolvedName(selectedChat)
        val l = shell(if (resolved != selectedChat) resolved else selectedChat,
            if (resolved != selectedChat) selectedChat else "",
            back = { navigate(1) })

        val history = store.history(selectedChat)
        if (history.isEmpty()) {
            l.addView(infoCard("Empty", "No messages captured yet.", surface, R.drawable.ic_chat))
        } else {
            history.forEach { m ->
                val mine = m.role == "assistant"
                val bubble = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = shape(if (mine) surfaceGreen else surface2, 14)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                        setMargins(if (mine) dp(40) else 0, dp(3), if (mine) 0 else dp(40), dp(3))
                    }
                }
                bubble.addView(tv(if (mine) "ARIA" else m.sender, 9f, if (mine) sage else muted).apply { typeface = Typeface.DEFAULT_BOLD })
                bubble.addView(tv(m.text, 14f, ink).apply { setPadding(0, dp(4), 0, 0) })
                bubble.addView(tv(formatTime(m.timestamp), 9f, muted).apply { gravity = Gravity.END; setPadding(0, dp(4), 0, 0) })
                l.addView(bubble)
            }
        }

        // per-sender follow-ups
        val tasks = store.pendingFollowUps().filter { it.sender == selectedChat }
        if (tasks.isNotEmpty()) {
            l.addView(sec("Pending follow-ups"))
            tasks.forEach { task ->
                val c = card(surfaceGold) {
                    val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                    row.addView(tv("□  ", 17f, gold))
                    row.addView(tv(task.commitment, 13f, ink), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(row)
                }
                c.setOnClickListener { store.markFollowUpDone(task.id); toast("Done ✓"); render() }
                l.addView(c)
            }
        }

        l.addView(sec("Actions"))
        l.addView(actionRow("Use in API Lab", R.drawable.ic_api, surfaceTeal) { navigate(2) })
        l.addView(actionRow("Clear conversation", R.drawable.ic_trash, surfaceRed) {
            store.clearConversation(selectedChat); toast("Cleared"); navigate(1)
        })
        show(l)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 2 — API LAB
    // ══════════════════════════════════════════════════════════════════════════
    private fun lab() {
        val l = shell("API Lab", "Test the AI pipeline directly. Nothing is sent to WhatsApp.")

        l.addView(infoCard("Heads up",
            "This only tests the AI prompt/response. It skips dedupe, group/contact filters, and the seen-gate — those only run on real WhatsApp notifications. Use \"Test AI pipeline\" on Home to exercise the full real path.",
            surfaceGold, R.drawable.ic_info))

        // Provider badge
        val provRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = shape(surfaceTeal, 14)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(12)) }
        }
        provRow.addView(icon(R.drawable.ic_api, sage, 19), LinearLayout.LayoutParams(dp(19), dp(19)).apply { setMargins(0, 0, dp(10), 0) })
        provRow.addView(tv("${store.provider.uppercase()} • ${activeModel()}", 13f, ink).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        val switchBtn = tv("Switch", 11f, teal).apply { typeface = Typeface.DEFAULT_BOLD }
        switchBtn.setOnClickListener {
            store.provider = if (store.provider.equals("GROQ", true)) "GEMINI" else "GROQ"
            toast("${store.provider.uppercase()} selected"); render()
        }
        provRow.addView(switchBtn)
        l.addView(provRow)

        // Inputs
        l.addView(sec("Message"))
        val senderField  = field("Sender name", selectedChat.ifBlank { "TestUser" })
        val messageField = field("Type a message…", "", multi = true)
        l.addView(senderField); l.addView(messageField)

        // Context toggle
        val hasCtx = selectedChat.isNotBlank() && store.history(selectedChat).isNotEmpty()
        val ctxSwitch = Switch(this).apply {
            text = if (hasCtx) "Context: ${selectedChat.take(20)}" else "No context loaded"
            setTextColor(if (hasCtx) ink else muted); textSize = 13f; isChecked = hasCtx
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }
        }
        l.addView(ctxSwitch)

        // Run button
        val runBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(dp(14), dp(14), dp(14), dp(14))
            background = shape(surfaceGreen, 14)
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(8), 0, dp(8)) }
        }
        val runLabel = tv("RUN  →", 14f, ink).apply { typeface = Typeface.DEFAULT_BOLD }
        runBtn.addView(runLabel)
        runBtn.setOnClickListener {
            val who = senderField.text.toString().trim().ifBlank { "TestUser" }
            val msg = messageField.text.toString().trim()
            if (msg.isBlank()) { toast("Enter a message first"); return@setOnClickListener }
            if (!store.hasApiKey()) { toast("No API key — go to Settings"); navigate(3); return@setOnClickListener }
            val ctx = if (ctxSwitch.isChecked && selectedChat.isNotBlank()) store.recentContext(selectedChat) else emptyList()
            runLabel.text = "Calling ${store.provider.uppercase()}…"
            runBtn.background = shape(surface2, 14)
            Thread {
                val r = AriaApi.generate(store, who, msg, ctx)
                runOnUiThread {
                    if (r.ok) {
                        val (cleaned, commitment) = FollowUpDetector.extractAndStrip(r.reply)
                        lastApiResult = cleaned
                        lastApiFollowUpPreview = commitment.orEmpty()
                        lastApiError  = ""
                        lastApiRaw    = r.raw
                        store.lastReply = cleaned
                        store.lastError = ""
                        store.logEvent("Lab API test passed", true)
                    } else {
                        lastApiResult = ""
                        lastApiFollowUpPreview = ""
                        lastApiError  = "${r.error} (HTTP ${r.code})"
                        lastApiRaw    = r.raw.take(600)
                        store.lastError = lastApiError
                        store.logEvent("Lab API test failed: HTTP ${r.code}", false)
                    }
                    render()
                }
            }.start()
        }
        l.addView(runBtn)

        // Result
        if (lastApiResult.isNotBlank()) {
            l.addView(sec("Reply"))
            l.addView(card(surfaceGreen) {
                addView(tv(lastApiResult, 15f, ink))
            })
            if (lastApiFollowUpPreview.isNotBlank()) {
                l.addView(card(surfaceGold) {
                    addView(tv("FOLLOW-UP DETECTED — preview only, Lab doesn't save it", 10f, gold).apply { typeface = Typeface.DEFAULT_BOLD })
                    addView(tv(lastApiFollowUpPreview, 13f, ink).apply { setPadding(0, dp(4), 0, 0) })
                })
            }
        }
        if (lastApiError.isNotBlank()) {
            l.addView(sec("Error"))
            l.addView(card(surfaceRed) {
                addView(tv(lastApiError, 13f, terracotta).apply { typeface = Typeface.DEFAULT_BOLD })
                if (lastApiRaw.isNotBlank()) {
                    addView(divider())
                    addView(tv(lastApiRaw.take(400), 11f, muted))
                }
            })
        }

        show(l)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 3 — SETTINGS
    // ══════════════════════════════════════════════════════════════════════════
    private fun settings() {
        val l = shell("Settings")

        // ── Automation ────────────────────────────────────────────────────────
        l.addView(sec("Automation"))
        val sw = Switch(this).apply {
            text = if (store.autoReply) "Auto Reply  ON" else "Auto Reply  OFF"
            setTextColor(ink); textSize = 14f; isChecked = store.autoReply
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }
            setOnCheckedChangeListener { _, c ->
                store.autoReply = c; text = if (c) "Auto Reply  ON" else "Auto Reply  OFF"
            }
        }
        l.addView(sw)

        val supSw = Switch(this).apply {
            text = if (store.suppressWaNotifications) "Suppress WA notifications  ON" else "Suppress WA notifications  OFF"
            setTextColor(muted); textSize = 13f; isChecked = store.suppressWaNotifications
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }
            setOnCheckedChangeListener { _, c ->
                store.suppressWaNotifications = c
                text = if (c) "Suppress WA notifications  ON" else "Suppress WA notifications  OFF"
            }
        }
        l.addView(supSw)

        val groupSw = Switch(this).apply {
            text = if (store.replyToGroups) "Reply to Groups  ON" else "Reply to Groups  OFF"
            setTextColor(muted); textSize = 13f; isChecked = store.replyToGroups
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }
            setOnCheckedChangeListener { _, c ->
                store.replyToGroups = c
                text = if (c) "Reply to Groups  ON" else "Reply to Groups  OFF"
            }
        }
        l.addView(groupSw)

        // ── Contact filter ───────────────────────────────────────────────────────
        l.addView(sec("Contact Filter"))
        l.addView(tv("Choose who Aria is allowed to auto-reply to.", 12f, muted).apply { setPadding(0, 0, 0, dp(6)) })
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(surface, 13)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(4), 0, dp(4)) }
        }
        val allBtn   = tv("ALL", 12f, ink).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -1, 1f) }
        val blockBtn = tv("BLOCKLIST", 12f, muted).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -1, 1f) }
        val whiteBtn = tv("WHITELIST", 12f, muted).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -1, 1f) }
        fun syncFilterMode() {
            val mode = store.contactFilterMode
            allBtn.background   = shape(if (mode == "NONE") surfaceGreen else surface, 10)
            blockBtn.background = shape(if (mode == "BLOCKLIST") surfaceRed else surface, 10)
            whiteBtn.background = shape(if (mode == "WHITELIST") surfaceTeal else surface, 10)
            allBtn.setTextColor(if (mode == "NONE") ink else muted)
            blockBtn.setTextColor(if (mode == "BLOCKLIST") ink else muted)
            whiteBtn.setTextColor(if (mode == "WHITELIST") ink else muted)
        }
        allBtn.setOnClickListener   { store.contactFilterMode = "NONE";      render() }
        blockBtn.setOnClickListener { store.contactFilterMode = "BLOCKLIST"; render() }
        whiteBtn.setOnClickListener { store.contactFilterMode = "WHITELIST"; render() }
        filterRow.addView(allBtn); filterRow.addView(blockBtn); filterRow.addView(whiteBtn); syncFilterMode()
        l.addView(filterRow)

        if (store.contactFilterMode != "NONE") {
            val label = if (store.contactFilterMode == "BLOCKLIST") "Never reply to:" else "Only reply to:"
            l.addView(tv(label, 11f, muted).apply { setPadding(0, dp(6), 0, dp(2)) })
            store.contactExceptions().sorted().forEach { name ->
                val c = card(surface2) {
                    val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                    row.addView(tv(name, 13f, ink), LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(tv("✕", 15f, terracotta))
                    addView(row)
                }
                c.setOnClickListener { store.removeContactException(name); toast("Removed"); render() }
                l.addView(c)
            }
            val exceptionField = field("Contact name (exactly as it appears in Chats)")
            l.addView(exceptionField)
            l.addView(actionRow("Pick from Contacts", R.drawable.ic_info, surfaceTeal) { pickContact() })
            l.addView(actionRow("Add typed name to list", R.drawable.ic_save, surface2) {
                val n = exceptionField.text.toString().trim()
                if (n.isBlank()) { toast("Enter a name"); return@actionRow }
                store.addContactException(n); toast("Added"); render()
            })
        }

        // ── Routine ───────────────────────────────────────────────────────────────
        l.addView(sec("Routine"))
        l.addView(tv("Tell Aria what you're usually doing at certain times, so it can decide whether to say you're busy.", 12f, muted).apply { setPadding(0, 0, 0, dp(6)) })
        store.routines().forEach { r ->
            val c = card(surface) {
                val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                val col = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                col.addView(tv(r.label, 14f, ink).apply { typeface = Typeface.DEFAULT_BOLD })
                col.addView(tv("${minToHHmm(r.startMinutes)} – ${minToHHmm(r.endMinutes)}", 12f, muted).apply { setPadding(0, dp(2), 0, 0) })
                row.addView(col)
                row.addView(tv("✕", 16f, terracotta))
                addView(row)
            }
            c.setOnClickListener { store.removeRoutine(r.id); toast("Removed"); render() }
            l.addView(c)
        }
        var newStart = 9 * 60
        var newEnd = 17 * 60
        val routineLabelField = field("Routine label (e.g. College, Sleep, Gym)")
        val startBtn = tv("Start: ${minToHHmm(newStart)}", 13f, ink).apply {
            background = shape(surface2, 12); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val endBtn = tv("End: ${minToHHmm(newEnd)}", 13f, ink).apply {
            background = shape(surface2, 12); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        startBtn.setOnClickListener {
            TimePickerDialog(this, { _, hh, mm -> newStart = hh * 60 + mm; startBtn.text = "Start: ${minToHHmm(newStart)}" },
                newStart / 60, newStart % 60, true).show()
        }
        endBtn.setOnClickListener {
            TimePickerDialog(this, { _, hh, mm -> newEnd = hh * 60 + mm; endBtn.text = "End: ${minToHHmm(newEnd)}" },
                newEnd / 60, newEnd % 60, true).show()
        }
        l.addView(routineLabelField)
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }
        }
        timeRow.addView(startBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        timeRow.addView(endBtn, LinearLayout.LayoutParams(0, -2, 1f))
        l.addView(timeRow)
        l.addView(actionRow("Add routine", R.drawable.ic_save, surfaceTeal) {
            val label = routineLabelField.text.toString().trim()
            if (label.isBlank()) { toast("Enter a label"); return@actionRow }
            store.addRoutine(label, newStart, newEnd)
            toast("Routine added"); render()
        })

        // ── Provider ──────────────────────────────────────────────────────────
        l.addView(sec("AI Provider"))
        val provRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(surface, 13)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(4), 0, dp(4)) }
        }
        val groqBtn = tv("GROQ", 13f, ink).apply {
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }
        val gemBtn = tv("GEMINI", 13f, muted).apply {
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }
        fun syncProv() {
            val g = store.provider.equals("GROQ", true)
            groqBtn.background = shape(if (g) surfaceGreen else surface, 10)
            gemBtn.background  = shape(if (!g) surfaceTeal else surface, 10)
            groqBtn.setTextColor(if (g) ink else muted)
            gemBtn.setTextColor(if (!g) ink else muted)
        }
        groqBtn.setOnClickListener { store.provider = "GROQ";   syncProv() }
        gemBtn.setOnClickListener  { store.provider = "GEMINI"; syncProv() }
        provRow.addView(groqBtn); provRow.addView(gemBtn); syncProv(); l.addView(provRow)

        // Groq fields
        l.addView(sec("Groq"))
        val epField    = field("Endpoint URL", store.endpoint)
        val modelField = field("Model", store.model)
        val keyField   = field("API key", if (store.apiKey().isBlank()) "" else "••••••••••••", true)
        l.addView(epField); l.addView(modelField); l.addView(keyField)

        // Gemini fields
        l.addView(sec("Gemini"))
        val gmModel = field("Model", store.geminiModel)
        val gmKey   = field("API key", if (store.geminiApiKey().isBlank()) "" else "••••••••••••", true)
        l.addView(gmModel); l.addView(gmKey)

        // Reply behaviour
        l.addView(sec("Reply Behavior"))
        val promptField = field("System prompt", store.systemPrompt, multi = true)
        val markerField = field("Reply marker", store.marker)
        val minField    = field("Min delay (s)", store.minDelay.toString()).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val maxField    = field("Max delay (s)", store.maxDelay.toString()).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        l.addView(promptField); l.addView(markerField); l.addView(minField); l.addView(maxField)

        // ── Conversation timing ──────────────────────────────────────────────────
        l.addView(sec("Conversation Timing"))
        l.addView(tv("If nobody has messaged in longer than the gap below, Aria opens with the introduction. Otherwise it just replies normally.", 12f, muted).apply { setPadding(0, 0, 0, dp(6)) })
        val introField = field("Introduction message", store.introMessage, multi = true)
        val gapField   = field("Re-intro gap (hours)", store.reIntroGapHours.toString()).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        l.addView(introField); l.addView(gapField)

        // Save
        l.addView(sec("Save"))
        l.addView(actionRow("Save all settings", R.drawable.ic_save, surfaceGreen) {
            store.autoReply            = sw.isChecked
            store.suppressWaNotifications = supSw.isChecked
            store.endpoint             = epField.text.toString().trim().ifBlank { AriaStore.DEFAULT_ENDPOINT }
            store.model                = modelField.text.toString().trim().ifBlank { "openai/gpt-oss-120b" }
            if (!keyField.text.toString().startsWith("••")) store.setApiKey(keyField.text.toString())
            store.geminiModel          = gmModel.text.toString().trim().ifBlank { "gemini-2.5-flash" }
            if (!gmKey.text.toString().startsWith("••")) store.setGeminiApiKey(gmKey.text.toString())
            store.systemPrompt         = promptField.text.toString().trim().ifBlank { AriaStore.DEFAULT_PROMPT }
            store.marker               = markerField.text.toString()
            store.minDelay             = (minField.text.toString().toIntOrNull() ?: 2).coerceAtLeast(0)
            store.maxDelay             = (maxField.text.toString().toIntOrNull() ?: 5).coerceAtLeast(store.minDelay)
            store.replyToGroups        = groupSw.isChecked
            store.introMessage         = introField.text.toString().trim().ifBlank { AriaStore.DEFAULT_INTRO }
            store.reIntroGapHours      = (gapField.text.toString().toIntOrNull() ?: 5).coerceAtLeast(0)
            store.logEvent("Settings saved", true); toast("Saved ✓"); render()
        })
        l.addView(actionRow("Reset system prompt", R.drawable.ic_reset, surface2) {
            store.systemPrompt = AriaStore.DEFAULT_PROMPT; toast("Reset"); render()
        })
        l.addView(actionRow("Clear API keys", R.drawable.ic_trash, surfaceRed) {
            store.clearApiKey(); store.clearGeminiApiKey(); toast("Keys cleared"); render()
        })

        // Permissions
        l.addView(sec("Permissions"))
        l.addView(actionRow("Notification Access", R.drawable.ic_bell, surface2) { openNotifAccess() })
        l.addView(actionRow("Battery Optimization", R.drawable.ic_bolt, surface2) { reqBattery() })
        l.addView(actionRow("Contacts (${if (store.contactsPermissionGranted) "granted" else "not granted"})", R.drawable.ic_info, surface2) { reqContacts() })
        l.addView(actionRow("Restart Notification Listener", R.drawable.ic_reset, surface2) {
            AriaNotificationListener.rebind(this); toast("Requested — give it a few seconds")
        })
        l.addView(tv("If messages stop being captured (common on some phones' battery savers, or with the ringer on silent), tap the restart above, or open your phone's App Info → Battery for Aria and WhatsApp and allow unrestricted background activity / autostart.", 11f, muted).apply { setPadding(0, dp(6), 0, 0) })

        // Diagnostics link
        l.addView(sec("More"))
        l.addView(actionRow("Full Diagnostics", R.drawable.ic_activity, surface2) { navigate(6) })
        l.addView(actionRow("Follow-up Tasks", R.drawable.ic_bell, surfaceGold) { navigate(7) })

        show(l)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 6 — DIAGNOSTICS (sub-page from Settings)
    // ══════════════════════════════════════════════════════════════════════════
    private fun diagnostics() {
        val l = shell("Diagnostics", "", back = { navigate(3) })

        l.addView(infoCard("Notification Access",
            if (hasNotifAccess()) "Connected" else "Not granted — tap to open",
            if (hasNotifAccess()) surfaceGreen else surfaceRed, R.drawable.ic_bell) { openNotifAccess() })

        l.addView(infoCard("Contacts",
            if (store.contactsPermissionGranted) "READ_CONTACTS granted" else "Not granted — sender names shown as-is",
            if (store.contactsPermissionGranted) surfaceGreen else surface, R.drawable.ic_info) { reqContacts() })

        l.addView(infoCard("Battery",
            if (battIgnored()) "Background exemption active" else "Optimization active — may restrict Aria",
            if (battIgnored()) surfaceGreen else surface, R.drawable.ic_bolt) { reqBattery() })

        l.addView(infoCard("RemoteInput",
            if (store.lastTargetReady) store.lastTargetDescription else "No reply target captured yet",
            if (store.lastTargetReady) surfaceGreen else surface, R.drawable.ic_reply))

        l.addView(infoCard("Capture",
            store.lastCapture, surface, R.drawable.ic_bell))

        l.addView(infoCard("Context",
            "${store.conversations().size} conversations • ${store.history().size} messages", surface, R.drawable.ic_chat))

        l.addView(infoCard("Follow-ups",
            "${store.pendingFollowUps().size} pending • ${store.followUps().size} total", surface, R.drawable.ic_bolt))

        val err = store.lastError
        l.addView(infoCard("Last API Error",
            if (err.isBlank()) "None" else err,
            if (err.isBlank()) surface else surfaceRed, R.drawable.ic_warning))

        l.addView(sec("Actions"))
        l.addView(tv("Runs the exact real capture pipeline (dedupe, group/contact filters, seen-gate) using a fake notification — the closest thing to a live WhatsApp test.", 12f, muted).apply { setPadding(0, 0, 0, dp(6)) })
        val testSenderField = field("Test sender name", "Aria Test Contact")
        val testGroupSw = Switch(this).apply {
            text = "Simulate as a group message"
            setTextColor(muted); textSize = 13f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(8)) }
        }
        l.addView(testSenderField); l.addView(testGroupSw)
        l.addView(actionRow("Post synthetic test",     R.drawable.ic_play,  terracotta)  {
            runSyntheticTest(testSenderField.text.toString().trim().ifBlank { "Aria Test Contact" }, testGroupSw.isChecked)
        })
        l.addView(actionRow("Direct RemoteInput test", R.drawable.ic_reply, surfaceTeal) { directReplyTest() })
        l.addView(tv("To confirm groups are actually excluded: turn OFF \"Reply to Groups\" in Settings, run this with the switch above ON, then check the Event Log below for \"Skipped auto-reply — group replies are off\". Same idea for Contact Filter — set a BLOCKLIST/WHITELIST name that matches the sender above.", 11f, muted).apply { setPadding(0, dp(6), 0, 0) })

        l.addView(sec("Event Log"))
        store.events().take(15).forEach { raw ->
            val p   = raw.split('|', limit = 3)
            val tag = p.getOrElse(1) { "INFO" }
            val msg = p.getOrElse(2) { raw }
            l.addView(card(if (tag == "FAIL") surfaceRed else surface) {
                addView(tv(tag, 9f, if (tag == "FAIL") terracotta else sage).apply { typeface = Typeface.DEFAULT_BOLD })
                addView(tv(msg, 12f, ink).apply { setPadding(0, dp(3), 0, 0) })
            })
        }
        l.addView(actionRow("Clear event log", R.drawable.ic_trash, surface2) { store.clearEvents(); toast("Cleared"); render() })

        show(l)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 7 — FOLLOW-UPS (sub-page)
    // ══════════════════════════════════════════════════════════════════════════
    private fun followUps() {
        val l = shell("Follow-ups", "", back = { navigate(0) })
        val tasks = store.followUps()

        if (tasks.isEmpty()) {
            l.addView(infoCard("None", "Aria has made no commitments yet.", surface, R.drawable.ic_bell))
        } else {
            val pending = tasks.filter { !it.done }
            val done    = tasks.filter { it.done }

            if (pending.isNotEmpty()) {
                l.addView(sec("Pending (${pending.size}) — tap to mark done"))
                pending.forEach { task ->
                    val c = card(surfaceGold) {
                        val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                        row.addView(tv("□  ", 18f, gold))
                        val col = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                        }
                        col.addView(tv(task.sender, 10f, muted).apply { typeface = Typeface.DEFAULT_BOLD })
                        col.addView(tv(task.commitment, 14f, ink).apply { setPadding(0, dp(2), 0, 0) })
                        col.addView(tv(formatTime(task.createdAt), 9f, muted).apply { setPadding(0, dp(3), 0, 0) })
                        row.addView(col)
                        addView(row)
                    }
                    c.setOnClickListener { store.markFollowUpDone(task.id); toast("Done ✓"); render() }
                    l.addView(c)
                }
            }
            if (done.isNotEmpty()) {
                l.addView(sec("Done (${done.size})"))
                done.takeLast(5).reversed().forEach { task ->
                    l.addView(card(surfaceGreen) {
                        val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                        row.addView(tv("✓  ", 16f, sage))
                        val col = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                        }
                        col.addView(tv(task.sender, 10f, muted).apply { typeface = Typeface.DEFAULT_BOLD })
                        col.addView(tv(task.commitment, 13f, muted).apply { setPadding(0, dp(2), 0, 0) })
                        row.addView(col)
                        addView(row)
                    })
                }
            }
        }

        l.addView(sec("Actions"))
        l.addView(actionRow("Clear completed", R.drawable.ic_trash, surface2) {
            store.clearDoneFollowUps(); toast("Cleared"); render()
        })
        show(l)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 4 — SYSTEM
    // ══════════════════════════════════════════════════════════════════════════
    private fun systemPage() {
        val l = shell("System")
        l.addView(infoCard("Version",     "V35 • Back Navigation • Scroll Fix • Contact Picker • Lab Tag Fix • Testable Filters", surfaceTeal, R.drawable.ic_info))
        l.addView(infoCard("Security",    "API keys encrypted with Android Keystore AES-256-GCM. Notification text is untrusted input — never executed as instructions.", surface, R.drawable.ic_key))
        l.addView(infoCard("Pipeline",    "WA notification → dedup (key + content) → contact resolve → group/contact filter → burst engine → seen-gate → context window → AI generation → RemoteInput delivery → follow-up tag extraction.", surfaceGreen, R.drawable.ic_bolt))
        l.addView(infoCard("Design",      "Dark cocoa base • sage active states • muted teal secondary • terracotta errors. Claymorphism — rounded rectangular clay surfaces, no white canvas, no bento grid.", surface2, R.drawable.ic_palette))
        l.addView(infoCard("Compatibility","Android 8+ (API 26+). WA direct replies require WhatsApp to expose a RemoteInput action on the notification.", surface, R.drawable.ic_info))
        l.addView(sec("Actions"))
        l.addView(actionRow("Full Diagnostics", R.drawable.ic_activity, surface2) { navigate(6) })
        show(l)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun runSyntheticTest(sender: String = "Aria Test Contact", isGroup: Boolean = false) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            toast("Allow notifications then try again"); return
        }
        if (!hasNotifAccess()) { openNotifAccess(); toast("Grant Notification Access then try again"); return }
        if (!store.hasApiKey()) { navigate(3); toast("Add an API key first"); return }
        store.lastError = ""; store.lastTargetReady = false
        store.logEvent("Synthetic test posted${if (isGroup) " (simulated group)" else ""} — sender: $sender", null)
        AriaTestNotification.post(this, sender, "Hello Aria, this is a synthetic capture test.", isGroup)
        toast("Test message posted — check Event Log below"); navigate(6)
    }

    private fun directReplyTest() {
        if (!store.lastTargetReady) { toast("No RemoteInput target captured yet"); return }
        Thread {
            val ok = AriaNotificationListener.sendDirectReply(this, "Aria direct responder test")
            runOnUiThread {
                if (ok) { store.lastReply = "Aria direct responder test"; store.logEvent("Direct reply sent", true); toast("Sent") }
                else { store.lastError = "No active RemoteInput target"; toast("Failed") }
                render()
            }
        }.start()
    }

    private fun activeModel()    = if (store.provider.equals("GEMINI", true)) store.geminiModel else store.model
    private fun hasNotifAccess() = (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName)
    private fun openNotifAccess()= startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    private fun battIgnored()    = (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    private fun reqBattery() {
        runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
    private fun reqContacts() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
    private fun formatTime(ts: Long) = SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()).format(Date(ts))
    private fun minToHHmm(m: Int) = String.format(Locale.getDefault(), "%02d:%02d", (m / 60) % 24, m % 60)
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
