#!/system/bin/sh
# Stop only the module-owned native daemon when removing the module.
if [ -f /data/adb/pulsext-touch/daemon.pid ]; then
  pid="$(cat /data/adb/pulsext-touch/daemon.pid 2>/dev/null)"
  case "$pid" in ''|*[!0-9]*) ;; *) kill "$pid" 2>/dev/null ;; esac
fi
pkill -TERM -x rodin_daemon 2>/dev/null
rm -rf /data/adb/pulsext-touch
