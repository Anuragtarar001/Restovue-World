# Milestone 6 test report

Date: 2026-11-08

## Automated results

- JVM: 181 tests, 0 failures, 0 errors, 0 skipped.
- Targeted Samsung UI instrumentation: 4 tests passed.
- Samsung production validation: 2 tests passed (four fixtures plus selected-level MediaStore export).
- Samsung bounded preview timing: 1 test passed.
- MediaStore export decoded to original dimensions and matched the production render within JPEG tolerance.

New coverage includes generation gating, latest-mode-wins, comparison press/release, double-tap zoom semantics, optimizer determinism/budget, tone-curve monotonicity, Gamma +38 mapping, device budgets and darker-face priority.

## Final gates

- `gradlew clean testDebugUnitTest assembleDebug lintDebug`: **PASS** in 3m24s.
- Lint: 0 fatal, 0 errors, 33 warnings, 2 hints.
- `gradlew connectedDebugAndroidTest`: **PASS** on Samsung SM-S926B in 11m04s; 21 tests, 0 failures/errors/skips. The offline emulator was detected but skipped because it was offline.
- Debug APK: 48,819,785 bytes; SHA-256 `ABB20AA0A0C723DB27126F74E67FD9E9F301E88AA09E496B71DE74E76F0A2B3B`.

## Not fully tested

- Physical mid-range phone.
- Actual four-woman wedding image.
- Manual pinch/pan acceptance and process-death restoration.
- 48 MP export (retains safe block).

