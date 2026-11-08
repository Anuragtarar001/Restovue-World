# Milestone 6.3 unified correction pipeline

The production sequence is now:

1. Decode and orient the source.
2. Build bounded photometric evidence and advanced scene evidence.
3. Run bundled offline face/person inference.
4. Build semantic and composition evidence.
5. Generate global Natural, Enhanced, and Extreme candidates.
6. Generate person/background regional proposals.
7. Protect semantic subjects from unsuitable global darkening.
8. Render and quality-check the global candidate.
9. Calculate the regional residual after the accepted global operation.
10. Render the residual through the refined soft mask and face constraints.
11. Validate clipping, shadow chroma, saturation anomalies, halos, and mask leakage.
12. Return `COMBINED`, `GLOBAL_ONLY`, `REGIONAL_ONLY`, or `NO_OP` with an immutable diagnostic report.

Residual calculation prevents double correction. For example, positive global exposure, brightness, shadows, and midtone lift reduce the remaining person exposure/gamma request. Negative global exposure is prohibited for a correctly exposed protected subject; if it were present, the regional plan could explicitly compensate it. Background contrast remains mask-scoped so it cannot unnecessarily darken a face.

Preview and export use the same `ProductionSmartMagicSession`, source fingerprint, geometry version, selected mode, plan, and accepted regional strength. Full-resolution export rerenders the accepted plan rather than exporting the preview bitmap. Stale source or geometry identity is rejected by the existing generation/session checks.

No network dependency, cloud processing, or new model was introduced.

