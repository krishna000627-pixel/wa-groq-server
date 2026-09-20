from http.server import HTTPServer, BaseHTTPRequestHandler
import json, os, time, urllib.request, urllib.error
from datetime import datetime, timezone, timedelta

CONFIG_FILE="wa_config.json"
DASHBOARD_FILE="dashboard.html"
IST=timezone(timedelta(hours=5, minutes=30))
DEFAULT_PROMPT="""You are Aria, Krishna's AI assistant managing his WhatsApp.

IDENTITY
- You are an AI assistant, not Krishna.
- Never impersonate Krishna or claim to personally be him.
- Never invent Krishna's statements, actions, plans, possessions, promises, or personal information.
- If information is not supplied, say you do not know.

TIME AND MEMORY
- Current time is supplied separately by the server in IST.
- Treat IST as authoritative for dates, weekdays and context expiry.
- Conversation context is retained for 3 calendar days according to IST.
- If a person returns after a long gap, especially after several hours, treat it as a fresh interaction and use a brief natural AI-agent greeting when appropriate.
- Never expose internal timestamps unless useful.

STYLE
- Sound like a real AI assistant: natural, concise and clear.
- Hinglish in -> Hinglish out; English in -> English out.
- Normally 1-3 short lines.
- Match energy without pretending to be Krishna.
- Do not repeatedly introduce yourself.

CLOSED-END
- Give complete answers that naturally end the interaction.
- Do not use open-ended engagement bait such as "anything else?", "what else can I help with?", or "tell me more".
- If someone asks for something that requires Krishna personally, state that Krishna will handle/send it later only when supported by context.
- Never guess a subject, book, chapter, item, deadline or action.

ACCURACY
- Do not hallucinate schedules, locations, relationships, names, books, subjects or events.
- If a factual answer requires missing information, say so instead of guessing.
- Skip only blank messages, obvious forwards/spam, or media with no usable text."""

DEFAULT_CONFIG={"groq_api_key":"","groq_model":"openai/gpt-oss-120b","system_prompt":DEFAULT_PROMPT,
"delay_min":8,"delay_max":12,"context_window_minutes":4320,"context_max_messages":12,"enabled":True}
CONTEXT={}

def load_config():
    c=DEFAULT_CONFIG.copy()
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE,encoding="utf-8") as f:c.update(json.load(f))
        except Exception: pass
    if os.environ.get("GROQ_API_KEY"): c["groq_api_key"]=os.environ["GROQ_API_KEY"]
    return c
def save_config(c):
    with open(CONFIG_FILE,"w",encoding="utf-8") as f:json.dump(c,f,indent=2)
def now_ist(): return datetime.now(IST)
def prune(sender,cfg):
    now=time.time(); window=cfg.get("context_window_minutes",4320)*60
    h=[m for m in CONTEXT.get(sender,[]) if now-m["ts"]<=window]
    CONTEXT[sender]=h[-int(cfg.get("context_max_messages",12)):]
    return CONTEXT[sender]
def add(sender,role,content):
    CONTEXT.setdefault(sender,[]).append({"role":role,"content":content,"ts":time.time()})
def ask(cfg,sender,message):
    key=cfg.get("groq_api_key","")
    if not key:return None,"No GROQ_API_KEY configured"
    hist=prune(sender,cfg)
    messages=[{"role":"system","content":cfg.get("system_prompt") or DEFAULT_PROMPT},
              {"role":"system","content":f"CURRENT IST: {now_ist().strftime('%A, %d %B %Y, %I:%M:%S %p IST')}"}]
    messages += [{"role":m["role"],"content":m["content"]} for m in hist]
    messages.append({"role":"user","content":f"Message from {sender}: {message}"})
    body=json.dumps({"model":cfg.get("groq_model","openai/gpt-oss-120b"),"max_tokens":300,
                     "temperature":0.45,"messages":messages}).encode()
    req=urllib.request.Request("https://api.groq.com/openai/v1/chat/completions",data=body,
      headers={"Content-Type":"application/json","Authorization":f"Bearer {key}","Accept":"application/json"},method="POST")
    try:
        with urllib.request.urlopen(req,timeout=25) as r:d=json.loads(r.read())
        msg=d["choices"][0]["message"]; reply=(msg.get("content") or "").strip()
        if not reply:return None,"Groq returned an empty reply"
        if reply.upper().startswith("SKIP"):return None,"Groq decided to skip"
        add(sender,"user",f"Message from {sender}: {message}");add(sender,"assistant",reply)
        return reply,None
    except urllib.error.HTTPError as e:return None,f"Groq HTTP {e.code}: {e.read().decode()[:300]}"
    except Exception as e:return None,f"{type(e).__name__}: {e}"
