from http.server import HTTPServer, BaseHTTPRequestHandler
import json, os, urllib.request, urllib.error

CONFIG_FILE = "wa_config.json"

DEFAULT_CONFIG = {
    "groq_api_key": "",
    "groq_model": "openai/gpt-oss-120b",
    "system_prompt": "You are Aria, Krishna's personal AI assistant managing his WhatsApp.\n\nIMPORTANT: Every message you receive is INDEPENDENT. Treat each message fresh.\n\nKrishna's schedule (IST):\n- 7-9 AM: Morning routine\n- 11 AM-4:40 PM: School\n- 5-7 PM: Coaching\n- 11 PM-7 AM: Sleeping\n\nRules:\n1. Treat every message as new.\n2. Introduce yourself as Aria, Krishna's AI assistant — naturally, not every time.\n3. Reply based on Krishna's schedule.\n4. Hinglish = Hinglish, English = English.\n5. Banter gets banter. Match the energy.\n6. Max 1-2 lines. Punchy.\n7. SKIP only for: forwards, spam, blank, media with no text.\n8. For EVERYTHING else — always reply.\n9. Never sound robotic or formal.",
    "delay_min": 8,
    "delay_max": 12,
    "enabled": True
}

def load_config():
    cfg = DEFAULT_CONFIG.copy()
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE) as f:
            saved = json.load(f)
            for k, v in saved.items():
                cfg[k] = v
    # Always prefer env var for API key
    env_key = os.environ.get("GROQ_API_KEY", "")
    if env_key:
        cfg["groq_api_key"] = env_key
    return cfg

def save_config(cfg):
    with open(CONFIG_FILE, "w") as f:
        json.dump(cfg, f, indent=2)

def ask_groq(cfg, sender, message):
    api_key = cfg.get("groq_api_key", "")
    if not api_key:
        return None, "No API key set"

    body = json.dumps({
        "model": cfg.get("groq_model", "openai/gpt-oss-120b"),
        "max_tokens": 150,
        "temperature": 0.7,
        "messages": [
            {"role": "system", "content": cfg.get("system_prompt", "")},
            {"role": "user",   "content": f"Message from {sender}: {message}"}
        ]
    }).encode()

    req = urllib.request.Request(
        "https://api.groq.com/openai/v1/chat/completions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
            "User-Agent": "Mozilla/5.0 (compatible; WAGroqBot/1.0)",
            "Accept": "application/json"
        },
        method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            data = json.loads(r.read())
            reply = data["choices"][0]["message"]["content"].strip()
            if reply.upper().startswith("SKIP"):
                return None, "Groq decided to skip"
            return reply, None
    except urllib.error.HTTPError as e:
        err = e.read().decode()
        return None, f"Groq HTTP {e.code}: {err[:200]}"
    except Exception as e:
        return None, str(e)

