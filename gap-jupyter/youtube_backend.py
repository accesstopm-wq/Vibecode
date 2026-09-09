#!/usr/bin/env python3
import json
import os
import re
import subprocess
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse, unquote

HOST = "0.0.0.0"
PORT = int(os.environ.get("YOUTUBE_BACKEND_PORT", "8765"))
YTDLP = os.environ.get("YTDLP_BIN", "yt-dlp")
DENO = os.environ.get("DENO_BIN", "/root/.deno/bin/deno")

YOUTUBE_RE = re.compile(r"(?:youtu\.be/|youtube\.com/(?:watch\?v=|embed/|shorts/|live/))([A-Za-z0-9_-]{11})")
ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
CALLBACK_RE = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$\.]*$")


def video_id(value):
    value = unquote((value or "").strip())
    if ID_RE.fullmatch(value):
        return value
    try:
        parsed = urlparse(value)
        host = (parsed.hostname or "").lower()
        if host in ("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"):
            qid = parse_qs(parsed.query).get("v", [""])[0]
            if ID_RE.fullmatch(qid):
                return qid
            parts = [p for p in parsed.path.split("/") if p]
            if len(parts) >= 2 and parts[0] in ("embed", "shorts", "live") and ID_RE.fullmatch(parts[1]):
                return parts[1]
        if host in ("youtu.be", "www.youtu.be"):
            part = (parsed.path.strip("/").split("/") or [""])[0]
            if ID_RE.fullmatch(part):
                return part
    except Exception:
        pass
    match = YOUTUBE_RE.search(value)
    return match.group(1) if match else None


def run_ytdlp(url, extractor_args, fmt):
    cmd = [
        YTDLP,
        "--no-playlist",
        "--no-warnings",
        "--quiet",
        "--get-url",
        "--js-runtimes", "deno:" + DENO,
        "--remote-components", "ejs:github",
    ]
    if extractor_args:
        cmd += ["--extractor-args", extractor_args]
    cmd += ["-f", fmt, url]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    lines = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if result.returncode == 0 and lines:
        return lines[-1]
    message = (result.stderr or result.stdout or "yt-dlp failed").strip()
    raise RuntimeError(message[-1200:])


def extract_url(value):
    vid = video_id(value)
    if not vid:
        raise ValueError("Invalid YouTube URL or video ID")

    url = "https://www.youtube.com/watch?v=" + vid
    attempts = [
        ("youtube:player_client=web_safari", "best[protocol^=m3u8]/best"),
        ("youtube:player_client=web_embedded", "best[ext=mp4][height<=720]/best[height<=720]/best"),
        ("youtube:player_client=tv", "best[ext=mp4][height<=720]/best[height<=720]/best"),
        ("", "best[ext=mp4][height<=720]/best[ext=mp4]/best"),
    ]
    errors = []
    for extractor_args, fmt in attempts:
        try:
            return vid, run_ytdlp(url, extractor_args, fmt)
        except Exception as exc:
            errors.append(str(exc))
    raise RuntimeError("; ".join(errors)[-1800:])


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def send_jsonp(self, payload, callback):
        # JSONP is loaded through a <script> tag on old VIDAA browsers.
        # Always return HTTP 200 so backend errors reach the JS callback
        # instead of becoming the browser's generic script onerror/network error.
        body = (callback + "(" + json.dumps(payload, ensure_ascii=False) + ");").encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/javascript; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.send_json(200, {"ok": True, "service": "youtube-backend"})
            return

        if parsed.path != "/api/stream":
            self.send_json(404, {"error": "Not found"})
            return

        params = parse_qs(parsed.query)
        value = params.get("url", [""])[0]
        callback = params.get("callback", [""])[0]
        if callback and not CALLBACK_RE.fullmatch(callback):
            self.send_json(400, {"ok": False, "error": "Invalid callback"})
            return

        try:
            vid, stream_url = extract_url(value)
            payload = {"ok": True, "videoId": vid, "url": stream_url}
            if callback:
                self.send_jsonp(payload, callback)
            else:
                self.send_json(200, payload)
        except ValueError as exc:
            payload = {"ok": False, "error": str(exc)}
            if callback:
                self.send_jsonp(payload, callback)
            else:
                self.send_json(400, payload)
        except subprocess.TimeoutExpired:
            payload = {"ok": False, "error": "yt-dlp timed out"}
            if callback:
                self.send_jsonp(payload, callback)
            else:
                self.send_json(504, payload)
        except Exception as exc:
            payload = {"ok": False, "error": str(exc)}
            if callback:
                self.send_jsonp(payload, callback)
            else:
                self.send_json(502, payload)

    def log_message(self, fmt, *args):
        print("[youtube-backend] " + (fmt % args), flush=True)


if __name__ == "__main__":
    print("YouTube backend listening on %s:%s" % (HOST, PORT), flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
