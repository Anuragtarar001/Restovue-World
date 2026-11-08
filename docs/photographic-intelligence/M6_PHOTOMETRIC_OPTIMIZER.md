# Milestone 6 adaptive photometric optimizer

`AdaptivePhotographicOptimizer` is a deterministic bounded block-coordinate search. It operates on a 256–320 px analysis rendering and never searches over a full-resolution source.

## Candidate groups

1. Exposure and white-balance constraints.
2. Tonal structure: midtone gamma, shadows and highlights.
3. Color finishing: vibrance/saturation with chroma headroom.
4. Detail finishing constrained by noise and halo evidence.

Each candidate is actually rendered and reanalyzed. The score combines median-luminance error, robust-range objective, clipping, color drift, halo risk, saturation anomalies and perceptual magnitude. Low-key, high-key and warm-light evidence reduces pressure toward a generic target.

Hard rejection covers new clipping, saturation anomalies, halo risk and non-finite results. Candidate budgets are 3/4/5 for constrained/mainstream/high-throughput devices. Cancellation is checked between candidates and inside rendering.

When reliable person semantics exist, the regional plan and its bounded quality search remain authoritative. The global optimizer is used for no-person/global cases, avoiding double correction.

Status: implemented and unit-tested; formulas are engineering-calibrated hypotheses, not a learned professional aesthetic model.

