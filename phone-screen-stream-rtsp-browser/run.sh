#!/data/data/com.termux/files/usr/bin/bash
set -e
RTSP_URL="PASTE_YOUR_SCREENSTREAM_RTSP_URL_HERE"
STREAM_PATH="screen"
MEDIAMTX_VERSION="v1.21.1"
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN="$BASE_DIR/mediamtx"
CONFIG="$BASE_DIR/mediamtx.yml"
ARCHIVE="$BASE_DIR/mediamtx.tar.gz"

if [ "$RTSP_URL" = "PASTE_YOUR_SCREENSTREAM_RTSP_URL_HERE" ]; then
  echo "ERROR: Put the ScreenStream RTSP URL into run.sh first."
  echo 'Example: RTSP_URL="rtsp://192.168.1.9:8554/screen"'
  exit 1
fi

case "$(uname -m)" in
  aarch64|arm64) ASSET="mediamtx_${MEDIAMTX_VERSION}_linux_arm64.tar.gz" ;;
  *) echo "Unsupported architecture: $(uname -m)"; exit 1 ;;
esac

if [ ! -x "$BIN" ]; then
  echo "Downloading MediaMTX $MEDIAMTX_VERSION..."
  curl -fL --retry 3 "https://github.com/bluenviron/mediamtx/releases/download/${MEDIAMTX_VERSION}_/${ASSET}" -o "$ARCHIVE"
  tar -xzf "$ARCHIVE" -C "$BASE_DIR" mediamtx
  chmod +x "$BIN"
  rm -f "$ARCHIVE"
fi

LAN_IP="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}')"
[ -n "$LAN_IP" ] || LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
[ -n "$LAN_IP" ] || { echo "Could not determine LAN IP. Run: ip addr"; exit 1; }

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
echo "ScreenStream RTSP: $RTSP_URL"
echo
echo "OPEN ON OTHER PHONE / TV:"
echo "http://${LAN_IP}:8889/${STREAM_PATH}"
echo
echo "Press Ctrl+C to stop."
echo

exec "$BIN" "$CONFIG"
