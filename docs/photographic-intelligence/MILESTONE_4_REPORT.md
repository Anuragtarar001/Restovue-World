# Milestone 4 Report

## Executive summary

Milestone 4 implements and device-tests a real plan-driven regional renderer with soft person masks,
linear-light compositing, face/group safeguards, explicit rejection, quality fallback and diagnostic
outputs. The renderer is functional as a standalone domain component. Milestone status is **PARTIAL**
because it is not yet wired atomically into production Smart Magic preview/export, real-camera and
12/48 MP coverage are incomplete, and inherited fresh-install airplane-mode verification remains
open.

## Implemented

- Typed regional plan/model and evidence-based plan builder.
- Edge-aware confidence-mask refinement and normalized sampling.
- Subject-only and background-only exposure/tone/color processing.
- Linear-light soft compositing with alpha preservation.
- Conservative face and combined-group protections.
- Source/mask/transformation stale-result rejection.
- Clipping, protected-interior, mask leakage, boundary, shadow chroma and noise validation.
- One bounded 60% retry, then exact-original fallback.
- Real TFLite device diagnostics for all supplied images.

## Acceptance status

PASS: app builds; regression-focused tests; real masks; subject/background paths; soft masks; face
constraints; conservative groups; invalid-mask rejection; bounded parameters; protection veto;
Extreme safety; diagnostic separation; no-op; three supplied fixtures; boundary outputs; no broad
model/dependency; existing UI unchanged.

PARTIAL: preview/export parity (shared mathematics proven, production wiring absent); full transform
matrix (unit coordinate coverage exists, regional end-to-end crop/perspective export not run); device
performance (SM-S926B fixture measurements only); real-image quality calibration (generated fixtures,
not a broad real-camera suite); inherited offline fresh-install test.

NOT TESTED: 12 MP and 48 MP regional export, lower-tier device, thermal/battery, private real-camera
portrait suite, user rotation/crop/perspective after semantic inference.

## Milestone 4 source changes

- `RegionalModels.kt`: immutable regional plan/request/result contracts.
- `RegionalPlanBuilder.kt`: evidence-to-operation planning for three levels.
- `EdgeAwareMaskRefiner.kt`: bounded joint-bilateral refinement and mask sampling.
- `RegionalCorrectionRenderer.kt`: operators, compositing, safeguards and validation.
- `SemanticReasoning.kt`: marks supported M4 regional decisions executable instead of deferred.
- `RegionalCorrectionRendererTest.kt`: 13 focused JVM regression tests.
- `Milestone4RegionalDiagnosticsTest.kt`: real-model Android render/diagnostic test.
- `app/src/androidTest/assets/milestone4/`: the three owner-supplied generated fixtures.
- `docs/photographic-intelligence/19_*` through `26_*` and this report.
- `research/photographic-intelligence/milestone4-diagnostics/device-sm-s926b/`: 39 device outputs.

Milestone 3 files were already uncommitted on entry and were preserved; they are not claimed as new
M4 work.

## Verification

- 161/161 JVM unit tests passed.
- 14/14 connected tests passed on Samsung SM-S926B.
- Clean debug assembly passed.
- Android lint passed with zero errors (33 warnings, one informational issue).
- Three supplied fixtures produced 39 diagnostic files and were visually inspected.

## Dependencies and APK

No M4 dependency or model was added. Existing bundled TensorFlow Lite assets are reused. Current
debug APK is `app/build/outputs/apk/debug/app-debug.apk`, 48,639,561 bytes, SHA-256
`720AF3EB876254BDBCE5EB862B573113416E92D5A97E958DA8CA9801FB3DEE93`.

## Milestone 5 handoff

Before new corrections, connect an immutable semantic session to both production preview and export,
regenerate/transform masks after geometry changes, calibrate on licensed real photographs, profile
12/48 MP rendering, and complete fresh-install airplane-mode verification. Do not add glare removal
or broad segmentation until these gates pass.

