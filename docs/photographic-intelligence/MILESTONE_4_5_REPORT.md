# Milestone 4.5 Report

Date: 2026-11-08  
Status: **PARTIAL**  
Device: Samsung SM-S926B (`[redacted]`)

## A. Executive summary

The critical production bypass is fixed. The real Smart Magic screen now creates a versioned semantic session, runs the regional engine for eligible people, exposes Natural/Enhanced/Extreme, caches mode previews, compares against the original, and saves the selected plan through MediaStore. Preview and export share the semantic evidence, mode plan, and accepted regional strength.

Global Smart Magic remains the fallback for images without reliable human evidence. A detected person without a safe regional plan is not exposed globally. Quantized and perceptually negligible results preserve the original with truthful UI messaging.

The milestone is PARTIAL because 48 MP full-resolution export is safely blocked by the device heap policy, a fresh-install airplane-mode test was not performed, and the broad real-camera validation matrix was unavailable.

## B. Production integration

Key additions/changes:

- `SmartMagicProcessingCoordinator.kt`: sole production analysis/planning/render coordinator.
- `ProductionSmartMagicModels.kt`: immutable semantic session and typed production results.
- `PerceptualImpactEstimator.kt`: bounded OKLab/luminance/chroma impact estimation.
- `SmartMagicViewModel.kt`: generation-safe session lifecycle, mode caching/switching, production save selection.
- `SmartMagicExportManager.kt`: source/session verification, selected preview plan reuse, full-resolution render, MediaStore finalization.
- `RegionalCorrectionRenderer.kt`: bounded strength search, perceptual outcomes, normalized shadow-color validation, deep-shadow chroma guard.
- `SmartMagicScreen.kt`: accessible Natural/Enhanced/Extreme controls; no technical masks or sliders.
- `RestoreXApplication.kt`: bundled semantic analyzer and coordinator wiring.

Pipeline:

`Photo Picker -> oriented decode -> bounded analysis -> photometric diagnosis -> bundled semantic inference -> regional/global planning -> production preview -> selected plan/strength -> full-resolution render -> MediaStore`

No diagnostic asset can enter ordinary Save Photo.

## C. Calibration results

Final production-path fixtures:

| Fixture | Level | Outcome | Changed sample | Mean OKLab delta | Render/validation |
|---|---|---|---:|---:|---:|
| Cream portrait | Natural | Quantized no-op | 0% | 0 | 10.47s |
| Cream portrait | Enhanced | Perceptually negligible | 4.495% | 0.000064 | 4.36s |
| Cream portrait | Extreme | Perceptually negligible | 13.006% | 0.000186 | 4.37s |
| Fine-hair backlit | Natural | Applied | 44.805% | 0.024778 | 5.70s |
| Fine-hair backlit | Enhanced | Applied | 45.073% | 0.039127 | 5.50s |
| Fine-hair backlit | Extreme | Applied | 45.185% | 0.051666 | 5.50s |
| Group portrait | Natural | Applied | 64.631% | 0.011565 | 6.77s |
| Group portrait | Enhanced | Applied | 65.137% | 0.018473 | 6.81s |
| Group portrait | Extreme | Applied | 65.380% | 0.024814 | 6.77s |

These timings are 1536x1024 validation renders with quality/impact sampling. Production preview is bounded to 720 pixels and is not represented by the full-size numbers.

The cream portrait is deliberately unchanged: available contrast/vibrance operators did not create a meaningful improvement and dehaze was out of scope. Fine-hair and group outputs show progressively stronger subject recovery. Face/headroom and group guards remain active.

An intermediate group output revealed blue/dark chroma speckles. Root cause analysis found that absolute RGB chroma incorrectly grew under valid exposure and a neighbor-color cleanup introduced artifacts. The final implementation validates normalized saturation and suppresses recovery smoothly on deep, highly chromatic outliers without borrowing neighboring colors.

## D. Quality search

The search is bounded to strengths 1.00, 0.70, 0.45, and 0.30 and stops at the strongest passing candidate. Export evaluates only the preview-accepted strength. Every candidate records quality failures, impact, and runtime in `execution_trace.json`.

The final fixtures pass at requested strength for all backlit/group modes. Cream outputs are classified quantized/negligible rather than falsely `APPLIED`. Protections remain authoritative in every mode.

## E. Preview/export verification

- `Milestone45ProductionValidationTest` passed 2/2 on the SM-S926B.
- MediaStore output was reopened at original dimensions and compared against a direct full-resolution render with lossy-JPEG tolerance.
- A changed preview was asserted not to become the original export.
- Export requires matching source identity and compatible geometry.
- Regional export locks to the preview-selected strength; failure at full resolution is surfaced instead of silently switching plans.
- 12 MP production export passed at 4000x3000 without downscaling.

## F. Device performance

