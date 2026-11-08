#!/system/bin/sh
MODDIR=${0%/*}
mkdir -p /data/adb/pulsext-touch
chmod 0755 "$MODDIR/service.sh" "$MODDIR/pulsext-touchctl"
