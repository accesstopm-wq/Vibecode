#!/data/data/com.termux/files/usr/bin/bash
set -e
RTSP_URL="${1:-}"

if [ -z "$RTSP_URL" ]; then
  echo "Usage: ./run.sh \"rtsp://PHONE_IP:PORT/PATH\""
  exit 1
fi


