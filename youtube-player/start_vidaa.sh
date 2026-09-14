#!/data/data/com.termux/files/usr/bin/bash
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SERVER="$SCRIPT_DIR/server.py"
CONFIG_TOKEN_FILE="/storage/emulated/0/Documents/.vidaa-config-token.txt"
PROXY_CONFIG_URL="https://raw.githubusercontent.com/accesstopm-wq/Vibecode/main/youtube-player/backend-url.json"

if [ ! -f "$SERVER" ]; then
  echo "ERROR: server.py not found: $SERVER"
  exit 1
fi

if [ ! -f "$CONFIG_TOKEN_FILE" ]; then
  echo "ERROR: config token missing: $CONFIG_TOKEN_FILE"
  exit 1
fi

pkill -f "python.*server.py" 2>/dev/null || true
pkill -f cloudflared 2>/dev/null || true
sleep 2

: > "$HOME/cloudflared.log"
nohup cloudflared tunnel --url http://127.0.0.1:8765 --no-autoupdate > "$HOME/cloudflared.log" 2>&1 &

URL=""
for i in $(seq 1 30); do
  URL=$(grep -o 'https://[-a-zA-Z0-9]*\.trycloudflare\.com' "$HOME/cloudflared.log" | tail -1)
  [ -n "$URL" ] && break
  sleep 1
done

if [ -z "$URL" ]; then
  echo "ERROR: Cloudflare URL not found"
  cat "$HOME/cloudflared.log"
  exit 1
fi

echo "Cloudflare: $URL"
sed -i "s|^BASE_URL=.*|BASE_URL='$URL'|" "$SERVER"

nohup python "$SERVER" > "$HOME/server.log" 2>&1 &
sleep 2

HEALTH=$(curl -s --max-time 10 http://127.0.0.1:8765/health || true)
echo "Backend: $HEALTH"
case "$HEALTH" in
  *'"ok":true'*|*'"ok": true'*) ;;
  *) echo "ERROR: backend did not start"; tail -30 "$HOME/server.log"; exit 1;;
esac

PROXY_URL=$(curl -fsS --max-time 10 "${PROXY_CONFIG_URL}?ts=$(date +%s)" | python -c 'import sys,json; print(json.load(sys.stdin).get("apiBase",""))' 2>/dev/null || true)
if [ -z "$PROXY_URL" ]; then
  echo "ERROR: stable config proxy URL not found"
  exit 1
fi

CONFIG_TOKEN=$(tr -d '[:space:]' < "$CONFIG_TOKEN_FILE")
if [ -z "$CONFIG_TOKEN" ]; then
  echo "ERROR: config token is empty"
  exit 1
fi

PAYLOAD=$(printf '{"origin":"%s"}' "$URL")
RESULT=$(curl -fsS --max-time 20 -X POST \
  -H "Authorization: Bearer $CONFIG_TOKEN" \
  -H 'Content-Type: application/json' \
  "$PROXY_URL/config" \
  -d "$PAYLOAD" || true)

case "$RESULT" in
  *'"ok":true'*) ;;
  *)
    echo "ERROR: failed to update stable config proxy"
    echo "$RESULT"
    exit 1
    ;;
esac

echo
echo "========================================"
echo "TUNNEL: $URL"
echo "PROXY:  $PROXY_URL"
echo "CONFIG: UPDATED"
echo "========================================"
printf '%s\n' "$URL" > "$HOME/vidaa-backend-url.txt"
