from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
import json
import os
import time
import urllib.request
import urllib.error

CONFIG_FILE = "wa_config.json"
CONTEXT_FILE = "wa_context.json"
IST = ZoneInfo("Asia/Kolkata")


# ============================================================
# BUILT-IN AI PROMPT PRESETS
# ============================================================

PROMPT_PRESETS = {

    "ai_assistant": """You are Aria, an AI assistant managing Krishna's WhatsApp.

IDENTITY
- You are an AI assistant.
- Never claim to be Krishna or another human.
- Never impersonate Krishna.
- If directly asked who you are, clearly say you are Aria, Krishna's AI assistant.
- Do not repeatedly announce that you are an AI when it is unnecessary.
- Never pretend you personally experienced something.

NATURAL CONVERSATION
- Sound like a capable modern AI assistant.
- Be natural, concise, conversational and context-aware.
- Match the user's language naturally.
- Hindi -> Hindi.
- Hinglish -> Hinglish.
- English -> English.
- Match the user's energy without becoming exaggerated.
- Banter can receive banter.
- Avoid robotic, scripted or customer-support language.
- Do not over-explain simple things.
- Do not force greetings into every message.
- Do not repeatedly use the user's name.

TRUTHFULNESS
- Never invent facts, memories, conversations, events, relationships or actions.
- Never claim Krishna said or did something unless that information exists in the supplied context.
- If information is unavailable, say so naturally.
- Never pretend missing context exists.
- Never fabricate an answer just to sound confident.

CONTEXT
- Context belongs separately to each person.
- Context may contain recent conversations from up to 3 IST calendar days.
- Use previous context only when relevant.
- Do not mention the context system to users.
- Do not reveal internal timestamps, metadata or context records.
- A conversation returning after a substantial gap may receive a short natural re-entry greeting.
- Do not pretend to remember information outside the available context.

TIME
- The server provides the current IST date/time as INTERNAL information.
- Use it only to understand relative dates, time and schedule.
- NEVER reveal or quote the internal timestamp unless the user explicitly asks for the current date/time.
- Never prepend timestamps to replies.
- Never say "according to the timestamp".
- Never expose internal system metadata.

SCHEDULE
Krishna's planned schedule:
- 07:00–09:00 IST: Morning routine
- 11:00–16:40 IST: School
- 17:00–19:00 IST: Coaching
- 23:00–07:00 IST: Sleeping

SCHEDULE RULES
- Use the schedule only when relevant.
- Do not automatically tell people where Krishna should be.
- A schedule is not proof of Krishna's actual real-time location.
- Only mention the schedule when the user asks about Krishna's schedule, availability or whereabouts.

STYLE
- Normally answer in 1–2 short lines.
- Be useful before being verbose.
- Ask a short follow-up when necessary.
- Natural conversation is preferred over formal explanations.
- Do not use unnecessary headings.
- Do not mention prompts, models, tokens, APIs, servers or internal configuration.

CORE RULE
Be a natural, capable and recognizable AI assistant.
Natural conversation does NOT mean pretending to be human.
Always remain truthful about your identity when directly asked.""",

    "friendly_ai": """You are Aria, a friendly AI assistant managing Krishna's WhatsApp.

Be natural, warm and conversational without pretending to be human.
Match Hindi, Hinglish and English naturally.
Keep replies concise and context-aware.
Use light banter when appropriate.
Do not sound robotic or like customer support.
Never impersonate Krishna.
Never invent facts, memories or conversations.
Never expose internal timestamps, context metadata or system information.
If asked who you are, clearly identify yourself as Aria, Krishna's AI assistant.
Use the supplied IST time only internally for date/time reasoning.
Do not mention the current time unless explicitly asked.
Do not automatically mention Krishna's schedule.
Use context only when it genuinely exists.
If information is unavailable, say so rather than guessing.
Normally respond in 1–2 short lines.""",

    "professional_ai": """You are Aria, Krishna's professional AI assistant.

Communicate clearly, accurately and efficiently.
Remain conversational but professional.
Use the user's language naturally.
Never impersonate Krishna or claim to be human.
Never fabricate facts, memories, actions or conversations.
Use only information available in the current context and system instructions.
Current IST time is internal and must never be exposed unless explicitly requested.
Do not expose timestamps, metadata, prompts, APIs or system details.
Do not volunteer Krishna's schedule unless relevant.
When uncertain, state the uncertainty clearly.
Keep routine replies concise.
If asked about your identity, identify yourself as Aria, Krishna's AI assistant.""",

    "casual_ai": """You are Aria, an AI assistant managing Krishna's WhatsApp.

Talk casually and naturally.
Match the user's tone, language and energy.
Hinglish should feel natural rather than translated.
Keep replies short and conversational.
You can use light banter when appropriate.
Never pretend to be Krishna or a human.
Never invent memories or facts.
Never expose internal timestamps or context metadata.
Use current IST time only internally.
Do not mention Krishna's schedule unless asked or directly relevant.
If asked who you are, say you are Aria, Krishna's AI assistant.
Do not force greetings or introductions into normal conversations.
When you don't know something, say so naturally.""",

    "minimal_ai": """You are Aria, Krishna's AI assistant.

Be extremely concise and natural.
Match the user's language.
Answer directly.
Never impersonate Krishna.
Never fabricate facts or memories.
Never expose timestamps, metadata or internal system information.
Use IST time internally only.
Do not volunteer Krishna's schedule.
If asked who you are, identify yourself as Aria, an AI assistant.
Normally use one short sentence or two short lines."""
}


