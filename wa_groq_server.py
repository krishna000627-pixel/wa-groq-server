from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import os
import urllib.request
import urllib.error
import time
from datetime import datetime
from zoneinfo import ZoneInfo

CONFIG_FILE = "wa_config.json"
CONTEXT_FILE = "wa_context.json"

IST = ZoneInfo("Asia/Kolkata")


# ============================================================
# DEFAULT CONFIG
# ============================================================

DEFAULT_CONFIG = {
    "groq_api_key": "",
    "groq_model": "openai/gpt-oss-120b",

    "system_prompt": """You are Aria, an AI assistant operating on Krishna's WhatsApp.

IDENTITY:
- You are an AI agent, not Krishna.
- Never pretend to be Krishna.
- Never write as if you personally are Krishna.
- Never claim that you personally performed an action for Krishna unless the system explicitly confirms it.
- If someone asks who you are, identify yourself naturally as Aria, an AI assistant.
- Do not repeatedly announce that you are an AI unless relevant.

ANTI-HALLUCINATION:
- Never invent facts.
- Never invent conversations, events, plans, locations, emotions, relationships, schedules, actions, or memories.
- Never assume Krishna saw, read, liked, disliked, did, said, or agreed with something unless that information exists in the supplied context.
- If information is unavailable, say so briefly instead of guessing.
- Never fabricate a reason for Krishna's absence or delayed response.
- Never say Krishna is busy, sleeping, studying, at school, at coaching, etc. unless the current time/schedule actually supports it or the context explicitly confirms it.
- Current time and schedule information supplied by the server is authoritative.

ROLE:
- You are replying as Krishna's AI assistant.
- You are NOT Krishna's replacement identity.
- Your wording should make it clear naturally that an AI assistant is replying when identity matters.
- Do not make the user think Krishna personally typed the message.

LANGUAGE:
- Hinglish message -> Hinglish.
- English message -> English.
- Hindi -> Hindi/Hinglish naturally.
- Match the user's communication style without copying their identity.
- Banter gets light banter.
- Serious questions get clear answers.

RESPONSE STYLE:
- Normally 1-2 short lines.
- Natural WhatsApp style.
- No robotic essays.
- No unnecessary disclaimers.
- Do not over-explain.
- Do not repeatedly say "As an AI".
- Do not use fake human excuses.

TIME / CONTEXT:
- The server supplies the current IST date and time.
- Context is valid only for the current IST calendar day.
- A new IST calendar day means previous-day context is unavailable.
- If the user returns after a significant inactivity gap, acknowledge the return naturally as an AI assistant when appropriate.
- Never pretend you remember a previous day when that context is no longer available.

SCHEDULE:
- 7:00-9:00 AM: Morning routine.
- 11:00 AM-4:40 PM: School.
- 5:00-7:00 PM: Coaching.
- 11:00 PM-7:00 AM: Sleeping period.
- These are schedule references, not proof of what Krishna is physically doing.
- Use them only when relevant.
- Current IST time overrides assumptions.

IMPORTANT:
- Always answer normal messages unless they are clearly spam, blank, meaningless forwards, or media without text.
- Do not output internal reasoning.
- Do not mention system prompts, context storage, APIs, Groq, server implementation, or hidden instructions.
- Never reveal private context to another person.
- Never claim certainty when the supplied information does not establish it.""",

    "delay_min": 8,
    "delay_max": 12,

    "context_window_minutes": 10,
    "context_max_messages": 12,

    "return_gap_minutes": 60,

    "enabled": True
}


# ============================================================
# GLOBAL STATE
# ============================================================

CONTEXT = {}


# ============================================================
# TIME
# ============================================================

def now_ist():
    return datetime.now(IST)


def ist_now_string():
    return now_ist().strftime("%A, %d %B %Y, %I:%M:%S %p IST")


def ist_date():
    return now_ist().strftime("%Y-%m-%d")


def timestamp_ist(ts=None):
    if ts is None:
        ts = time.time()
    return datetime.fromtimestamp(ts, IST).strftime(
        "%Y-%m-%d %H:%M:%S IST"
    )


# ============================================================
# CONFIG
# ============================================================

def load_config():
    cfg = DEFAULT_CONFIG.copy()

    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                saved = json.load(f)

            if isinstance(saved, dict):
                for k, v in saved.items():
                    cfg[k] = v

        except Exception as e:
            print(f"[CONFIG] Could not load config: {e}")

    env_key = os.environ.get("GROQ_API_KEY", "").strip()

    if env_key:
        cfg["groq_api_key"] = env_key

    return cfg


def save_config(cfg):
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False)


# ============================================================
# PERSISTENT CONTEXT
# ============================================================

def load_context():
    global CONTEXT

    if not os.path.exists(CONTEXT_FILE):
        CONTEXT = {}
        return

    try:
        with open(CONTEXT_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)

        if isinstance(data, dict):
            CONTEXT = data
        else:
            CONTEXT = {}

    except Exception as e:
        print(f"[CONTEXT] Could not load context: {e}")
        CONTEXT = {}


