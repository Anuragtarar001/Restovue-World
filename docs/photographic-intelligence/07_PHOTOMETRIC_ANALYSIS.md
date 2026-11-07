# Photometric analysis v2

`PhotometricAnalyzerV2` is Android-independent and consumes the existing immutable `RasterImage`. It does not modify pixels.

## Sampling and coordinates

- Source samples are bounded to at most 262,144 using `ceil(sqrt(sourcePixels / budget))` stride.
- Regions use oriented-source normalized coordinates.
- Two grids are produced: 4×4 and 8×8 (80 regions). This costs approximately 80 small 256-bin histograms and supports coarse relationships plus local evidence without source-sized buffers.
- Fully transparent pixels are excluded. Partial alpha is a statistical weight `alpha / 255`; color channels are not composited against an invented background.
- Every image row checks cancellation.

## Definitions

sRGB channels are decoded with the standard piecewise transfer function. Linear luminance is:

`Y = 0.2126 R_linear + 0.7152 G_linear + 0.0722 B_linear`

The global histogram has 1,024 bins. Region histograms have 256 bins. Percentiles use alpha-weighted cumulative mass. Robust range is `p95 - p05`; global contrast is `p75 - p25`. Shadow occupancy is below linear 0.035 and highlight occupancy above linear 0.80. Endpoint clipping uses the linear equivalents of sRGB 2/255 and 253/255, with per-channel clipping also recorded.

Saturation is sRGB HSV-style chroma/max and is used diagnostically, not as linear luminance. Neutral evidence requires saturation ≤0.10 and luminance 0.035–0.92. White-balance gains are conservative neutral-channel equalization, confidence-gated and bounded 0.85–1.15; they are estimates, not Kelvin claims.

Central differences provide gradient energy. A four-neighbor Laplacian provides local/fine-detail energy. The noise proxy only uses low-gradient samples, but remains an uncalibrated proxy that can confuse fine texture and compression artifacts.

## Robustness and limitations

Tests cover black, white, grey, saturated, transparent, partially transparent, tiny, panoramic, tall, noisy, and clipped rasters. Values are finite and normalized. Packed 8-bit source data remains sRGB-centric; wide-gamut/profile analysis is outside this milestone. Fixed-stride sampling can alias highly periodic patterns; later calibration may add stratified sampling while retaining determinism.