DASHBOARD_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>WA Groq Server</title>
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:'Segoe UI',sans-serif;background:#0d1117;color:#e6edf3;min-height:100vh}
  header{background:#161b22;border-bottom:1px solid #30363d;padding:16px 24px;display:flex;align-items:center;gap:12px}
  .logo{width:36px;height:36px;background:linear-gradient(135deg,#25d366,#128c7e);border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:18px}
  header h1{font-size:18px;font-weight:700}
  .status-dot{width:8px;height:8px;border-radius:50%;background:#25d366;margin-left:auto;animation:pulse 2s infinite}
  @keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}
  .container{max-width:720px;margin:0 auto;padding:24px 16px;display:flex;flex-direction:column;gap:20px}
  .card{background:#161b22;border:1px solid #30363d;border-radius:12px;padding:20px}
  .card h2{font-size:14px;font-weight:600;color:#8b949e;text-transform:uppercase;letter-spacing:.06em;margin-bottom:16px}
  label{display:block;font-size:13px;color:#8b949e;margin-bottom:4px;margin-top:12px}
  label:first-of-type{margin-top:0}
  input,select,textarea{width:100%;background:#0d1117;border:1px solid #30363d;color:#e6edf3;border-radius:8px;padding:9px 12px;font-size:14px;font-family:inherit;outline:none;transition:border .2s}
  input:focus,select:focus,textarea:focus{border-color:#25d366}
  textarea{resize:vertical;min-height:140px;line-height:1.6}
  input[type=number]{width:100px}
  .row{display:flex;gap:12px;align-items:flex-end}
  .row .field{flex:1}
  .toggle-row{display:flex;align-items:center;gap:12px;margin-top:8px}
  .toggle{position:relative;width:44px;height:24px;flex-shrink:0}
  .toggle input{opacity:0;width:0;height:0}
  .slider{position:absolute;inset:0;background:#30363d;border-radius:24px;cursor:pointer;transition:.3s}
  .slider:before{content:'';position:absolute;width:18px;height:18px;left:3px;bottom:3px;background:#fff;border-radius:50%;transition:.3s}
  input:checked+.slider{background:#25d366}
  input:checked+.slider:before{transform:translateX(20px)}
  .btn{padding:10px 20px;border-radius:8px;font-size:14px;font-weight:600;cursor:pointer;border:none;transition:opacity .2s}
  .btn-green{background:#25d366;color:#fff}
  .btn-outline{background:transparent;border:1px solid #30363d;color:#8b949e}
  .btn:hover{opacity:.85}
  .btn:disabled{opacity:.4;cursor:default}
  .actions{display:flex;gap:10px;margin-top:16px}
  .webhook-url{background:#0d1117;border:1px solid #30363d;border-radius:8px;padding:10px 12px;font-family:monospace;font-size:13px;color:#25d366;word-break:break-all;flex:1}
  .copy-btn{padding:6px 14px;font-size:12px;border-radius:6px;background:#21262d;border:1px solid #30363d;color:#e6edf3;cursor:pointer;white-space:nowrap}
  .copy-btn:hover{background:#30363d}
  .log-box{background:#0d1117;border:1px solid #30363d;border-radius:8px;padding:12px;font-family:monospace;font-size:12px;height:220px;overflow-y:auto;line-height:1.8}
  .log-entry{padding:2px 0;border-bottom:1px solid #21262d}
  .log-entry:last-child{border:none}
  .log-sent{color:#25d366}
  .log-skip{color:#f59e0b}
  .log-err{color:#ef4444}
  .log-time{color:#8b949e;margin-right:8px}
  .test-result{margin-top:12px;padding:12px;border-radius:8px;font-size:13px;display:none}
  .test-result.ok{background:#052a1a;border:1px solid #25d366;color:#25d366}
  .test-result.err{background:#2a0505;border:1px solid #ef4444;color:#ef4444}
  .saved-toast{display:none;color:#25d366;font-size:13px;margin-left:auto;align-self:center}
  .env-note{font-size:11px;color:#f59e0b;margin-top:4px}
  select option{background:#161b22}
</style>
</head>
<body>
<header>
  <div class="logo">⚡</div>
  <h1>WA Groq — Aria</h1>
  <span class="status-dot"></span>
</header>
<div class="container">
  <div class="card">
    <h2>📡 Webhook URL</h2>
    <div style="display:flex;gap:8px;align-items:center">
      <div class="webhook-url" id="webhookUrl">Loading...</div>
      <button class="copy-btn" onclick="copyUrl()">Copy</button>
    </div>
  </div>
  <div class="card">
    <h2>⚙️ Settings</h2>
    <label>Groq API Key</label>
    <input type="password" id="apiKey" placeholder="gsk_... (or set GROQ_API_KEY env var on Render)" />
    <p class="env-note">💡 Set GROQ_API_KEY in Render environment to persist across restarts</p>
    <label>Model</label>
    <select id="model">
      <option value="openai/gpt-oss-120b">openai/gpt-oss-120b — smartest (14400 RPD)</option>
      <option value="openai/gpt-oss-20b">openai/gpt-oss-20b — faster (14400 RPD)</option>
      <option value="qwen/qwen3.6-27b">qwen/qwen3.6-27b — alternative</option>
    </select>
    <label>System Prompt</label>
    <textarea id="prompt"></textarea>
    <label>Reply Delay</label>
    <div class="row">
      <div class="field"><input type="number" id="delayMin" placeholder="min" min="0" max="60" /></div>
      <div style="color:#8b949e;padding-bottom:10px">to</div>
      <div class="field"><input type="number" id="delayMax" placeholder="max" min="0" max="60" /></div>
      <div style="color:#8b949e;padding-bottom:10px">seconds</div>
    </div>
    <div class="toggle-row" style="margin-top:16px">
      <label class="toggle">
        <input type="checkbox" id="enabled" checked />
        <span class="slider"></span>
      </label>
      <span style="font-size:14px">Auto-reply enabled</span>
      <span class="saved-toast" id="savedToast">✓ Saved</span>
    </div>
    <div class="actions">
      <button class="btn btn-green" onclick="saveSettings()">Save Settings</button>
      <button class="btn btn-outline" onclick="testGroq()">Test Groq</button>
    </div>
    <div class="test-result" id="testResult"></div>
  </div>
  <div class="card">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h2 style="margin:0">📋 Recent Activity</h2>
      <button class="copy-btn" onclick="clearLogs()">Clear</button>
    </div>
    <div class="log-box" id="logBox">
      <div style="color:#8b949e;text-align:center;padding:20px">No activity yet</div>
    </div>
  </div>
</div>
<script>
async function loadConfig() {
  const r = await fetch('/config');
  const c = await r.json();
  document.getElementById('apiKey').value    = c.groq_api_key || '';
  document.getElementById('model').value     = c.groq_model || 'openai/gpt-oss-120b';
  document.getElementById('prompt').value    = c.system_prompt || '';
  document.getElementById('delayMin').value  = c.delay_min ?? 8;
  document.getElementById('delayMax').value  = c.delay_max ?? 12;
  document.getElementById('enabled').checked = c.enabled !== false;
  document.getElementById('webhookUrl').textContent = window.location.origin + '/webhook';
}
async function saveSettings() {
  const cfg = {
    groq_api_key:  document.getElementById('apiKey').value.trim(),
    groq_model:    document.getElementById('model').value,
    system_prompt: document.getElementById('prompt').value,
    delay_min:     parseInt(document.getElementById('delayMin').value) || 8,
    delay_max:     parseInt(document.getElementById('delayMax').value) || 12,
    enabled:       document.getElementById('enabled').checked
  };
  await fetch('/config', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(cfg)});
  const t = document.getElementById('savedToast');
  t.style.display = 'inline';
  setTimeout(() => t.style.display = 'none', 2000);
}
async function testGroq() {
  const btn = event.target;
  btn.disabled = true; btn.textContent = 'Testing...';
  const el = document.getElementById('testResult');
  el.style.display = 'none';
  try {
    const r = await fetch('/test', {method:'POST'});
    const d = await r.json();
    el.style.display = 'block';
    if (d.reply) {
      el.className = 'test-result ok';
      el.textContent = '✅ Groq replied: "' + d.reply + '"';
    } else {
      el.className = 'test-result err';
      el.textContent = '❌ ' + (d.error || 'Unknown error');
    }
  } catch(e) {
    el.style.display = 'block';
    el.className = 'test-result err';
    el.textContent = '❌ ' + e.message;
  }
  btn.disabled = false; btn.textContent = 'Test Groq';
}
async function loadLogs() {
  const r = await fetch('/logs');
  const d = await r.json();
  const box = document.getElementById('logBox');
  if (!d.logs || d.logs.length === 0) {
    box.innerHTML = '<div style="color:#8b949e;text-align:center;padding:20px">No activity yet</div>';
    return;
  }
  box.innerHTML = d.logs.slice().reverse().map(l => {
    const cls  = l.type==='sent'?'log-sent':l.type==='skip'?'log-skip':'log-err';
    const icon = l.type==='sent'?'✅':l.type==='skip'?'⏭':'❌';
    return `<div class="log-entry ${cls}"><span class="log-time">${l.time}</span>${icon} <b>${l.sender}</b>: ${l.text}</div>`;
  }).join('');
}
function clearLogs() { fetch('/logs/clear',{method:'POST'}).then(loadLogs); }
function copyUrl() {
  navigator.clipboard.writeText(document.getElementById('webhookUrl').textContent);
  event.target.textContent='Copied!';
  setTimeout(()=>event.target.textContent='Copy',1500);
}
loadConfig(); loadLogs(); setInterval(loadLogs, 3000);
</script>
</body>
</html>"""

class Handler(BaseHTTPRequestHandler):
    logs = []
    def log_message(self, *a): pass

    def send_json(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type","application/json")
        self.send_header("Content-Length", len(body))
        self.send_header("Access-Control-Allow-Origin","*")
        self.end_headers()
        self.wfile.write(body)

    def send_html(self, html):
        body = html.encode()
        self.send_response(200)
        self.send_header("Content-Type","text/html; charset=utf-8")
        self.send_header("Content-Length", len(body))
        self.end_headers()
        self.wfile.write(body)

    def read_body(self):
        return self.rfile.read(int(self.headers.get("Content-Length",0)))

    def add_log(self, t, sender, text):
        from datetime import datetime
        Handler.logs.append({"type":t,"sender":sender,"text":text[:120],"time":datetime.now().strftime("%H:%M:%S")})
        Handler.logs = Handler.logs[-50:]

    def do_GET(self):
        if self.path in ("/","/dashboard"): self.send_html(DASHBOARD_HTML)
        elif self.path == "/config":        self.send_json(200, load_config())
        elif self.path == "/logs":          self.send_json(200, {"logs": Handler.logs})
        elif self.path == "/health":        self.send_json(200, {"status":"ok"})
        else:                               self.send_json(404, {"error":"not found"})

    def do_POST(self):
        if self.path == "/config":
            try:
                cfg = json.loads(self.read_body())
                save_config(cfg)
                self.send_json(200, {"status":"saved"})
            except Exception as e:
                self.send_json(400, {"error": str(e)})

        elif self.path == "/test":
            cfg = load_config()
            reply, err = ask_groq(cfg, "TestUser", "Hello! Reply in a fun way to test if you are working.")
            if reply: self.add_log("sent","TestUser",reply); self.send_json(200,{"reply":reply})
            else:      self.send_json(200,{"error":err})

        elif self.path == "/logs/clear":
            Handler.logs = []
            self.send_json(200,{"status":"cleared"})

        elif self.path == "/webhook":
            try:
                raw  = self.read_body()
                body = json.loads(raw)
            except Exception:
                self.send_json(400,{"error":"invalid json"}); return

            cfg = load_config()
            if not cfg.get("enabled", True):
                self.send_json(200,{"replies":[]}); return

            query   = body.get("query", body)
            sender  = query.get("sender","") or "Unknown"
            message = query.get("message","") or ""

            # Strip [test] tag from sender
            sender = sender.replace("[test]","").strip()

            if not message.strip():
                self.add_log("skip", sender, "skipped — empty message")
                self.send_json(200,{"replies":[]}); return

            print(f"[WA] {sender}: {message[:100]}")
            reply, err = ask_groq(cfg, sender, message)

            if reply:
                self.add_log("sent", sender, f'-> "{reply}"')
                self.send_json(200,{"replies":[{"message":reply,"delay":cfg.get("delay_min",8),"delayMax":cfg.get("delay_max",12)}]})
            else:
                self.add_log("skip", sender, f'skipped — {err}')
                self.send_json(200,{"replies":[]})

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin","*")
        self.send_header("Access-Control-Allow-Methods","GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers","Content-Type")
        self.end_headers()

if __name__ == "__main__":
    PORT = int(os.environ.get("PORT", 8080))
    print(f"WA Groq Server on :{PORT}")
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