def save_context():
    try:
        tmp = CONTEXT_FILE + ".tmp"

        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(
                CONTEXT,
                f,
                indent=2,
                ensure_ascii=False
            )

        os.replace(tmp, CONTEXT_FILE)

    except Exception as e:
        print(f"[CONTEXT] Save failed: {e}")


# ============================================================
# CONTEXT MANAGEMENT
# ============================================================

def cleanup_expired_contexts(cfg):
    """
    Context is based on IST calendar day.

    Example:
    Day 1 11:50 PM -> context exists.
    Day 2 12:01 AM -> Day 1 context is deleted.

    This is NOT a rolling 24-hour window.
    """

    global CONTEXT

    today = ist_date()

    expired = []

    for sender, data in list(CONTEXT.items()):

        if not isinstance(data, dict):
            expired.append(sender)
            continue

        saved_day = data.get("ist_date")

        if saved_day != today:
            expired.append(sender)

    for sender in expired:
        del CONTEXT[sender]

    if expired:
        save_context()

    return expired


def get_context(sender, cfg):
    cleanup_expired_contexts(cfg)

    today = ist_date()

    data = CONTEXT.get(sender)

    if not data:
        return []

    if data.get("ist_date") != today:
        CONTEXT.pop(sender, None)
        save_context()
        return []

    history = data.get("messages", [])

    now = time.time()

    window_secs = (
        int(cfg.get("context_window_minutes", 10)) * 60
    )

    max_msgs = int(
        cfg.get("context_max_messages", 12)
    )

    # Keep only the recent rolling conversation window
    history = [
        m for m in history
        if now - float(m.get("ts", now)) <= window_secs
    ]

    history = history[-max_msgs:]

    data["messages"] = history

    CONTEXT[sender] = data

    save_context()

    return history


def get_sender_state(sender, cfg):
    """
    Returns information about this sender's current interaction.

    The context can exist for the entire IST day, but a long
    inactivity gap is detected separately.
    """

    cleanup_expired_contexts(cfg)

    data = CONTEXT.get(sender)

    if not data:
        return {
            "is_first_today": True,
            "gap_minutes": None,
            "last_seen": None,
            "ist_date": ist_date(),
            "message_count": 0
        }

    last_seen_ts = data.get("last_seen_ts")

    if not last_seen_ts:
        return {
            "is_first_today": False,
            "gap_minutes": None,
            "last_seen": None,
            "ist_date": data.get("ist_date"),
            "message_count": len(data.get("messages", []))
        }

    gap_minutes = max(
        0,
        (time.time() - float(last_seen_ts)) / 60
    )

    return_gap = float(
        cfg.get("return_gap_minutes", 60)
    )

    return {
        "is_first_today": False,
        "gap_minutes": round(gap_minutes, 1),
        "returning_after_gap": gap_minutes >= return_gap,
        "last_seen": timestamp_ist(last_seen_ts),
        "ist_date": data.get("ist_date"),
        "message_count": len(data.get("messages", []))
    }


def add_to_context(sender, role, content):
    global CONTEXT

    today = ist_date()
    now = time.time()

    data = CONTEXT.get(sender)

    # New IST day = completely new context
    if not data or data.get("ist_date") != today:
        data = {
            "ist_date": today,
            "created_ts": now,
            "last_seen_ts": None,
            "messages": []
        }

    data.setdefault("messages", [])

    data["messages"].append({
        "role": role,
        "content": content,
        "ts": now,
        "ist_timestamp": timestamp_ist(now),
        "ist_date": today
    })

    data["last_seen_ts"] = now
    data["ist_date"] = today

    # Keep persistent storage reasonable
    data["messages"] = data["messages"][-50:]

    CONTEXT[sender] = data

    save_context()


# ============================================================
# SAFE REPLY EXTRACTION
# ============================================================

def extract_reply(choice):
    """
    Supports standard chat models and reasoning models.

    Important:
    We prefer message.content.

    We DO NOT dump an entire reasoning trace to the user.
    """

    msg = choice.get("message", {})

    content = (
        msg.get("content") or ""
    ).strip()

    if content:
        return content

    # Some Groq reasoning models may provide final content
    # through other fields.
    reasoning = (
        msg.get("reasoning") or ""
    ).strip()

    if not reasoning:
        return ""

    lines = [
        line.strip()
        for line in reasoning.splitlines()
        if line.strip()
    ]

    if not lines:
        return ""

    # Try to locate an explicit final-answer marker.
    markers = [
        "final answer:",
        "final:",
        "answer:",
        "reply:"
    ]

    lower_lines = [
        line.lower()
        for line in lines
    ]

    for marker in markers:
        for i, line in enumerate(lower_lines):
            if marker in line:
                original = lines[i]

                if ":" in original:
                    candidate = original.split(":", 1)[1].strip()
                    if candidate:
                        return candidate

                if i + 1 < len(lines):
                    return lines[i + 1]

    # Conservative fallback:
    # only use the last line if it looks like an actual reply,
    # not an obvious reasoning line.
    candidate = lines[-1]

    bad_starts = (
        "the user",
        "we need",
        "i should",
        "i need to",
        "let's",
        "analysis",
        "reasoning",
        "the assistant"
    )

    if candidate.lower().startswith(bad_starts):
        return ""

    return candidate


