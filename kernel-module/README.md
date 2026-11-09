# PulseXt Touch Engine

PulseXt now builds the Rodin Essential v1.18.5 native touch backend and packages
its `rodin_daemon` and `rodin_ctl` binaries in the KernelSU module.

## Supported hardware

The imported Rodin touch implementation is **device-specific**. The module
starts it only when `ro.product.device=rodin` (POCO X7 Pro / Redmi Turbo 4).
Other devices are detected and rejected without writing touch nodes. This is
intentional: Xiaomi's ITouchFeature AIDL, Goodix/FocalTech HAL integration,
THP timing layout, and the input resampler are not generic Android interfaces.

## Install

1. Run the **Build PulseXt KernelSU module** GitHub Actions workflow.
2. Download the `PulseXt-KernelSU-module` artifact.
3. Install the ZIP with KernelSU Next and reboot.
4. Verify with:
   ```sh
   su -c /data/adb/modules/pulsext_touch_engine/pulsext-touchctl probe
   ```

## Hardware probe (CPU)

The module also installs `pulsext-hwprobe`, a read-only helper used by PulseXt
when enumerating CPUFreq policies. It reports each policy's related CPU list,
current governor, supported governors, hardware min/max and active min/max.
CPUFreq sysfs values are emitted unchanged in kHz; conversion to MHz is done
only in the app's display layer. The helper does not write frequency values.

Run diagnostics with:

```sh
su -c /data/adb/modules/pulsext_touch_engine/pulsext-hwprobe cpu
```

## Game Profile rates

- Default: calls `SET touch 0` and restores the OEM state captured by Rodin.
- 240 Hz: Rodin profile 1.
- 480 Hz: Rodin profile 2.
- 1000 Hz: Rodin profile 3; the daemon keeps the physical source at 480 Hz and
  starts the one-millisecond Android event resampler.

The control script verifies daemon readback, the active touch profile, and
`touch_resampler_ready=1000` before reporting 1000 Hz as ready. A requested
profile is not considered successful merely because a shell command exited 0.

## Diagnostics

- `pulsext-touchctl status`: daemon and live backend snapshot.
- `pulsext-touchctl apply 240|480|1000`: apply and verify a profile.
- `pulsext-touchctl restore`: release the custom profile and restore OEM state.

The native daemon stores its session state under
`/data/adb/pulsext-touch/rodin-state`. The module does not install Rodin's APK;
PulseXt remains the UI and invokes the native control client through Root.

## Porting to other devices

Do not remove the device guard until a separate adapter is implemented and
validated for that device's touch HAL/driver. In particular, do not reuse
Rodin's THP memory layout or Xiaomi AIDL commands on unrelated hardware.
