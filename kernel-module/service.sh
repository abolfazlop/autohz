#!/system/bin/sh
MODDIR=${0%/*}
BASE=/data/adb/pulsext-touch
mkdir -p "$BASE"
chmod 0700 "$BASE"
# Keep a small root-owned diagnostic snapshot; do not write vendor touch nodes.
while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
while true; do
  "$MODDIR/pulsext-touchctl" probe > "$BASE/status.tmp" 2>/dev/null
  chmod 0600 "$BASE/status.tmp"
  mv "$BASE/status.tmp" "$BASE/status"
  sleep 30
done
