# Photographic Intelligence Engine 2.0 — Milestone 1 report

Date: 2026-11-07  
Restovue revision audited: `2425c7aa780e0487399627873081a19249fc7034`  
Scope: research, databases and architecture only. No Android application source/build configuration was modified.

## Executive summary

Restovue already has a credible deterministic offline foundation: bounded preview decoding, latest-state-wins rendering, non-destructive state/history, shared preview/export algorithms, linear-light Smart Magic exposure/tone analysis, OkLab color handling, conventional denoise/dehaze/detail operations and safe MediaStore/SAF export. Smart Magic is genuinely image-adaptive, but its scene understanding is heuristic and global: there is no face detector, subject model, semantic segmentation, compositional scene graph or regional automatic correction.

Milestone 1 acquired and parsed the real EmulsionPresets source, created a reproducible XMP parser, separated artistic looks from correction evidence, and produced versioned scene/composition/region/conflict/value databases. The architecture recommends adding bundled face + person evidence first, with broad semantic parsing gated on exact license, size and device benchmarks.

## Existing Restovue capabilities

Detailed evidence is in `01_CODEBASE_AUDIT.md`. Key reusable elements:

- Immutable `EditState`, Android-free engine/repository ports, parameter-only bounded history.
- 1.5 MP preview policy, 720 px interactive path, cancellation/generation checks and byte-bounded raster caches.
- Smart analysis of linear-light percentiles, clipping, color, neutral regions, edge/noise proxies and coarse multi-score scenes.
- Smart exposure in linear RGB, tone mapping on luminance, OkLab vibrance/gamut protection, dark-channel dehaze, luma/chroma NR and edge-aware detail.
- One plan reused for Smart preview and export; source is re-decoded and never overwritten.
- Normalized manual masks/brushes, sparse tiles and coordinate mapping suitable for future semantic operations.

Important deficiencies:

- Manual editor exposure/tone/color largely acts on gamma-encoded RGB; those controls are not Lightroom-equivalent.
- Smart scene labels rely on skin-color, upper-blue/lower-green and center/border heuristics. They do not identify objects or faces.
- Smart correction is global and produces one parameter set. No P1–P4 composition or automatic regional correction exists.
- Smart sharpening calculates a radius parameter but its renderer uses a fixed 3×3 fine-detail neighborhood.
- Spatial effects use packed 8-bit intermediates and can be resolution dependent.
- Full-resolution export is whole-image and heap-preflighted, not tiled.

Regression baseline: 111 unit tests passed (24 suites; zero failures/errors/skips). No device tests were run for this research milestone.

## Lightroom preset research

Source: `https://github.com/peva3/EmulsionPresets` at commit `0cdb71c60603867a046bc92c63617feec7894dd1` (2026-10-23), MIT license.

- 451 production XMP files under `Presets/`; 16 separate research-reference XMPs were recorded but excluded.
- Parsed successfully: 451. Failed: 0. Duplicate UUIDs: 0.
- All production files declare process version 15.4 and contain a composite tone-curve element; many curves are structural neutral baselines, not correction evidence.
- 121 unique explicit XMP attribute names.
- Classification based on category, name/description and actual parameter content: 20 corrective, 20 technical utility, 53 mixed corrective/artistic, 358 primarily artistic, 0 uncertain.
- 516 condition-mapping rows were produced because a preset can map to multiple cautiously inferred conditions.
- Observed explicit-value coverage/ranges (not automatic recommendations): exposure 299 presets, -1..+2 EV; contrast 413, -45..80; highlights 413, -100..30; shadows 413, -48..90; whites 402, -43..30; blacks 413, -50..40; vibrance 217, -45..64; saturation 266, -50..30; clarity 348, -40..55; texture 307, -25..25; dehaze 280, -22..45; sharpening 388, 10..60; luminance NR 368, 0..60; color NR 13, 10..60.
- Only 12 presets contain attributes whose names suggest masks/gradient/paint/correction. Presence is recorded verbatim; the parser does not claim their semantics are supported.
- Adobe Auto Tone documentation confirms content-adaptive adjustment of exposure, contrast, highlights, shadows, whites, blacks, saturation and vibrance via Adobe's hosted ML service. It is not an offline algorithm specification and is not reusable by Restovue.
- darktable/RawTherapee references support scene-linear exposure, soft toe/shoulder tone mapping, mask-aware regional control and the warning that clipped data cannot be recovered. They were used as design guidance, not imported numerical recipes.

Artifacts:

- `xmp_raw_values.json`: explicit values, missing=`null`, HSL, grading, curves, local fields and raw attributes.
- `xmp_parameter_matrix.csv`: 451-row analysis matrix.
- `xmp_source_manifest.json`: commit/license, SHA-256 and size of every source XMP.
- `xmp_parse_errors.json`: empty error array after validation.
- `preset_condition_mapping.csv`: confidence/evidence/risk/suitability classification.
- `scripts/parse_xmp.py`: standard-library-only reproducible parser.

## Smart Value Database

`smart_value_database.json/.csv` contains 21 condition records. Twenty contain source-observed summaries linked to parsed preset IDs; all remain `unvalidated`. One explicit night-road/headlight-glare record has no source numeric values and is labeled `engineering_hypothesis`. No mathematically derived or experimentally validated parameter ranges are claimed.

Numeric summaries preserve count/minimum/median/maximum and provenance. They are not copied directly into production ranges because Lightroom and Restovue transfer functions differ. The database requires runtime photometric/region evidence, compositional priority, protections, conflict checks and validation before a value can become eligible.

