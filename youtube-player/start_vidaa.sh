#!/data/data/com.termux/files/usr/bin/bash
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SERVER="$SCRIPT_DIR/server.py"
TOKEN_FILE="/storage/emulated/0/Documents/.token.txt"
OWNER="accesstopm-wq"
REPO="Vibecode"
FILE="youtube-player/backend-url.json"
BRANCH="main"

if [ ! -f "$SERVER" ]; then
  echo "ERROR: server.py not found: $SERVER"
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

GH_TOKEN=$(tr -d '[:space:]' < "$TOKEN_FILE" 2>/dev/null || true)
if [ -z "$GH_TOKEN" ]; then
  echo "ERROR: GitHub token missing: $TOKEN_FILE"
  exit 1
fi

RESPONSE=$(curl -s --max-time 20 \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO/contents/$FILE?ref=$BRANCH")

SHA=$(printf '%s' "$RESPONSE" | python -c 'import sys,json; print(json.load(sys.stdin).get("sha",""))' 2>/dev/null || true)
if [ -z "$SHA" ]; then
  echo "ERROR: could not read GitHub file SHA"
  echo "$RESPONSE"
  exit 1
fi

CONTENT=$(printf '{"apiBase":"%s"}\n' "$URL" | base64 | tr -d '\n')
RESULT=$(curl -s --max-time 30 -X PUT \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$OWNER/$REPO/contents/$FILE" \
  -d "{\"message\":\"Update YouTube backend URL\",\"content\":\"$CONTENT\",\"sha\":\"$SHA\",\"branch\":\"$BRANCH\"}")

COMMIT=$(printf '%s' "$RESULT" | python -c 'import sys,json; print(json.load(sys.stdin).get("commit",{}).get("sha",""))' 2>/dev/null || true)
if [ -z "$COMMIT" ]; then
  echo "ERROR: GitHub update failed"
  echo "$RESULT"
  exit 1
fi

echo
echo "========================================"
echo "BACKEND: $URL"
echo "GITHUB:  UPDATED"
echo "COMMIT:  $COMMIT"
echo "========================================"
printf '%s\n' "$URL" > "$HOME/vidaa-backend-url.txt"
