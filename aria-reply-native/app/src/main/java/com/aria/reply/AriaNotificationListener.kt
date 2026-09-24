package com.aria.reply

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class AriaNotificationListener : NotificationListenerService() {

    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val pendingReplies = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private lateinit var store: AriaStore

    override fun onCreate() {
        super.onCreate()
        store = AriaStore(this)
        ensureFollowUpChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val isSelf = pkg == packageName
        val synthetic = isSelf && sbn.notification.extras?.getBoolean("aria_synthetic_test", false) == true

        // ── Never process our own non-synthetic notifications ─────────────────
        if (isSelf && !synthetic) return

        val isWhatsApp = pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b"
        if (!isWhatsApp && !synthetic) return

        val notification = sbn.notification ?: return
        val extras = notification.extras
        val title = extras.getString("android.title")?.trim().orEmpty()
        val text  = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()

        // ── Filter Aria's own sent messages surfaced by WhatsApp ──────────────
        if (title.equals("You", ignoreCase = true)) {
            store.logEvent("Ignored WhatsApp outgoing self message", null); return
        }

        // ── Filter WhatsApp group summary lines e.g. "3 new messages" ────────
        if (SUMMARY_PATTERN.matches(text)) {
            store.logEvent("Ignored WhatsApp summary notification: $text", null); return
        }

        if (title.isBlank() || text.isBlank()) return

        // ── Deduplicate by notification key (SBN key) ─────────────────────────
        val dedupeKey = sbn.key.ifBlank { "$pkg|$title|$text" }
        if (store.checkAndMarkSeen(dedupeKey)) {
            store.logEvent("Deduplicated notification key: $dedupeKey", null); return
        }

        // ── Contact resolution ────────────────────────────────────────────────
        val resolvedName = ContactResolver.resolve(this, title)
        if (resolvedName != title) {
            store.storeResolvedContact(title, resolvedName)
            store.logEvent("Contact resolved: $title → $resolvedName", null)
        }
        val displaySender = resolvedName

        // ── Content dedupe (catches WhatsApp reposting the same text under a ──
        // new notification key, which the key-based check above misses) ───────
        if (!synthetic && store.checkAndMarkSeenContent(displaySender, text)) {
            store.logEvent("Deduplicated repeated message from $displaySender", null); return
        }

        // ── Group filter ────────────────────────────────────────────────────
        val isGroup = notification.extras?.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) == true
        val groupBlocked = isGroup && !store.replyToGroups

        // ── Contact exception filter ────────────────────────────────────────
        val contactBlocked = !store.isAllowedByContactFilter(displaySender)

        store.lastCapture = "$displaySender: $text"
        store.addConversationMessage(displaySender, "user", text)
        store.logEvent("Captured ${if (synthetic) "synthetic" else "WhatsApp"} notification from $displaySender", null)

        if (groupBlocked) {
            store.logEvent("Skipped auto-reply — group replies are off ($displaySender)", null); return
        }
        if (contactBlocked) {
            store.logEvent("Skipped auto-reply — $displaySender is filtered by contact rules (${store.contactFilterMode})", null); return
        }

        // ── Optional WA notification suppression ──────────────────────────────
        if (isWhatsApp && store.suppressWaNotifications && !synthetic) {
            runCatching { cancelNotification(sbn.key) }
            postAriaSummary(displaySender, text)
        }

        val action = notification.actions?.firstOrNull { a ->
            a.remoteInputs?.any { it.allowFreeFormInput } == true
        }
        if (action == null) {
            store.lastTargetReady = false
            store.lastTargetDescription = "Notification captured, but no RemoteInput reply action was exposed."
            store.lastTargetPackage = pkg
            store.lastTargetAt = System.currentTimeMillis()
            if (!store.autoReply && !synthetic) return
            return
        }

        val remoteInput = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return
        val now = System.currentTimeMillis()
        store.lastTargetReady = true
        store.lastTargetDescription = "$pkg • ${action.title ?: "Reply"} • ${remoteInput.resultKey}"
        store.lastTargetPackage = pkg
        store.lastTargetAt = now
        lastReplyAction = action
        lastRemoteInput = remoteInput
        store.logEvent("RemoteInput target detected", true)

        if (!store.autoReply && !synthetic) return

        // ── Burst gate ────────────────────────────────────────────────────────
        // Messages arriving during the delay window are already stored; the
        // eventual generation sees the full burst as conversation context.
        val conversationKey = displaySender
        if (pendingReplies.containsKey(conversationKey) || inFlight.contains(conversationKey)) {
            store.logEvent("Coalesced message into pending reply for $displaySender", null); return
        }

        // ── Seen-gate ────────────────────────────────────────────────────────
        // If Aria already replied to this sender and you haven't opened/cleared
        // that chat yet (WhatsApp cancels the notification when you do), hold
        // off replying again — the message is still captured above either way.
        if (!synthetic && store.isPendingAck(conversationKey)) {
            store.logEvent("Held reply — waiting for you to see the previous reply to $displaySender", null); return
        }

        val delay = if (synthetic) 0L else store.minDelay.coerceAtLeast(0).toLong()
        val future = executor.schedule({
            pendingReplies.remove(conversationKey)
            if (!store.autoReply && !synthetic) return@schedule
            if (!inFlight.add(conversationKey)) return@schedule
            try {
                val history = store.recentContext(conversationKey)
                val latest = history.lastOrNull { it.role == "user" } ?: return@schedule
                val context = history.dropLast(1)
                val result = AriaApi.generate(store, conversationKey, latest.text, context)
                if (!result.ok || result.reply.isBlank()) {
                    store.lastError = "${result.error} (HTTP ${result.code})"
                    store.logEvent("AI generation failed: HTTP ${result.code}", false)
                    return@schedule
                }
                // ── Follow-up tag extraction (strips [[FOLLOWUP: ...]] before sending) ─
                val (cleanedReply, commitment) = FollowUpDetector.extractAndStrip(result.reply)
                val reply = store.marker + cleanedReply

                // ── Deliver via RemoteInput ───────────────────────────────────
                val results = Bundle().apply { putCharSequence(remoteInput.resultKey, reply) }
                val fillIn  = Intent().apply { RemoteInput.addResultsToIntent(action.remoteInputs, this, results) }
                val sent = runCatching { action.actionIntent.send(this, 0, fillIn); true }.getOrDefault(false)
                if (sent) {
                    store.lastReply = reply
                    store.addConversationMessage(conversationKey, "assistant", reply)
                    store.lastError = ""
                    store.logEvent("Reply delivered through RemoteInput", true)
                    if (!synthetic) store.setPendingAck(conversationKey, true)

                    // ── Follow-up tracking ─────────────────────────────────────
                    if (commitment != null) {
                        val isNew = store.addFollowUp(conversationKey, commitment)
                        store.logEvent(if (isNew) "Follow-up task created: $commitment" else "Follow-up task updated (already had one pending for $conversationKey): $commitment", null)
                        FollowUpNotifier.refresh(this@AriaNotificationListener)
                    }
                } else {
                    store.lastError = "RemoteInput PendingIntent could not be sent"
                    store.logEvent("RemoteInput reply send failed", false)
                }
            } finally {
                inFlight.remove(conversationKey)
            }
        }, delay, TimeUnit.SECONDS)
        pendingReplies[conversationKey] = future
    }

    /**
     * Fires when a notification is cleared — either you opened the chat (WhatsApp
     * cancels its notification when the thread is read) or swiped it away. Either way,
     * treat it as "seen" and re-arm auto-reply for that sender (see the seen-gate above).
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val isWhatsApp = pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b"
        if (!isWhatsApp) return
        val title = sbn.notification?.extras?.getString("android.title")?.trim().orEmpty()
        if (title.isBlank() || title.equals("You", ignoreCase = true)) return
        val displaySender = ContactResolver.resolve(this, title)
        if (store.isPendingAck(displaySender)) {
            store.setPendingAck(displaySender, false)
            store.logEvent("Marked $displaySender as seen — auto-reply re-armed", null)
        }
    }

    // ── Aria summary notification (replaces suppressed WA notification) ───────
    private fun postAriaSummary(sender: String, message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureSummaryChannel(nm)
        val n = Notification.Builder(this, CHANNEL_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Aria — $sender")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        nm.notify(("summary_$sender".hashCode()), n)
    }

    // ── Persistent follow-up notification ─────────────────────────────────────
    // Posting/refreshing now lives in FollowUpNotifier so both this service and the
    // Follow-ups screen (MainActivity) can keep the same notification in sync.

    private fun ensureFollowUpChannel() {
        FollowUpNotifier.ensureChannel(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureSummaryChannel(nm)
    }

    private fun ensureSummaryChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_SUMMARY) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_SUMMARY, "Aria Message Summary", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    companion object {
        private const val CHANNEL_SUMMARY  = "aria_summary"

        @Volatile private var lastReplyAction: Notification.Action? = null
        @Volatile private var lastRemoteInput: RemoteInput? = null

        // WhatsApp summary lines like "3 new messages" or "2 new messages from John"
        private val SUMMARY_PATTERN = Regex("^\\d+\\s+new messages?(?:\\s+from\\s+.+)?$", RegexOption.IGNORE_CASE)

        /**
         * "Die catcher" — forces Android to rebind the listener if the OS unbound it
         * (common on aggressive-battery-management OEM ROMs, which can silently drop
         * the listener so notifications stop being captured even though the toggle
         * looks fine). Safe to call anytime; requestRebind() is a no-op if already bound.
         */
        fun rebind(context: Context) {
            runCatching {
                NotificationListenerService.requestRebind(android.content.ComponentName(context, AriaNotificationListener::class.java))
                AriaStore(context).logEvent("Requested notification listener rebind", null)
            }
        }

        fun sendDirectReply(context: Context, reply: String): Boolean {
            val action = lastReplyAction ?: return false
            val ri = lastRemoteInput ?: return false
            return runCatching {
                val results = Bundle().apply { putCharSequence(ri.resultKey, reply) }
                val fillIn  = Intent().apply { RemoteInput.addResultsToIntent(action.remoteInputs, this, results) }
                action.actionIntent.send(context, 0, fillIn)
                AriaStore(context).logEvent("Direct RemoteInput action invoked", true)
                true
            }.getOrElse {
                AriaStore(context).logEvent("Direct RemoteInput action failed: ${it.message}", false)
                false
            }
        }
    }

    override fun onDestroy() {
        pendingReplies.values.forEach { it.cancel(false) }
        pendingReplies.clear(); inFlight.clear(); executor.shutdownNow(); super.onDestroy()
    }
}
