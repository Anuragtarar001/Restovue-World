# Milestone 6.2 Build Verification

- Git branch: `milestone-4-regional-correction`
- Baseline commit: `6556f5a523cb42711f985b1c3dda7680ac086a3d`
- Working tree: intentionally dirty with preserved Milestones 3-6.2 work; no reset, clean, commit, or push performed.
- Variant: `debug`
- Package: `com.restorex.app`
- Version: `0.9.0-rc1` (`versionCode 9`)
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK bytes: `48,950,857`
- SHA-256: `09BB618CA5AAE8283468AD14A9019A17655FBBD9C3116FE1EA800AD499A12DC7`

## Device verification

- Device: Samsung SM-S926B
- Serial used by ADB: `[redacted]`
- Android: 17, API 37
- Installation: `adb install -r` succeeded without removing app data. The final rebuilt artifact is installed at handoff.
- Installed package reports version code 9 and version name 0.9.0-rc1.
- App launch event was successfully injected while the device had no active network transport.

The repository contains no `INTERNET` permission and no network/cloud processing was introduced.
