# Milestone 2 test report

Date: 2026-11-07

## Automated verification

Final gate command:

`./gradlew.bat clean testDebugUnitTest assembleDebug lintDebug`

Result: **PASS**. The final run contains 140 JVM unit tests in 29 suites, with 0 failures, 0 errors, and 0 skipped. This includes the pre-existing Smart Magic regression suite. Android lint reports 0 issues. Debug assembly succeeds.

Connected command:

`./gradlew.bat connectedDebugAndroidTest`

Result: **PASS** on physical Samsung SM-S926B / Android 17: 9 tests, 0 failures/errors/skips. The offline emulator entry was correctly skipped. These are existing application integration/export/UI regressions; the new Milestone 2 engine remains headless and has no device UI integration.

## Milestone 2 coverage

The five new suites cover:

- sRGB-to-linear luminance, alpha weighting, histogram/percentile order, bounded sampling, normalized grids, tiny/unusual aspect ratios, saturated/grayscale inputs, and cancellation-safe failure for invisible images.
- Twenty deterministic synthetic distributions: balanced, under/overexposed, backlit arrangements, center/background inversions, low/high key, silhouette-like, warm/bright assumptions, point sources, broad windows, haze-like low contrast, noise, saturation, greyscale, empty endpoints, and unusual shape. The semantic names are fixture assumptions only.
- Point-source versus broad-bright-surface connected component behavior and explicit unknown semantic subject state.
- Knowledge validity, provenance protection, invalid references/ranges/units, false validation, unsupported semantics, and dependency cycles.
- Determinism, bounded values, exposure monotonicity, highlight-headroom constraints, noise-limited recovery, protection vetoes, missing-mask deferral, no-op, level independence, perturbation stability, diagnostics privacy, and correction-plan round trip.

## Measured JVM benchmark

The final clean JVM run uses the Android Studio JBR on the development PC, not the Samsung and not a release benchmark. Times include a cold/warm mix and therefore are descriptive only:

| Source raster | Samples analyzed | Analysis time |
|---:|---:|---:|
| 307,200 px | 76,800 | 90.268 ms |
| 12,000,000 px | 245,388 | 234.406 ms |
| 48,000,000 px | 245,388 | 202.345 ms |

Typed knowledge creation plus validation measured 0.784 ms and correction planning measured 1.325 ms in the same run. Source `IntArray` allocation is outside the timed analysis call. The 48 MP result being faster than 12 MP reflects JVM warm-up/cache effects, not a performance guarantee. Peak managed/native memory and allocations were not measured with a profiler, so no memory number is claimed. In production, the analysis contract expects an already bounded raster; the large-source tests prove iteration/sample bounds, not decoder memory behavior.

## Quality scope

Synthetic tests establish mathematical behavior and safety, not photographic superiority. No real-photo calibration set was supplied, no visual preference study occurred, and all adaptive formulas remain uncalibrated engineering hypotheses. No renderer output changed in this milestone.
