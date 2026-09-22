# Aria Reply V30 — Full V28 Feature Scope

versionCode 30 · versionName 30.0

## What's new in V30 (over V29)

### Incoming/Outgoing Separation
- Aria's own WhatsApp reply is never captured as incoming.
- WhatsApp summary notifications ("2 new messages") are ignored.
- Deduplication by notification key + text (not text alone).
- Reply loop prevention.

### Burst / Context Engine
- Messages arriving within the burst delay become one response cycle.
- Entire burst is stored before generation starts.
- Context is sender-specific (no cross-contact leakage).
- Assistant replies stored with role "assistant" — separate from "user" messages.
- Context window size is deterministic and visible in API Test Lab.

### Contact Core
- READ_CONTACTS permission declared and requestable from Settings.
- Notification sender resolved against device contacts.
- Contact identity cached in AriaStore to avoid repeated cursor queries.
- Contact permission state shown in Diagnostics.

### Aria Follow-up Core
Commitment phrases detected (English + Hinglish):
- "I'll tell him / I'll inform / I'll send / I'll remind / I'll let…"
- "bata dunga / inform kar dunga / bhej dunga / remind kar…"
- Creates persistent follow-up tasks stored in AriaStore (JSON).
- Tasks survive app restart (SharedPreferences persist).
- Persistent Aria notification (ongoing) until all tasks checked.
- Home badge shows pending task count.
- Actions tab shows per-task checkboxes; ticking completes and refreshes notification.

### Conversation Intelligence
- Chat list on Chats tab — contacts ordered newest-first.
- Last message preview per contact.
- Full local conversation history per chat (up to 400 entries).
- Per-chat context (no cross-contact context leakage).
- Pending follow-ups associated with sender.

### Notification Core
- Optional raw WhatsApp notification suppression (toggle in Settings).
- When suppression is on, Aria posts a summary notification in its own channel.
- Contact/context processing occurs BEFORE suppression.
- Aria's own notification IDs (7001/7002) never captured as WhatsApp input.

### Navigation Back-Stack
- Chat detail → Back → Chats (not Home).
- API Lab → Back → previous screen (Home / Chats / Settings).
- Settings subflows → Back → Settings.
- Selected bottom-navigation tab highlighted with sage outline indicator.

### API Test Lab
- Separate Groq / Gemini provider selector.
- Custom sender field.
- Custom message field.
- Context toggle + context preview (loads stored context for the given sender).
- HTTP status + response body shown in diagnostics card.

### Provider Core
- Groq highlighted as active provider (shown in Settings hero card).
- Gemini supported independently.
- Encrypted API keys via Android Keystore AES/GCM.
- Provider-specific model stored in AriaStore.
- HTTP status code + provider name in error messages.

### UI
- Dark cocoa/brown pastel base (#241714).
- Sage/olive active states (#A8B58A).
- Muted teal secondary (#86AAA0).
- Terracotta error state (#C97A5A).
- No white canvas. No bento-grid layout. No random colour changes.
- Consistent rounded rectangular clay surfaces.

### Diagnostics
All checks in one screen:
- Notification listener running/not
- Contacts permission granted/denied
- Battery optimization ignored/not
- API provider + key status (length shown, never revealed)
- RemoteInput info
- Last capture
- Context messages stored (count)
- Follow-up state (pending count)
- Auto reply on/off
- Raw suppression on/off
- Last API error

### Boot Persistence
- BootReceiver restores persistent follow-up notification on device reboot.

## Build
- versionCode 30, versionName 30.0
- Release signing preserved (GitHub Secrets flow unchanged from V28).
- APK artifact: aria-reply-native-v30.
