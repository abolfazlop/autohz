# Hz Switcher

A root-enabled Android refresh-rate controller with an English HyperOS-inspired interface.

## Target device
- POCO X7 Pro
- HyperOS 3
- Physical display modes are detected at runtime; the app does not assume that every firmware exposes 60/90/120 Hz.

## Features
- Root authorization check
- Live refresh-rate display
- 60 / 90 / 120 Hz buttons enabled only when Android reports that physical mode
- HyperOS refresh policy writes using the Secure `miui_refresh_rate` key, plus Android min/max refresh settings
- Preferred display mode request using the detected physical resolution
- Post-apply display diagnostics (does not claim success from a settings write alone)
- Backup and restore of the settings changed by the app

## Important
HyperOS 3 behavior can vary by region and firmware build. A successful settings command is not proof that the panel is currently running at the requested rate. The app checks display diagnostics, but must still be tested on the target phone. Some apps may render below the panel refresh rate.

Root permission is required. Grant it through Magisk, KernelSU, or another compatible superuser manager.

## Build
GitHub Actions builds a debug APK on each push. Open **Actions**, select the latest **Build Android APK** run, and download the `Hz-Switcher-debug-apk` artifact.