## Photographic knowledge

- `scene_taxonomy.json`: exactly 140 composable conditions in six families. Every entry is `taxonomy_only`; no unsupported detector is implied.
- `composition_rules.json`: P1 primary subject, P2 supporting subject, P3 atmosphere/context and P4 potential distraction, plus weak-cue safeguards and snow/night-road/waterfall/sunset examples.
- `region_correction_knowledge.json`: 22 regions with measurements, corrections, protections, eligibility and failure modes.
- `correction_conflicts.json`: 15 coexistence/order/regional/protection/revalidation rules.
- `04_QUALITY_VALIDATION_PLAN.md`: global and per-priority measurements for subject visibility, clipping, skin/vegetation/snow/night preservation, noise, halos, boundaries, sharpening, banding and preview/export consistency.

## Segmentation recommendation

Initial practical stack:

1. Bundled on-device face detection for boxes/contours (not identity or skin segmentation).
2. Bundled person/selfie segmentation for human/background confidence masks.
3. Photometric connected-region analysis for lights/bloom.
4. Evaluate one broad TFLite/MediaPipe scene parser only after exact model license, classes, model hash/size, quantization, device latency, memory and boundary quality pass a formal gate.

Pascal VOC DeepLab lacks sky/vegetation/water/road/building classes because they collapse into background. Cityscapes covers urban categories but misses many photographic regions and carries dataset licensing concerns. A mobile ADE20K parser is promising but no exact commercially cleared Android artifact was approved. Therefore hair, clothing, sky, vegetation and water segmentation remain missing.

Candidates researched: bundled ML Kit face detection, ML Kit Selfie Segmentation, MediaPipe Image Segmenter, DeepLabV3 MobileNet/VOC, LR-ASPP MobileNetV3/Cityscapes, lightweight ADE20K parsers, ONNX Runtime Mobile/NNAPI, LiteRT/TFLite and ML Kit object detection (9 candidate runtime/model approaches).

## Parameters without Restovue equivalents

At least six semantic groups are missing or materially incomplete: arbitrary composite point curves, per-channel point curves, Lightroom sharpening-detail behavior, three-way/global color grading with Adobe blending semantics, profile/camera calibration, and negative dehaze. Lens/profile fields are intentionally not mapped without matching camera semantics. `02_PARAMETER_MAPPING.md` records each mapping and calibration requirement.

## Proposed implementation architecture

See `05_ENGINE_ARCHITECTURE.md`. The essential design is evidence → semantic regions → scene graph/P1–P4 → diagnosis/opportunity/protection → database eligibility/conflict resolution → three independent plans → bounded preview rendering → validation → cached accepted result. Export reuses the selected plan/masks and never silently reanalyzes to a different look.

## Milestone 2 proposal

1. Freeze schemas and implement Android-free knowledge loaders/validators.
2. Add regression/property tests for current Smart functions and plan determinism.
3. Implement explainable photometric diagnosis, protection and no-op decisions without ML.
4. After owner approval, benchmark bundled face/person candidates and complete dependency/license review.
5. Add scene graph and a single high-value regional portrait/backlight recipe.
6. Add conflict resolution and objective rejection gates.
7. Calculate Natural/Enhanced/Extreme independently from one diagnosis.
8. Calibrate on licensed real photographs with blinded review before UI integration.

Acceptance gate: no UI/production claim until a regional recipe demonstrably improves P1 without damaging protected background/atmosphere, preview/export agree, offline fresh-install behavior is proven, and memory/latency pass target/lower-tier device tests.

## Verification results

- JSON files parsed: 8 core datasets plus `verification_summary.json`; all valid.
- CSV schemas validated: parameter matrix 451 rows, condition mapping 516 rows, smart-value matrix 21 rows.
- Representative explicit XMP values were compared to three original source files; 30 total automated research checks passed.
- Source references missing: 0. Duplicate preset IDs: 0. Parse failures: 0.
- Conditions mapped: 20 source condition names plus one engineering-hypothesis example. Conditions without any source numeric evidence: 1. Experimentally validated condition recipes: 0.
- Models/approaches researched: 9. Production models added: 0.
- Android application source changes: 0. Gradle/dependencies/manifest changes: 0.
- Git baseline contained pre-existing untracked `app.zip`; it remains untouched. New changes are confined to `docs/photographic-intelligence/` and `research/photographic-intelligence/`.

## Remaining technical risks

- Presets are looks applied to already-exposed RAW/JPEG inputs, not paired before/after correction ground truth.
- Model/dataset licensing and commercial redistribution must be resolved per exact artifact.
- Face/person boundaries, hair, occlusion, groups and small subjects may cause visible mask errors.
- Color-space/profile handling is still sRGB-centric; wide-gamut behavior is not validated.
- Full-resolution regional processing needs a correct tiled/halo strategy and mask coordinate tests.
- Objective metrics can reject artifacts but cannot alone establish photographic preference.

## Open questions requiring approval before Milestone 2

1. Is a bundled ML dependency of roughly 5–12 MB acceptable if exact measured AAB impact and offline behavior pass review?
2. May Milestone 2 add bundled ML Kit face/person dependencies, or should it begin with photometric diagnosis only?
3. What lower-tier Android device should join the SM-S926B performance/quality matrix?
4. Who will provide/approve the licensed real-photo calibration set and photographic reviewers?
5. Should wide-gamut input/output be a Milestone 2 requirement or a later explicitly scoped color-management milestone?
