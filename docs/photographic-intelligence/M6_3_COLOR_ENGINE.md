# Milestone 6.3 color engine

The production color path retains measured neutral-region RGB gain correction, perceptual vibrance, saturation, gamut bounding, chroma-noise protection, and mask-aware regional white-balance support.

The adjustment report distinguishes the actual representations:

- Red, green, and blue are unitless channel gains with neutral value 1.
- Vibrance and saturation are normalized deltas with neutral value 0.
- White-balance temperature remains `UNSUPPORTED` because no calibrated Kelvin mapping exists.
- Tint remains `UNSUPPORTED` because no independent production tint operator exists.
- Hue correction and semantic selective color remain `UNSUPPORTED`.

Intent protections suppress aggressive neutralization for warm sunrise/sunset evidence and mixed-color event lighting. RGB gains are bounded and validated for clipping and perceptual color anomalies; channels are not independently histogram-maximized. Saturated decorations and skin appearance are protected through global quality validation and regional face weighting.

The real-photo tests show the warm sunrise character remained intact and vivid wedding-stage lighting was not neutralized. These observations are visual review findings, not a claim of universal color accuracy.

