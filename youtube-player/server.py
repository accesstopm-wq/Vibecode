#!/usr/bin/env python3
import os, re, time, json, shutil, signal, threading, subprocess
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

HOST='127.0.0.1'
PORT=8765
BASE_URL='https://REPLACED_BY_START_SCRIPT.trycloudflare.com'
ROOT=os.path.expanduser('~/vidaa_streams')
URL_TTL=45*60
HLS_TIME=2
HLS_LIST_SIZE=30
READY_TIMEOUT=90
os.makedirs(ROOT, exist_ok=True)
LOCK=threading.RLock(); STATES={}; SESSIONS={}; COUNTER=0

def now(): return time.time()
def vid_dir(v): return os.path.join(ROOT,re.sub(r'[^A-Za-z0-9_-]','_',v))
def sess_dir(v,t): return os.path.join(vid_dir(v),t)
def token(g):
    global COUNTER
    COUNTER+=1
    return '%d-%d-%d'%(int(now()*1000),COUNTER,g)
def run(cmd,timeout=60): return subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=timeout)

def extract_streams(source):
    def get(fmt):
        try: p=run(['yt-dlp','--no-playlist','--no-warnings','--quiet','-f',fmt,'-g',source],60)
        except subprocess.TimeoutExpired: return None
        if p.returncode!=0:return None
        lines=[x.strip() for x in p.stdout.splitlines() if x.strip()]
        return lines[0] if lines else None
    v=get('bestvideo[ext=mp4][vcodec^=avc1][height<=720]') or get('bestvideo[height<=720]')
    a=get('bestaudio[ext=m4a][acodec^=mp4a]') or get('bestaudio')
    if not v or not a: raise RuntimeError('yt-dlp could not extract video/audio URL')
    try:
        p=run(['yt-dlp','--no-playlist','--no-warnings','--quiet','--print','%(duration)s',source],60)
        duration=float(p.stdout.strip().splitlines()[0]) if p.returncode==0 and p.stdout.strip() else 0
    except Exception: duration=0
    return v,a,duration

def state_for(v):
    return STATES.setdefault(v,{'video_url':None,'audio_url':None,'duration':0,'url_time':0,'active':None,'building':None,'generation':0,'source_url':None,'condition':threading.Condition(LOCK)})

def cached_streams(v,source):
    with LOCK:
        s=state_for(v)
        if s['video_url'] and s['audio_url'] and now()-s['url_time']<URL_TTL:
            return s['video_url'],s['audio_url'],s.get('duration',0)
    print('[backend] extracting YouTube URLs:',v,flush=True)
    vu,au,duration=extract_streams(source)
    with LOCK:
        s=state_for(v);s['video_url']=vu;s['audio_url']=au;s['duration']=duration;s['url_time']=now();s['source_url']=source
    print('[backend] duration=',duration,flush=True)
    return vu,au,duration

def ready(sess):
    p=os.path.join(sess['dir'],'index.m3u8')
    if not os.path.isfile(p):return False
    try:
        if os.path.getsize(p)==0:return False
        return any(n.endswith('.ts') and os.path.getsize(os.path.join(sess['dir'],n))>0 for n in os.listdir(sess['dir']))
    except Exception:return False

def ffmpeg(v,t,vu,au,start):
    d=sess_dir(v,t);os.makedirs(d,exist_ok=True);out=os.path.join(d,'index.m3u8')
    cmd=['ffmpeg','-hide_banner','-loglevel','warning',
         '-reconnect','1','-reconnect_streamed','1','-reconnect_on_network_error','1','-reconnect_on_http_error','4xx,5xx','-reconnect_delay_max','5','-ss',str(start),'-i',vu,
         '-reconnect','1','-reconnect_streamed','1','-reconnect_on_network_error','1','-reconnect_on_http_error','4xx,5xx','-reconnect_delay_max','5','-ss',str(start),'-i',au,
         '-map','0:v:0','-map','1:a:0','-c:v','copy','-c:a','aac','-ar','48000','-ac','2','-b:a','128k','-fflags','+genpts','-avoid_negative_ts','make_zero','-af','aresample=async=1:first_pts=0','-muxdelay','0','-muxpreload','0','-f','hls','-hls_time',str(HLS_TIME),'-hls_list_size',str(HLS_LIST_SIZE),'-hls_flags','independent_segments+temp_file+delete_segments','-hls_delete_threshold','2','-hls_segment_type','mpegts',out]
    log=open(os.path.join(d,'ffmpeg.log'),'w');p=subprocess.Popen(cmd,stdout=subprocess.DEVNULL,stderr=log);return p,log

def cleanup(sess):
    if not sess:return
    sess['cancelled']=True
    try:
        p=sess.get('process')
        if p and p.poll() is None:
            p.terminate()
            try:p.wait(3)
            except subprocess.TimeoutExpired:p.kill()
    except Exception:pass
    try:
        if sess.get('log'):sess['log'].close()
    except Exception:pass
    shutil.rmtree(sess.get('dir',''),ignore_errors=True)
    with LOCK:SESSIONS.pop(sess.get('token'),None)

