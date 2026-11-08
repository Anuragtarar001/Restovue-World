# Milestone 4 Test Report

## Automated coverage

`RegionalCorrectionRendererTest` contains 13 focused JVM tests covering subject/background
isolation, linear-light soft blending, alpha preservation, invalid/stale masks, protection vetoes,
face/group guards, no-op, cancellation, refinement bounds, deterministic preview/export calls,
Extreme bounds and saturated-shadow artifact protection.

Android instrumentation `Milestone4RegionalDiagnosticsTest` executes the actual bundled TFLite face
and person models on all three supplied fixtures, renders all levels and exports 39 diagnostic files.
The complete three-fixture run passed on Samsung SM-S926B. Targeted group reruns were used while
repairing visually detected chroma artifacts.

Synthetic JVM tests establish numerical behavior, not photographic quality. The supplied images
are generated visual fixtures without labeled masks. Real-camera calibration, 12 MP/48 MP export,
rotation/crop/perspective end-to-end regional export, rapid switching, and low-memory stress remain
NOT TESTED in this milestone.

Final verification on 2026-11-08:

- `gradlew clean testDebugUnitTest assembleDebug lintDebug`: PASS in 3m19s.
- Unit tests: 161 passed, 0 failed, 0 skipped across 32 suites.
- Lint: 0 errors, 33 warnings and 1 informational issue.
- `gradlew connectedDebugAndroidTest`: 14 passed, 0 failed, 0 skipped on SM-S926B.
- The connected count includes the three-fixture M4 diagnostic, four semantic-model tests,
  two existing Smart Magic tests, UI regressions and processor/compression tests.

