# Photographic Intelligence Engine 2.0 — Milestone 2 report

Date: 2026-11-07  
Branch: `milestone-2-photographic-intelligence`  
Baseline: `2425c7aa780e0487399627873081a19249fc7034`

## Executive summary

Milestone 2 implements an Android-independent, deterministic photographic evidence and correction-planning engine without changing Restovue's renderer, Smart Magic, UI, navigation, dependencies, or Gradle configuration. The engine measures linear-light global and spatial evidence, produces explainable multi-label diagnoses, keeps semantics explicitly unknown, identifies photometric bright components, applies protection/conflict logic, and emits immutable Natural/Enhanced/Extreme plans or a reasoned no-op.

The work is decision infrastructure only. It does not alter pixels and is not connected to the production UI. Adaptive formulas are marked `ENGINEERING_HYPOTHESIS` / `UNCALIBRATED`.

## Files added

Production domain code:

- `app/src/main/java/com/restorex/app/domain/photointelligence/Models.kt`
- `app/src/main/java/com/restorex/app/domain/photointelligence/PhotometricAnalyzerV2.kt`
- `app/src/main/java/com/restorex/app/domain/photointelligence/Reasoning.kt`
- `app/src/main/java/com/restorex/app/domain/photointelligence/KnowledgeV2.kt`
- `app/src/main/java/com/restorex/app/domain/photointelligence/CorrectionPlanner.kt`
- `app/src/main/java/com/restorex/app/domain/photointelligence/CorrectionPlanCodec.kt`

Tests:

- `app/src/test/java/com/restorex/app/domain/photointelligence/TestImages.kt`
- `PhotometricAnalyzerV2Test.kt`, `ReasoningTest.kt`, `KnowledgeV2Test.kt`, `CorrectionDecisionEngineTest.kt`, and `PhotometricPerformanceTest.kt` in the same test package.

Knowledge tooling/data:

- `research/photographic-intelligence/scripts/build_v2.py`
- `research/photographic-intelligence/v2/research_observations.json`
- `decision_knowledge.json`, `executable_knowledge.json`, `migration_manifest.json`, and `validation_report.json` in the same v2 directory.

Documentation:

- `docs/photographic-intelligence/06_SMART_VALUE_DATABASE_V2.md` through `11_MILESTONE_2_TEST_REPORT.md`
- `docs/photographic-intelligence/MILESTONE_2_REPORT.md`

No existing Kotlin, UI, renderer, manifest, Gradle, or dependency file was modified.

## Baseline and Milestone 1 discrepancies

The actual Milestone 1 files match its report: 451 XMP records/manifest entries, 0 parse errors, 516 condition mappings, 140 taxonomy conditions, 22 region records, 15 conflict rules, and 21 Smart Value records. All 21 recipes remain unvalidated. The composition file contains 4 priorities, 8 estimation signals, and 4 scenario examples (16 descriptive records), rather than a flat `rules` array; the v2 migration handles its actual structure. No source reference is missing and no duplicate preset ID was found.

The pre-existing untracked `app.zip` remains untouched. Milestone 1 docs/research were uncommitted at branch creation and remain preserved.

## Architecture implemented

- `Models.kt`: explicit evidence, diagnosis, graph, protection, operation, parameter, validation, and plan contracts.
- `PhotometricAnalyzerV2`: bounded alpha-weighted linear-light global and 4×4/8×8 spatial analysis.
- `Reasoning.kt`: multi-label diagnoses, bright connected components, and non-semantic composition graph.
- `KnowledgeV2.kt`: three-layer knowledge model, typed default database, and fail-closed validator.
- `CorrectionPlanner.kt`: eligibility, protection vetoes, deterministic conflicts, adaptive formulas, level-specific plans, no-op, and privacy-safe JSON diagnostics.
- `CorrectionPlanCodec.kt`: deterministic versioned plan persistence with defensive decode bounds.

The package depends only on `RasterImage`, verified `SmartMagicColorMath`, Kotlin, and Java standard APIs. It contains no Android type.

## Smart Value Database v2

`build_v2.py` reproducibly writes five files under `research/photographic-intelligence/v2/`: research observations, decision knowledge, executable knowledge, migration manifest, and validation report. The migration hashes ten Milestone 1 sources and never overwrites them.