# ============================================================
# DEFAULT CONFIG
# ============================================================

DEFAULT_CONFIG = {
    "groq_model": "openai/gpt-oss-120b",
    "prompt_category": "ai_assistant",

    "delay_min": 8,
    "delay_max": 12,

    "context_days": 3,
    "context_max_messages": 20,

    "new_session_gap_minutes": 120,

    "enabled": True
}


# ============================================================
# CONTEXT STORAGE
# ============================================================

CONTEXT = {}


def now_ist():
    return datetime.now(IST)


def ist_string(dt=None):
    dt = dt or now_ist()
    return dt.strftime("%A, %d %B %Y, %I:%M:%S %p IST")


def ist_date_string(dt=None):
    dt = dt or now_ist()
    return dt.strftime("%Y-%m-%d")


def load_config():
    cfg = DEFAULT_CONFIG.copy()

    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                saved = json.load(f)

            if isinstance(saved, dict):
                cfg.update(saved)

        except Exception:
            pass

    # ENV always has priority.
    if os.environ.get("GROQ_API_KEY"):
        cfg["groq_api_key"] = os.environ["GROQ_API_KEY"]

    if os.environ.get("GROQ_MODEL"):
        cfg["groq_model"] = os.environ["GROQ_MODEL"]

    # Optional permanent custom prompt.
    if os.environ.get("ARIA_SYSTEM_PROMPT"):
        cfg["env_prompt"] = os.environ["ARIA_SYSTEM_PROMPT"]

    return cfg


def save_config(cfg):
    # Never store API key or ENV prompt in config.
    safe = {
        "groq_model": cfg.get("groq_model", DEFAULT_CONFIG["groq_model"]),
        "prompt_category": cfg.get("prompt_category", "ai_assistant"),
        "delay_min": cfg.get("delay_min", 8),
        "delay_max": cfg.get("delay_max", 12),
        "context_days": cfg.get("context_days", 3),
        "context_max_messages": cfg.get("context_max_messages", 20),
        "new_session_gap_minutes": cfg.get("new_session_gap_minutes", 120),
        "enabled": cfg.get("enabled", True)
    }

    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(safe, f, indent=2)


def load_context():
    global CONTEXT

    if not os.path.exists(CONTEXT_FILE):
        return

    try:
        with open(CONTEXT_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)

        if isinstance(data, dict):
            CONTEXT = data

    except Exception:
        CONTEXT = {}


def save_context():
    try:
        with open(CONTEXT_FILE, "w", encoding="utf-8") as f:
            json.dump(CONTEXT, f, indent=2)
    except Exception:
        pass


def clean_context(sender, cfg):
    """
    Context expiry is based on IST calendar dates.

    Example:
    Current day = Sept 20
    Keep = Sept 18, 19, 20
    Delete = Sept 17 and older
    """

    today = now_ist().date()
    cutoff = today - timedelta(days=int(cfg.get("context_days", 3)) - 1)

    history = CONTEXT.get(sender, [])

    cleaned = []

    for item in history:
        try:
            ts = float(item["ts"])
            dt = datetime.fromtimestamp(ts, IST)

            if dt.date() >= cutoff:
                cleaned.append(item)

        except Exception:
            continue

    max_messages = int(cfg.get("context_max_messages", 20))

    cleaned = cleaned[-max_messages:]

    CONTEXT[sender] = cleaned
    save_context()

    return cleaned


def get_context(sender, cfg):
    return clean_context(sender, cfg)


def add_to_context(sender, role, content):
    if sender not in CONTEXT:
        CONTEXT[sender] = []

    CONTEXT[sender].append({
        "role": role,
        "content": content,
        "ts": time.time(),
        "ist_date": ist_date_string()
    })

    save_context()


def minutes_since_last_message(sender, cfg):
    history = get_context(sender, cfg)

    if not history:
        return None

    try:
        last_ts = float(history[-1]["ts"])
        return (time.time() - last_ts) / 60
    except Exception:
        return None


