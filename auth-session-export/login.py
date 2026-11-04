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
    "api_requested": False,
    "api_http_status": None,
    "api_json_received": False,
}

api_result = None

try:
    base = os.environ["AD_URL"].strip().rstrip("/") + "/"
    login_url = urljoin(base, "login/")
    target_url = urljoin(base, "brand/ranking/")
    api_url = urljoin(base, "/api/ua/brands/")

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

    login_response = session.post(
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
    login_response.raise_for_status()

    cookies = session.cookies.get_dict()
    status["csrftoken_received"] = bool(cookies.get("csrftoken"))
    status["sessionid_received"] = bool(cookies.get("sessionid"))
    if not (status["csrftoken_received"] and status["sessionid_received"]):
        raise RuntimeError("Expected authentication cookies were not received")

    protected = session.get(target_url, timeout=30, allow_redirects=True)
    protected.raise_for_status()
    status["protected_page_access"] = "/login" not in protected.url.lower()
    status["authentication_success"] = status["protected_page_access"]
    if not status["authentication_success"]:
        raise RuntimeError("Protected page redirected to login")

    # The same Session sends its csrftoken and sessionid cookies automatically.
    api_response = session.get(
        api_url,
        params={"limit": 10, "period": "week_20261026"},
        headers={"Accept": "application/json"},
        timeout=30,
    )
    status["api_requested"] = True
    status["api_http_status"] = api_response.status_code
    try:
        api_result = api_response.json()
        status["api_json_received"] = True
    except ValueError:
        api_result = {
            "error": "Response was not valid JSON",
            "http_status": api_response.status_code,
            "content_type": api_response.headers.get("Content-Type"),
            "body_preview": api_response.text[:2000],
        }
    api_response.raise_for_status()

except Exception as exc:
    status["error_type"] = type(exc).__name__
    if api_result is None:
        api_result = {
            "error": "Request did not produce a JSON response",
            "error_type": type(exc).__name__,
            "http_status": status["api_http_status"],
        }

Path("brands.json").write_text(
    json.dumps(api_result, ensure_ascii=False, indent=2), encoding="utf-8"
)
Path("auth_status.json").write_text(
    json.dumps(status, ensure_ascii=False, indent=2), encoding="utf-8"
)

print(
    "Authentication:", "success" if status["authentication_success"] else "failed",
    "| csrftoken:", "received" if status["csrftoken_received"] else "missing",
    "| sessionid:", "received" if status["sessionid_received"] else "missing",
    "| API HTTP:", status["api_http_status"],
    "| JSON:", "received" if status["api_json_received"] else "not received",
)
sys.exit(0 if status["authentication_success"] and status["api_json_received"]
         and status["api_http_status"] is not None
         and 200 <= status["api_http_status"] < 300 else 1)
