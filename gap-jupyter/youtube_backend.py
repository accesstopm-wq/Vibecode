#!/usr/bin/env python3
import json, os, re, subprocess, urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse, unquote

HOST = "0.0.0.0"
PORT = int(os.environ.get("YOUTUBE_BACKEND_PORT", "8765"))
YTDLP = os.environ.get("YTDLP_BIN", "yt-dlp")
DENO = os.environ.get("DENO_BIN", "/opt/gap/.deno/bin/deno")
POT_URL = os.environ.get("YOUTUBE_POT_URL", "http://127.0.0.1:4416")

# Current public instances. We try them concurrently; failed instances are harmless.
INVIDIOUS = [
    "https://inv.nadeko.net",
    "https://invidious.nerdvpn.de",
    "https://yt.chocolatemoo53.com",
    "https://invidious.tiekoetter.com",
    "https://invidious.f5.si",
]
PIPED = [
    "https://pipedapi.kavin.rocks", "https://pipedapi.tokhmi.xyz",
    "https://pipedapi.moomoo.me", "https://pipedapi.syncpundit.io",
    "https://api-piped.mha.fi", "https://piped-api.garudalinux.org",
    "https://pipedapi.rivo.lol", "https://pipedapi.leptons.xyz",
    "https://pipedapi.qdi.fi", "https://piped-api.hostux.net",
    "https://pdapi.vern.cc", "https://pipedapi.pfcd.me",
    "https://pipedapi.frontendfriendly.xyz", "https://api.piped.yt",
    "https://pipedapi.astartes.nl", "https://pipedapi.osphost.fi",
    "https://pipedapi.simpleprivacy.fr", "https://pipedapi.drgns.space",
    "https://piapi.ggtyler.dev", "https://api.watch.pluto.lat",
    "https://piped-backend.seitan-ayoub.lol", "https://pipedapi.owo.si",
    "https://api.piped.minionflo.net", "https://pipedapi.nezumi.party",
    "https://pipedapi.ducks.party", "https://pipedapi.ngn.tf",
    "https://pipedapi.coldforge.xyz", "https://piped-api.codespace.cz",
    "https://pipedapi.reallyaweso.me", "https://pipedapi.phoenixthrush.com",
    "https://api.piped.private.coffee",
]

YOUTUBE_RE = re.compile(r"(?:youtu\.be/|youtube\.com/(?:watch\?v=|embed/|shorts/|live/))([A-Za-z0-9_-]{11})")
ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
CALLBACK_RE = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$.]*$")


def video_id(value):
    value = unquote((value or "").strip())
    if ID_RE.fullmatch(value): return value
    try:
        p = urlparse(value); host = (p.hostname or "").lower()
        if host in ("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"):
            q = parse_qs(p.query).get("v", [""])[0]
            if ID_RE.fullmatch(q): return q
            parts = [x for x in p.path.split("/") if x]
            if len(parts) >= 2 and parts[0] in ("embed", "shorts", "live") and ID_RE.fullmatch(parts[1]): return parts[1]
        if host in ("youtu.be", "www.youtu.be"):
            x = p.path.strip("/").split("/")[0]
            if ID_RE.fullmatch(x): return x
    except Exception: pass
    m = YOUTUBE_RE.search(value)
    return m.group(1) if m else None


def run_ytdlp(url, args, fmt):
    cmd = [YTDLP, "--no-playlist", "--no-warnings", "--quiet", "--get-url",
           "--js-runtimes", "deno:" + DENO, "--remote-components", "ejs:github"]
    if args: cmd += ["--extractor-args", args]
    cmd += ["-f", fmt, url]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    lines = [x.strip() for x in r.stdout.splitlines() if x.strip()]
    if r.returncode == 0 and lines: return lines[-1]
    raise RuntimeError((r.stderr or r.stdout or "yt-dlp failed").strip()[-1200:])


def probe_invidious(base, vid):
    # local=true asks Invidious to proxy the media, avoiding direct YouTube access from our TV.
    u = base.rstrip("/") + "/api/v1/videos/" + vid + "?local=true&fields=videoId,formatStreams"
    req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=10) as r: data = json.loads(r.read().decode("utf-8"))
    candidates = []
    for s in data.get("formatStreams", []):
        url = s.get("url") or ""
        typ = (s.get("type") or "").lower()
        if not url or ("video/mp4" not in typ and str(s.get("container", "")).lower() != "mp4"): continue
        label = str(s.get("qualityLabel") or "")
        m = re.search(r"(\d+)", label)
        h = int(m.group(1)) if m else 0
        if h <= 720: candidates.append((h, url))
    if not candidates:
        raise RuntimeError("no proxied MP4 stream")
    return max(candidates, key=lambda x: x[0])[1]


