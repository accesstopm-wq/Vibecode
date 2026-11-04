import json
import os
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

import requests


status = {
    "authentication_success": False,
    "csrftoken_received": False,
    "sessionid_received": False,
    "protected_page_access": False,
}

try:
    base = os.environ["AD_URL"].strip().rstrip("/") + "/"
    login_url = urljoin(base, "login/")
    target_url = urljoin(base, "brand/ranking/")

    session = requests.Session()
    page = session.get(login_url, timeout=30)
    page.raise_for_status()

    csrf = session.cookies.get("csrftoken")
    if not csrf:
        match = re.search(
            r'name=["\']csrfmiddlewaretoken["\']\s+value=["\']([^"\']+)',
            page.text,
        )
        if match:
            csrf = match.group(1)

    if not csrf:
        raise RuntimeError("CSRF token unavailable")

    response = session.post(
        login_url,
        data={
            "csrfmiddlewaretoken": csrf,
            "username": os.environ["AD_LOGIN"],
            "password": os.environ["AD_PASS"],
            "next": target_url,
        },
        headers={"Referer": login_url},
        timeout=30,
        allow_redirects=True,
    )
    response.raise_for_status()

    cookies = session.cookies.get_dict()
    status["csrftoken_received"] = bool(cookies.get("csrftoken"))
    status["sessionid_received"] = bool(cookies.get("sessionid"))

    protected = session.get(target_url, timeout=30, allow_redirects=True)
    protected.raise_for_status()
    status["protected_page_access"] = "/login" not in protected.url.lower()
    status["authentication_success"] = (
        status["protected_page_access"]
        and status["csrftoken_received"]
        and status["sessionid_received"]
    )
except Exception as exc:
    status["error_type"] = type(exc).__name__

Path("auth_status.json").write_text(
    json.dumps(status, indent=2), encoding="utf-8"
)
print(
    "Authentication:", "success" if status["authentication_success"] else "failed",
    "| csrftoken:", "received" if status["csrftoken_received"] else "missing",
    "| sessionid:", "received" if status["sessionid_received"] else "missing",
    "| protected page:", "accessible" if status["protected_page_access"] else "not accessible",
)
sys.exit(0 if status["authentication_success"] else 1)
