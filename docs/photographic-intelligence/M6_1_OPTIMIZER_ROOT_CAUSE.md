# Milestone 6.1 optimizer root cause

## Verified execution trace

Baseline on Samsung SM-S926B:

1. Person segmentation succeeded: mean model score 0.8820, weighted area 7.394%, boundary uncertainty 0.0364.
2. Full-frame BlazeFace returned zero faces.
3. Subject mean linear luminance was 0.08381; background was 0.18516; subject shadow occupancy was 48.67%.
4. The semantic rule required a face association for a moderate 0.10135 deficit.
5. Diagnosis became `INSUFFICIENT_EVIDENCE`.
6. Natural, Enhanced and Extreme plans contained zero operations.
7. Because accepted human evidence existed, the global optimizer was intentionally bypassed.
8. Regional rendering returned `QUANTIZED_NO_OP` for all modes.

This—not conservative quality rejection, export substitution or later tone neutralization—was the primary failure.

## Secondary objective flaw

Regional rendering previously discarded any correction classed as globally perceptually negligible. A small primary subject can be photographically important while contributing little to a full-frame average. M6.1 now accepts a safe local result when its mask-interior mean luminance changes meaningfully, even if global OKLab magnitude is small.

## Detection calibration

A broad person-guided crop still missed the face. An initial confidence-centroid crop localized a hand and was rejected during visual/coordinate review. The final method derives the body axis from lower-middle person confidence, searches a bounded head zone, and still requires a BlazeFace result. Final face score: 0.9158 at the correct head coordinates.

