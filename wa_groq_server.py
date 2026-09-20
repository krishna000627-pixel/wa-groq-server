from http.server import HTTPServer, BaseHTTPRequestHandler
import os, json, time, mimetypes
from urllib.parse import urlparse, unquote
from pathlib import Path
from datetime import datetime, timezone, timedelta

try:
    from groq import Groq
except ImportError:
    Groq = None

IST = timezone(timedelta(hours=5, minutes=30))
CFGFILE = "wa_config.json"
CONTEXT = {}
LOGS = []

try:
    PROMPT = open("aria_prompt.txt", encoding="utf-8").read()
except Exception:
    PROMPT = "You are Aria, Krishna's personal AI assistant. Reply naturally and briefly. Never generate code unless Krishna explicitly asks for code."

DEFAULT = {
    "groq_api_key": "",
    "groq_model": "openai/gpt-oss-120b",
    "system_prompt": PROMPT,
    "delay_min": 8,
    "delay_max": 12,
    "context_days": 3,
}


def config():
    c = DEFAULT.copy()
    try:
        with open(CFGFILE, encoding="utf-8") as f:
            c.update(json.load(f))
    except Exception:
        pass
    if os.getenv("GROQ_API_KEY"):
        c["groq_api_key"] = os.getenv("GROQ_API_KEY")
    return c


def save(c):
    # Never persist an environment-provided secret into Git/files.
    if os.getenv("GROQ_API_KEY"):
        c["groq_api_key"] = ""
    with open(CFGFILE, "w", encoding="utf-8") as f:
        json.dump(c, f, ensure_ascii=False, indent=2)


def ist_now():
    return datetime.now(IST)


def now_text():
    return ist_now().strftime("%H:%M:%S")


def log(kind, sender, text):
    LOGS.append({
        "type": kind,
        "sender": str(sender),
        "text": str(text)[:500],
        "time": now_text(),
    })
    del LOGS[:-80]


def ctx(sender, c):
    cutoff = time.time() - int(c.get("context_days", 3)) * 86400
    history = [m for m in CONTEXT.get(sender, []) if m["ts"] >= cutoff]
    CONTEXT[sender] = history[-12:]
    return CONTEXT[sender]


def append_context(sender, role, content):
    CONTEXT.setdefault(sender, []).append({
        "role": role,
        "content": content,
        "ts": time.time(),
    })
    CONTEXT[sender] = CONTEXT[sender][-12:]


def ask(c, sender, message):
    key = c.get("groq_api_key", "")
    if not key:
        return None, "No Groq API key configured."
    if Groq is None:
        return None, "Groq SDK is not installed on this deployment."

    history = ctx(sender, c)
    system = c.get("system_prompt") or PROMPT
    current = ist_now().strftime("%A, %d %B %Y, %I:%M:%S %p IST")
    system = system + "\n\nCURRENT IST: " + current

    messages = [{"role": "system", "content": system}]
    messages.extend({"role": x["role"], "content": x["content"]} for x in history)
    messages.append({"role": "user", "content": f"Message from {sender}: {message}"})

    try:
        client = Groq(api_key=key)
        completion = client.chat.completions.create(
            model=c.get("groq_model", DEFAULT["groq_model"]),
            messages=messages,
            max_tokens=350,
            temperature=0.55,
        )
        choice = completion.choices[0] if completion.choices else None
        if not choice:
            return None, "Groq returned no choices."

        msg = choice.message
        reply = (getattr(msg, "content", None) or "").strip()

        # Some reasoning models may expose a response in reasoning_content.
        if not reply:
            reasoning = (getattr(msg, "reasoning_content", None) or "").strip()
            if reasoning:
                lines = [x.strip() for x in reasoning.splitlines() if x.strip()]
                reply = lines[-1] if lines else ""

        if not reply:
            return None, f"Empty Groq response; finish_reason={getattr(choice, 'finish_reason', None)}"
        if reply.upper() == "SKIP":
            return None, "Skipped by model."

        append_context(sender, "user", message)
        append_context(sender, "assistant", reply)
        return reply, None

    except Exception as e:
        # Preserve the useful upstream error, including Cloudflare 1010 when present.
        text = str(e)
        status = getattr(e, "status_code", None)
        if status:
            return None, f"Groq HTTP {status}: {text[:500]}"
        return None, f"{type(e).__name__}: {text[:500]}"


def raw_debug(c):
    key = c.get("groq_api_key", "")
    if not key:
        return {"status": None, "error": "No Groq API key configured."}
    if Groq is None:
        return {"status": None, "error": "Groq SDK is not installed."}
    try:
        client = Groq(api_key=key)
        completion = client.chat.completions.create(
            model=c.get("groq_model", DEFAULT["groq_model"]),
            messages=[{"role": "user", "content": "Say hello in one short sentence."}],
            max_tokens=100,
        )
        msg = completion.choices[0].message if completion.choices else None
        return {
            "status": 200,
            "transport": "official-groq-python-sdk",
            "model": c.get("groq_model", DEFAULT["groq_model"]),
            "reply": (getattr(msg, "content", None) or "").strip() if msg else "",
        }
    except Exception as e:
        status = getattr(e, "status_code", None)
        return {
            "status": status,
            "transport": "official-groq-python-sdk",
            "error": f"Groq HTTP {status}: {e}" if status else f"{type(e).__name__}: {e}",
        }