def build(v,source,b):
    try:
        with LOCK:
            s=STATES.get(v)
            if not s or s.get('generation')!=b['generation'] or s.get('building') is not b:
                b['cancelled']=True
        if b.get('cancelled'): return
        vu,au,duration=cached_streams(v,source)
        with LOCK:
            s=STATES.get(v)
            if not s or s.get('generation')!=b['generation'] or s.get('building') is not b:
                b['cancelled']=True
        if b.get('cancelled'): return
        print('[backend] BUILD',v,'start=',b['start'],'generation=',b['generation'],flush=True)
        p,log=ffmpeg(v,b['token'],vu,au,b['start'])
        with LOCK:b['process']=p;b['log']=log
        deadline=now()+READY_TIMEOUT
        while now()<deadline:
            with LOCK:
                s=STATES.get(v)
                stale=(not s or s.get('generation')!=b['generation'] or s.get('building') is not b or b.get('cancelled'))
            if stale:
                b['cancelled']=True
                break
            if ready(b): break
            if p.poll() is not None: break
            time.sleep(.2)
        if b.get('cancelled') or not ready(b):
            if not b.get('cancelled'): print('[backend] BUILD FAILED',v,'start=',b['start'],flush=True)
            return
        old=None
        with LOCK:
            s=STATES.get(v)
            if not s or s.get('generation')!=b['generation'] or s.get('building') is not b:
                b['cancelled']=True
            else:
                old=s.get('active');s['active']=b;s['building']=None;b['active']=True;b['duration']=duration
                print('[backend] READY/SWITCH',v,'start=',b['start'],'generation=',b['generation'],flush=True)
                s['condition'].notify_all()
        if b.get('cancelled'):
            return
        if old and old is not b:
            print('[backend] CLEANUP OLD',v,'start=',old['start'],flush=True)
            cleanup(old)
    except Exception as e:
        print('[backend] build exception:',repr(e),flush=True)
        with LOCK:
            s=STATES.get(v)
            if s and s.get('building') is b:
                s['building']=None;s['condition'].notify_all()
    finally:
        with LOCK:
            s=STATES.get(v)
            if s and s.get('building') is b and (b.get('cancelled') or b.get('active')):
                s['building']=None;s['condition'].notify_all()
        if b.get('cancelled') and not b.get('active'):
            cleanup(b)

def request(v,source,start):
    old_build=None
    with LOCK:
        s=state_for(v);s['source_url']=source;active=s.get('active')
        if active and abs(active['start']-start)<1:return active,None
        s['generation']+=1;gen=s['generation']
        old_build=s.get('building')
        if old_build:
            old_build['cancelled']=True
        t=token(gen)
        b={'video_id':v,'token':t,'generation':gen,'start':start,'duration':s.get('duration',0),'dir':sess_dir(v,t),'process':None,'log':None,'active':False,'cancelled':False}
        SESSIONS[t]=b;s['building']=b
        worker=threading.Thread(target=build,args=(v,source,b),daemon=True);worker.start()
    if old_build:cleanup(old_build)
    deadline=now()+READY_TIMEOUT
    with LOCK:
        s=STATES.get(v)
        while now()<deadline:
            if s.get('generation')!=gen:
                return None,'superseded'
            active=s.get('active')
            if active and active.get('generation')==gen:
                return active,None
            s['condition'].wait(timeout=.2)
        return None,'timeout'

class Handler(BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    def log_message(self,*a):pass
    def json(self,obj,status=200):
        b=json.dumps(obj).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(b)));self.send_header('Access-Control-Allow-Origin','*');self.send_header('Access-Control-Allow-Methods','GET,OPTIONS');self.send_header('Access-Control-Allow-Headers','*');self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(b)
    def do_OPTIONS(self):self.json({},204)
    def do_GET(self):
        u=urlparse(self.path);q=parse_qs(u.query);p=u.path
        if p=='/health':return self.json({'ok':True})
        if p=='/api/stream':
            source=q.get('url',[''])[0]
            if not source:return self.json({'error':'missing url'},400)
            m=re.search(r'(?:v=|youtu\\.be/|/shorts/|/embed/)([A-Za-z0-9_-]{11})',source)
            if not m:return self.json({'error':'invalid YouTube URL'},400)
            v=m.group(1)
            try:start=max(0,float(q.get('start',['0'])[0]))
            except:start=0
            sess,err=request(v,source,start)
            if not sess:
                if err=='superseded':return self.json({'ok':False,'stale':True,'error':'request superseded'},409)
                if err=='timeout':return self.json({'ok':False,'error':'stream build timeout'},504)
                return self.json({'ok':False,'error':'stream build failed'},502)
            duration=state_for(v).get('duration',0)
            url=BASE_URL.rstrip('/')+'/hls/'+v+'/'+sess['token']+'/index.m3u8'
            return self.json({'ok':True,'videoId':v,'url':url,'start':sess['start'],'duration':duration,'generation':sess['generation']})
        if p.startswith('/hls/'):
            parts=p.strip('/').split('/')
            if len(parts)<4:return self.send_error(404)
            v,t=parts[1],parts[2];rel='/'.join(parts[3:])
            with LOCK:sess=SESSIONS.get(t)
            if not sess:return self.send_error(404)
            root=os.path.realpath(sess['dir']);f=os.path.realpath(os.path.join(sess['dir'],rel))
            if not f.startswith(root+os.sep):return self.send_error(403)
            if not os.path.isfile(f):return self.send_error(404)
            typ='application/vnd.apple.mpegurl' if f.endswith('.m3u8') else 'video/mp2t'
            try:
                size=os.path.getsize(f);self.send_response(200);self.send_header('Content-Type',typ);self.send_header('Content-Length',str(size));self.send_header('Access-Control-Allow-Origin','*');self.send_header('Cache-Control','no-cache');self.end_headers()
                with open(f,'rb') as x:
                    while True:
                        b=x.read(65536)
                        if not b:break
                        self.wfile.write(b)
            except (BrokenPipeError,ConnectionResetError):pass
            return
        self.send_error(404)

def stop(*a):
    with LOCK:ss=list(SESSIONS.values())
    for s in ss:cleanup(s)
    os._exit(0)
signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGINT,stop)
print('[backend] starting on %s:%d'%(HOST,PORT),flush=True);print('[backend] BASE_URL:',BASE_URL,flush=True)
ThreadingHTTPServer((HOST,PORT),Handler).serve_forever()
