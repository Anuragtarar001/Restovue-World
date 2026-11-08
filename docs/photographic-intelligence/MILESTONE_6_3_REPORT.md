# Restovue Photographic Intelligence Engine — Milestone 6.3 report

## Executive summary

Milestone 6.3 is **PARTIAL**. The central architectural defect is fixed: production Smart Magic can now combine measured global corrections with residual person/background corrections. Independent brightness is implemented, diagnostics match combined rendering, and all three supplied originals were processed through the real device production path. Sunrise and wedding results show meaningful subject recovery. The indoor portrait remains too conservative and its Extreme regional candidate is rejected after an expensive quality search.

## Source changes

Principal implementation files:

- `SmartMagicProcessingCoordinator.kt`: unified global/regional orchestration, residual planning, semantic protection, combined export.
- `AdaptivePhotographicOptimizer.kt`: measured brightness, contrast, whites, and blacks candidate generation.
- `SmartMagicPipeline.kt`: endpoint-protected perceptual brightness operator.
- `RegionalPlanBuilder.kt`: face-aware group and protected-subject midtone opportunities.
- `AdjustmentDiagnosticBuilder.kt`: actual combined strategy and residual values.
- `ProductionSmartMagicModels.kt`: schema 2 and `COMBINED` strategy.
- `SmartMagicModels.kt`: bounded brightness parameter.
- `SmartMagicScreen.kt`: zoom badge moved away from the AFTER label.
- JVM and Android tests for operator math, combined rendering, diagnostics, and supplied photographs.

## Acceptance status

| Requirement | Status | Evidence |
|---|---|---|
| Combine global and regional corrections | PASS | `COMBINED` production renders on sunrise, papa Natural/Enhanced, and wedding |
| Exposure evaluated and rendered where relevant | PASS | global candidate and diagnostic pipeline |
| Independent brightness | PASS | distinct endpoint-protected operator and unit tests |
| Contrast, highlights, shadows, whites, blacks | PASS | production global pipeline and diagnostics |
| Gamma/midtone curves | PASS | global and regional operations; face-aware weighting |
| Image-dependent candidate selection | PASS | measured optimizer evidence and real-photo varying plans |
| Artifact and intent safeguards | PASS | clipping/chroma/halo checks; indoor Extreme rejection |
| Individual face illumination evaluation | PARTIAL | detected faces evaluated; detector found only 2/4 wedding faces |
| Indoor portrait visibly improved | PARTIAL | tonal/background change, but face visibility remains inadequate |
| Wedding regional analysis | PARTIAL | detected-face recovery works; incomplete face detection |
| Sunrise atmosphere preserved | PASS | contact sheet and regional measurements |
| Mode calibration | PASS | independent plans and measured differences/rejections |
| Diagnostics match renderer | PASS | 29 fixed categories with actual combined/residual values |
| Comparison UI remains functional | PARTIAL | code/tests preserved; no fresh unlocked physical interaction test |
| Export performance improved | PARTIAL | ~9–11 s on two cases; indoor Extreme 48.21 s |
| Existing features preserved | PASS | build/unit regression gate and focused device tests |
| Mid-range validation | NOT TESTED | no representative physical device available |
| 48 MP export | NOT TESTED | outside available fixture/device run |

## Visual and machine-readable evidence

Git-ignored artifacts are stored under `research/photographic-intelligence/milestone6-3/`. Each case contains original/previous/Natural/Enhanced/Extreme images, aligned difference maps, a contact sheet, and `validation.json` with complete adjustment reports, per-face measurements, strategies, outcomes, and timings.

Face-aligned mode contact sheets are also present for every detected face. The source photographs and all derived validation pixels remain Git-ignored.

## Build and test result

- `gradlew clean testDebugUnitTest assembleDebug lintDebug`: PASS in 3m27s.
- JVM tests: 194 passed, 0 failed, 0 skipped across 41 suites.
- `gradlew assembleDebugAndroidTest`: PASS.
- Focused S24+ real-photo instrumentation: 3 independent cases passed before the final clean build.
- Final clean APK install: BLOCKED because the phone disconnected before installation.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 48,950,857 bytes, SHA-256 `D6A33D9E150FFB9183097C2C48935163CD1AA7CE539F861CF494DD2423063AAA`.

## Remaining work

1. Diagnose and reduce the indoor portrait's shadow-chroma rejection without weakening the guard.
2. Improve face detection coverage or add a licensed, measured instance-aware strategy before claiming four-person correction.
3. Fuse or cache expensive full-frame detail/noise passes and avoid rerendering rejected regional candidates.
4. Run unlocked physical comparison UI, representative mid-range, memory profiler, and 48 MP export validation.

Milestone 7 should not begin on the assumption that indoor portrait calibration or mid-range performance is complete.

