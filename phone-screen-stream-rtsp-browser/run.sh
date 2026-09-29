#!/data/data/com.termux/files/usr/bin/bash
set -e

RTSP_URL="${1:-}"
STREAM_PATH="screen"
MEDIAMTX_VERSION="v1.21.1"

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN="$BASE_DIR/mediamtx"
CONFIG="$BASE_DIR/mediamtx.yml"
ARCHIVE="$BASE_DIR/mediamtx.tar.gz"

if [ -z "$RTSP_URL" ]; then
  echo "Usage: ./run.sh \"rtsp://PHONE_IP:PORT/PATH\""
  exit 1
fi

case "$(uname -m)" in
  aarch64|arm64)
    ASSET="mediamtx_${MEDIAMTX_VERSION}_linux_arm64.tar.gz"
    ;;
  *)
    echo "Unsupported architecture: $(uname -m)"
    exit 1
    ;;
esac

if [ ! -x "$BIN" ]; then
  echo "MediaMTX Linux binary cannot run directly in Android Termux."
  echo "Building MediaMTX as an Android ARM64 PIE binary..."

  if ! command -v go >/dev/null 2>&1; then
    echo "Installing Go..."
    pkg install -y golang git
  fi

  SRC="$BASE_DIR/.mediamtx-src"

  if [ ! -d "$SRC/.git" ]; then
    echo "Downloading MediaMTX source..."
    rm -rf "$SRC"
    git clone --depth 1 --branch "$MEDIAMTX_VERSION" https://github.com/bluenviron/mediamtx.git "$SRC"
  fi

  cd "$SRC"
  echo "Generating MediaMTX sources..."
  go generate ./...

  echo "Building Android ARM64 PIE binary..."
  CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build -buildmode=pie -o "$BIN" .

  chmod +x "$BIN"
  cd "$BASE_DIR"

  echo "MediaMTX Android build ready."
fi

LAN_IP="$(ip -4 addr show wlan0 2>/dev/null | awk '/inet / {print $2; exit}' | cut -d/ -f1)"

# Fallback: any private IPv4 reported by hostname -I.
if [ -z "$LAN_IP" ]; then
  LAN_IP="$(hostname -I 2>/dev/null | tr ' ' '\n' | awk '/^(192\\.168\\.|10\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.)/ {print; exit}')"
fi

# Optional second argument lets you force the LAN IP:
# ./run.sh "rtsp://..." 192.168.1.9
if [ -n "${2:-}" ]; then
  LAN_IP="$2"
fi

if [ -z "$LAN_IP" ]; then
  echo "Could not determine LAN IP."
  echo
  echo "Run: ip -4 addr show wlan0"
  exit 1
fi

cat > "$CONFIG" <<EOF
logLevel: info

webrtc: true
webrtcAddress: :8889
webrtcEncryption: false
webrtcAllowOrigins: ["*"]
webrtcLocalUDPAddress: :8189
webrtcLocalTCPAddress: :8189
webrtcAdditionalHosts: [${LAN_IP}]

paths:
  ${STREAM_PATH}:
    source: ${RTSP_URL}
    rtspTransport: tcp
EOF

echo
echo "=============================================="
echo " RTSP -> BROWSER BRIDGE"
echo "=============================================="
echo "ScreenStream RTSP:"
echo "  $RTSP_URL"
echo
echo "OPEN ON OTHER PHONE / TV:"
echo "  http://${LAN_IP}:8889/${STREAM_PATH}"
echo
echo "Press Ctrl+C to stop."
echo "=============================================="
echo

exec "$BIN" "$CONFIG"