- Session creation: 1.40-2.45 seconds for the three fixtures.
- Bundled inference: 46-48 ms warm; 313 ms first measured fixture.
- Final retained three-fixture diagnostic run: 80.677 seconds for three sessions, nine full-size renders, PNG diagnostics, close-ups, and contact sheet.
- Complete 12 MP synthetic production test: 30.889 seconds including source creation, preview/session, decode, render, encode, reopen.
- Normal preview longest side: 720 pixels; analysis: 1280 pixels.
- Final APK: 48,721,481 bytes.

Peak managed/native memory was not captured with a profiler. No OOM or ANR occurred in executed tests. A 48 MP source exceeds the safe heap budget and is rejected before allocation.

## G. Testing

Final gate:

`gradlew clean testDebugUnitTest assembleDebug lintDebug`

- Build: PASS, 2m36s.
- JVM: 168 passed, 0 failed, 0 skipped.
- Lint: 0 issues.
- Connected fixture/export suite: 2 passed.
- Retained production fixture run: 1 passed, 80.677s.
- 12 MP production export: 1 passed, 30.889s.
- Complete connected instrumentation regression: 18 passed, 0 failed, 0 skipped in 7m04s.
- Physical app install/launch: PASS using replacement install; user data was not cleared.
- Network state at launch command reported not connected; manifest/dependency audit found no network permission/client.

## H. Visual deliverables

Directory: `research/photographic-intelligence/milestone4-5-validation/`

Contains 33 files (45,997,237 bytes): original/Natural/Enhanced/Extreme, amplified differences, mask overlay, trace, quality report, boundary close-ups, and `M45_VISUAL_CONTACT_SHEET.png`.

Visual inspection found no hard fine-hair silhouette halo. Group output remains conservative. Very small source/compression chroma outliers can remain visible at extreme zoom; broader calibration is required.

## I. Acceptance matrix

| # | Requirement | Status | Evidence / limitation |
|---:|---|---|---|
| 1 | Production invokes regional engine | PASS | Coordinator wired through ViewModel and export |
| 2 | Global correction remains | PASS | No-person coordinator test/global pipeline |
| 3 | Preview/export share decisions | PASS | Immutable session and locked selected strength |
| 4 | Three modes use intended plans | PASS | UI, cache, traces |
| 5 | Saved image matches selected preview | PASS | MediaStore reopen/direct render tolerance |
| 6 | Diagnostics cannot export | PASS | Instrumentation-only generation |
| 7 | Stale sessions isolated | PASS | Generation/session/geometry checks and tests |
| 8 | Well-exposed subjects protected | PASS | Cream fixture no-op/negligible |
| 9 | Backlit correction | PASS | Three applied levels |
| 10 | Conservative group correction | PASS | Combined mask, face/group guard, close-up review |
| 11 | Shadow-chroma amplification prevented | PARTIAL | Metric/root cause fixed; broader real-camera calibration absent |
| 12 | Bounded quality search | PASS | Four fixed candidates maximum |
| 13 | No-op identified | PASS | Quantized no-op |
| 14 | Negligible corrections identified | PASS | OKLab impact estimator |
| 15 | No forced difference | PASS | Cream source preserved |
| 16 | 12 MP full-resolution export | PASS | 4000x3000 device test |
| 17 | 48 MP full-resolution export | BLOCKED | Safe heap preflight rejects; no tiled renderer |
| 18 | Geometry/orientation | PARTIAL | ImageDecoder orientation and stale-ratio test; full transform matrix not physically exercised |
| 19 | Preview latency measured | PARTIAL | Full-size stage timings and bounded preview; no UI-frame profiler capture |
| 20 | Export latency measured | PASS | 12 MP complete test 30.889s |
| 21 | Memory measured | PARTIAL | Heap policy/no OOM; no peak profiler capture |
| 22 | Offline operation | PARTIAL | Bundled assets/no permissions; no destructive fresh-install airplane test |
| 23 | Regression tests pass | PASS | 168 JVM, connected tests, lint |
| 24 | Real-camera coverage | NOT TESTED | Only generated fixtures/public test material available |
| 25 | Three fixtures use production path | PASS | Coordinator traces and outputs |
| 26 | Contact sheet | PASS | Generated and inspected |
| 27 | Existing app remains functional | PASS | Clean build/install/launch; targeted regressions |
| 28 | Simple user experience | PASS | Select, three modes, compare, save |

## J. Remaining limitations and Milestone 5 readiness

- No memory-safe tiled 48 MP renderer.
- No broad real-camera/human grading set.
- No fresh-install airplane-mode proof because existing app data was preserved.
- Process-death restoration, HEIC production input, low-tier device, thermal endurance, and arbitrary perspective/crop mask regeneration remain unverified.
- Preview rendering is background/cancellable but still takes seconds; it is not display-interactive.

Milestone 5 readiness: **CONDITIONAL GO** for planning and isolated development, but **NO-GO for declaring the photographic engine production-complete** until real-camera calibration and the 48 MP output policy are resolved.

No Milestone 5 implementation was started.

## Artifact

`app/build/outputs/apk/debug/app-debug.apk`  
Size: 48,721,481 bytes  
SHA-256: `FDF757AAA117F6EABFA1937491DFA0810EED6AE67E3D5642EB6BE3C9E68CD324`
