# Correction decision engine

The headless pipeline is:

`PhotometricEvidence → diagnoses → protections/scene graph → eligibility → conflict resolution → three CorrectionPlan values`

It is not wired to Smart Magic UI or rendering in Milestone 2.

## Supported diagnoses

The engine supports measurable multi-label diagnoses for global under/overexposure, strong backlight pattern, uneven illumination, excessive highlight/shadow occupancy, severe endpoint clipping, low global/local contrast, flat tone distribution, reliable neutral-evidence color cast, weak/excessive chroma, high noise risk, low recoverable detail, bright-center/dark-background arrangement, and already-balanced exposure.

Every diagnosis includes measured evidence, counter-evidence where relevant, severity, confidence, affected grid IDs, limitations, and potential-intent status. Terms such as “backlight” describe a luminance arrangement, not a detected person or light source.

## Protections and eligibility

Executable dispositions include `APPLY`, `APPLY_CONSERVATIVELY`, `PROTECT`, `SKIP`, `UNSUPPORTED`, and `INSUFFICIENT_EVIDENCE`. Current protections cover balanced tone, endpoint clipping/headroom, noise amplification, unreliable neutral evidence, and unresolved semantics. Confidence gates differ by level. Unsupported capabilities are rejected.

A backlight arrangement produces a deferred regional shadow operation requiring `SEMANTIC_PRIMARY_SUBJECT_MASK`; it is never silently replaced by global lifting. No-op includes an explicit reason.

## Conflict rules

The runtime implements deterministic constraints for shadow recovery versus noise, positive exposure versus highlight headroom, and clarity versus noise. Knowledge also preserves ordering/limits for dehaze/color and detail/denoise. Protection can veto eligibility before rendering. Validation requirements always include finite output, no new clipping, and preview/export plan equality.

## Plan contract

Each immutable plan stores schema/analysis/knowledge versions, level, source identity, typed operations, exact unit/range/formula/provenance/calibration metadata, evidence references, required capabilities, protections, deferrals, rejections, validation requirements, and explanations. Full masks are never embedded. The compact codec validates magic, schema, enum names, collection sizes, and trailing data.
