#!/system/bin/sh
# PulseXt safe boot service.
# The native Rodin daemon is deliberately opt-in: an unstable vendor daemon
# must never be allowed to freeze or reboot a device during boot.
MODDIR=${0%/*}
BASE=/data/adb/pulsext-touch
STATE="$BASE/rodin-state"
DAEMON="$MODDIR/bin/rodin_daemon"
LOG="$BASE/rodin-daemon.log"
umask 077
mkdir -p "$BASE" "$STATE"
chmod 0700 "$BASE" "$STATE"

# Bounded wait: never hold a boot service indefinitely.
n=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$n" -lt 90 ]; do
  sleep 2
  n=$((n + 1))
done
if [ "$(getprop sys.boot_completed)" != "1" ]; then
  echo "status=safe_exit_boot_not_completed" > "$BASE/status"
  exit 0
fi

DEVICE="$(getprop ro.product.device)"
if [ "$DEVICE" != "rodin" ]; then
  echo "status=unsupported_device device=$DEVICE" > "$BASE/status"
  exit 0
fi

# Default OFF after upgrade/install. Enable manually only after confirming
# device stability: touch /data/adb/pulsext-touch/enable_daemon
if [ ! -f "$BASE/enable_daemon" ]; then
  echo "status=safe_mode daemon=disabled reason=opt_in_required" > "$BASE/status"
  exit 0
fi

if [ ! -x "$DAEMON" ]; then
  echo "status=safe_exit reason=native_backend_missing" > "$BASE/status"
  exit 0
fi

# Start once only. No watchdog, no polling loop, no periodic shell probes.
if pidof rodin_daemon >/dev/null 2>&1; then
  echo "status=already_running" > "$BASE/status"
  exit 0
fi
if [ -s "$LOG" ]; then
  log_size=$(wc -c < "$LOG" 2>/dev/null)
  if [ "${log_size:-0}" -gt 262144 ]; then mv "$LOG" "$LOG.old"; fi
fi
RODIN_STATE_DIR="$STATE" "$DAEMON" >> "$LOG" 2>&1 &
echo "status=started pid=$!" > "$BASE/status"
exit 0
