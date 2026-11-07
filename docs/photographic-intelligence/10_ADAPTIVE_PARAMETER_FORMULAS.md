# Adaptive parameter formulas

All Milestone 2 formulas are deterministic **engineering hypotheses**, not calibrated production values. They plan existing global Smart Magic semantics but do not invoke the renderer.

## Exposure

The target-median estimate is `EV_raw = log2(targetY / max(medianY, 0.01))`. Level-specific targets are 0.27, 0.32, and 0.36. Positive EV is bounded by highlight headroom derived continuously from existing white clipping and highlight occupancy. Potential intent applies a level-specific caution factor. Final range is −0.6…+0.8 EV.

## Highlights and shadows

Highlight compression is a negative normalized value based on diagnosed highlight severity, independently capped at −0.22, −0.42, and −0.60. Shadow recovery is positive and severity-based, capped at +0.14, +0.28, and +0.40, then reduced continuously by noise risk. When denoise is also selected, conflict resolution adds a second conservative noise constraint.

## Contrast and color

Contrast uses low-contrast severity with independent caps 0.055, 0.13, and 0.20, reduced by endpoint clipping. Neutral-region channel gains are blended toward the confidence-gated estimate at 0.55, 0.78, and 0.92 strength and bounded 0.85–1.15. Vibrance uses weak-chroma severity and available saturation headroom, with caps 0.06, 0.14, and 0.22.

## Detail and noise

Luminance denoise follows noise severity with caps 0.12, 0.23, and 0.32. Clarity follows low-local-contrast severity with caps 0.035, 0.085, and 0.13, reduced by noise risk. No semantic skin/sky masks are assumed.

## Stability

All mappings use bounded continuous smooth-step or logarithmic functions. Tests verify finite bounds, deterministic output, monotonic exposure response, highlight-headroom behavior, noise-limited shadow recovery, and small-perturbation continuity. Natural/Enhanced/Extreme use separate targets, gates, caps, and caution policies; they are not scalar blends of one plan.
