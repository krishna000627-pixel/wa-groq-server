# Aria Reply Native V11

Native Kotlin Android notification-reply client for the existing Aria WhatsApp backend.

## V11

- `versionCode`: 11
- `versionName`: 11.0
- Dedicated release keystore included at `keystore/aria-release.jks`
- Alias: `aria-release`
- CI verifies and uses the included release key
- GitHub Actions generates the Gradle wrapper and builds the signed release APK

## Pipeline

WhatsApp notification
→ Android NotificationListenerService
→ Aria backend
→ Groq
→ JSON reply
→ WhatsApp Direct Reply action

Default backend:

`https://wa-groq-server.onrender.com/webhook`

Expected request:

`{"sender":"...","message":"..."}`

Expected response:

`{"replies":[{"message":"...","delay":8,"delayMax":12}]}`

The app uses WhatsApp's exposed `RemoteInput` reply action and therefore does not add a third-party auto-responder watermark.

## Android limitation

The app can only reply when the WhatsApp notification exposes a compatible Direct Reply/RemoteInput action. It does not bypass Android or WhatsApp security restrictions.

## Release signing

The dedicated release keystore is included because you explicitly requested it.

**Keep `keystore/aria-release.jks` and its password backed up securely. Do not publish the password or keystore publicly.**

For production GitHub security, move the password values to GitHub Actions Secrets before making the repository public.
