# Restovue Smart Magic implementation

## Scope and architecture

Smart Magic is a dedicated, offline one-tap workflow. It does not call the manual editor, a
network service, an ML model, or Android's stock color filters.

- `SmartMagicAnalyzer` is Android-free and measures a preview no larger than 1280 pixels on its
  longest side. It samples at most about 260,000 visible pixels.
- `SmartMagicSceneClassifier` produces overlapping confidence scores. A photograph can be both
  portrait-like and backlit. These scores are heuristics, not semantic AI labels.
- `SmartMagicParameterEngine` converts measurements into immutable, bounded parameters. The same
  parameter object is retained for preview and export.
- `SmartMagicPipeline` performs deterministic linear-light tone/color processing followed by
  bounded-memory spatial processing.
- `SmartMagicViewModel` owns generation-based cancellation, UI state, the retained session, and
  export state.
- `SmartMagicExportManager` decodes the original once, performs a memory preflight, renders with
  the retained parameters, and safely finalizes a MediaStore item in `Pictures/Restovue`.

The existing editor pipeline, history, caches, filters, and utility processors are unchanged.

## Analysis

ARGB input is treated as sRGB. Samples with alpha below 16/255 are excluded. Gamma-encoded sRGB
is converted to linear light using the IEC 61966-2-1 transfer function:

```
Clinear = Csrgb / 12.92                         when Csrgb <= 0.04045
Clinear = ((Csrgb + 0.055) / 1.055) ^ 2.4      otherwise
Y = 0.2126 Rlinear + 0.7152 Glinear + 0.0722 Blinear
```

A 1024-bin linear-luminance histogram supplies P01, P05, P25, P50, P75, P95 and P99, clipping
fractions, interquartile contrast and effective P95-P05 range. Neighbor samples supply gradient,
Laplacian, local-contrast, fine-detail and smooth-region noise estimates.

Color measurements include RGB means, HSV-like saturation, neutral-region means, high-saturation
fraction, the Hasler-Susstrunk opponent-color colorfulness measure, and conservative skin-like
chromatic evidence. White-balance confidence increases only when a useful fraction of moderately
lit, low-chroma samples exists. Warm lighting reduces that confidence.

Spatial evidence includes upper-frame blue, lower-frame green and center-versus-border luminance.
It supports conservative landscape/backlight hints. Portrait confidence uses central skin-like
evidence only; no face detector is bundled and the result is deliberately not described as face
detection. Document confidence combines brightness, low saturation and edge density.

## Parameter selection

The parameter engine uses continuous `smoothstep` mappings and an internal strength of 0.70.
There are no preset threshold ladders. Bounds are enforced by `SmartMagicParameters`:

| Parameter | Bound |
|---|---:|
| Exposure | -0.6 to +0.8 EV |
| Contrast | -0.10 to +0.22 |
| Highlights | -0.60 to 0 |
| Shadows | 0 to +0.40 |
| Whites | -0.10 to +0.15 |
| Blacks | -0.15 to +0.10 |
| RGB white-balance gains | 0.85 to 1.15 |
| Saturation | -0.05 to +0.08 |
| Vibrance | 0 to +0.25 |
| Clarity | 0 to +0.15 |
| Texture | 0 to +0.12 |
| Dehaze | 0 to +0.10 |
| Luminance / chroma NR | 0..0.35 / 0..0.30 |
| Sharpening | 0 to +0.40 |

Exposure starts from `log2(targetMedian / measuredMedian)`. The target changes for low-light,
high-key, low-key and document evidence. Balanced, low-key and document confidences attenuate the
result after it has been safely bounded. Highlight clipping applies a further negative bias.
Shadow lifting is reduced as estimated noise rises and is strongly reduced for intentional low-key
evidence.

White balance uses the mean of reliable near-neutral regions. Per-channel gains move only partway
toward neutral according to confidence, are damped for warm-light evidence, and are normalized to
approximately preserve luminance. They represent channel gains, not an asserted Kelvin estimate.

Vibrance is intentionally distinct from saturation. Saturation is a small global OKLab chroma
scale. Vibrance is weighted by `(1 - normalizedChroma)^1.45`, so already-saturated colors receive
little or no additional chroma. Skin-like pixels receive additional local chroma protection.

## Rendering order and math

1. Decode and normalize orientation with `ImageDecoder`.
2. Convert sRGB channels to linear sRGB.
3. Apply confidence-weighted RGB adaptation gains.
4. Apply exposure as `Cout = Cin * 2^EV`.
5. Apply a monotonic luminance tone curve. A rational shoulder is enabled only when positive
   exposure or highlight compression requires headroom. Shadow/highlight terms are smooth
   endpoint-preserving basis functions (`Y(1-Y)^2` and `Y^2(1-Y)`). Contrast blends toward the
   monotonic smoothstep curve `3Y^2 - 2Y^3`.
