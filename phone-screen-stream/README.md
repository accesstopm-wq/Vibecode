# Phone Screen Stream — Termux → TV

Local-only screen + system-audio streaming over Wi-Fi.

## What it does

- Captures the Android display with scrcpy.
- Captures Android playback/system audio.
- Keeps mirroring alive with the phone's physical screen off.
- H.264 video is passed through without re-encoding.
- Audio is AAC for TV/browser compatibility.
- Serves an HLS stream from Termux.
- No GitHub Pages, cloud server, account or external streaming server.

## Install

Enable the Termux X11 repository, then install:

```bash
pkg update
pkg install x11-repo
pkg install scrcpy ffmpeg python
```

scrcpy is available in the Termux X11 package repository.

## Run

From this directory:

```bash
chmod +x stream.sh
./stream.sh
```

The script prints an address such as:

```
http://192.168.1.123:8787/
```

Open that exact address on the TV while the phone and TV are on the same Wi-Fi.

## Tuning

Default:

- 1280px maximum dimension
- 6 Mbps video
- 30 FPS
- 128 kbps AAC audio
- 2 second HLS segments

Examples:

```bash
WIDTH=1920 BITRATE=10M FPS=30 ./stream.sh
```

For lower Wi-Fi load:

```bash
WIDTH=1280 BITRATE=4M FPS=30 ./stream.sh
```

## Screen off

The script starts scrcpy with:

```
--turn-screen-off --stay-awake
```

This is specifically the scrcpy mirroring mode where the physical phone display is turned off while mirroring continues.

## Important

Android 11+ is required for system-audio forwarding. Some apps can opt out of audio capture, and DRM-protected content may appear black or mute when captured.
