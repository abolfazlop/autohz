# PulseXt Touch Engine (KernelSU)

This is the first installable KernelSU module scaffold. It provides a root-owned, read-only hardware probe and a stable `pulsext-touchctl` command interface.

## Install
1. Download the `PulseXt-KernelSU-module` ZIP from the GitHub Actions artifact.
2. Install it from KernelSU Manager > Modules and reboot.
3. Run `su -c /data/adb/modules/pulsext_touch_engine/pulsext-touchctl probe`.

## Safety status
This build intentionally does **not** claim to change touch sampling. It does not write guessed sysfs nodes, alter input event devices, or synthesize touch events. `apply 240`, `apply 480`, and `apply 1000` return `unsupported_backend` until a verified vendor adapter and native event-resampler are implemented and validated for the device. This prevents a false success report or a broken touchscreen.

The probe reports device identity and candidate touch-related sysfs paths only. Candidate paths are hints, not proof of a writable sampling-rate control.
