# PulseXt Touch Engine (KernelSU)

Version 0.2.0 adds a device-scoped adapter dispatcher and a root-owned control interface.

## Install
1. Download the `PulseXt-KernelSU-module` artifact from the **Build PulseXt KernelSU module** workflow.
2. Install the ZIP in KernelSU Manager and reboot.
3. Run:
   ```sh
   su -c /data/adb/modules/pulsext_touch_engine/pulsext-touchctl probe
   ```

## Control interface
- `pulsext-touchctl probe`: identifies the device and lists likely input devices.
- `pulsext-touchctl apply 240|480|1000`: dispatches only to an executable adapter named for the exact `ro.product.device` value. The adapter must verify hardware readback and print exactly `verified=true`.
- `pulsext-touchctl restore`: asks that same adapter to restore its own saved state and verifies the result.
- `pulsext-touchctl apply 0` is not a substitute for restore; the Android app uses the restore command for Default.

## Adapter contract
Adapters belong in `adapters/<ro.product.device>.sh`, must be executable, and implement:
- `probe`: report supported rates and hardware identity without changing state.
- `apply <rate>`: save original state once, apply only documented values, read back the live value, and emit `verified=true` only on a match.
- `restore`: restore the exact saved state and verify readback.

The dispatcher intentionally ships with **no generic vendor adapter**. Touch sampling controls are not standardized across Android kernels; a guessed sysfs write can disable or destabilize touch input. The module therefore reports `unsupported_backend` until a device-specific adapter has been implemented and validated. It does not claim that 240/480/1000 Hz is active merely because a request was made.

## 1000 Hz
A genuine 1000 Hz mode may require a vendor touch-feature service and/or a privileged evdev/uinput resampling engine. This dispatcher does not synthesize input events. A native resampler must be developed and tested against the target kernel's event protocol before an adapter can advertise 1000 Hz.

## Device support
The module is architecture-neutral shell control code. Actual hardware support is provided by separately validated per-device adapters; unknown devices fail closed.
