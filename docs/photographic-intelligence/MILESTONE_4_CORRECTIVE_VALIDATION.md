# Milestone 4 Corrective Validation

Date: 2026-11-08  
Device: Samsung SM-S926B (physical device; Gradle label `SM-S926B - 17`)  
Scope: corrective validation only; Milestone 5 was not started.

## Result

Milestone 4 remains **PARTIAL**.

The regional renderer itself is real: controlled subject-only and background-only exposure tests changed the intended pixels, preserved protected interiors, produced soft boundary transitions, and the lossless exported PNGs decoded to exactly the corrected renderer buffers. The genuine automatic planner also produced non-identical output for eligible levels.

The production Smart Magic workflow is **not yet regionally integrated**. `SmartMagicViewModel` and `SmartMagicExportManager` still use the existing global `SmartMagicPipeline`; they do not consume `RegionalCorrectionRenderer` output. Therefore this validation proves the renderer and diagnostic export path, but it does not prove that the normal user-facing preview or gallery export uses regional corrections.

The files named `M4_01_subject_protection.png`, `M4_02_backlit_fine_hair.png`, and `M4_03_group_portrait.png` are original test fixtures. They are not corrected outputs. Actual rendered outputs are under:

`research/photographic-intelligence/milestone4-corrective-validation/validated-device-rerun/`

## Root-cause findings

1. The three `M4_*.png` files are inputs. Comparing them to their source copies correctly produces zero changed pixels.
2. The automatic planner did not always request a visible correction:
   - Image 1 Natural produced a valid but sub-quantization background-vibrance plan. It is now reported as `NO_OP`, not `APPLIED`.
   - Images 2 and 3 Enhanced and Extreme were rejected after two bounded attempts because of `SHADOW_CHROMA_AMPLIFICATION`; the original was intentionally preserved.
3. Where automatic corrections passed validation, the renderer generated changed buffers. Image 1 Enhanced/Extreme and images 2/3 Natural have nonzero RGB differences.
4. The validation exporter did not replace corrected results with the original. Each controlled PNG was reopened and its decoded ARGB array exactly matched the corrected buffer.
5. Production Smart Magic currently bypasses the regional renderer. This is a genuine integration gap, not an exporter byte-copy defect in the validation path.

## Automatic execution results

All three masks came from bundled on-device person segmentation. Face counts came from bundled face detection. Planner dispositions and renderer statuses below are copied from the machine-readable traces.

| Fixture | Level | Planned operation | Renderer result | Changed pixels | Mean abs RGB | Explanation |
|---|---|---|---|---:|---:|---|
| M4_01 subject protection | Natural | Background vibrance 0.01798 | `NO_OP` | 0 | 0.00000 | Effect vanished after 8-bit quantization |
| M4_01 subject protection | Enhanced | Background vibrance 0.07693 | `APPLIED` | 70,573 (4.4879%) | 0.01499 | Person interior unchanged |
| M4_01 subject protection | Extreme | Background vibrance 0.10000 | `APPLIED` | 203,785 (12.9591%) | 0.04371 | Person interior unchanged |
| M4_02 fine hair | Natural | Person exposure +0.28 EV; shadows 0.06495 | `REDUCED` to 0.60 | 694,176 (44.1345%) | 4.32691 | Passed after bounded reduction |
| M4_02 fine hair | Enhanced | Person exposure +0.50 EV; shadows 0.11135 | `SKIPPED_PROTECTION` | 0 | 0.00000 | `SHADOW_CHROMA_AMPLIFICATION` |
| M4_02 fine hair | Extreme | Person exposure +0.72 EV; shadows 0.15775 | `SKIPPED_PROTECTION` | 0 | 0.00000 | `SHADOW_CHROMA_AMPLIFICATION` |
| M4_03 group | Natural | Person exposure +0.11009 EV; shadows 0.04943 | `REDUCED` to 0.60 | 1,007,199 (64.0360%) | 2.22886 | Conservative combined-person correction |
| M4_03 group | Enhanced | Person exposure +0.18872 EV; shadows 0.08474 | `SKIPPED_PROTECTION` | 0 | 0.00000 | `SHADOW_CHROMA_AMPLIFICATION` |
| M4_03 group | Extreme | Person exposure +0.26735 EV; shadows 0.12005 | `SKIPPED_PROTECTION` | 0 | 0.00000 | `SHADOW_CHROMA_AMPLIFICATION` |

Image-level diagnosis:

- M4_01: weak chroma, high noise risk, uneven illumination, already-balanced exposure; decision `PROTECT_CORRECTLY_EXPOSED_SUBJECT`.
- M4_02: excessive highlight occupancy, weak chroma, uneven illumination, strong backlight pattern, already-balanced global exposure; decision `REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE`.
- M4_03: global underexposure, high noise risk, uneven illumination, already-balanced global exposure; decision `REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE` with three detected faces and a combined person mask.

## Controlled engineering tests

Forced corrections are isolated test operations, not production presets:

- Subject-only exposure: +0.12 EV.
- Background-only exposure: +0.08 EV.
- Protected-interior tolerance asserted by the device test: no more than 1.25 mean 8-bit channel levels; measured values were effectively zero.
- Each exported controlled PNG was decoded and compared to the in-memory corrected buffer with exact integer-pixel equality.

