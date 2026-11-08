#!/system/bin/sh
ui_print "*******************************"
ui_print "     PulseXt Touch Engine     "
ui_print "*******************************"
MODDIR=${0%/*}
ui_print "Checking native touch backend..."
if [ ! -s "$MODDIR/bin/rodin_daemon" ] || [ ! -s "$MODDIR/bin/rodin_ctl" ]; then
  abort "! Rodin native binaries are missing. Install the complete PulseXt module ZIP."
fi
chmod 0755 "$MODDIR/service.sh" "$MODDIR/post-fs-data.sh" "$MODDIR/uninstall.sh" "$MODDIR/pulsext-touchctl"
chmod 0755 "$MODDIR/bin/rodin_daemon" "$MODDIR/bin/rodin_ctl"
if [ ! -x "$MODDIR/bin/rodin_ctl" ] || [ ! -x "$MODDIR/bin/rodin_daemon" ]; then
  abort "! Could not set executable permissions on native backend."
fi
ui_print "Native binaries verified."