class H(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def out(self, code, data, typ="application/json"):
        body = data if isinstance(data, bytes) else json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", typ)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read(self):
        n = int(self.headers.get("Content-Length", "0"))
        return json.loads(self.rfile.read(n)) if n else {}

    def do_OPTIONS(self):
        self.out(200, {})

    def do_GET(self):
        path = urlparse(self.path).path

        if path in ("/", "/dashboard"):
            try:
                self.out(200, Path("dashboard.html").read_bytes(), "text/html; charset=utf-8")
            except Exception as e:
                self.out(500, {"error": str(e)})
            return

        if path == "/debug":
            c = config()
            result = raw_debug(c)
            result["current_ist"] = ist_now().strftime("%A, %d %B %Y, %I:%M:%S %p IST")
            result["api_configured"] = bool(c.get("groq_api_key"))
            self.out(200, result)
            return

        if path in ("/manifest.webmanifest", "/aria_prompt.txt") or path.startswith("/assets/"):
            rel = unquote(path.lstrip("/"))
            base = Path.cwd().resolve()
            target = (base / rel).resolve()
            try:
                if not str(target).startswith(str(base) + os.sep) or not target.is_file():
                    raise FileNotFoundError
                data = target.read_bytes()
                typ = mimetypes.guess_type(str(target))[0] or "application/octet-stream"
                self.out(200, data, typ)
            except Exception:
                self.out(404, {"error": "asset not found"})
            return

        if path == "/config":
            c = config()
            c["groq_api_key"] = "configured" if c.get("groq_api_key") else ""
            self.out(200, c)
            return

        if path == "/logs":
            self.out(200, {"logs": LOGS})
            return

        if path == "/contexts":
            c = config()
            cutoff = time.time() - int(c.get("context_days", 3)) * 86400
            result = {}
            for sender, arr in CONTEXT.items():
                active = [x for x in arr if x["ts"] >= cutoff]
                if active:
                    result[sender] = {
                        "count": len(active),
                        "messages": active,
                        "last_seen": datetime.fromtimestamp(active[-1]["ts"], IST).strftime("%d %b %H:%M IST"),
                    }
            self.out(200, {"contexts": result})
            return

        if path == "/health":
            self.out(200, {"status": "ok", "ist": ist_now().isoformat(), "groq_sdk": Groq is not None})
            return

        self.out(404, {"error": "not found"})

    def do_POST(self):
        try:
            b = self.read()
        except Exception:
            self.out(400, {"error": "invalid json"})
            return

        if self.path == "/config":
            c = config()
            c.update(b)
            save(c)
            self.out(200, {"status": "saved"})
            return

        if self.path == "/logs/clear":
            LOGS.clear()
            self.out(200, {"status": "cleared"})
            return

        if self.path == "/contexts/clear":
            CONTEXT.clear()
            self.out(200, {"status": "cleared"})
            return

        if self.path == "/test":
            sender = str(b.get("sender") or "TestUser").strip()
            message = str(b.get("message") or "").strip()
            if not message:
                self.out(400, {"error": "Message is required."})
                return
            c = config()
            log("received", sender, message)
            reply, err = ask(c, sender, message)
            if not reply:
                # Always preserve the received test message for local context preview.
                append_context(sender, "user", message)
                log("errors" if err and "Skipped" not in err else "skipped", sender, err or "No response")
                self.out(200, {"error": err or "No response", "context_created": True})
            else:
                log("sent", sender, reply)
                self.out(200, {"reply": reply, "context_created": True})
            return

        if self.path == "/webhook":
            query = b.get("query", b) if isinstance(b, dict) else {}
            sender = str(query.get("sender") or query.get("from") or "Unknown").strip()
            message = str(query.get("message") or query.get("text") or "").strip()
            if not message:
                self.out(200, {"replies": []})
                return
            c = config()
            log("received", sender, message)
            reply, err = ask(c, sender, message)
            if reply:
                log("sent", sender, reply)
                self.out(200, {"replies": [{"message": reply, "delay": c.get("delay_min", 8), "delayMax": c.get("delay_max", 12)}]})
            else:
                log("errors" if err and "Skipped" not in err else "skipped", sender, err or "No response")
                self.out(200, {"replies": [], "error": err})
            return

        self.out(404, {"error": "not found"})


if __name__ == "__main__":
    port = int(os.getenv("PORT", "8000"))
    print(f"WA Groq Server on :{port} | IST: {ist_now().isoformat()}")
    HTTPServer(("0.0.0.0", port), H).serve_forever()
