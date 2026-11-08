# Adaptive Calibration

Natural, Enhanced, and Extreme share one evidence/session but are independently calculated. Global modes run `SmartMagicParameterEngine` at strengths 0.42, 0.70, and 0.88; this changes every evidence-based formula rather than blending a fixed output. Regional modes use the existing level-specific `RegionalPlanBuilder` limits and bounded renderer validation.

`PerceptualImpactEstimator` samples bounded pixels and measures changed fraction, mean/p95 OKLab distance, signed and p95 linear-luminance change, and mean chroma change. It distinguishes quantized no-op from a mathematically changed but photographically negligible output. Negligible output is not strengthened merely to force a visible difference.

Fixture findings:

- Cream-shirt portrait: background vibrance changed 4.5%/13.0% of sampled pixels for Enhanced/Extreme, but mean OKLab differences were only 0.000064/0.000186. Both are correctly classified perceptually negligible and the subject/source is preserved. The available background operators do not justify stronger dehaze in this milestone.
- Backlit portrait: subject exposure/shadow planning produces progressively visible Natural/Enhanced/Extreme results while preserving the bright sky/water. Final mean OKLab deltas are approximately 0.026, 0.041, and 0.054.
- Group portrait: group guard and face headroom constraints retain three faces while increasing subject visibility. Final mean OKLab deltas are approximately 0.014, 0.022, and 0.030.

Deep chromatic shadows use a smooth saturation/luminance guard that reduces recovery on likely chroma outliers without borrowing neighboring colors. This replaced a neighbor-color cleanup that produced visible dark/blue speckles in an intermediate group validation output.

These values are engineering calibration on three generated fixtures, not general production calibration across real cameras.