# ============================================================
# PROMPT ENGINE
# ============================================================

def get_selected_prompt(cfg):
    # Permanent ENV override.
    env_prompt = cfg.get("env_prompt")

    if env_prompt:
        return env_prompt

    category = cfg.get("prompt_category", "ai_assistant")

    return PROMPT_PRESETS.get(
        category,
        PROMPT_PRESETS["ai_assistant"]
    )


def build_system_prompt(cfg, sender):
    base_prompt = get_selected_prompt(cfg)

    current_time = ist_string()

    gap = minutes_since_last_message(sender, cfg)

    if gap is None:
        session_note = """
SESSION:
This is the first available message from this sender.
A natural brief greeting may be appropriate if the message itself is a greeting.
"""
    elif gap >= float(cfg.get("new_session_gap_minutes", 120)):
        session_note = f"""
SESSION:
The sender returned after a substantial conversation gap.
Gap: approximately {round(gap)} minutes.

Treat this as a new conversational session.
If appropriate, a short natural re-entry greeting is allowed.

Do NOT reveal the exact gap unless the user asks.
"""
    else:
        session_note = """
SESSION:
This is an ongoing conversation.
Do not restart the conversation with an unnecessary introduction.
"""

    internal_time = f"""
INTERNAL TIME — DO NOT REVEAL UNLESS EXPLICITLY ASKED:
{current_time}

Use this only for:
- relative date reasoning
- current time questions
- schedule reasoning
- understanding today/tomorrow/yesterday

Never copy this timestamp into a reply.
Never prepend timestamps to replies.
"""

    return base_prompt + "\n" + session_note + "\n" + internal_time


# ============================================================
# GROQ
# ============================================================

def extract_reply(choice):
    message = choice.get("message", {})

    content = (message.get("content") or "").strip()

    if content:
        return content

    reasoning = (message.get("reasoning") or "").strip()

    if reasoning:
        # Safety fallback for reasoning models.
        lines = [
            line.strip()
            for line in reasoning.splitlines()
            if line.strip()
        ]

        if lines:
            return lines[-1]

    return ""


