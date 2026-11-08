# Milestone 6.2 Test Report

## Required build gate

Command:

`gradlew.bat clean testDebugUnitTest assembleDebug lintDebug`

Latest Milestone 6.2.1 result: PASS in 2m55s.

- JVM tests: 193 passed, 0 failed, 0 skipped across 41 suites.
- Debug APK: built successfully.
- Lint: 0 errors, 33 warnings, 5 hints. Warnings are existing/deprecation guidance; no lint error was suppressed for this milestone.

## Connected device gate

Command: `gradlew.bat connectedDebugAndroidTest`

Device: Samsung SM-S926B. Result: PASS in 8m48s.

- Discovered: 23 tests.
- Passed: 21.
- Failed/errors: 0.
- Skipped: 2 private sunrise calibration cases because the full clean suite intentionally did not inject user photos into app-private storage.

The exact sunrise test was also run separately after the code changes with its private pair injected and passed. The full-suite skips are privacy-fixture handling, not failures.

## Milestone 6.2.1 connected addendum

The new exact diagnostics test passed on Samsung SM-S926B and generated complete Natural, Enhanced, and Extreme reports. The six-test Smart Magic UI class also passed in a focused unlocked-device run.

A later 26-test aggregate run was not clean: 19 passed, 1 private-fixture case skipped, and 6 Compose UI tests could not launch after the secure device auto-locked during the 9m37s run. Device inspection showed `mCurrentFocus=Bouncer`. Repeating the UI scope while still locked reproduced the infrastructure failure. This is recorded as an incomplete aggregate rerun, not as a passing suite and not as a photographic-engine regression.

## New/updated coverage

- 0%, 50%, and 100% comparison divider states on device.
- real Smart Magic Compose screen labels, buttons, zoom, and comparison entry.
- actual mode callback identity.
- truthful monitor content and cancellation UI.
- valid/invalid determinate progress math.
- coordinator stage ordering and monotonic candidate counts.
- rendering stage completion.
- actual regional adjustment unit summary.
- existing stale-generation tests.
- existing differential dark-face group correction test.
- production export selection and 12 MP reopening tests.

## Not executed

- Exact four-woman wedding source/exports: BLOCKED, files unavailable.
- Mid-range physical device performance: NOT TESTED, device unavailable.
- Full user-driven Photo Picker workflow automation: not automated because the system picker is outside the Compose test harness; coordinator, screen, export, and MediaStore components were each exercised on device.
