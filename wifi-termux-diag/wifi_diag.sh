#!/data/data/com.termux/files/usr/bin/bash

LOG="/storage/emulated/0/Documents/wifilogs.txt"
TMP="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/wifi_diag_$$"

mkdir -p "$(dirname "$LOG")" 2>/dev/null || true

echo "" >> "$LOG"
echo "================================================================" >> "$LOG"
echo "[TERMUX DIAG] $(date '+%Y-%m-%d %H:%M:%S.%3N')" >> "$LOG"
echo "================================================================" >> "$LOG"

section() {
  echo "" >> "$LOG"
  echo "--- $1 ---" >> "$LOG"
}

run() {
  echo "> $*" >> "$LOG"
  "$@" >> "$LOG" 2>&1 || echo "[command unavailable/failed: exit $?]" >> "$LOG"
}

section "BASIC SYSTEM"
run uname -a
run getprop ro.product.manufacturer
run getprop ro.product.model
run getprop ro.build.version.release
run getprop ro.build.version.sdk
run getprop ro.boot.hardware

section "WIFI INTERFACE"
if command -v ip >/dev/null 2>&1; then
  run ip addr show wlan0
  run ip route show
  run ip -s link show wlan0
else
  echo "ip command not available" >> "$LOG"
fi

section "PROC WIRELESS"
if [ -r /proc/net/wireless ]; then
  run cat /proc/net/wireless
else
  echo "/proc/net/wireless unavailable" >> "$LOG"
fi

section "WIFI SYSFS STATISTICS"
for f in /sys/class/net/wlan0/statistics/rx_packets \
         /sys/class/net/wlan0/statistics/tx_packets \
         /sys/class/net/wlan0/statistics/rx_bytes \
         /sys/class/net/wlan0/statistics/tx_bytes \
         /sys/class/net/wlan0/statistics/rx_errors \
         /sys/class/net/wlan0/statistics/tx_errors \
         /sys/class/net/wlan0/statistics/rx_dropped \
         /sys/class/net/wlan0/statistics/tx_dropped \
         /sys/class/net/wlan0/statistics/collisions; do
  if [ -r "$f" ]; then
    printf '%s = ' "$f" >> "$LOG"
    cat "$f" >> "$LOG"
  fi
done

section "ANDROID WIFI DUMPSYS"
if command -v dumpsys >/dev/null 2>&1; then
  run dumpsys wifi
else
  echo "dumpsys unavailable from this Termux context" >> "$LOG"
fi

section "CONNECTIVITY DUMPSYS"
if command -v dumpsys >/dev/null 2>&1; then
  run dumpsys connectivity
  run dumpsys netd
  run dumpsys network_stack
else
  echo "dumpsys unavailable from this Termux context" >> "$LOG"
fi

section "ROUTER / INTERNET PING"
GATEWAY=""
if command -v ip >/dev/null 2>&1; then
  GATEWAY=$(ip route 2>/dev/null | awk '/default/ {print $3; exit}')
fi
if [ -n "$GATEWAY" ]; then
  echo "Gateway: $GATEWAY" >> "$LOG"
  if command -v ping >/dev/null 2>&1; then
    run ping -c 20 -W 1 "$GATEWAY"
  fi
else
  echo "Gateway not detected" >> "$LOG"
fi

if command -v ping >/dev/null 2>&1; then
  run ping -c 20 -W 1 1.1.1.1
fi

section "DNS"
if command -v nslookup >/dev/null 2>&1; then
  run nslookup example.com
elif command -v getent >/dev/null 2>&1; then
  run getent hosts example.com
else
  echo "DNS utility unavailable" >> "$LOG"
fi

section "TRAFFIC / SOCKETS"
if command -v ss >/dev/null 2>&1; then
  run ss -s
  run ss -rn
else
  echo "ss unavailable" >> "$LOG"
fi

section "ANDROID LOGCAT - WIFI"
if command -v logcat >/dev/null 2>&1; then
  logcat -d -b all -v threadtime 2>/dev/null | grep -iE 'wifi|wlan|wpa|supplicant|cfg80211|firmware|networkagent|connectivity' | tail -n 1500 >> "$LOG" || true
else
  echo "logcat unavailable from this Termux context" >> "$LOG"
fi

section "KERNEL LOG"
if command -v dmesg >/dev/null 2>&1; then
  dmesg 2>&1 | grep -iE 'wifi|wlan|wpa|80211|cfg80211|firmware|qca|ath|mtk|mediatek|exynos' | tail -n 1000 >> "$LOG" || true
else
  echo "dmesg unavailable or restricted" >> "$LOG"
fi

section "END"
echo "[TERMUX DIAG END] $(date '+%Y-%m-%d %H:%M:%S.%3N')" >> "$LOG"
echo "Log: $LOG"
