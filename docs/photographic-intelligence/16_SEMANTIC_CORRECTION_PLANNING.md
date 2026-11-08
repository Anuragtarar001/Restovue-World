# Semantic correction planning

`SemanticRegionPhotometricAnalyzer` weights each oriented source pixel by the soft person score, with the complement used for background statistics. It reports effective weight, linear-light luminance, shadow/highlight occupancy, endpoint clipping, saturation, and a conservative noise proxy. Bounding boxes are not used for exposure statistics.

`SemanticSubjectDiagnosisEngine` can return:

- `REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE` when an accepted mask, associated face, subject deficit, and subject shadow evidence agree.
- `PROTECT_CORRECTLY_EXPOSED_SUBJECT` when subject luminance and clipping are conservative.
- `PROTECT_INTENTIONAL_SILHOUETTE` when a strongly dark human region lacks reliable facial evidence against a bright background.
- `MASK_UNRELIABLE`, `INSUFFICIENT_EVIDENCE`, or `NO_PERSON` otherwise.

These are evidence decisions, not pixel edits. `SemanticCorrectionPlanner` preserves all global Milestone 2 plans and emits a separate supplement. Subject-fill and background-only operations are marked `DEFERRED_UNTIL_MILESTONE_4`, reference the compact mask ID, and identify protected regions. An unreliable mask never becomes a global exposure fallback.

All thresholds and formulas are `ENGINEERING_HYPOTHESIS`; real-photo calibration is still required.
