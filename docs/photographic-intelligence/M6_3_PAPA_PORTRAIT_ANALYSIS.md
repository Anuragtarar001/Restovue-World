# Milestone 6.3 indoor portrait analysis

## Input and execution

- Source: private `papa-original.jpeg`, processed from a Git-ignored device test directory.
- Geometry: 899 × 1599.
- Faces detected: 1.
- Analysis/inference: 8.83 s on Samsung SM-S926B.
- Production strategies: Natural `COMBINED`, Enhanced `COMBINED`, Extreme `GLOBAL_ONLY`.

Natural and Enhanced accepted background contrast/vibrance/detail plus a small face midtone operation. Extreme rejected its regional face candidate for `SHADOW_CHROMA_AMPLIFICATION` and preserved the validated global result.

Full-resolution render times were 19.90 s, 20.35 s, and 48.21 s. Mean OKLab differences from the source were 0.0101, 0.0120, and 0.0099.

## Visual result

The output changes white balance, white placement, detail, and background separation while protecting the face. The face luminance remained within approximately 0.1%, 0.6%, and 2.3% of the original for Natural, Enhanced, and Extreme; it did not receive the clearly visible illumination improvement requested by the owner.

## Status

This critical case is **PARTIAL**. The architecture now evaluates and attempts global plus regional operations, and the quality rejection is transparent, but the indoor portrait is not yet visibly better enough. The result must not be represented as professional completion. Further work should isolate the chroma failure and make face recovery less expensive without weakening the validator.

Evidence: `research/photographic-intelligence/milestone6-3/papa/`.

