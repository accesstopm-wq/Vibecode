import json
import os
import re
from pathlib import Path
from urllib.parse import urljoin

import requests
from cryptography.fernet import Fernet


base = os.environ["AD_URL"].strip().rstrip("/") + "/"
login = os.environ["AD_LOGIN"]
password = os.environ["AD_PASS"]
export_key = os.environ["AD_EXPORT_KEY"].encode()

# AD_URL is the only source for the host and application path.
login_url = urljoin(base, "login/")
target_url = urljoin(base, "brand/ranking/")

session = requests.Session()
response = session.get(login_url, timeout=30)
response.raise_for_status()

csrf = session.cookies.get("csrftoken")
if not csrf:
    match = re.search(
        r'name=["\']csrfmiddlewaretoken["\']\\s+value=["\']([^"\']+)',
        response.text,
    )
    if not match:
        raise RuntimeError("Could not obtain CSRF token from login page")
    csrf = match.group(1)

response = session.post(
    login_url,
    data={
        "csrfmiddlewaretoken": csrf,
        "username": login,
        "password": password,
        "next": target_url,
    },
    headers={"Referer": login_url},
    timeout=30,
    allow_redirects=True,
)
response.raise_for_status()

check = session.get(target_url, timeout=30, allow_redirects=True)
check.raise_for_status()
if "/login" in check.url.lower():
    raise RuntimeError("Authentication failed: protected page redirected to login")

cookies = session.cookies.get_dict()
tokens = {
    "csrftoken": cookies.get("csrftoken"),
    "sessionid": cookies.get("sessionid"),
}
if not all(tokens.values()):
    raise RuntimeError("Login completed but expected session cookies were not found")

# Store credentials only as an encrypted artifact; never print them in Actions logs.
cipher = Fernet(export_key)
Path("session_tokens.enc").write_bytes(
    cipher.encrypt(json.dumps(tokens).encode("utf-8"))
)
print("Authentication successful. Encrypted token artifact prepared.")