def ask_groq(cfg, sender, message, use_context=True):
    api_key = cfg.get("groq_api_key", "")

    if not api_key:
        return None, "GROQ_API_KEY is missing in Render Environment."

    history = get_context(sender, cfg) if use_context else []

    system_prompt = build_system_prompt(cfg, sender)

    messages = [
        {
            "role": "system",
            "content": system_prompt
        }
    ]

    # Context contains only actual conversation.
    # Internal timestamps NEVER enter these messages.
    for item in history:
        role = item.get("role")

        if role in ("user", "assistant"):
            messages.append({
                "role": role,
                "content": item.get("content", "")
            })

    # Important:
    # Do not prefix the user's message with timestamp metadata.
    messages.append({
        "role": "user",
        "content": message
    })

    body = json.dumps({
        "model": cfg.get(
            "groq_model",
            "openai/gpt-oss-120b"
        ),
        "max_tokens": 500,
        "temperature": 0.7,
        "messages": messages
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://api.groq.com/openai/v1/chat/completions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
            "User-Agent": "WAGroqBot/2.0",
            "Accept": "application/json"
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            data = json.loads(r.read())

        if not data.get("choices"):
            return None, f"Groq bad response: {json.dumps(data)[:500]}"

        reply = extract_reply(data["choices"][0])

        if not reply:
            return None, "Groq returned an empty reply."

        # Do not allow the model to expose internal system metadata.
        forbidden_prefixes = (
            "[internal time",
            "internal time:",
            "timestamp:",
            "[timestamp"
        )

        lower = reply.lower().strip()

        if lower.startswith(forbidden_prefixes):
            return None, "Blocked internal metadata leakage."

        if reply.upper().strip() == "SKIP":
            return None, "Groq decided to skip."

        # Store ONLY real conversation.
        add_to_context(
            sender,
            "user",
            message
        )

        add_to_context(
            sender,
            "assistant",
            reply
        )

        return reply, None

    except urllib.error.HTTPError as e:
        try:
            err = e.read().decode("utf-8")
        except Exception:
            err = str(e)

        return None, f"Groq HTTP {e.code}: {err[:500]}"

    except Exception as e:
        return None, f"{type(e).__name__}: {str(e)}"


def raw_groq_request(cfg, prompt="Say hello in one short sentence.", max_tokens=100):
    api_key = cfg.get("groq_api_key", "")

    if not api_key:
        return None, "GROQ_API_KEY is missing", None

    body = json.dumps({
        "model": cfg.get(
            "groq_model",
            "openai/gpt-oss-120b"
        ),
        "max_tokens": max_tokens,
        "messages": [
            {
                "role": "user",
                "content": prompt
            }
        ]
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://api.groq.com/openai/v1/chat/completions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
            "User-Agent": "WAGroqBot/2.0",
            "Accept": "application/json"
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read()), None, r.status

    except urllib.error.HTTPError as e:
        try:
            return None, e.read().decode("utf-8"), e.code
        except Exception:
            return None, str(e), e.code

    except Exception as e:
        return None, f"{type(e).__name__}: {str(e)}", None


# ============================================================
# DASHBOARD
# ============================================================

DASHBOARD_HTML = r"""<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">

<title>WA Groq — Aria</title>

<style>
*{box-sizing:border-box;margin:0;padding:0}

body{
font-family:Segoe UI,Arial,sans-serif;
background:#0d1117;
color:#e6edf3;
min-height:100vh
}

header{
background:#161b22;
border-bottom:1px solid #30363d;
padding:16px 20px;
display:flex;
align-items:center;
gap:12px
}

.logo{
width:38px;
height:38px;
border-radius:50%;
background:linear-gradient(135deg,#25d366,#128c7e);
display:flex;
align-items:center;
justify-content:center
}

header h1{
font-size:18px
}

.status{
margin-left:auto;
width:9px;
height:9px;
background:#25d366;
border-radius:50%
}

.container{
max-width:760px;
margin:auto;
padding:20px 14px;
display:flex;
flex-direction:column;
gap:18px
}

.card{
background:#161b22;
border:1px solid #30363d;
border-radius:12px;
padding:18px
}

.card h2{
font-size:14px;
color:#8b949e;
text-transform:uppercase;
letter-spacing:.06em;
margin-bottom:14px
}

label{
display:block;
font-size:13px;
color:#8b949e;
margin:12px 0 5px
}

input,select,textarea{
width:100%;
background:#0d1117;
border:1px solid #30363d;
border-radius:8px;
padding:10px 12px;
color:#e6edf3;
font-family:inherit;
font-size:14px;
outline:none
}

textarea{
min-height:180px;
resize:vertical;
line-height:1.5
}

input:focus,
select:focus,
textarea:focus{
border-color:#25d366
}

.row{
display:flex;
gap:10px;
flex-wrap:wrap
}

.row>*{
flex:1;
min-width:100px
}

.btn{
border:0;
border-radius:8px;
padding:10px 16px;
font-weight:600;
cursor:pointer;
font-size:14px
}

.green{
background:#25d366;
color:white
}

.outline{
background:transparent;
border:1px solid #30363d;
color:#e6edf3
}

.red{
background:transparent;
border:1px solid #ef4444;
color:#ef4444
}

.actions{
display:flex;
gap:9px;
flex-wrap:wrap;
margin-top:15px
}

.note{
font-size:11px;
color:#8b949e;
margin-top:5px;
line-height:1.5
}

.preset-info{
background:#0d1117;
border:1px solid #30363d;
border-radius:8px;
padding:10px;
font-size:12px;
color:#8b949e;
margin-top:8px;
line-height:1.5
}

.log{
background:#0d1117;
border:1px solid #30363d;
border-radius:8px;
padding:12px;
font-family:monospace;
font-size:12px;
height:260px;
overflow:auto;
line-height:1.7
}

.ctx{
background:#0d1117;
border:1px solid #30363d;
border-radius:8px;
padding:10px;
margin-bottom:8px
}

.ctx b{
color:#e6edf3
}

.ctx span{
color:#25d366
}

.debug{
background:#0d1117;
border:1px solid #30363d;
border-radius:8px;
padding:12px;
font-family:monospace;
font-size:11px;
white-space:pre-wrap;
word-break:break-word;
display:none;
max-height:350px;
overflow:auto
}

.modal{
display:none;
position:fixed;
inset:0;
background:rgba(0,0,0,.75);
align-items:center;
justify-content:center;
z-index:10
}

.modal.open{
display:flex
}

.modal-inner{
width:92%;
max-width:480px;
background:#161b22;
border:1px solid #30363d;
border-radius:12px;
padding:20px
}

.result{
margin-top:12px;
padding:10px;
border-radius:8px;
font-size:13px;
display:none;
word-break:break-word
}

.ok{
display:block;
background:#052a1a;
border:1px solid #25d366;
color:#25d366
}

.err{
display:block;
background:#2a0505;
border:1px solid #ef4444;
color:#ef4444
}
</style>
</head>

<body>

<header>
<div class="logo">AI</div>
<h1>WA Groq — Aria</h1>
<div class="status"></div>
</header>

<div class="container">

<div class="card">

<h2>Webhook</h2>

<div style="display:flex;gap:8px">
<input id="webhook" readonly>
<button class="btn outline" onclick="copyWebhook()">Copy</button>
</div>

</div>


<div class="card">

<h2>AI Configuration</h2>

<label>Prompt Category</label>

<select id="category" onchange="categoryChanged()">
<option value="ai_assistant">AI Assistant — Default</option>
<option value="friendly_ai">Friendly AI</option>
<option value="professional_ai">Professional AI</option>
<option value="casual_ai">Casual AI</option>
<option value="minimal_ai">Minimal AI</option>
</select>

<div class="preset-info" id="presetInfo">
Default: natural AI assistant with identity, truthfulness,
context and IST protection.
</div>


<label>Groq Model</label>

<select id="model">
<option value="openai/gpt-oss-120b">openai/gpt-oss-120b</option>
<option value="openai/gpt-oss-20b">openai/gpt-oss-20b</option>
<option value="llama-3.3-70b-versatile">llama-3.3-70b-versatile</option>
<option value="llama-3.1-8b-instant">llama-3.1-8b-instant</option>
</select>


<label>API Key</label>

<input
id="apiKey"
type="password"
placeholder="GROQ_API_KEY is preferred"
/>

<p class="note">
Recommended: set GROQ_API_KEY in Render Environment.
The key is not written to wa_config.json.
</p>


<label>System Prompt Preview</label>

<textarea id="prompt" readonly></textarea>

<p class="note">
Built-in prompt is part of the server and cannot disappear when
wa_config.json is deleted.
</p>


<div class="row">

<div>
<label>Reply Delay Min</label>
<input id="delayMin" type="number" min="0" max="60">
</div>

<div>
<label>Reply Delay Max</label>
<input id="delayMax" type="number" min="0" max="60">
</div>

</div>


<div class="row">

<div>
<label>Context Days</label>
<input id="contextDays" type="number" min="1" max="7">
</div>

<div>
<label>Max Context Messages</label>
<input id="ctxMax" type="number" min="1" max="50">
</div>

<div>
<label>New Session Gap (minutes)</label>
<input id="gap" type="number" min="1" max="1440">
</div>

</div>


<label>Auto Reply</label>

<input
id="enabled"
type="checkbox"
style="width:auto;accent-color:#25d366"
checked
>


<div class="actions">

<button class="btn green" onclick="saveSettings()">
Save Settings
</button>

<button class="btn outline" onclick="openTest()">
Test Groq
</button>

<button class="btn outline" onclick="clearContext()">
Clear Context
</button>

</div>

</div>


<div class="card">

<h2>Debug Request</h2>

<button class="btn red" id="debugBtn" onclick="debugRequest()">
Run Debug Request
</button>

<div class="debug" id="debug"></div>

</div>


<div class="card">

<h2>Active Contexts</h2>

<div id="contexts">Loading...</div>

</div>


<div class="card">

<h2>Recent Activity</h2>

<button class="btn outline" onclick="clearLogs()" style="margin-bottom:10px">
Clear Logs
</button>

<div class="log" id="logs">Loading...</div>

</div>

</div>


<div class="modal" id="modal">

<div class="modal-inner">

<h2 style="margin-bottom:15px">
Test Aria
</h2>

<label>Sender</label>
<input id="testSender" placeholder="TestUser">

<label>Message</label>
<textarea id="testMessage" placeholder="Hey!"></textarea>

<label>
<input
id="testCtx"
type="checkbox"
style="width:auto;accent-color:#25d366"
>
 Include existing context
</label>

<div class="actions">

<button class="btn green" onclick="sendTest()">
Send
</button>

<button class="btn outline" onclick="closeTest()">
Close
</button>

</div>

<div id="result" class="result"></div>

</div>

</div>


<script>

const descriptions = {

ai_assistant:
"Default: natural AI assistant with identity, truthfulness, context and IST protection.",

friendly_ai:
"Friendly and conversational while remaining clearly an AI.",

professional_ai:
"Clear, efficient and professional AI assistant.",

casual_ai:
"Casual, natural Hinglish-friendly conversational assistant.",

minimal_ai:
"Very concise AI responses with minimal extra wording."
};


let presets = {};


async function loadPresets(){

const r = await fetch('/presets');
presets = await r.json();

categoryChanged();

}


function categoryChanged(){

const category =
document.getElementById('category').value;

document.getElementById('prompt').value =
presets[category] || '';

document.getElementById('presetInfo').textContent =
descriptions[category] || '';

}


async function loadConfig(){

const r = await fetch('/config');

const c = await r.json();

document.getElementById('category').value =
c.prompt_category || 'ai_assistant';

document.getElementById('model').value =
c.groq_model || 'openai/gpt-oss-120b';

document.getElementById('apiKey').value =
c.groq_api_key || '';

document.getElementById('delayMin').value =
c.delay_min ?? 8;

document.getElementById('delayMax').value =
c.delay_max ?? 12;

document.getElementById('contextDays').value =
c.context_days ?? 3;

document.getElementById('ctxMax').value =
c.context_max_messages ?? 20;

document.getElementById('gap').value =
c.new_session_gap_minutes ?? 120;

document.getElementById('enabled').checked =
c.enabled !== false;

categoryChanged();

document.getElementById('webhook').value =
location.origin + '/webhook';

}


async function saveSettings(){

const cfg = {

groq_api_key:
document.getElementById('apiKey').value.trim(),

groq_model:
document.getElementById('model').value,

prompt_category:
document.getElementById('category').value,

delay_min:
parseInt(document.getElementById('delayMin').value) || 8,

delay_max:
parseInt(document.getElementById('delayMax').value) || 12,

context_days:
parseInt(document.getElementById('contextDays').value) || 3,

context_max_messages:
parseInt(document.getElementById('ctxMax').value) || 20,

new_session_gap_minutes:
parseInt(document.getElementById('gap').value) || 120,

enabled:
document.getElementById('enabled').checked

};

await fetch('/config',{
method:'POST',
headers:{'Content-Type':'application/json'},
body:JSON.stringify(cfg)
});

alert('Settings saved');

}


async function debugRequest(){

const btn =
document.getElementById('debugBtn');

const box =
document.getElementById('debug');

btn.disabled = true;
btn.textContent = 'Running...';

box.style.display = 'block';
box.textContent = 'Sending request...';

try{

const r = await fetch('/debug');

const d = await r.json();

box.textContent =
JSON.stringify(d,null,2);

}catch(e){

box.textContent =
'Debug failed: ' + e.message;

}

btn.disabled = false;
btn.textContent = 'Run Debug Request';

}


async function loadContexts(){

const r = await fetch('/contexts');

const d = await r.json();

const box =
document.getElementById('contexts');

const entries =
Object.entries(d.contexts || {});

if(!entries.length){

box.innerHTML =
'<span style="color:#8b949e">No active contexts</span>';

return;

}

box.innerHTML =
entries.map(([sender,x]) => `

<div class="ctx">

<b>${escapeHtml(sender)}</b>

<span>
 — ${x.count} messages
</span>

<div style="color:#8b949e;font-size:11px;margin-top:5px">
IST date: ${escapeHtml(x.last_date)}
</div>

</div>

`).join('');

}


async function loadLogs(){

const r = await fetch('/logs');

const d = await r.json();

const box =
document.getElementById('logs');

if(!d.logs.length){

box.textContent =
'No activity yet';

return;

}

box.innerHTML =
d.logs.slice().reverse().map(x => `

<div style="margin-bottom:7px">

<span style="color:#8b949e">
${escapeHtml(x.time)}
</span>

<b>${escapeHtml(x.sender)}</b>:

${escapeHtml(x.text)}

</div>

`).join('');

}


function openTest(){

document.getElementById('modal')
.classList.add('open');

document.getElementById('result')
.style.display='none';

}


function closeTest(){

document.getElementById('modal')
.classList.remove('open');

}


async function sendTest(){

const sender =
document.getElementById('testSender').value.trim()
|| 'TestUser';

const message =
document.getElementById('testMessage').value.trim();

const useContext =
document.getElementById('testCtx').checked;

const result =
document.getElementById('result');

if(!message){

result.className='result err';
result.style.display='block';
result.textContent='Enter a message.';
return;

}

result.className='result';
result.style.display='block';
result.textContent='Sending...';

try{

const r = await fetch('/test',{
method:'POST',
headers:{
'Content-Type':'application/json'
},
body:JSON.stringify({
sender:sender,
message:message,
use_context:useContext
})
});

const d = await r.json();

if(d.reply){

result.className='result ok';

result.textContent =
'Aria → ' + d.reply;

}else{

result.className='result err';

result.textContent =
d.error || 'No reply';

}

}catch(e){

result.className='result err';

result.textContent =
'Request failed: ' + e.message;

}

loadLogs();
loadContexts();

}


async function clearContext(){

await fetch('/contexts/clear',{
method:'POST'
});

loadContexts();

}


async function clearLogs(){

await fetch('/logs/clear',{
method:'POST'
});

loadLogs();

}


function copyWebhook(){

navigator.clipboard.writeText(
document.getElementById('webhook').value
);

}


function escapeHtml(x){

return String(x)
.replaceAll('&','&amp;')
.replaceAll('<','&lt;')
.replaceAll('>','&gt;')
.replaceAll('"','&quot;');

}


loadPresets().then(loadConfig);

loadLogs();
loadContexts();

setInterval(loadLogs,3000);
setInterval(loadContexts,5000);

</script>

</body>
</html>
"""


# ============================================================
# HTTP HANDLER
# ============================================================

class Handler(BaseHTTPRequestHandler):

    logs = []

    def log_message(self, *args):
        pass

    def send_json(self, code, data):

        body = json.dumps(
            data,
            ensure_ascii=False
        ).encode("utf-8")

        self.send_response(code)

        self.send_header(
            "Content-Type",
            "application/json; charset=utf-8"
        )

        self.send_header(
            "Content-Length",
            str(len(body))
        )

        self.send_header(
            "Access-Control-Allow-Origin",
            "*"
        )

        self.end_headers()

        self.wfile.write(body)


    def send_html(self, html):

        body = html.encode("utf-8")

        self.send_response(200)

        self.send_header(
            "Content-Type",
            "text/html; charset=utf-8"
        )

        self.send_header(
            "Content-Length",
            str(len(body))
        )

        self.end_headers()

        self.wfile.write(body)


    def read_body(self):

        length = int(
            self.headers.get(
                "Content-Length",
                0
            )
        )

        return (
            self.rfile.read(length)
            if length > 0
            else b""
        )


    def add_log(self, log_type, sender, text):

        Handler.logs.append({

            "type": log_type,

            "sender": sender,

            "text": str(text)[:500],

            # Dashboard timestamp only.
            # It is NOT sent to Groq.
            "time": now_ist().strftime("%H:%M:%S")

        })

        Handler.logs = Handler.logs[-100:]


    # --------------------------------------------------------
    # GET
    # --------------------------------------------------------

    def do_GET(self):

        if self.path in ("/", "/dashboard"):

            self.send_html(
                DASHBOARD_HTML
            )

        elif self.path == "/health":

            self.send_json(
                200,
                {
                    "status": "ok",
                    "current_ist": ist_string()
                }
            )

        elif self.path == "/presets":

            self.send_json(
                200,
                PROMPT_PRESETS
            )

        elif self.path == "/config":

            cfg = load_config()

            # Never expose the full API key.
            key = cfg.get("groq_api_key", "")

            if key:
                cfg["groq_api_key"] = (
                    key[:6] + "..." + key[-4:]
                )

            # Do not expose ENV custom prompt unnecessarily.
            cfg.pop("env_prompt", None)

            self.send_json(200, cfg)

        elif self.path == "/logs":

            self.send_json(
                200,
                {
                    "logs": Handler.logs
                }
            )

        elif self.path == "/contexts":

            cfg = load_config()

            result = {}

            for sender in list(CONTEXT.keys()):

                history = get_context(
                    sender,
                    cfg
                )

                if history:

                    last = history[-1]

                    result[sender] = {

                        "count": len(history),

                        "last_date":
                            last.get(
                                "ist_date",
                                "unknown"
                            )

                    }

            self.send_json(
                200,
                {
                    "contexts": result
                }
            )

        elif self.path == "/debug":

            cfg = load_config()

            key = cfg.get(
                "groq_api_key",
                ""
            )

            if len(key) > 12:

                key_preview = (
                    key[:8]
                    + "..."
                    + key[-4:]
                )

            elif key:

                key_preview = "SET"

            else:

                key_preview = "MISSING"

            data, err, status = raw_groq_request(
                cfg
            )

            result = {

                "current_ist":
                    ist_string(),

                "key":
                    key_preview,

                "status":
                    status,

                "selected_prompt_category":
                    cfg.get(
                        "prompt_category",
                        "ai_assistant"
                    ),

                "context_days":
                    cfg.get(
                        "context_days",
                        3
                    )
            }

            if data:

                result["groq_raw"] = data

            else:

                result["groq_error"] = err

            self.send_json(
                200,
                result
            )

        else:

            self.send_json(
                404,
                {
                    "error": "not found"
                }
            )


    # --------------------------------------------------------
    # POST
    # --------------------------------------------------------

    def do_POST(self):

        if self.path == "/config":

            try:

                incoming = json.loads(
                    self.read_body()
                )

                cfg = load_config()

                allowed = [

                    "groq_model",
                    "prompt_category",
                    "delay_min",
                    "delay_max",
                    "context_days",
                    "context_max_messages",
                    "new_session_gap_minutes",
                    "enabled"

                ]

                for key in allowed:

                    if key in incoming:

                        cfg[key] = incoming[key]

                # API key can be entered through dashboard,
                # but ENV remains preferred.
                if (
                    incoming.get("groq_api_key")
                    and not os.environ.get("GROQ_API_KEY")
                ):

                    cfg["groq_api_key"] = (
                        incoming["groq_api_key"]
                    )

                save_config(cfg)

                self.send_json(
                    200,
                    {
                        "status": "saved"
                    }
                )

            except Exception as e:

                self.send_json(
                    400,
                    {
                        "error": str(e)
                    }
                )


        elif self.path == "/test":

            cfg = load_config()

            try:

                body = json.loads(
                    self.read_body()
                )

            except Exception as e:

                self.send_json(
                    400,
                    {
                        "error":
                            f"Invalid JSON: {e}"
                    }
                )

                return

            sender = (
                body.get("sender")
                or "TestUser"
            ).strip()

            message = (
                body.get("message")
                or ""
            ).strip()

            use_context = bool(
                body.get(
                    "use_context",
                    False
                )
            )

            if not message:

                self.send_json(
                    400,
                    {
                        "error":
                            "Message is empty."
                    }
                )

                return

            # For isolated tests, temporarily ignore
            # existing context unless requested.
            original = None

            if not use_context:

                original = CONTEXT.get(
                    sender
                )

                if sender in CONTEXT:

                    del CONTEXT[sender]

            reply, err = ask_groq(
                cfg,
                sender,
                message,
                use_context=True
            )

            if original is not None:

                CONTEXT[sender] = original

                save_context()

            if reply:

                self.add_log(
                    "sent",
                    f"[TEST] {sender}",
                    reply
                )

                self.send_json(
                    200,
                    {
                        "reply": reply
                    }
                )

            else:

                self.send_json(
                    200,
                    {
                        "error":
                            err or "No reply"
                    }
                )


        elif self.path == "/logs/clear":

            Handler.logs = []

            self.send_json(
                200,
                {
                    "status": "cleared"
                }
            )


        elif self.path == "/contexts/clear":

            CONTEXT.clear()

            save_context()

            self.send_json(
                200,
                {
                    "status": "cleared"
                }
            )


        elif self.path == "/webhook":

            try:

                body = json.loads(
                    self.read_body()
                )

            except Exception:

                self.send_json(
                    400,
                    {
                        "error":
                            "invalid json"
                    }
                )

                return

            cfg = load_config()

            if not cfg.get(
                "enabled",
                True
            ):

                self.send_json(
                    200,
                    {
                        "replies": []
                    }
                )

                return

            query = body.get(
                "query",
                body
            )

            if not isinstance(
                query,
                dict
            ):

                self.send_json(
                    400,
                    {
                        "error":
                            "invalid query"
                    }
                )

                return

            sender = str(
                query.get(
                    "sender",
                    "Unknown"
                )
            ).replace(
                "[test]",
                ""
            ).strip()

            message = str(
                query.get(
                    "message",
                    ""
                )
            ).strip()

            if not message:

                self.add_log(
                    "skip",
                    sender,
                    "empty message"
                )

                self.send_json(
                    200,
                    {
                        "replies": []
                    }
                )

                return

            reply, err = ask_groq(
                cfg,
                sender,
                message,
                use_context=True
            )

            if reply:

                self.add_log(
                    "sent",
                    sender,
                    reply
                )

                self.send_json(
                    200,
                    {
                        "replies": [
                            {
                                "message":
                                    reply,

                                "delay":
                                    cfg.get(
                                        "delay_min",
                                        8
                                    ),

                                "delayMax":
                                    cfg.get(
                                        "delay_max",
                                        12
                                    )
                            }
                        ]
                    }
                )

            else:

                self.add_log(
                    "skip",
                    sender,
                    err or "No reply"
                )

                self.send_json(
                    200,
                    {
                        "replies": []
                    }
                )


    def do_OPTIONS(self):

        self.send_response(200)

        self.send_header(
            "Access-Control-Allow-Origin",
            "*"
        )

        self.send_header(
            "Access-Control-Allow-Methods",
            "GET, POST, OPTIONS"
        )

        self.send_header(
            "Access-Control-Allow-Headers",
            "Content-Type"
        )

        self.end_headers()


# ============================================================
# START
# ============================================================

if __name__ == "__main__":

    load_context()

    cfg = load_config()

    # Initial cleanup according to IST.
    for sender in list(CONTEXT.keys()):
        clean_context(
            sender,
            cfg
        )

    PORT = int(
        os.environ.get(
            "PORT",
            8080
        )
    )

    print(
        f"WA Groq Server running on :{PORT}"
    )

    print(
        "IST:",
        ist_string()
    )

    print(
        "Prompt:",
        cfg.get(
            "prompt_category",
            "ai_assistant"
        )
    )

    print(
        "Context:",
        cfg.get(
            "context_days",
            3
        ),
        "IST calendar days"
    )

    HTTPServer(
        ("0.0.0.0", PORT),
        Handler
    ).serve_forever()

