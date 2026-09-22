# Aria Reply V23 — Clay Core

## Added
- Full pastel claymorphism redesign with soft elevated cards and vector navigation/action icons.
- Home control center with live automation, AI provider, notification access and pipeline state.
- Groq + Gemini provider support.
- Separate encrypted Groq and Gemini API-key storage using Android Keystore.
- Gemini `generateContent` REST integration using `x-goog-api-key`.
- Synthetic notification test using Android `RemoteInput`.
- Synthetic test is captured by the real `NotificationListenerService` path.
- Synthetic test can run the configured AI provider and send the generated reply through the captured RemoteInput.
- Test receiver records the returned reply and updates diagnostics.
- V23 versionCode/versionName.

## Gemini
Configure the Gemini API key and model in Settings. The official Gemini API requires an API key and uses the `x-goog-api-key` header. See Google AI documentation.

## Test flow
1. Enable Android Notification Access for Aria.
2. Allow notification permission on Android 13+.
3. Configure the active AI provider and API key.
4. Open Diagnostics.
5. Run `POST ARIA SYNTHETIC MESSAGE`.
6. The notification listener captures the synthetic notification.
7. The listener finds the RemoteInput action.
8. Aria generates a reply using the selected provider.
9. Aria sends that reply through RemoteInput to `AriaTestReceiver`.
10. Diagnostics/Home show the captured message and reply.