Research/XMP values are non-executable. Six formula definitions have explicit units/ranges and remain uncalibrated. Sixteen photometric condition definitions and nine recipe definitions exist; the semantic subject-fill recipe is explicitly non-executable. The Kotlin validator rejects malformed IDs/references/units/ranges, non-finite values, cyclic dependencies, unsupported semantic execution, and provenance/calibration promotion.

## Photometric intelligence

Supported measurements include a 1,024-bin linear-luminance histogram, p01/p05/p25/p50/p75/p95/p99, weighted mean/median, shadow/highlight occupancy, global and per-channel clipping, robust range, global/local contrast, saturation/chroma distributions, neutral evidence and channel gains, edge density, gradient/detail energy, noise proxy, and 80 normalized spatial regions. Fully transparent pixels are excluded and partial alpha is weighted.

Supported diagnoses are global under/overexposure, backlight luminance arrangement, uneven illumination, excessive highlight/shadow occupancy, severe clipping, low global/local contrast, flat tone distribution, reliable neutral-based cast, weak/excessive chroma, noise risk, low recoverable detail, center/background imbalance, and already-balanced exposure. Each records actual metrics, counter-evidence, severity, confidence, affected region IDs, intent uncertainty, and limitations.

## Composition intelligence

The engine identifies only photometric salience candidates and bright-component geometry. It cannot infer a person, face, vehicle, road, sky, snow, waterfall, vegetation, or semantic P1. All current priorities remain `UNRESOLVED`; the scene graph has an explicit absent semantic primary subject. Bright components distinguish point-like, bloom, specular candidate, diffuse, and broad surfaces without assigning object identity.

This is intentionally conservative and compatible with future mask/model evidence.

## Correction decision engine

Planning supports existing global renderer semantics: exposure, highlight compression, shadow recovery, tone contrast, white balance gains, vibrance, noise reduction, and clarity. White/black point, saturation, dehaze, and detail types are modeled but emitted only when a validated rule supports them. Missing regional subject masks produce `DEFERRED_UNTIL_MILESTONE_3_OR_4`, never a global substitute.

Protections cover balanced exposure, endpoint headroom, high noise, unreliable neutral evidence, and unresolved semantics. High-confidence protections can veto operations. Conflicts constrain exposure by highlights, shadow recovery by noise, and clarity by noise. Plans include validation gates for finite output, new clipping, and preview/export plan identity.

Natural, Enhanced, and Extreme independently choose confidence gates, median targets, intent caution, and operation caps. Extreme is not a multiplier and must obey the same protections. A no-op is explicit and explained.

## Verification

- JSON parse check: 16 files, 0 invalid.
- v2 migration: valid; no duplicate IDs/source promotion.
- JVM tests: 140 tests / 29 suites / 0 failures, errors, or skips.
- Physical SM-S926B connected regressions: 9 / 9 passed.
- Android lint: 0 issues.
- Clean debug build: passed.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

Performance measurements are in `11_MILESTONE_2_TEST_REPORT.md`. Desktop JVM bounded analysis sampled 245,388 pixels for both 12 MP and 48 MP sources. This is not a phone benchmark. No profiler-grade peak-memory/allocation figure is claimed.

## Known limitations

1. No semantic model, face/person mask, scene object classification, or automatic P1 assignment exists.
2. Backlight and scene-like fixture names describe photometric distributions only.
3. Noise/detail and intent estimates are conservative proxies and uncalibrated.
4. Formulas are engineering hypotheses; synthetic tests do not prove visual quality.
5. The plan is not wired to rendering, preview, export, or UI by design.
6. Packed source color is treated as sRGB; wide-gamut/profile analysis is not implemented.
7. Diagnostics JSON is a structured development summary, not a full persistence format; full plan persistence uses the defensive binary codec.
8. Managed/native allocation and on-device M2 latency were not profiled.

## Milestone 3 readiness

Milestone 3 can supply `SemanticEvidence` through normalized region/mask references and model identity, attach it to `SceneGraph` nodes, and resolve composition priorities without changing the plan contract. Before selecting a model, approve exact license, packaged size, class coverage, boundary quality, S24+/lower-tier latency, memory, and offline fresh-install behavior. Recommended first value is face/person evidence; broad sky/vegetation/water parsing should remain a separate evaluated decision.

No Milestone 3 implementation has begun.
