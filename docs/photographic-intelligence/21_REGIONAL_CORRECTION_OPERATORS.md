# Regional Correction Operators

Exposure is applied in linear sRGB using `2^EV`, followed by bounded highlight roll-off. Shadows
and highlights use smooth luminance-dependent terms and preserve RGB ratios. Contrast is a bounded
monotonic S-like luminance adjustment. Vibrance uses OKLab chroma with less gain for already vivid
pixels. White balance uses bounded red/blue gain deltas.

All operator results are composited through the soft mask in linear light. Straight source alpha is
preserved; fully transparent pixels, including their hidden RGB, are copied unchanged.

Subject exposure is bounded by highlight headroom, semantic confidence, measured deficit, noise
risk, face constraints and a group guard. Dark saturated outliers receive a local 3x3 chroma-only
cleanup while retaining corrected luminance. This was added after real visual review exposed blue
and red shadow speckles; discontinuous per-pixel suppression was rejected because it produced black
pinholes.

The formulas remain `ENGINEERING_HYPOTHESIS`, not photographically calibrated production values.

