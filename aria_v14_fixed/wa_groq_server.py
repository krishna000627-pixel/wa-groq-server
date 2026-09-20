from http.server import HTTPServer,BaseHTTPRequestHandler
import os,json,time,urllib.request,urllib.error,mimetypes
from urllib.parse import urlparse,unquote
from pathlib import Path
from datetime import datetime,timezone,timedelta
IST=timezone(timedelta(hours=5,minutes=30)); CFGFILE='wa_config.json'; CONTEXT={}; LOGS=[]
PROMPT=open('aria_prompt.txt',encoding='utf-8').read()
DEFAULT={'groq_api_key':'','groq_model':'openai/gpt-oss-120b','system_prompt':PROMPT,'delay_min':8,'delay_max':12,'context_days':3}
def config():
 c=DEFAULT.copy()
 try:c.update(json.load(open(CFGFILE,encoding='utf-8')))
 except:pass
 if os.getenv('GROQ_API_KEY'):c['groq_api_key']=os.getenv('GROQ_API_KEY')
 return c
def save(c):
 if os.getenv('GROQ_API_KEY'):c['groq_api_key']=''
 json.dump(c,open(CFGFILE,'w',encoding='utf-8'),ensure_ascii=False,indent=2)
def now():return datetime.now(IST).strftime('%H:%M:%S')
def log(t,s,x):LOGS.append({'type':t,'sender':s,'text':str(x)[:500],'time':now()});del LOGS[:-80]
def ctx(s,c):
 cut=time.time()-int(c.get('context_days',3))*86400;CONTEXT[s]=[m for m in CONTEXT.get(s,[]) if m['ts']>=cut];return CONTEXT[s][-12:]
def ask(c,s,m):
 key=c.get('groq_api_key','')
 if not key:return None,'No Groq API key configured.'
 h=ctx(s,c);sys=c.get('system_prompt') or PROMPT
 msgs=[{'role':'system','content':sys+'\nCURRENT IST: '+datetime.now(IST).isoformat()}]+[{'role':x['role'],'content':x['content']} for x in h]+[{'role':'user','content':f'Message from {s}: {m}'}]
 body=json.dumps({'model':c.get('groq_model',DEFAULT['groq_model']),'max_tokens':350,'temperature':.55,'messages':msgs}).encode();req=urllib.request.Request('https://api.groq.com/openai/v1/chat/completions',data=body,headers={'Content-Type':'application/json','Authorization':'Bearer '+key},method='POST')
 try:
  with urllib.request.urlopen(req,timeout=30) as r:d=json.loads(r.read())
  reply=(d.get('choices',[{}])[0].get('message',{}).get('content') or '').strip()
  if not reply:return None,'Empty response.'
  if reply.upper()=='SKIP':return None,'Skipped by model.'
  CONTEXT.setdefault(s,[]).append({'role':'user','content':m,'ts':time.time()});CONTEXT[s].append({'role':'assistant','content':reply,'ts':time.time()});return reply,None
 except urllib.error.HTTPError as e:return None,f'Groq HTTP {e.code}: {e.read().decode()[:300]}'
 except Exception as e:return None,f'{type(e).__name__}: {e}'
class H(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def out(self,code,data,typ='application/json'):
  b=data if isinstance(data,bytes) else json.dumps(data,ensure_ascii=False).encode();self.send_response(code);self.send_header('Content-Type',typ);self.send_header('Access-Control-Allow-Origin','*');self.send_header('Content-Length',str(len(b)));self.end_headers();self.wfile.write(b)
 def read(self):
  n=int(self.headers.get('Content-Length','0'));return json.loads(self.rfile.read(n)) if n else {}
 def do_GET(self):
  path=urlparse(self.path).path
  if path in ('/','/dashboard'):
   try:self.out(200,open('dashboard.html','rb').read(),'text/html; charset=utf-8')
   except:self.out(500,{'error':'dashboard missing'})
  elif path in ('/manifest.webmanifest','/aria_prompt.txt') or path.startswith('/assets/'):
   rel=unquote(path.lstrip('/'))
   base=Path(os.getcwd()).resolve(); target=(base/rel).resolve()
   try:
    if not str(target).startswith(str(base)+os.sep) or not target.is_file(): raise FileNotFoundError
    data=target.read_bytes(); typ=mimetypes.guess_type(str(target))[0] or 'application/octet-stream'
    self.out(200,data,typ)
   except Exception:self.out(404,{'error':'asset not found'})
  elif path=='/config':
   c=config();c['groq_api_key']='configured' if c.get('groq_api_key') else '';self.out(200,c)
  elif path=='/logs':self.out(200,{'logs':LOGS})
  elif path=='/contexts':
   c=config();cut=time.time()-int(c.get('context_days',3))*86400;self.out(200,{'contexts':{s:{'count':len([x for x in a if x['ts']>=cut]),'last_seen':datetime.fromtimestamp(a[-1]['ts'],IST).strftime('%d %b %H:%M IST')} for s,a in CONTEXT.items() if any(x['ts']>=cut for x in a)}})
  elif path=='/health':self.out(200,{'status':'ok','ist':datetime.now(IST).isoformat()})
  else:self.out(404,{'error':'not found'})
 def do_POST(self):
  try:b=self.read()
  except:self.out(400,{'error':'invalid json'});return
  if self.path=='/config':c=config();c.update(b);save(c);self.out(200,{'status':'saved'});return
  if self.path=='/logs/clear':LOGS.clear();self.out(200,{'status':'cleared'});return
  if self.path=='/contexts/clear':CONTEXT.clear();self.out(200,{'status':'cleared'});return
  if self.path=='/test':
   s=b.get('sender','TestUser');m=b.get('message','').strip();c=config();log('received',s,m);reply,err=ask(c,s,m)
   # TestUser is a real local preview context: retain the incoming test message
   # even when Groq is unavailable, so the Contexts screen can always show it.
   if not reply:
    CONTEXT.setdefault(s,[]).append({'role':'user','content':m,'ts':time.time()})
   if reply:log('sent',s,reply);self.out(200,{'reply':reply,'context_created':True})
   else:log('errors' if err and 'Skipped' not in err else 'skipped',s,err or 'No response');self.out(200,{'error':err or 'No response','context_created':True})
   return
  if self.path=='/webhook':
   s=b.get('sender') or b.get('from') or 'Unknown';m=b.get('message') or b.get('text') or '';c=config();log('received',s,m);reply,err=ask(c,s,m)
   if reply:log('sent',s,reply);self.out(200,{'reply':reply})
   else:log('errors' if err and 'Skipped' not in err else 'skipped',s,err or 'No response');self.out(200,{'reply':None,'error':err})
   return
  self.out(404,{'error':'not found'})
if __name__=='__main__':HTTPServer(('0.0.0.0',int(os.getenv('PORT','8000'))),H).serve_forever()
