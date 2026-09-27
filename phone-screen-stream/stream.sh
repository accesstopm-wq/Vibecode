#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

BASE="$(cd "$(dirname "$0")" && pwd)"
PORT="${PORT:-8787}"
WIDTH="${WIDTH:-1280}"
BITRATE="${BITRATE:-6M}"
FPS="${FPS:-30}"
HLS_TIME="${HLS_TIME:-2}"

CAPTURE="$BASE/.capture.mkv"
WEB="$BASE/www"
HLS="$WEB/live"

mkdir -p "$WEB" "$HLS"
rm -f "$CAPTURE"
touch "$CAPTURE"

cleanup() {
  trap - EXIT INT TERM
  jobs -pr | xargs -r kill 2>/dev/null || true
  rm -f "$CAPTURE"
}
trap cleanup EXIT INT TERM

command -v scrcpy >/dev/null || {
  echo "scrcpy is not installed."
  echo "Install it from Termux:X11 packages: pkg install x11-repo && pkg install scrcpy"
  exit 1
}
command -v ffmpeg >/dev/null || {
  echo "ffmpeg is not installed. Run: pkg install ffmpeg"
  exit 1
}
command -v python >/dev/null || {
  echo "python is not installed. Run: pkg install python"
  exit 1
}

cat > "$WEB/index.html" <<'EOF'
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Phone Stream</title>
<style>
html,body{margin:0;background:#000;width:100%;height:100%;overflow:hidden}
video{width:100%;height:100%;object-fit:contain}
#err{position:fixed;left:0;right:0;bottom:0;padding:12px;color:#fff;background:#9008;font:16px sans-serif}
</style>
</head>
<body>
<video id="v" controls autoplay playsinline></video>
<div id="err" hidden></div>
<script>
const v=document.getElementById('v'), e=document.getElementById('err');
const src='/live/index.m3u8';
if (v.canPlayType('application/vnd.apple.mpegurl')) {
  v.src=src;
  v.play().catch(()=>{});
} else {
  const s=document.createElement('script');
  s.src='https://cdn.jsdelivr.net/npm/hls.js@1.6.2/dist/hls.min.js';
  s.onload=()=>{
    if(window.Hls && Hls.isSupported()){
      const h=new Hls({lowLatencyMode:false});
      h.loadSource(src); h.attachMedia(v);
      h.on(Hls.Events.MANIFEST_PARSED,()=>v.play().catch(()=>{}));
      h.on(Hls.Events.ERROR,(_,d)=>{
        if(d.fatal){e.hidden=false;e.textContent='Stream error: '+d.details;}
      });
    } else {
      e.hidden=false;e.textContent='This browser does not support HLS.';
    }
  };
  s.onerror=()=>{e.hidden=false;e.textContent='Cannot load HLS player.'};
  document.head.appendChild(s);
}
</script>
</body>
</html>
EOF

rm -f "$HLS"/*

# ffmpeg consumes the live Matroska stream produced by scrcpy.
# Video stays H.264; audio is converted to AAC for broad TV/browser support.
tail -c +1 -f "$CAPTURE" | ffmpeg -hide_banner -loglevel warning -fflags +genpts -i pipe:0   -map 0:v:0 -map 0:a:0?   -c:v copy   -c:a aac -b:a 128k -ac 2   -f hls   -hls_time "$HLS_TIME"   -hls_list_size 5   -hls_flags delete_segments+append_list+independent_segments   -hls_segment_type mpegts   "$HLS/index.m3u8" &
FFPID=$!

(
  while [ ! -f "$HLS/index.m3u8" ]; do sleep 0.2; done
  echo
  echo "=== PHONE SCREEN STREAM ==="
  echo "Open on TV:"
  IP="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}')"
  [ -n "$IP" ] || IP="$(ip -4 addr show wlan0 2>/dev/null | awk '/inet /{print $2}' | cut -d/ -f1 | head -n1)"
  echo "http://$IP:$PORT/"
  echo
  echo "Screen-off mode: ON"
  echo "Press Ctrl+C to stop."
  echo
) &

# HTTP server
python -m http.server "$PORT" --bind 0.0.0.0 --directory "$WEB" &
PYFPID=$!

# Capture both display and system playback audio.
# --turn-screen-off keeps mirroring alive while the physical screen is off.
# --stay-awake prevents normal sleep while the stream is running.
scrcpy   --no-window   --no-playback     --turn-screen-off   --stay-awake   --require-audio   --video-codec=h264   --audio-codec=aac   --max-size="$WIDTH"   --video-bit-rate="$BITRATE"   --max-fps="$FPS"   --record="$CAPTURE"   --record-format=mkv
