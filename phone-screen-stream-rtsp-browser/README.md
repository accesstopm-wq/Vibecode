# RTSP → Browser bridge for Termux

ScreenStream RTSP → MediaMTX → WebRTC → browser.

Install:
```bash
pkg update
pkg install curl tar
git clone https://github.com/accesstopm-wq/Vibecode.git
cd Vibecode/phone-screen-stream-rtsp-browser
```

Edit `run.sh` and put your ScreenStream URL into `RTSP_URL="..."`.

Then:
```bash
chmod +x run.sh
./run.sh
```

The script downloads MediaMTX v1.21.1 for ARM64 on first run, starts the RTSP proxy and prints the browser URL.

**Open the printed HTTP URL on the other phone/TV. Do not open the RTSP URL in Chrome.**

Stop with Ctrl+C## Run

No `nano`, no file editing. Pass the ScreenStream RTSP URL directly:

```bash
chmod +x run.sh
./run.sh "rtsp://192.168.1.9:8554/screen"
```

Then:
```bash
chmod +x run.sh
./run.sh
```

The script downloads MediaMTX v1.21.1 for ARM64 on first run, starts the RTSP proxy and prints the browser URL.

**Open the printed HTTP URL on the other phone/TV. Do not open the RTSP URL in Chrome.**

Stop with Ctrl+C.