def raw(cfg):
    key=cfg.get("groq_api_key","")
    if not key:return None,"missing key",None
    body=json.dumps({"model":cfg.get("groq_model"),"max_tokens":50,"messages":[{"role":"user","content":"Say hello in one short sentence."}]}).encode()
    req=urllib.request.Request("https://api.groq.com/openai/v1/chat/completions",data=body,
      headers={"Content-Type":"application/json","Authorization":f"Bearer {key}","Accept":"application/json"},method="POST")
    try:
        with urllib.request.urlopen(req,timeout=25) as r:return json.loads(r.read()),None,r.status
    except urllib.error.HTTPError as e:return None,e.read().decode()[:500],e.code
    except Exception as e:return None,str(e),None

class H(BaseHTTPRequestHandler):
    logs=[]
    def log_message(self,*a):pass
    def sendj(self,code,obj):
        b=json.dumps(obj).encode();self.send_response(code);self.send_header("Content-Type","application/json")
        self.send_header("Content-Length",str(len(b)));self.send_header("Access-Control-Allow-Origin","*");self.end_headers();self.wfile.write(b)
    def body(self):
        n=int(self.headers.get("Content-Length","0"));return self.rfile.read(n) if n else b""
    def log(self,t,s,x):
        H.logs.append({"type":t,"sender":s,"text":x[:160],"time":now_ist().strftime("%H:%M:%S")});H.logs=H.logs[-50:]
    def do_GET(self):
        if self.path in ("/","/dashboard"):
            try:
                with open(DASHBOARD_FILE,encoding="utf-8") as f:b=f.read()
            except Exception as e:b=f"<h1>Dashboard unavailable</h1><p>{e}</p>"
            b=b.encode();self.send_response(200);self.send_header("Content-Type","text/html;charset=utf-8");self.send_header("Content-Length",str(len(b)));self.end_headers();self.wfile.write(b)
        elif self.path=="/config":self.sendj(200,load_config())
        elif self.path=="/logs":self.sendj(200,{"logs":H.logs})
        elif self.path=="/contexts":
            c=load_config();out={}
            for s,h in list(CONTEXT.items()):
                a=prune(s,c)
                if a:out[s]={"count":len(a),"last_seen":datetime.fromtimestamp(a[-1]["ts"],IST).strftime("%A, %d %B %Y, %I:%M:%S %p IST")}
            self.sendj(200,{"contexts":out})
        elif self.path=="/debug":
            d,e,s=raw(load_config());self.sendj(200,{"current_ist":now_ist().strftime("%A, %d %B %Y, %I:%M:%S %p IST"),"status":s,"groq_raw":d,"groq_error":e})
        elif self.path=="/health":self.sendj(200,{"status":"ok"})
        else:self.sendj(404,{"error":"not found"})
    def do_POST(self):
        if self.path=="/config":
            try:save_config(json.loads(self.body()));self.sendj(200,{"status":"saved"})
            except Exception as e:self.sendj(400,{"error":str(e)})
        elif self.path=="/contexts/clear":CONTEXT.clear();self.sendj(200,{"status":"cleared"})
        elif self.path=="/logs/clear":H.logs=[];self.sendj(200,{"status":"cleared"})
        elif self.path=="/webhook":
            try:q=json.loads(self.body());q=q.get("query",q);s=(q.get("sender") or "Unknown").replace("[test]","").strip();m=(q.get("message") or "").strip()
            except Exception:self.sendj(400,{"error":"invalid json"});return
            c=load_config()
            if not c.get("enabled",True) or not m:self.sendj(200,{"replies":[]});return
            r,e=ask(c,s,m)
            if r:self.log("sent",s,f"-> {r}");self.sendj(200,{"replies":[{"message":r,"delay":c.get("delay_min",8),"delayMax":c.get("delay_max",12)}]})
            else:self.log("skip",s,e or "skipped");self.sendj(200,{"replies":[]})
        else:self.sendj(404,{"error":"not found"})
    def do_OPTIONS(self):
        self.send_response(200);self.send_header("Access-Control-Allow-Origin","*");self.send_header("Access-Control-Allow-Methods","GET,POST,OPTIONS");self.send_header("Access-Control-Allow-Headers","Content-Type");self.end_headers()

if __name__=="__main__":
    HTTPServer(("0.0.0.0",int(os.environ.get("PORT",8080))),H).serve_forever()
