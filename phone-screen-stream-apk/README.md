# Phone Screen Stream APK

Android MediaProjection streamer for local Wi-Fi. Captures the phone display with MediaCodec H.264 and system playback audio with AudioPlaybackCapture, then serves a browser page over HTTP/WebSocket.

Start the app, grant screen/audio capture, then open the address shown by the app on a TV browser. The service is a foreground mediaProjection service so capture can continue while the phone display is turned off. Android may still impose OEM battery-management restrictions.

System audio is subject to Android AudioPlaybackCapture rules: apps can prevent their playback from being captured, and protected/DRM content may not be capturable.