# ============================================================
# SYSTEM CONTEXT
# ============================================================

def build_runtime_context(sender, cfg):
    now = now_ist()

    state = get_sender_state(sender, cfg)

    current_time = now.strftime("%I:%M:%S %p")
    current_date = now.strftime("%A, %d %B %Y")

    gap = state.get("gap_minutes")

    if gap is None:
        gap_text = "No previous interaction today."
    else:
        gap_text = f"{gap} minutes since previous interaction."

    if state.get("is_first_today"):
        interaction_type = (
            "FIRST INTERACTION OF THIS IST DAY. "
            "A brief AI-agent introduction/greeting is appropriate."
        )
    elif state.get("returning_after_gap"):
        interaction_type = (
            "RETURNING AFTER A SIGNIFICANT GAP. "
            "Keep today's context, but a brief natural AI-agent "
            "greeting is appropriate before answering."
        )
    else:
        interaction_type = (
            "CONTINUING THE CURRENT DAY'S CONVERSATION. "
            "Do not unnecessarily re-introduce yourself."
        )

    return f"""
CURRENT SERVER TIME:
{current_date}
{current_time} IST

CURRENT IST DATE:
{now.strftime("%Y-%m-%d")}

SENDER:
{sender}

INTERACTION STATE:
{interaction_type}

TIME SINCE PREVIOUS INTERACTION:
{gap_text}

IMPORTANT CONTEXT POLICY:
- Context is valid only for the current IST calendar day.
- Previous IST day's context has already been deleted.
- Do not claim memory of conversations from an expired day.
- Current server time is authoritative.
- Do not invent the user's current physical situation.
"""


# ============================================================
# GROQ REQUEST
# ============================================================

