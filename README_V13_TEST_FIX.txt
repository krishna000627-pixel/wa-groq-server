ARIA V13 — TEST NAVIGATION FIX

Fix:
- Home > Test AI Assistant no longer navigates to a blank screen.
- The button now opens the same Test Assistant panel used from Settings.
- The previous bug occurred because Home called go('test') while there was no #test page; the actual test UI lives inside the dynamic #subpage.

Run:
python wa_groq_server.py
Open:
http://127.0.0.1:8000/dashboard
