# Milestone 6.1 implementation audit

Date: 2026-11-08

## Verified Milestone 6 path

`SmartMagicViewModel` creates an original preview, asks `SmartMagicProcessingCoordinator` for one immutable source session, then renders the selected `EnhancementLevel`. Export reuses that session and selected plan through `SmartMagicExportManager`.

Implemented and connected:

- linear-light global/region photometry;
- deterministic global candidate optimizer for images without reliable human evidence;
- BlazeFace and person segmentation through bundled TFLite models;
- soft person masks and mask-weighted subject/background statistics;
- semantic subject diagnosis and regional plans;
- regional exposure, shadows and midtone gamma;
- bounded quality validation and fallback candidates;
- distinct Natural/Enhanced/Extreme plans;
- preview/export session identity and stale-result protection.

## Important architecture finding

When any accepted human mask exists, the coordinator deliberately bypasses `AdaptivePhotographicOptimizer` and relies on `SemanticSubjectDiagnosisEngine`, `RegionalPlanBuilder` and `RegionalCorrectionRenderer`. Therefore global candidate scoring could not rescue a human image whose regional plan was empty.

Before M6.1 the regional diagnosis required a face association for a moderate subject deficit. The supplied environmental portrait had a reliable person mask but no full-frame face detection, so every mode received an empty regional plan and returned the exact source pixels.

## Deferred or partial capabilities

- No skin segmentation or individual person instance masks.
- No learned aesthetic ranking.
- Regional candidate selection remains safety-first bounded strength search, not an unconstrained aesthetic optimizer.
- 48 MP export remains safely blocked by the existing memory policy.
- Physical mid-range-device benchmarking remains unavailable.

