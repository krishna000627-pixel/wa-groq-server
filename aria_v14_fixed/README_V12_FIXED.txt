ARIA WEB V12 — FIXED

Fixes:
- Fixed static asset serving for app_icon.png, banner.png, logo.png, welcome_card.png and manifest.
- Fixed broken image icons caused by the previous server only serving dashboard.html and API routes.
- Tightened Home layout for mobile: compact Auto Reply, balanced stats cards, cleaner spacing.
- Kept the banner image intact and displayed at its native 4:1 ratio.
- Kept About, TestUser preview, AI Configuration, Context Settings and Reply Delay controls.
- Aria prompt keeps code generation disabled unless Krishna explicitly requests code.
- Routine context: 07:00–09:00 morning routine/study; 11:00–17:00 school/phone unavailable; 17:00–19:00 coaching/occasional checks.

Run:
python wa_groq_server.py
Open:
http://127.0.0.1:8000/dashboard
