ARIA BACKEND FIX

What changed:
1. Replaced urllib/Python-urllib Groq transport with Groq's official Python SDK.
2. Added the groq dependency.
3. Debug endpoint now reports the SDK transport and a sanitized completion result.
4. Context expiry is based on IST calendar dates: today + previous 2 dates.
5. Runtime supplies current IST, last interaction, gap minutes and fresh-context state.
6. 1-5 hour return gaps trigger a brief AI-agent greeting instruction.
7. Built-in prompt is restored if the dashboard prompt becomes blank/deleted.
8. Requests for things Krishna personally must handle no longer cause the model to invent a subject/book/item.
9. GROQ_API_KEY from Render environment is not exposed through /config.

Install:
pip install -r requirements.txt

Run:
python wa_groq_server.py

Termux replacement:
cd ~/wa-groq-server
cp wa_groq_server.py wa_groq_server.py.bak
cp requirements.txt requirements.txt.bak
# Copy the files from this ZIP into the repo, then:
git add wa_groq_server.py requirements.txt
git commit -m "fix: use official Groq SDK and harden Aria context"
git push

Test:
curl -s https://YOUR-RENDER-SERVICE.onrender.com/health

curl -s https://YOUR-RENDER-SERVICE.onrender.com/debug

curl -s -X POST https://YOUR-RENDER-SERVICE.onrender.com/webhook   -H "Content-Type: application/json"   -d '{"sender":"TestUser","message":"Say hello in one short sentence."}'

Important:
Keep GROQ_API_KEY only in Render Environment Variables.
Do not put the key in the APK, dashboard source, GitHub, or this ZIP.
