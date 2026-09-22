# Aria Reply Native V20

Native Android background reply engine for WhatsApp / WhatsApp Business.

## Architecture

`NotificationListenerService → AriaApi → direct AI/JSON endpoint → RemoteInput reply`

The native client no longer requires the Render webhook for its primary API path. You can point it directly at a Groq/OpenAI-compatible `/chat/completions` endpoint or a JSON endpoint returning `replies[0].message`, `reply`, or `message`.

## Features

- Multi-page native UI: Control Center, System Test, Settings, About
- WhatsApp + WhatsApp Business notification capture
- RemoteInput reply delivery when the notification exposes a reply action
- Direct API call from the phone; no Render relay required
- Groq/OpenAI-compatible chat-completions request format
- Generic JSON endpoint support
- Android Keystore encryption for the stored API key
- Prompt-injection guard: notification text is explicitly untrusted data
- Configurable model, endpoint, system prompt, marker and delay window
- `*Automated Response*` marker support; WhatsApp renders its own message UI
- Notification-access diagnostics
- Battery-optimization exemption shortcut
- App settings / OEM auto-launch settings shortcut
- API test and notification capture test
- High-quality Aria PNG assets embedded in `res/drawable-nodpi`
- CI release workflow with optional secure keystore injection

## Build locally

Debug build:

```bash
gradle :app:assembleDebug
```

Release signing is environment-driven. The repository intentionally does **not** commit a release keystore.

Required environment variables for a signed release:

```text
ARIA_KEYSTORE_FILE
ARIA_KEYSTORE_PASSWORD
ARIA_KEY_ALIAS
ARIA_KEY_PASSWORD
```

## GitHub Actions signing

Create repository secrets:

- `ARIA_KEYSTORE_BASE64`
- `ARIA_KEYSTORE_PASSWORD`
- `ARIA_KEY_ALIAS`
- `ARIA_KEY_PASSWORD`

The workflow strips accidental whitespace from the Base64 secret, decodes it, validates the JKS with `keytool`, then assembles the signed APK.

If the Base64 secret itself is corrupt, regenerate it directly from the exact JKS file with:

```bash
base64 -w 0 ~/aria-keystore/aria-release.jks
```

## Generate a fresh release keystore

Use `tools/make-keystore.sh` in Termux. The script creates the JKS outside the Git repository and prints the four values required by CI.

## WhatsApp behavior

Aria can only reply through a notification action that exposes `RemoteInput`. If a WhatsApp version/device does not expose a reply action for a notification, Aria records the notification but cannot inject a reply into WhatsApp.

The marker is ordinary message text. Aria cannot create a privileged/native WhatsApp badge or alter WhatsApp's internal UI.

## V21 CI pipeline

The release workflow restores and validates the signing keystore, injects the signing values into Gradle, runs `assembleRelease`, and uploads the APK directly. There is no redundant second `apksigner sign` step.

The app's release build is signed by the Android Gradle Plugin when the signing environment variables are present. Signature verification can be performed separately when needed, but it is not required to produce the release artifact.

## V21 UI/API fixes

- Programmatic UI dimensions now use density-aware dp values, preventing clipped button labels on higher-density devices.
- Action buttons use a consistent native dark/accent treatment and stable 52dp touch targets.
- API tests now detect an empty API key before making a request.
- HTTP 401 failures are surfaced as an explicit missing/rejected API-key error.
- API keys remain encrypted through Android Keystore storage.
