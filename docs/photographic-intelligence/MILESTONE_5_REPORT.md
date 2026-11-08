# Milestone 5 Report

## Executive summary

Milestone 5 is **PARTIAL**. Advanced measured evidence, intent protection, selective regional
dehaze, dimension-scaled local contrast, noise-aware detail, bounded highlight planning, and richer
perceptual validation are integrated into the real production coordinator. The Samsung validation
shows useful progressive backlit/group correction and a restrained background-only improvement on
the cream portrait with no visible subject alteration. Production export parity remains covered.

Completion is partial because 48 MP streaming export is not implemented, the real-camera dataset is
only one licensed image, memory/thermal profiling is incomplete, and dehaze did not activate on a
real hazy-landscape photograph due missing data.

## Implementation

Main source additions/changes:

* `AdvancedPhotometricAnalysis.kt`: bounded atmosphere, glare, shadow/noise, detail, and intent evidence.
* `RegionalPlanBuilder.kt`: independently bounded dehaze/local-contrast/detail/highlight plans.
* `RegionalCorrectionRenderer.kt`: soft-mask selective dehaze, scaled local contrast, guarded detail.
* `SmartMagicProcessingCoordinator.kt`: advanced evidence in immutable sessions and global calibration.
* `ProductionSmartMagicModels.kt` / `PerceptualImpactEstimator.kt`: local contrast, saturation anomaly,
  and halo-risk measurements.
* `TiledExportPlanner.kt`: tested 48 MP tile/overlap geometry only.
* New JVM tests and expanded production device diagnostics.

No Internet permission, network SDK, cloud processing, analytics, or new dependency was added by
Milestone 5. Existing bundled TensorFlow Lite semantic inference remains offline.

## Visual and calibration findings

The cream portrait originally remained unchanged because the intent veto was overly broad and the
local kernel was scale-inappropriate. The final implementation narrows intent diagnoses and uses a
dimension-scaled neighborhood. Natural remains negligible; Enhanced/Extreme are applied to the
background. Backlit and group modes show monotonic, bounded increases. Saturation anomaly fraction
was zero in all retained outputs. Halo-risk stayed very low; the largest sampled value was 0.003246
for Extreme backlit correction. The public-domain portrait was a legitimate semantic no-op.

## Acceptance matrix

| Requirement | Status | Evidence / limitation |
|---|---|---|
| Production integration | PASS | coordinator sessions/plans and production instrumentation |
| Selective dehaze with intent guards | PARTIAL | implemented/tested synthetically; no real hazy/fog pair |
| Backlit recovery | PASS | three applied device modes and contact sheet |
| Deep-shadow color control | PASS | zero sampled saturation anomalies; validator retained |
| Highlight does not invent detail | PASS | bounded compression only; clipping guard |
| Light/reflection protection | PARTIAL | evidence/guards; limited real coverage |
| Local contrast halo safety | PASS | device metrics and visual review |
| Noise-aware detail | PARTIAL | deterministic gating; no broad noisy real set |
| Mask alignment | PASS | existing transform/mask suite plus device overlays |
| Independent modes | PASS | distinct plans and measured output where justified |
| Legitimate no-op | PASS | public-domain portrait and cream Natural |
| Real-photo framework | PASS | production harness/artifact schema |
| Required real-photo breadth | PARTIAL | 1 real + 3 generated fixtures |
| Preview/export decisions shared | PASS | immutable session and MediaStore parity test |
| 12 MP export | PASS | inherited and regression-tested production path |
| 48 MP export | BLOCKED | tile geometry only; no streaming encoder/full verification |
| Stage performance | PARTIAL | session/render/export timings; no peak-memory profiler |
| Offline/no network | PASS | manifest/code audit and bundled inference |

## Limitations and next recommendation

Do not claim full Milestone 5 completion. Before Milestone 6, obtain an approved real-camera set for
fog, haze, sunset, night lights, noisy shadows, snow/beach, and no-person scenes; profile the scaled
neighborhood operator; and implement a genuine region decoder plus streaming encoder for 48 MP.
Readiness for Milestone 6: **CONDITIONAL GO**, only if those unresolved items are accepted as explicit
carry-forward work. No Milestone 6 work was started.

## Final verification and artifact

* Clean JVM suite: 174 passed, 0 failed, 0 skipped.
* Connected Samsung suite: 18 passed, 0 failed, 0 skipped (8m05s).
* Lint: 0 errors, 33 warnings, 1 hint.
* Targeted production/MediaStore validation: 2 passed in 172.656s.
* Device measurements: bounded preview 2,953ms; 12 MP production export 26,503ms; separate 12 MP
  reopenable gallery export 39,626ms.
* Debug APK: `app/build/outputs/apk/debug/app-debug.apk` — 48,754,249 bytes.
* SHA-256: `47BA54940F2E174C23695ED4568D84569B48F3B171BB9AC6A13B7121CB7014B5`.
* Contact sheet: `research/photographic-intelligence/milestone5-validation/M5_VISUAL_CONTACT_SHEET.png`.

