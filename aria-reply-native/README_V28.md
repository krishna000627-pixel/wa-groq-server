# Aria Reply V30

V30 is the complete local Android implementation baseline for the Aria WhatsApp response system.

## Core
- Incoming/outgoing separation and self-reply loop protection
- WhatsApp notification-summary filtering
- Notification-key deduplication
- Sender-specific burst batching
- Persistent per-contact conversation context
- Assistant replies stored separately from incoming messages
- Contact resolution with `READ_CONTACTS`
- Contact/context processing before optional raw-notification suppression
- Aria summary notifications when raw suppression is enabled

## Follow-up Core
- Commitment detection for English/Hinglish future-action phrases
- Persistent follow-up actions
- Actions survive restart
- Five-page bottom navigation with dedicated Actions page
- Checkbox completion
- Persistent Aria follow-up notification until no actions remain

## API Lab
- Groq active provider
- Gemini independent provider
- Custom sender and message
- Context injection toggle
- Visible context preview
- HTTP status and response/error diagnostics
- Android Keystore encrypted API keys

## Navigation/UI
- Home / Test / Chats / Actions / System bottom navigation
- Selected-page indicator
- Chat detail back-stack returns to Chats
- API Lab and System subflows preserve previous screen
- Dark cocoa / clay / sage / muted-teal visual system
- Rounded surfaces and icon-led controls

## Diagnostics
- Notification listener state
- Contacts permission state
- Battery optimization state
- Provider state
- Capture state
- Context state
- Follow-up state
- Last API error

## Build
- `versionCode 28`
- `versionName 30.0`
- Debug + release CI
- Release signing secret support with ephemeral CI fallback
- APK signature verification
