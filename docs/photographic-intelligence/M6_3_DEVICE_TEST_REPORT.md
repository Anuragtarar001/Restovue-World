# Milestone 6.3 device test report

Device: Samsung Galaxy S24+ / SM-S926B (`[redacted]`). Tests used the debug build and private photographs copied into the app's private test directory. The photographs were not bundled in the APK or committed.

Focused `Milestone63RealPhotoValidationTest` runs passed independently:

- `photoCase=papa`: `OK (1 test)`, 131.089 s.
- `photoCase=wedding`: `OK (1 test)`, 59.406 s.
- `photoCase=sunrise`: `OK (1 test)`, 53.199 s.

Each run exercised the production coordinator, real bundled face/person inference, mode plans, preview decision reuse, full-resolution `renderForExport`, diagnostic generation, and image encoding. Outputs were reopened by the test harness and compared with the source.

The final source also passed `assembleDebugAndroidTest`. A final reinstall of the clean-gate APK could not be performed because the phone disconnected; `adb devices -l` reported only an offline emulator and no `[redacted]`. Therefore the earlier focused runs are the physical execution evidence, while installation of the final byte-identical clean-build artifact is **BLOCKED** pending reconnection.

The device's secure lock screen can block touch-driven Compose UI automation. The Milestone 6.3 data/inference tests do not require unlocking and passed. A fresh physical interaction pass for the comparison slider was not repeated in this milestone; its existing automated coverage remains, so physical UI status is **NOT TESTED** here.

Offline constraints were preserved: no network permission, API, telemetry, or downloaded model was added.

Final debug APK identity: version code 9, version `0.9.0-rc1`, 48,950,857 bytes, SHA-256 `D6A33D9E150FFB9183097C2C48935163CD1AA7CE539F861CF494DD2423063AAA`.

