#!/system/bin/sh
MODDIR=${0%/*}
BASE=/data/adb/pulsext-touch
STATE="$BASE/rodin-state"
DAEMON="$MODDIR/bin/rodin_daemon"
LOG="$BASE/rodin-daemon.log"
umask 077
mkdir -p "$BASE" "$STATE"
chmod 0700 "$BASE" "$STATE"

while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done

# Rodin Essential's touch HAL and timing implementation is specific to Xiaomi
# Rodin (POCO X7 Pro / Redmi Turbo 4). Fail closed on every other device.
DEVICE="$(getprop ro.product.device)"
if [ "$DEVICE" != "rodin" ]; then
  echo "unsupported_device=$DEVICE; Rodin native touch backend not started" > "$BASE/status"
  exit 0
fi

if [ ! -x "$DAEMON" ] || [ ! -x "$MODDIR/bin/rodin_ctl" ]; then
  echo "error=native_backend_missing; reinstall module from latest Actions artifact" > "$BASE/status"
  exit 1
fi

while true; do
  if ! pidof rodin_daemon >/dev/null 2>&1; then
    echo "starting Rodin touch backend" >> "$LOG"
    RODIN_STATE_DIR="$STATE" "$DAEMON" >> "$LOG" 2>&1 &
    PID=$!
    echo "$PID" > "$BASE/daemon.pid"
  fi
  "$MODDIR/pulsext-touchctl" probe > "$BASE/status.tmp" 2>&1
  chmod 0600 "$BASE/status.tmp"
  mv "$BASE/status.tmp" "$BASE/status"
  sleep 5
done
