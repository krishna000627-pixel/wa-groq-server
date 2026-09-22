# Aria Reply V28

V28 implementation baseline:
- WhatsApp notification capture and summary filtering
- sender/contact resolution
- persistent per-contact context
- burst batching
- self-reply loop protection
- clarification-aware prompting
- commitment/follow-up extraction
- persistent follow-up tasks
- Groq + Gemini API test lab
- secure API-key storage through Android Keystore
- synthetic capture diagnostics
- RemoteInput direct responder path
- contact permission
- dark cocoa/clay UI
- icon-led navigation
- back navigation
- selected-page indicator
- debug/release CI and APK signature verification

Runtime behavior still depends on Android Notification Access, WhatsApp notification RemoteInput availability, API credentials, and device notification behavior.
