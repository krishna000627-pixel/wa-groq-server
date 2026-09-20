Aria V15 — Groq SDK restore

Restores the previously working official Groq Python SDK transport while preserving the V14 UI, assets, TestUser context, settings, and webhook behavior.

Key changes:
- Uses official `groq` Python SDK instead of direct urllib requests to api.groq.com.
- Adds /debug using the same SDK transport as /test and /webhook.
- Keeps TestUser context locally even when Groq generation fails.
- Serves dashboard assets from /assets/.
- Keeps current IST injected into the system prompt.
- Keeps API keys server-side; GROQ_API_KEY environment variable takes precedence.

Render requirements:
- GROQ_API_KEY must be set as a Render environment variable.
- requirements.txt installs the official Groq SDK.
- Start command can remain: python wa_groq_server.py


V16 UI/context patch:
- Context rows are now tappable and open a dedicated conversation context view.
- Recent Home activity rows also open the matching context when available.
- Added prompt-injection resistance for untrusted incoming message content.
