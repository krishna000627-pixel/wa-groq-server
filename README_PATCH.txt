ARIA NATIVE UI V5
==================

Changes:
- Native-style bottom navigation matching the supplied reference.
- Settings is a clean list of bold white titles only.
- Every Settings item opens its own page.
- AI Configuration shows only API connected / API not connected; no API key is displayed.
- Debug page tests the real /debug endpoint and reports connection status.
- Test Assistant sends through the real /webhook endpoint.
- Contexts includes TestUser preview.
- Context detail shows received messages and Aria replies.
- Backend now logs received messages as well as sent/skip/error events.
- About page includes Krishna Tiwari, Class 12 PCM and Aria application details.
- Inline SVG/CSS icons and the Home banner are embedded; no external image dependency.

Termux:
  cd ~/wa-groq-server
  unzip -o ~/Downloads/aria_native_ui_v5.zip
  git add dashboard.html wa_groq_server.py
  git commit -m "feat: native Aria UI v5 with context detail and working debug"
  git push

Important:
The ZIP contains the two replacement files only. Keep your existing wa_config.json and deployment files.
