#!/usr/bin/env python3
import base64
import hashlib
import json
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

TOKEN = os.environ.get("GITHUB_TOKEN")
REPO = os.environ.get("GITHUB_REPOSITORY")
BRANCH = os.environ.get("GITHUB_REF_NAME", "main")
ROOT = Path("/workspace")

if not TOKEN or not REPO:
    raise SystemExit("Save to GitHub is not configured: missing GITHUB_TOKEN or GITHUB_REPOSITORY")

API = f"https://api.github.com/repos/{REPO}/contents"
HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "Vibecode-GAP-Jupyter",
}


def request(method, url, data=None):
    body = None if data is None else json.dumps(data).encode()
    req = Request(url, data=body, headers=HEADERS, method=method)
    try:
        with urlopen(req) as response:
            return response.status, json.loads(response.read().decode())
    except HTTPError as e:
        payload = e.read().decode(errors="replace")
        if e.code == 404:
            return 404, None
        raise SystemExit(f"GitHub API error {e.code}: {payload}")


files = sorted(ROOT.rglob("*.ipynb"))
if not files:
    raise SystemExit("No .ipynb notebooks found in /workspace")

saved = 0
for path in files:
    rel = path.relative_to(ROOT).as_posix()
    content = path.read_bytes()
    encoded = base64.b64encode(content).decode()

    # GitHub's Contents API returns a git blob SHA. Calculate the same SHA locally
    # so unchanged notebooks do not create unnecessary commits.
    blob_sha = hashlib.sha1(b"blob %d\0" % len(content) + content).hexdigest()
    status, remote = request("GET", f"{API}/{rel}?ref={BRANCH}")
    remote_sha = remote.get("sha") if remote else None

    if remote_sha == blob_sha:
        print(f"UNCHANGED  {rel}")
        continue

    payload = {
        "message": f"Save GAP notebook: {path.name}",
        "content": encoded,
        "branch": BRANCH,
    }
    if remote_sha:
        payload["sha"] = remote_sha

    request("PUT", f"{API}/{rel}", payload)
    print(f"SAVED      {rel}")
    saved += 1

print(f"Done. {saved} notebook(s) saved to {REPO}:{BRANCH}.")