6. Preserve chromatic ratios while replacing luminance.
7. Convert linear RGB to OKLab, apply protected saturation/vibrance, and iteratively reduce chroma
   until the result is inside the linear-sRGB gamut.
8. Optionally apply a conservative 3x3 dark-channel-prior approximation. It is enabled only by
   combined haze and landscape evidence, with bright low-saturation sky protection.
9. Apply a 3x3 luminance/chroma bilateral filter when measured noise warrants it.
10. Apply mid-frequency clarity from a scale-aware separable blur.
11. Apply fine texture and thresholded, edge-masked luminance sharpening.
12. Proportionally compress channels above the small encoding headroom. This prevents new 255
    plateaus without changing hue.
13. Convert linear RGB to sRGB and encode once.

Alpha is copied exactly. Fully transparent pixels are not color-processed. Preview and export call
the same implementation and use the same parameters. Scale-dependent clarity radius follows
`RasterImage.scaleFromOriginal`; the parameter decision is never recomputed at export.

## Noise reduction, detail, and memory

Noise reduction uses luminance-guided bilateral weights and filters Y, Cb and Cr independently;
chroma can therefore be reduced without blurring luminance detail. Clarity uses an O(n) separable
packed-buffer blur. Texture and sharpening use a 3x3 luminance residual, a noise threshold and an
edge-selectivity mask. Portrait confidence lowers global clarity, texture, sharpening and color
boosts. No beauty smoothing is performed.

The analysis input is bounded to 1280 pixels and the displayed processed preview to 1080 pixels.
Export reuses the owned ARGB raster for the global pass. Spatial
stages use one or two packed `IntArray` buffers rather than several full-resolution float planes.
The exporter estimates a 13-byte-per-pixel peak and rejects images that cannot fit within 65% of
the current managed-heap limit with a clear message; it never silently downscales. Cancellation is
checked throughout analysis and rendering. MediaStore items remain pending until encoding
completes and are deleted on cancellation or failure.

## UI and offline behavior

The home-screen Smart Magic card opens Android Photo Picker. The workflow shows real stage names,
an indeterminate progress indicator (no fabricated percentage), the enhanced preview, press-and-
hold original comparison, Save Photo, Choose Another Photo, and Open/Share after saving. Gallery
export is supported directly on Android 10 and newer. Android 9 users receive a truthful message
and can use the existing SAF-based editor export workflow.

The manifest still contains no network or broad-storage permission. No dependency was added.

GPU execution was evaluated but not added. This is a one-shot workflow rather than a continuously
dragged adjustment, and introducing a second renderer would add texture transfers, context-loss
handling, preview/export divergence and maintenance risk. The optimized CPU renderer has no
per-pixel heap allocations and supplies a deterministic fallback on every supported device.

Measured on the connected Samsung SM-S926B, the instrumentation calibration took 3.008 seconds for
1080×720 analysis plus rendering. The 4000×3000 full spatial render, JPEG encode, MediaStore
finalization and reopen verification took 40.262 seconds. Immediately after that test, managed heap
was approximately 195.0 MB (18.4 MB before) against a 268.4 MB maximum. No OOM, ANR, crash, stale
gallery item or dimension change occurred. These figures are specific to the synthetic test and
device; they are not guarantees for other images or phones.

## Testing and limitations

Automated coverage includes sRGB/linear round trips, luminance coefficients, histogram ordering,
exposure adaptation, white-balance bounds, vivid-color protection, noise discrimination, edge
cases, tone-curve monotonicity, tonal-region separation, determinism, source immutability, alpha,
cancellation, preview/export equality at equal resolution, UI semantics, and reopenable MediaStore
export on a device.

Important limitations:

- Scene understanding is conventional statistical/spatial inference, not semantic recognition.
- Skin-like chroma evidence is not a face mask. It provides conservative color protection but no
  spatial skin texture mask.
- The haze detector and dark-channel implementation are deliberately conservative; fog and haze
  cannot always be distinguished reliably from intentional soft/high-key light.
- Input ICC profiles and wide-gamut editing are normalized by Android decoding to the working
  bitmap representation. The pipeline's documented working space is sRGB, not a wide-gamut or
  scene-referred space.
- Extremely large originals that exceed the explicit memory budget are rejected rather than
  silently reduced. A tiled spatial renderer is future work.
- Objective synthetic checks do not establish subjective professional superiority. Human grading
  over a licensed photographic corpus remains necessary before marketing image-quality claims.

