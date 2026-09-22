# Aria Reply V25 — Warm Clay Core

Native Android notification-to-AI reply engine with a redesigned warm-clay interface.

## UI redesign
- Warm brown background instead of white.
- Oat, cream, sage, terracotta, olive and dusty-aqua palette.
- No elevation shadows.
- No bento grid.
- Rounded clay cards with restrained borders.
- Icon-led navigation and action rows.
- Large touch targets and explicit system state.

## V25 features
1. Warm Clay visual system
2. Icon-led bottom navigation
3. Auto Reply state and switch
4. Groq provider
5. Gemini provider
6. Groq endpoint/model controls
7. Gemini model controls
8. Android Keystore encrypted API keys
9. Notification access status
10. Battery optimization status
11. RemoteInput target persistence
12. Synthetic Aria notification capture
13. Synthetic AI reply loop
14. Direct RemoteInput diagnostics
15. API-only diagnostics
16. Local activity log
17. Clear activity log
18. Save-all configuration
19. System prompt reset
20. Runtime/API error surface
21. Response marker control
22. Minimum/maximum reply delay
23. Last capture dashboard state
24. Last reply dashboard state
25. Provider/model readiness state

## Test flow
1. Grant Notification Access.
2. Grant notification permission on Android 13+.
3. Configure the active provider and save.
4. Open Diagnostics.
5. Run **POST ARIA SYNTHETIC MESSAGE**.
6. Aria's notification listener captures the synthetic notification.
7. The listener detects the real RemoteInput action.
8. The active AI provider generates the reply.
9. The reply is sent through RemoteInput.
10. The test receiver records the returned reply.

## Build
The repository workflow uses Java 17 and Gradle 8.9 and uploads the release APK as an artifact.
