# Milestone 3 report

## Executive summary

Milestone 3 adds real bundled face detection and human foreground segmentation, compact soft masks, coordinate contracts, quality rejection, face/person association, mask-weighted regional photometry, semantic scene-graph integration, confidence-aware composition, and deferred correction planning. It does not alter Smart Magic pixels, editor UI, navigation, or export.

## Source changes

- `gradle/libs.versions.toml`, `app/build.gradle.kts`: pinned telemetry-free TensorFlow Lite 2.17.0.
- `app/src/main/assets/models/`: two verified local models and manifest.
- `domain/photointelligence/SemanticModels.kt`: Android-free semantic contracts.
- `ConfidenceMask.kt`: bounded compact soft-mask engine and quality metrics.
- `SemanticCoordinates.kt`: EXIF and letterbox transforms.
- `SemanticReasoning.kt`: association, regional measurements, graph/composition, diagnoses, deferred planning.
- `data/LiteRtSemanticAnalyzer.kt`: direct real Android inference adapter and BlazeFace decoder.
- `src/debug/.../SemanticDiagnosticRenderer.kt`: debug-only aligned overlay renderer.
- Unit/instrumentation tests and a public-domain test-only portrait.

## Genuine capabilities

The app can detect face boxes, six model keypoints and detector score; generate a combined soft human mask; associate faces to that mask; and calculate human/background exposure evidence. It cannot identify individuals or demographics, split people into instances, or detect scene classes beyond person/face/background complement.

## Masks and composition

Masks are normalized to the oriented source, quantized to 8-bit confidence, capped at one megapixel, and sampled bilinearly. Quality gates reject empty, full-frame, uncertain, or fragmented outputs. P1 requires multiple cues and remains unresolved otherwise. P2/P4 are not asserted without evidence.

## Planning integration

The existing Milestone 2 global plan is preserved. New semantic decisions can protect a correctly exposed person or silhouette and can propose subject-fill/background-only operations, but every regional operation is explicitly deferred to Milestone 4. No regional rendering was implemented.

## Acceptance status

| Requirement | Status | Evidence / limitation |
|---|---|---|
| Bundled face detection | PASS | Real on-device portrait test |
| Bundled person segmentation | PASS | Real soft mask on device |
| Soft mask, quality, rejection | PASS | Domain + device tests |
| Coordinates | PASS for EXIF/letterbox/current preview normalization | User perspective/crop application awaits M4 renderer |
| Face/person association | PASS for combined mask | No instance segmentation |
| Scene graph and regional photometry | PASS | Soft-mask weighted tests |
| Composition uncertainty | PASS | Person is not automatically P1 |
| Backlit/protection decision contracts | PASS (uncalibrated) | Synthetic/domain evidence only |
| Regional correction | DEFERRED | Correctly excluded from M3 |
| Debug overlay | PASS | Debug-only renderer; device file generated |
| Offline fresh install in airplane mode | PARTIAL | Bundled assets + no INTERNET; airplane-mode fresh install not performed |
| Full requested real-photo matrix | NOT TESTED | No licensed/user fixture set supplied |
| Lower-tier API 28 device | NOT TESTED | Emulator offline; only SM-S926B available |

Because the airplane-mode fresh-install procedure and broad real-photo matrix were not completed, Milestone 3 is technically **PARTIAL**, not falsely declared fully complete.

## Verification

- `gradlew clean testDebugUnitTest assembleDebug lintDebug bundleDebug`: PASS.
- JVM: 148 tests, 0 failures/errors, 31 suites.
- Lint: 0 errors, 33 warnings, 1 hint.
- `connectedDebugAndroidTest` on SM-S926B: 13 tests, 0 failures/errors; four new semantic tests.
- Final merged manifest: zero `INTERNET` and zero `ACCESS_NETWORK_STATE` permissions.
- Resolved runtime dependency audit: TensorFlow Lite 2.17.0; no DataTransport, Firebase, OkHttp, or Retrofit.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 48,590,409 bytes, SHA-256 `ae5ad54c5335a5209add5187c847913f5fcd123a32ceb3b30c96e5b7b4fb044f`.
- Debug AAB: `app/build/outputs/bundle/debug/app-debug.aab`, 18,728,759 bytes, SHA-256 `398f1faf4caa5da0a1c1022eee86426b325d3422ef1319f0c1aed4ef3877b69f`.
- Diagnostic overlay: `research/photographic-intelligence/milestone3-diagnostics/public-domain-portrait-overlay.png`; visually inspected for aligned face box and person mask.

## Milestone 4 handoff

Milestone 4 can consume mask IDs, normalized oriented-source masks, regional photometry, protection decisions, and deferred operations. It must add edge-aware full-resolution mask sampling, validate transforms through crop/rotation/perspective, calibrate regional exposure formulas on labeled real photos, and compare preview/export masks. Do not add a broad segmentation model without a separate license/class-map/benchmark gate.
