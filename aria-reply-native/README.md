# Aria Reply V22

V22 is a full UI/UX and reliability rebuild on top of the V21 native notification-to-reply engine.

## Core changes

- Redesigned dark ARIA control-center UI.
- Four focused navigation areas: Home, Test, Activity, Settings.
- Persistent Auto Reply state reflected consistently across the app.
- API credentials are entered blank and stored encrypted with Android Keystore.
- API configuration explicitly reports NOT CONFIGURED / SAVED states.
- Dedicated API connection test with readable errors.
- Notification access status and device controls.
- Synthetic notification-reader test.
- Direct RemoteInput responder test after a WhatsApp reply action is captured.
- Full Capture → API → Generate → RemoteInput diagnostic pipeline.
- Persistent activity/event log.
- Structured error and success states.
- Improved permission and battery-optimization controls.
- About/security information moved into Settings.
- Fixed the V21 Kotlin `const val` build error in `AriaStore.kt`.
- V22 release CI builds and uploads the signed APK directly; redundant post-build signing/verification steps were removed.

## Security

The API key is never displayed after saving. It is encrypted using an Android Keystore AES-GCM key. Notification text is treated as untrusted input and is separated from the system prompt.

## Build

The GitHub Actions workflow restores the configured release keystore, validates it, injects signing credentials into Gradle, builds `assembleRelease`, and uploads `aria-reply-native-v22-signed.apk`.