def run_invidious(vid):
    errors = []
    with ThreadPoolExecutor(max_workers=len(INVIDIOUS)) as pool:
        fs = {pool.submit(probe_invidious, x, vid): x for x in INVIDIOUS}
        for f in as_completed(fs):
            base = fs[f]
            try: return f.result()
            except Exception as e: errors.append(base + ": " + str(e))
    raise RuntimeError("Invidious fallback failed: " + "; ".join(errors)[-1600:])


def probe_piped(base, vid):
    req = urllib.request.Request(base.rstrip("/") + "/streams/" + vid, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=8) as r: data = json.loads(r.read().decode("utf-8"))
    candidates = []
    for s in data.get("videoStreams", []):
        if s.get("videoOnly") or not s.get("url"): continue
        mime = str(s.get("mimeType") or "").lower(); fmt = str(s.get("format") or "").upper()
        if not (mime.startswith("video/mp4") or fmt == "MPEG_4"): continue
        try: h = int(s.get("height") or 0)
        except Exception: h = 0
        candidates.append((h, s["url"]))
    good = [x for x in candidates if x[0] <= 720] or candidates
    if not good: raise RuntimeError("no compatible MP4 stream")
    return max(good, key=lambda x: x[0])[1]


def run_piped(vid):
    errors = []
    with ThreadPoolExecutor(max_workers=min(16, len(PIPED))) as pool:
        fs = {pool.submit(probe_piped, x, vid): x for x in PIPED}
        for f in as_completed(fs):
            base = fs[f]
            try: return f.result()
            except Exception as e: errors.append(base + ": " + str(e))
    raise RuntimeError("Piped fallback failed: " + "; ".join(errors)[-1600:])


def extract_url(value):
    vid = video_id(value)
    if not vid: raise ValueError("Invalid YouTube URL or video ID")
    url = "https://www.youtube.com/watch?v=" + vid
    pot = "youtubepot-bgutilhttp:base_url=" + POT_URL
    attempts = [
        ("youtube:player_client=android_vr", "best[ext=mp4][height<=720]/best[height<=720]/best"),
        ("youtube:player_client=web_embedded", "best[ext=mp4][height<=720]/best[height<=720]/best"),
        ("youtube:player_client=tv", "best[ext=mp4][height<=720]/best[height<=720]/best"),
        ("youtube:player_client=mweb;" + pot, "best[ext=mp4][height<=720]/best[height<=720]/best"),
        (pot, "best[ext=mp4][height<=720]/best[ext=mp4]/best"),
    ]
    errors = []
    for args, fmt in attempts:
        try: return vid, run_ytdlp(url, args, fmt)
        except Exception as e: errors.append(str(e))
    for fn in (run_invidious, run_piped):
        try: return vid, fn(vid)
        except Exception as e: errors.append(str(e))
    raise RuntimeError("; ".join(errors)[-1800:])


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status); self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body))); self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store"); self.end_headers(); self.wfile.write(body)
    def send_jsonp(self, payload, callback):
        body = (callback + "(" + json.dumps(payload, ensure_ascii=False) + ");").encode("utf-8")
        self.send_response(200); self.send_header("Content-Type", "application/javascript; charset=utf-8")
        self.send_header("Content-Length", str(len(body))); self.send_header("Cache-Control", "no-store")
        self.end_headers(); self.wfile.write(body)
    def do_OPTIONS(self):
        self.send_response(204); self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS"); self.send_header("Access-Control-Allow-Headers", "Content-Type"); self.end_headers()
    def do_GET(self):
        p = urlparse(self.path)
        if p.path == "/health": self.send_json(200, {"ok": True, "service": "youtube-backend"}); return
        if p.path != "/api/stream": self.send_json(404, {"error": "Not found"}); return
        q = parse_qs(p.query); value = q.get("url", [""])[0]; cb = q.get("callback", [""])[0]
        if cb and not CALLBACK_RE.fullmatch(cb): self.send_json(400, {"ok": False, "error": "Invalid callback"}); return
        try:
            vid, stream = extract_url(value); payload = {"ok": True, "videoId": vid, "url": stream}
            if cb: self.send_jsonp(payload, cb)
            else: self.send_json(200, payload)
        except ValueError as e:
            payload = {"ok": False, "error": str(e)}
            if cb: self.send_jsonp(payload, cb)
            else: self.send_json(400, payload)
        except subprocess.TimeoutExpired:
            payload = {"ok": False, "error": "yt-dlp timed out"}
            if cb: self.send_jsonp(payload, cb)
            else: self.send_json(504, payload)
        except Exception as e:
            payload = {"ok": False, "error": str(e)}
            if cb: self.send_jsonp(payload, cb)
            else: self.send_json(502, payload)
    def log_message(self, fmt, *args): print("[youtube-backend] " + (fmt % args), flush=True)

if __name__ == "__main__":
    print("YouTube backend listening on %s:%s" % (HOST, PORT), flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
