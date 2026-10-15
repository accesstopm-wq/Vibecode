# Wi-Fi Termux Diagnostics

Run `wifi_diag.sh` in Termux when the Wi-Fi speed is degraded. The script writes diagnostics to `/storage/emulated/0/Documents/wifilogs.txt` (the same log location used by WiFi Bug Hunter).

## First-time setup

```bash
termux-setup-storage
pkg update
pkg install iproute2 dnsutils curl
chmod +x ~/wifi_diag.sh
```

## Run

```bash
bash ~/wifi_diag.sh
```

For richer Android system logs, the script attempts `dumpsys` and `logcat` if they are available to the Termux process. Availability depends on Android/OEM permissions. Root is **not** required for the normal diagnostics.

Run it once while the bug is active, then again after Wi-Fi recovers.
