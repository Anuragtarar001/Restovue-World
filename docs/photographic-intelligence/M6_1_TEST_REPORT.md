# Milestone 6.1 test report

## New coverage

- Exact private sunrise pair baseline and final production-path test on SM-S926B.
- Correct model-confirmed face location and score for the mandatory image.
- Conservative recovery with reliable person mask when face localization is unavailable.
- Intentional deep-silhouette protection.
- Already-correct person protection.
- Whole-subject gamma without a fabricated face mask.
- Darker-face priority and clipping protection.
- Small important-subject correction survives global perceptual averaging.
- Exact preview and full-resolution export timing.

Private inputs are injected into app-private device storage and are excluded from Git. Standard connected runs skip the two private calibration tests when those files are absent.

## Final gates

- `gradlew clean testDebugUnitTest assembleDebug lintDebug`: PASS in 3m09s.
- JVM: 186 tests, 0 failures/errors/skips.
- Android lint: 0 errors, 33 warnings, 2 hints.
- `gradlew connectedDebugAndroidTest`: PASS on Samsung SM-S926B in 9m28s; 23 tests, 0 failures/errors/skips.
- Exact sunrise calibration test: PASS with private inputs injected.
- Exact bounded-preview/export timing test: PASS.
- Debug APK: 48,819,785 bytes; SHA-256 `1EFF40E2376F124A2F764B6FC69EDAC201C8F57367A7A3DC076A8B7B7D2DF2BF`.

