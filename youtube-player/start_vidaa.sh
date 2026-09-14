#!/data/data/com.termux/files/usr/bin/bash
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SERVER="$SCRIPT_DIR/server.py"
REPO_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
CONFIG_FILE="$REPO_DIR/vidaa-backend-url.json"

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

printf '{"apiBase":"%s"}\n' "$URL" > "$CONFIG_FILE"

git -C "$REPO_DIR" add vidaa-backend-url.json
git -C "$REPO_DIR" diff --cached --quiet && echo "GitHub config already up to date" || {
  git -C "$REPO_DIR" commit -m "Update VIDAA tunnel URL"
  git -C "$REPO_DIR" push origin main
}

echo
echo "========================================"
echo "TUNNEL: $URL"
echo "GITHUB: UPDATED"
echo "========================================"
printf '%s\n' "$URL" > "$HOME/vidaa-backend-url.txt"
