# PulseXt device compatibility and root hardware layer

PulseXt is intended for multiple Android devices. Hardware support must be discovered at runtime; never infer frequency limits from a model name or hardcode SoC-specific sysfs paths.

## Root backends
The first backend uses the standard `su -c` interface, which can be provided by KernelSU, Magisk, or APatch. Root availability is checked by executing `id -u` and requiring UID 0.

## Hardware discovery
`RootHardwareController` discovers all CPU cpufreq policies under `/sys/devices/system/cpu/cpufreq/policy*`. It reads `cpuinfo_min_freq`, `cpuinfo_max_freq`, current scaling bounds, related CPUs, available governors and current governor. Frequency writes are rejected unless they fall inside the live hardware-reported range, and writes are read-back verified.

GPU discovery is intentionally conservative: it enumerates generic devfreq entries and selects candidates explicitly named for GPU/Mali/3D. It reads available governors and frequency bounds where exposed. A device without a writable and discoverable GPU interface is reported as unsupported rather than receiving guessed writes.

## Safety and compatibility rules
- Do not write guessed frequencies or vendor paths.
- Re-discover bounds immediately before each write.
- Never set minimum above maximum.
- Only use governors listed by the active policy/device.
- Verify a write by reading the value back.
- Unsupported controls must remain unavailable and explain why.
- Thermal protection and OEM limits remain authoritative; this layer does not disable them.
- App UI must distinguish “saved”, “command accepted”, and “hardware verified”.

## Current scope
This commit introduces the device-agnostic root discovery and validated CPU/GPU primitives. It does not yet connect every Game Profile control, add a foreground game-detection service, implement Quick Toggles, or provide a tested adaptive AI controller. Those require staged integration and device-level tests on representative Qualcomm and MediaTek devices. Do not describe the full feature set as complete until those tests pass.
