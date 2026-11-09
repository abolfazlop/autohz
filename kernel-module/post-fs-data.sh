#!/system/bin/sh
MODDIR=${0%/*}
BASE=/data/adb/pulsext-touch
mkdir -p "$BASE"
chmod 0700 "$BASE"
chmod 0755 "$MODDIR/service.sh" "$MODDIR/post-fs-data.sh" "$MODDIR/uninstall.sh" "$MODDIR/pulsext-touchctl" "$MODDIR/pulsext-hwprobe" 2>/dev/null
[ -x "$MODDIR/bin/rodin_daemon" ] && chmod 0755 "$MODDIR/bin/rodin_daemon"
[ -x "$MODDIR/bin/rodin_ctl" ] && chmod 0755 "$MODDIR/bin/rodin_ctl"
# Deliberately do not start any daemon from post-fs-data.
exit 0