def ask_groq(cfg, sender, message):
    api_key = cfg.get("groq_api_key", "").strip()

    if not api_key:
        return None, "No API key set — add GROQ_API_KEY in Render environment"

    history = get_context(sender, cfg)
    state = get_sender_state(sender, cfg)

    messages = []

    system_prompt = cfg.get(
        "system_prompt",
        DEFAULT_CONFIG["system_prompt"]
    )

    runtime_context = build_runtime_context(
        sender,
        cfg
    )

    messages.append({
        "role": "system",
        "content": system_prompt + "\n\n" + runtime_context
    })

    # Add conversation context
    for m in history:

        role = m.get("role")

        if role not in ("user", "assistant"):
            continue

        content = m.get("content", "")

        if content:
            messages.append({
                "role": role,
                "content": content
            })

    # Explicit current message
    messages.append({
        "role": "user",
        "content": (
            f"Message from {sender}:\n{message}"
        )
    })

    body = json.dumps({
        "model": cfg.get(
            "groq_model",
            "openai/gpt-oss-120b"
        ),

        "max_tokens": 500,

        "temperature": 0.45,

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

        with urllib.request.urlopen(
            req,
            timeout=30
        ) as r:

            data = json.loads(
                r.read().decode("utf-8")
            )

            if (
                "choices" not in data
                or not data["choices"]
            ):
                return (
                    None,
                    f"Groq bad response: "
                    f"{json.dumps(data)[:500]}"
                )

            choice = data["choices"][0]

            reply = extract_reply(choice)

            if not reply:
                return (
                    None,
                    "Empty Groq reply. "
                    f"finish_reason={choice.get('finish_reason')}"
                )

            # Model can internally decide to skip
            if reply.strip().upper() == "SKIP":
                return None, "Groq decided to skip"

            # Store only after successful response
            add_to_context(
                sender,
                "user",
                f"Message from {sender}: {message}"
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

        return (
            None,
            f"Groq HTTP {e.code}: {err[:500]}"
        )

    except Exception as e:

        return (
            None,
            f"{type(e).__name__}: {str(e)}"
        )


# ============================================================
# RAW DEBUG REQUEST
# ============================================================

def raw_groq_request(
    cfg,
    prompt="Say hello in one short sentence.",
    max_tokens=100
):

    api_key = cfg.get(
        "groq_api_key",
        ""
    ).strip()

    if not api_key:
        return None, "GROQ_API_KEY is empty", None

    body = json.dumps({
        "model": cfg.get(
            "groq_model",
            "openai/gpt-oss-120b"
        ),

        "max_tokens": max_tokens,

        "temperature": 0.45,

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

        with urllib.request.urlopen(
            req,
            timeout=30
        ) as r:

            return (
                json.loads(
                    r.read().decode("utf-8")
                ),
                None,
                r.status
            )

    except urllib.error.HTTPError as e:

        try:
            err = e.read().decode("utf-8")
        except Exception:
            err = str(e)

        return (
            None,
            err,
            e.code
        )

    except Exception as e:

        return (
            None,
            f"{type(e).__name__}: {str(e)}",
            None
        )


# ============================================================
# DASHBOARD
# ============================================================

DASHBOARD_HTML = """<!DOCTYPE html>
<html lang="en">
<head>

<meta charset="UTF-8">
<meta name="viewport"
      content="width=device-width,initial-scale=1">

<title>WA Groq Server</title>

<style>

*{
box-sizing:border-box;
margin:0;
padding:0
}

body{
font-family:'Segoe UI',sans-serif;
background:#0d1117;
color:#e6edf3;
min-height:100vh
}

header{
background:#161b22;
border-bottom:1px solid #30363d;
padding:16px 24px;
display:flex;
align-items:center;
gap:12px
}

.logo{
width:36px;
height:36px;
background:linear-gradient(
135deg,
#25d366,
#128c7e
);
border-radius:50%;
display:flex;
align-items:center;
justify-content:center;
font-size:18px
}

header h1{
font-size:18px;
font-weight:700
}

.status-dot{
width:8px;
height:8px;
border-radius:50%;
background:#25d366;
margin-left:auto;
animation:pulse 2s infinite
}

@keyframes pulse{
0%,100%{opacity:1}
50%{opacity:.4}
}

.container{
max-width:720px;
margin:0 auto;
padding:24px 16px;
display:flex;
flex-direction:column;
gap:20px
}

.card{
background:#161b22;
border:1px solid #30363d;
border-radius:12px;
padding:20px
}

.card h2{
font-size:14px;
font-weight:600;
color:#8b949e;
text-transform:uppercase;
letter-spacing:.06em;
margin-bottom:16px
}

label{
display:block;
font-size:13px;
color:#8b949e;
margin-bottom:4px;
margin-top:12px
}

input,
select,
textarea{
width:100%;
background:#0d1117;
border:1px solid #30363d;
color:#e6edf3;
border-radius:8px;
padding:9px 12px;
font-size:14px;
font-family:inherit;
outline:none
}

input:focus,
select:focus,
textarea:focus{
border-color:#25d366
}

textarea{
resize:vertical;
min-height:140px;
line-height:1.6
}

.row{
display:flex;
gap:12px;
align-items:flex-end;
flex-wrap:wrap
}

.row .field{
flex:1;
min-width:80px
}

.toggle-row{
display:flex;
align-items:center;
gap:12px;
margin-top:8px
}

.toggle{
position:relative;
width:44px;
height:24px;
flex-shrink:0
}

.toggle input{
opacity:0;
width:0;
height:0
}

.slider{
position:absolute;
inset:0;
background:#30363d;
border-radius:24px;
cursor:pointer;
transition:.3s
}

.slider:before{
content:'';
position:absolute;
width:18px;
height:18px;
left:3px;
bottom:3px;
background:#fff;
border-radius:50%;
transition:.3s
}

input:checked+.slider{
background:#25d366
}

input:checked+.slider:before{
transform:translateX(20px)
}

.btn{
padding:10px 20px;
border-radius:8px;
font-size:14px;
font-weight:600;
cursor:pointer;
border:none
}

.btn-green{
background:#25d366;
color:#fff
}

.btn-outline{
background:transparent;
border:1px solid #30363d;
color:#8b949e
}

.btn-red{
background:transparent;
border:1px solid #ef4444;
color:#ef4444
}

.btn:disabled{
opacity:.4;
cursor:default
}

.actions{
display:flex;
gap:10px;
margin-top:16px;
flex-wrap:wrap
}

.webhook-url{
background:#0d1117;
border:1px solid #30363d;
border-radius:8px;
padding:10px 12px;
font-family:monospace;
font-size:13px;
color:#25d366;
word-break:break-all;
flex:1
}

.copy-btn{
padding:6px 14px;
font-size:12px;
border-radius:6px;
background:#21262d;
border:1px solid #30363d;
color:#e6edf3;
cursor:pointer;
white-space:nowrap
}

.log-box{
background:#0d1117;
border:1px solid #30363d;
border-radius:8px;
padding:12px;
font-family:monospace;
font-size:12px;
height:220px;
overflow-y:auto;
line-height:1.8
}

.log-entry{
padding:2px 0;
border-bottom:1px solid #21262d
}

.log-sent{
color:#25d366
}

.log-skip{
color:#f59e0b
}

.log-err{
color:#ef4444
}

.log-time{
color:#8b949e;
margin-right:8px
}

.saved-toast{
display:none;
color:#25d366;
font-size:13px;
margin-left:auto
}

.env-note{
font-size:11px;
color:#f59e0b;
margin-top:4px
}

.ctx-badge{
display:inline-block;
background:#21262d;
border:1px solid #30363d;
border-radius:6px;
padding:2px 8px;
font-size:11px;
color:#8b949e;
margin-left:6px
}

.modal-overlay{
display:none;
position:fixed;
inset:0;
background:rgba(0,0,0,.75);
z-index:100;
align-items:center;
justify-content:center
}

.modal-overlay.open{
display:flex
}

.modal{
background:#161b22;
border:1px solid #30363d;
border-radius:14px;
padding:24px;
width:92%;
max-width:480px;
display:flex;
flex-direction:column;
gap:14px
}

.result-box{
padding:12px;
border-radius:8px;
font-size:13px;
line-height:1.6;
display:none;
word-break:break-all
}

.result-box.ok{
background:#052a1a;
border:1px solid #25d366;
color:#25d366
}

.result-box.err{
background:#2a0505;
border:1px solid #ef4444;
color:#ef4444
}

.debug-box{
background:#0d1117;
border:1px solid #30363d;
border-radius:8px;
padding:12px;
font-family:monospace;
font-size:11px;
line-height:1.7;
white-space:pre-wrap;
word-break:break-all;
max-height:300px;
overflow-y:auto;
display:none;
margin-top:12px
}

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

<div class="webhook-url"
     id="webhookUrl">
Loading...
</div>

<button class="copy-btn"
        onclick="copyUrl()">
Copy
</button>

</div>

</div>


<div class="card">

<h2>⚙️ Settings</h2>

<label>Groq API Key</label>

<input type="password"
       id="apiKey"
       placeholder="gsk_..." />

<p class="env-note">
Set GROQ_API_KEY in Render Environment for persistence.
</p>


<label>Model</label>

<select id="model">

<option value="openai/gpt-oss-120b">
openai/gpt-oss-120b — reasoning
</option>

<option value="openai/gpt-oss-20b">
openai/gpt-oss-20b — faster reasoning
</option>

<option value="qwen/qwen3.6-27b">
qwen/qwen3.6-27b
</option>

<option value="llama-3.3-70b-versatile">
llama-3.3-70b-versatile
</option>

<option value="llama-3.1-8b-instant">
llama-3.1-8b-instant
</option>

</select>


<label>System Prompt</label>

<textarea id="prompt"></textarea>


<label>Reply Delay</label>

<div class="row">

<div class="field">
<input type="number"
       id="delayMin"
       min="0"
       max="60">
</div>

<div style="color:#8b949e;padding-bottom:10px">
to
</div>

<div class="field">
<input type="number"
       id="delayMax"
       min="0"
       max="60">
</div>

<div style="color:#8b949e;padding-bottom:10px">
seconds
</div>

</div>


<label>
Rolling Context
<span class="ctx-badge">
same-day only
</span>
</label>

<div class="row">

<div class="field">

<label>
Message window
</label>

<input type="number"
       id="ctxMinutes"
       min="1"
       max="120">

</div>


<div class="field">

<label>
Max messages
</label>

<input type="number"
       id="ctxMax"
       min="1"
       max="50">

</div>


<div class="field">

<label>
Return gap
</label>

<input type="number"
       id="returnGap"
       min="1"
       max="1440">

</div>

</div>

<p class="env-note">
Context resets automatically at 00:00 IST.
Return-gap controls when Aria treats someone as returning.
</p>


<div class="toggle-row">

<label class="toggle">

<input type="checkbox"
       id="enabled"
       checked>

<span class="slider"></span>

</label>

<span>
Auto-reply enabled
</span>

<span class="saved-toast"
      id="savedToast">
✓ Saved
</span>

</div>


<div class="actions">

<button class="btn btn-green"
        onclick="saveSettings()">
Save Settings
</button>

<button class="btn btn-outline"
        onclick="openTestModal()">
Test Groq
</button>

<button class="btn btn-outline"
        onclick="clearCtx()">
Clear All Contexts
</button>

</div>

</div>


<div class="card">

<h2>🔍 Debug</h2>

<p style="font-size:13px;color:#8b949e;margin-bottom:12px">
Fires a raw request to Groq.
</p>

<button class="btn btn-red"
        onclick="runDebug()"
        id="debugBtn">
Run Debug Request
</button>

<div id="debugBox"
     class="debug-box">
</div>

</div>


<div class="card">

<h2>🧠 Active Contexts</h2>

<div id="ctxBox"
     style="font-size:13px;color:#8b949e">
Loading...
</div>

</div>


<div class="card">

<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">

<h2 style="margin:0">
📋 Recent Activity
</h2>

<button class="copy-btn"
        onclick="clearLogs()">
Clear
</button>

</div>

<div class="log-box"
     id="logBox">

<div style="color:#8b949e;text-align:center;padding:20px">
No activity yet
</div>

</div>

</div>

</div>


<!-- TEST MODAL -->

<div class="modal-overlay"
     id="testModal">

<div class="modal">

<div style="display:flex;justify-content:space-between;align-items:center">

<span style="font-size:15px;font-weight:700">
🧪 Test Groq
</span>

<button onclick="closeTestModal()"
        style="background:none;border:none;color:#8b949e;font-size:22px;cursor:pointer">
✕
</button>

</div>


<div>

<label>Sender name</label>

<input id="testSender"
       type="text"
       placeholder="Rahul">

</div>


<div>

<label>Message</label>

<textarea id="testMsg"
          placeholder="Type a test message...">
</textarea>

</div>


<div style="display:flex;gap:8px;align-items:center">

<input type="checkbox"
       id="testUseCtx"
       style="width:auto;accent-color:#25d366">

<label style="margin:0">
Include existing context
</label>

</div>


<button id="testRunBtn"
        class="btn btn-green"
        onclick="runCustomTest">
Send
</button>

<div id="testModalResult"
     class="result-box">
</div>

</div>

</div>


<script>

async function loadConfig(){

const r=await fetch('/config');
const c=await r.json();

document.getElementById('apiKey').value=
c.groq_api_key||'';

document.getElementById('model').value=
c.groq_model||'openai/gpt-oss-120b';

document.getElementById('prompt').value=
c.system_prompt||'';

document.getElementById('delayMin').value=
c.delay_min??8;

document.getElementById('delayMax').value=
c.delay_max??12;

document.getElementById('ctxMinutes').value=
c.context_window_minutes??10;

document.getElementById('ctxMax').value=
c.context_max_messages??12;

document.getElementById('returnGap').value=
c.return_gap_minutes??60;

document.getElementById('enabled').checked=
c.enabled!==false;

document.getElementById('webhookUrl').textContent=
window.location.origin+'/webhook';

}


async function saveSettings(){

const cfg={

groq_api_key:
document.getElementById('apiKey').value.trim(),

groq_model:
document.getElementById('model').value,

system_prompt:
document.getElementById('prompt').value,

delay_min:
parseInt(document.getElementById('delayMin').value)||8,

delay_max:
parseInt(document.getElementById('delayMax').value)||12,

context_window_minutes:
parseInt(document.getElementById('ctxMinutes').value)||10,

context_max_messages:
parseInt(document.getElementById('ctxMax').value)||12,

return_gap_minutes:
parseInt(document.getElementById('returnGap').value)||60,

enabled:
document.getElementById('enabled').checked

};

await fetch(
'/config',
{
method:'POST',
headers:{
'Content-Type':'application/json'
},
body:JSON.stringify(cfg)
}
);

const t=
document.getElementById('savedToast');

t.style.display='inline';

setTimeout(
()=>t.style.display='none',
2000
);

}


async function runDebug(){

const btn=
document.getElementById('debugBtn');

const box=
document.getElementById('debugBox');

btn.disabled=true;

btn.textContent='Running...';

box.style.display='block';

box.textContent=
'Sending request to Groq...';

try{

const r=
await fetch('/debug');

const d=
await r.json();

box.style.color=
d.groq_error||
d.exception
?'#ef4444'
:'#25d366';

box.textContent=
JSON.stringify(d,null,2);

}catch(e){

box.style.color='#ef4444';

box.textContent=
'Fetch failed: '+e.message;

}

btn.disabled=false;

btn.textContent=
'Run Debug Request';

}


function openTestModal(){

document
.getElementById('testModal')
.classList.add('open');

document
.getElementById('testModalResult')
.style.display='none';

}


function closeTestModal(){

document
.getElementById('testModal')
.classList.remove('open');

}


async function runCustomTest(){

const sender=
document.getElementById('testSender')
.value.trim()||'TestUser';

const message=
document.getElementById('testMsg')
.value.trim();

const useCtx=
document.getElementById('testUseCtx')
.checked;

const btn=
document.getElementById('testRunBtn');

const el=
document.getElementById('testModalResult');

if(!message){

el.className=
'result-box err';

el.style.display='block';

el.textContent=
'Type a message first';

return;

}

btn.disabled=true;

btn.textContent='Sending...';

el.style.display='none';

try{

const r=
await fetch(
'/test',
{
method:'POST',
headers:{
'Content-Type':
'application/json'
},
body:JSON.stringify({
sender,
message,
use_context:useCtx
})
}
);

const d=await r.json();

el.style.display='block';

if(d.reply){

el.className=
'result-box ok';

el.textContent=
'Aria → '+d.reply+
' | context: '+
(d.ctx_msgs??0);

}else{

el.className=
'result-box err';

el.textContent=
d.error||'Unknown error';

}

}catch(e){

el.style.display='block';

el.className=
'result-box err';

el.textContent=
'Fetch failed: '+e.message;

}

btn.disabled=false;

btn.textContent='Send';

}


async function loadLogs(){

const r=
await fetch('/logs');

const d=
await r.json();

const box=
document.getElementById('logBox');

if(!d.logs||!d.logs.length){

box.innerHTML=
'<div style="color:#8b949e;text-align:center;padding:20px">No activity yet</div>';

return;

}

box.innerHTML=
d.logs.slice().reverse().map(l=>{

const cls=
l.type==='sent'
?'log-sent'
:l.type==='skip'
?'log-skip'
:'log-err';

return `
<div class="log-entry ${cls}">
<span class="log-time">${l.time}</span>
${l.icon||'•'}
<b>${l.sender}</b>:
${l.text}
</div>
`;

}).join('');

}


async function loadCtx(){

const r=
await fetch('/contexts');

const d=
await r.json();

const box=
document.getElementById('ctxBox');

const entries=
Object.entries(d.contexts||{});

if(!entries.length){

box.innerHTML=
'<span style="color:#8b949e">No active contexts</span>';

return;

}

box.innerHTML=
entries.map(
([sender,info])=>`

<div style="
margin-bottom:8px;
padding:10px;
background:#0d1117;
border-radius:6px;
border:1px solid #30363d
">

<b>${sender}</b>

<span style="
color:#25d366;
margin-left:8px
">
${info.count} msgs
</span>

<span style="
color:#8b949e;
margin-left:8px;
font-size:11px
">
IST day: ${info.ist_date}
</span>

<br>

<span style="
color:#8b949e;
font-size:11px
">
last: ${info.last_seen}
</span>

</div>

`
).join('');

}


function clearCtx(){

fetch(
'/contexts/clear',
{method:'POST'}
).then(loadCtx);

}


function clearLogs(){

fetch(
'/logs/clear',
{method:'POST'}
).then(loadLogs);

}


function copyUrl(){

const text=
document.getElementById(
'webhookUrl'
).textContent;

navigator.clipboard.writeText(text);

}


loadConfig();

loadLogs();

loadCtx();

setInterval(
loadLogs,
3000
);

setInterval(
loadCtx,
5000
);

</script>

</body>
</html>
"""


# ============================================================
# HTTP HANDLER
# ============================================================

class Handler(BaseHTTPRequestHandler):

    logs = []

    def log_message(self, *a):
        pass


    def send_json(self, code, data):

        body=json.dumps(
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

        body=html.encode("utf-8")

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

        length=int(
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


    def add_log(
        self,
        t,
        sender,
        text
    ):

        icon = {
            "sent": "✅",
            "skip": "⏭️",
            "err": "❌"
        }.get(t, "•")

        Handler.logs.append({

            "type": t,

            "sender": sender,

            "text": str(text)[:200],

            "time":
                now_ist().strftime(
                    "%H:%M:%S"
                ),

            "icon": icon

        })

        Handler.logs = Handler.logs[-50:]


    # ========================================================
    # GET
    # ========================================================

    def do_GET(self):

        if self.path in (
            "/",
            "/dashboard"
        ):

            self.send_html(
                DASHBOARD_HTML
            )


        elif self.path == "/config":

            cfg=load_config()

            # Do not expose full API key unnecessarily.
            # UI can still work with env key.
            safe=cfg.copy()

            key=safe.get(
                "groq_api_key",
                ""
            )

            if key:

                safe["groq_api_key"] = (
                    key[:8] + "..." +
                    key[-4:]
                    if len(key)>12
                    else "SET"
                )

            self.send_json(
                200,
                safe
            )


        elif self.path == "/logs":

            self.send_json(
                200,
                {
                    "logs":
                        Handler.logs
                }
            )


        elif self.path == "/contexts":

            cfg=load_config()

            cleanup_expired_contexts(
                cfg
            )

            result={}

            for sender,data in CONTEXT.items():

                if not isinstance(
                    data,
                    dict
                ):
                    continue

                messages=data.get(
                    "messages",
                    []
                )

                if not messages:
                    continue

                last_seen=data.get(
                    "last_seen_ts"
                )

                result[sender]={

                    "count":
                        len(messages),

                    "ist_date":
                        data.get(
                            "ist_date"
                        ),

                    "last_seen":
                        timestamp_ist(
                            last_seen
                        )
                        if last_seen
                        else "unknown"
                }

            self.send_json(
                200,
                {
                    "contexts":
                        result,

                    "current_ist":
                        ist_now_string()
                }
            )


        elif self.path == "/debug":

            cfg=load_config()

            key=cfg.get(
                "groq_api_key",
                ""
            )

            key_preview=(
                key[:8]+"..."+key[-4:]
                if len(key)>12
                else (
                    "SET"
                    if key
                    else "MISSING"
                )
            )

            data,err,status=raw_groq_request(
                cfg
            )

            if data:

                self.send_json(
                    200,
                    {
                        "current_ist":
                            ist_now_string(),

                        "key":
                            key_preview,

                        "status":
                            status,

                        "groq_raw":
                            data
                    }
                )

            else:

                self.send_json(
                    200,
                    {
                        "current_ist":
                            ist_now_string(),

                        "key":
                            key_preview,

                        "groq_error":
                            status,

                        "groq_body":
                            err
                    }
                )


        elif self.path == "/health":

            cleanup_expired_contexts(
                load_config()
            )

            self.send_json(
                200,
                {
                    "status":"ok",

                    "server":
                        "WA Groq Aria",

                    "current_ist":
                        ist_now_string(),

                    "context_day":
                        ist_date(),

                    "active_contexts":
                        len(CONTEXT)
                }
            )


        else:

            self.send_json(
                404,
                {
                    "error":
                        "not found"
                }
            )


    # ========================================================
    # POST
    # ========================================================

    def do_POST(self):

        # ----------------------------------------------------
        # CONFIG
        # ----------------------------------------------------

        if self.path == "/config":

            try:

                incoming=json.loads(
                    self.read_body()
                )

                current=load_config()

                if not isinstance(
                    incoming,
                    dict
                ):
                    raise ValueError(
                        "Config must be an object"
                    )

                for k,v in incoming.items():

                    if k in DEFAULT_CONFIG:

                        current[k]=v

                # Do not save the masked API key
                # received from the dashboard.
                if (
                    "groq_api_key"
                    in incoming
                ):

                    submitted=(
                        incoming.get(
                            "groq_api_key"
                        ) or ""
                    ).strip()

                    if (
                        "..." in submitted
                        and current.get(
                            "groq_api_key"
                        )
                    ):
                        pass

                    elif submitted:
                        current[
                            "groq_api_key"
                        ]=submitted

                save_config(current)

                self.send_json(
                    200,
                    {
                        "status":
                            "saved"
                    }
                )

            except Exception as e:

                self.send_json(
                    400,
                    {
                        "error":
                            str(e)
                    }
                )


        # ----------------------------------------------------
        # TEST
        # ----------------------------------------------------

        elif self.path == "/test":

            cfg=load_config()

            sender="TestUser"

            message=(
                "Hello! Reply in a fun way "
                "to test if you are working."
            )

            use_ctx=False

            try:

                raw=self.read_body()

                if raw:

                    body=json.loads(
                        raw
                    )

                    sender=(
                        body.get(
                            "sender"
                        )
                        or "TestUser"
                    ).strip()

                    message=(
                        body.get(
                            "message"
                        )
                        or message
                    ).strip()

                    use_ctx=bool(
                        body.get(
                            "use_context",
                            False
                        )
                    )

            except Exception as e:

                self.send_json(
                    200,
                    {
                        "error":
                            f"Parse error: {e}"
                    }
                )

                return


            try:

                saved_ctx=None

                if (
                    not use_ctx
                    and sender in CONTEXT
                ):

                    saved_ctx=CONTEXT.pop(
                        sender
                    )

                    save_context()


                ctx_count=len(
                    get_context(
                        sender,
                        cfg
                    )
                )

                reply,err=ask_groq(
                    cfg,
                    sender,
                    message
                )

                # Restore test context
                # if context was disabled.
                if saved_ctx is not None:

                    CONTEXT[
                        sender
                    ]=saved_ctx

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
                            "reply":
                                reply,

                            "ctx_msgs":
                                ctx_count,

                            "current_ist":
                                ist_now_string()
                        }
                    )

                else:

                    self.send_json(
                        200,
                        {
                            "error":
                                err
                                or
                                "No reply"
                        }
                    )

            except Exception as e:

                self.send_json(
                    200,
                    {
                        "error":
                            f"{type(e).__name__}: {e}"
                    }
                )


        # ----------------------------------------------------
        # CLEAR LOGS
        # ----------------------------------------------------

        elif self.path == "/logs/clear":

            Handler.logs=[]

            self.send_json(
                200,
                {
                    "status":
                        "cleared"
                }
            )


        # ----------------------------------------------------
        # CLEAR CONTEXT
        # ----------------------------------------------------

        elif self.path == "/contexts/clear":

            CONTEXT.clear()

            save_context()

            self.send_json(
                200,
                {
                    "status":
                        "cleared"
                }
            )


        # ----------------------------------------------------
        # WHATSAPP WEBHOOK
        # ----------------------------------------------------

        elif self.path == "/webhook":

            try:

                raw=self.read_body()

                body=json.loads(
                    raw
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


            cfg=load_config()


            # Clean previous IST-day context
            cleanup_expired_contexts(
                cfg
            )


            if not cfg.get(
                "enabled",
                True
            ):

                self.send_json(
                    200,
                    {
                        "replies":[]
                    }
                )

                return


            query=body.get(
                "query",
                body
            )

            if not isinstance(
                query,
                dict
            ):
                query={}


            sender=(
                query.get(
                    "sender",
                    ""
                )
                or
                "Unknown"
            )

            sender=str(
                sender
            ).replace(
                "[test]",
                ""
            ).strip()


            message=(
                query.get(
                    "message",
                    ""
                )
                or
                ""
            )

            message=str(
                message
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
                        "replies":[]
                    }
                )

                return


            # Current interaction state
            state=get_sender_state(
                sender,
                cfg
            )

            print(
                f"[WA] "
                f"{ist_now_string()} | "
                f"{sender}: "
                f"{message[:120]}"
            )


            reply,err=ask_groq(
                cfg,
                sender,
                message
            )


            if reply:

                self.add_log(
                    "sent",
                    sender,
                    f'-> "{reply}"'
                )

                self.send_json(
                    200,
                    {
                        "replies":[
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
                        ],

                        "ist":
                            ist_now_string(),

                        "returning_after_gap":
                            state.get(
                                "returning_after_gap",
                                False
                            )
                    }
                )

            else:

                self.add_log(
                    "skip",
                    sender,
                    f"skipped — {err}"
                )

                self.send_json(
                    200,
                    {
                        "replies":[]
                    }
                )


    # ========================================================
    # OPTIONS
    # ========================================================

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
# START SERVER
# ============================================================

if __name__ == "__main__":

    load_context()

    cfg=load_config()

    # Immediately clean old IST-day contexts.
    cleanup_expired_contexts(cfg)

    PORT=int(
        os.environ.get(
            "PORT",
            8080
        )
    )

    print(
        "======================================"
    )

    print(
        "WA Groq Server — Aria"
    )

    print(
        f"Port: {PORT}"
    )

    print(
        f"Current IST: {ist_now_string()}"
    )

    print(
        f"Context date: {ist_date()}"
    )

    print(
        f"Active contexts: {len(CONTEXT)}"
    )

    print(
        "Context policy: IST calendar day"
    )

    print(
        "======================================"
    )

    HTTPServer(
        ("0.0.0.0", PORT),
        Handler
    ).serve_forever()