| Fixture | Control | Changed pixels | Target interior mean diff | Protected interior mean diff | Boundary-band mean diff | Export equals corrected buffer |
|---|---|---:|---:|---:|---:|---|
| M4_01 | Subject | 672,753 (42.7816%) | 5.8091 | 0.000000 | 2.1130 | Yes |
| M4_01 | Background | 942,111 (59.9106%) | 4.8471 | 0.000000 | 1.4605 | Yes |
| M4_02 | Subject | 687,243 (43.6937%) | 2.8132 | <0.000001 | 1.8264 | Yes |
| M4_02 | Background | 924,768 (58.7952%) | 4.7789 | 0.000008 | 1.1388 | Yes |
| M4_03 | Subject | 1,007,210 (64.0367%) | 2.5642 | 0.000002 | 1.1256 | Yes |
| M4_03 | Background | 574,636 (36.5344%) | 2.5197 | 0.000000 | 0.9112 | Yes |

These results demonstrate that intended regions changed and protected interiors did not. Changes inside the soft transition band are expected and measured separately.

## Mask statistics

| Fixture | Faces | Mask accepted | Weighted area | Mean foreground score | Boundary uncertainty |
|---|---:|---|---:|---:|---:|
| M4_01 | 1 | Yes | 0.410762 | 0.990624 | 0.017853 |
| M4_02 | 1 | Yes | 0.417513 | 0.984776 | 0.027710 |
| M4_03 | 3 | Yes | 0.630638 | 0.988097 | 0.030991 |

## Boundary inspection

The controlled difference maps show gradual transitions rather than binary cut-outs. Visual inspection at original resolution found no hard bright/dark shoulder outline in the three controlled outputs. The fine-hair case retains a broad soft transition through uncertain individual strands; the difference map exposes small color-dependent changes in this uncertainty band. This is not evidence of reconstructed hair detail, and broader real-camera validation is still required before calling the refinement calibrated.

Relevant files per fixture:

- `controlled_subject_boundary.png`
- `controlled_background_boundary.png`
- `controlled_subject_difference.png`
- `controlled_background_difference.png`

## Preview and export

- Renderer determinism: JVM tests call the same regional request through preview/export-equivalent invocations and assert exact pixel equality.
- Validation export: PASS. Corrected buffers are losslessly written, reopened, and exactly compared.
- Production regional preview: FAIL / not wired.
- Production regional gallery export: FAIL / not wired.

Consequently, the mandatory production preview/export requirement is not satisfied. No claim of full Milestone 4 validation is made.

## Machine-readable traces and images

Each fixture directory contains:

- `execution_trace.json`
- `original.png`
- `automatic_natural_export.png`
- `automatic_enhanced_export.png`
- `automatic_extreme_export.png`
- `controlled_subject_export.png`
- `controlled_background_export.png`
- controlled difference and boundary images

Trace paths:

- `research/photographic-intelligence/milestone4-corrective-validation/validated-device-rerun/M4_01_subject_protection/execution_trace.json`
- `research/photographic-intelligence/milestone4-corrective-validation/validated-device-rerun/M4_02_backlit_fine_hair/execution_trace.json`
- `research/photographic-intelligence/milestone4-corrective-validation/validated-device-rerun/M4_03_group_portrait/execution_trace.json`

Each trace records diagnosis, semantic decision, mask statistics, planner disposition, operations and parameters, renderer invocation, applied strength, quality failures/vetoes, export-relative path, corrected-buffer state, and RGB difference metrics.

## Corrective code change

`RegionalCorrectionRenderer` now compares its validated output against the source after output quantization. A correction with zero changed pixels returns `NO_OP`, strength 0, and reason `REGIONAL_QUANTIZED_NO_OP`. This prevents a mathematically nonzero but visibly/pixel-identical plan from being mislabeled `APPLIED`.

## Device verification

Commands and outcomes:

- Focused JVM renderer tests and Android-test compilation: PASS.
- `connectedDebugAndroidTest` filtered to `Milestone4CorrectiveValidationTest`: PASS on SM-S926B.
- Manual retained-install instrumentation rerun: `OK (1 test)`, 169.715 seconds.
- Generated and pulled: 33 files, 59,771,526 bytes.
- Real bundled face/person inference was executed; no synthetic mask was substituted.
- Final `clean testDebugUnitTest assembleDebug lintDebug`: PASS in 2m 17s.
- JVM tests: 162 passed, 0 failed, 0 skipped.
- Android lint: 0 issues.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 48,639,561 bytes, SHA-256 `53E5E070FD7CD3220E3FFF6AA6EFECEF4791549FB8D37F377545F2B62DD09697`.

## Final status

| Requirement | Status |
|---|---|
| Regional renderer produces changed pixels | PASS |
| Subject-only isolation | PASS in controlled device test |
| Background-only isolation | PASS in controlled device test |
| Protected interiors remain within tolerance | PASS |
| Lossless diagnostic export contains corrected buffer | PASS |
| Genuine automatic planner/render evaluation | PASS, with explicit no-op/vetoes |
| Fine-hair/shoulder visual inspection | PARTIAL; no hard halo seen, broader real-photo calibration pending |
| Production Smart Magic regional preview | FAIL / not integrated |
| Production Smart Magic regional export | FAIL / not integrated |
| Milestone 4 fully validated | **NO — PARTIAL** |

No Milestone 5 work was started.
