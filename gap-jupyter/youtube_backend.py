#!/usr/bin/env python3
import json
import os
import re
import subprocess
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST = "0.0.0.0"
PORT = int(os.environ.get("YOUTUBE_BACKEND_PORT", "8765"))
YTDLP = os.environ.get("YTDLP_BIN", "yt-dlp")
DENO = os.environ.get("DENO_BIN", "/root/.deno/bin/deno")

YOUTUBE_RE = re.compile(r"(?:youtu\\.be/|youtube\\.com/(?:watch\\?v=|embed/|shorts/))([A-Za-z0-9_-]{11})")
ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")


def video_id(value):
    value = (value or "").strip()
    if ID_RE.fullmatch(value):
        return value
    match = YOUTUBE_RE.search(value)
    return match.group(1) if match else None


def extract_url(value):
    vid = video_id(value)
    if not vid:
        raise ValueError("Invalid YouTube URL")

    url = "https://www.youtube.com/watch?v=" + vid
    cmd = [
        YTDLP,
        "--no-playlist",
        "--no-warnings",
        "--quiet",
        "--get-url",
        "--js-runtimes", "deno:" + DENO,
        "--remote-components", "ejs:github",
        "-f", "best[ext=mp4][height<=720]/best[ext=mp4]/best",
        url,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=45)
    if result.returncode != 0:
        message = (result.stderr or result.stdout or "yt-dlp failed").strip()
        raise RuntimeError(message[-1000:])

    lines = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if not lines:
        raise RuntimeError("yt-dlp returned no stream URL")
    return vid, lines[-1]


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

        value = parse_qs(parsed.query).get("url", [""])[0]
        try:
            vid, stream_url = extract_url(value)
            self.send_json(200, {"ok": True, "videoId": vid, "url": stream_url})
        except ValueError as exc:
            self.send_json(400, {"ok": False, "error": str(exc)})
        except subprocess.TimeoutExpired:
            self.send_json(504, {"ok": False, "error": "yt-dlp timed out"})
        except Exception as exc:
            self.send_json(502, {"ok": False, "error": str(exc)})

    def log_message(self, fmt, *args):
        print("[youtube-backend] " + (fmt % args), flush=True)


if __name__ == "__main__":
    print("YouTube backend listening on %s:%s" % (HOST, PORT), flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
