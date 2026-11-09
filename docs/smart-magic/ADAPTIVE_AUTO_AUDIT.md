# Restovue adaptive-auto audit

Baseline commit: `1db60697dc77a6c14a7d1e473b4184dac9db61d1`

## Existing manual Auto operations

The manual editor routes `Adjustments.autoMode` through `CpuImagePipeline.transformPixel`. `autoMode` is an enum, so Auto Enhance, Auto Contrast, Auto Levels, and Auto White Balance are mutually exclusive in one non-destructive `EditState`; the UI does not retain three independent Auto operations at once.

| Control | Implementation | Actual operation | Space | Scope |
|---|---|---|---|---|
| Auto Levels | `CpuImagePipeline.kt:209` | independent 2nd/98th percentile stretch of R, G, and B | gamma-encoded sRGB | global |
| Auto Contrast | `CpuImagePipeline.kt:214` | common 2nd/98th luminance endpoints applied independently to encoded R, G, B | gamma-encoded sRGB | global |
| Auto White Balance | `CpuImagePipeline.kt:219` | gray-world gains from whole-image encoded RGB means, bounded to ±14% | gamma-encoded sRGB | global |
| Auto Enhance | `CpuImagePipeline.kt:225` | 38%-weighted Auto Contrast, gray-world gains bounded to ±9%, exposure toward encoded average 0.46, plus vibrance | mixed encoded-domain operations | global |

These operations overlap. Auto Enhance already includes contrast stretching, exposure, white balance, and vibrance. Applying an independently derived Auto Contrast or Auto Levels transformation after it can repeat endpoint expansion and increase clipping.

Manual exposure multiplies encoded RGB by `2^EV`; brightness/shadows/highlights/whites/blacks add equal encoded-channel deltas; manual gamma applies `channel^(1/gamma)` independently. These manual controls are deterministic and preview/export share `CpuImagePipeline`, but several are not scene-linear photographic operations.

## Smart Magic production path

`SmartMagicViewModel` loads a bounded preview, `SmartMagicProcessingCoordinator` performs global, photometric, semantic, and advanced analysis, `AdaptivePhotographicOptimizer` renders bounded candidates through `SmartMagicPipeline`, and `RegionalCorrectionRenderer` applies residual soft-mask corrections. Preview and export reuse the same immutable session and accepted plans.

`SmartMagicPipeline` performs exposure and tonal work in linear sRGB, uses OKLab for perceptual vibrance, and applies bounded neighborhood stages. This is the safer reusable rendering foundation. `PhotometricAnalyzerV2`, `SmartMagicAnalyzer`, semantic masks, face boxes, regional quality validation, generation checks, progress events, and export reuse are also reusable.

## White-kurta purple-cast investigation

The manual Auto Enhance implementation contains a credible mechanism for the reported artifact:

1. Its white balance uses whole-image RGB means rather than reliable neutral pixels.
2. Statistics and gains are computed in gamma-encoded sRGB.
3. The gray-world assumption is unreliable in colored or mixed indoor lighting.
4. Auto exposure is applied after channel gains, so near-white channels can clip unequally.
5. Auto vibrance then increases residual chroma most strongly in low-saturation pixels.

The combination can turn a slightly non-neutral white highlight magenta/purple. Auto Levels is more dangerous because it stretches channels independently. This is a code-supported root-cause mechanism; the exact affected shoulder region still requires pixel-level reproduction on the supplied father portrait. The adaptive production path must not reuse these encoded-domain channel stretches.

## Existing comparison implementations

Manual mode draws edited and original bitmaps in one `Canvas` using one `EditorCoordinates` mapping. Smart Magic uses two Compose `Image` nodes with matching `ContentScale.Fit` and graphics transforms, and clips an original overlay. State generation checks are sound, but two independently measured image nodes and competing transform/divider gesture handlers are less robust than the manual single-canvas implementation.

## Required corrections

- Replace mutually implied Auto percentages with one target-based `AdaptiveAutoPlan` that reports independent diagnostic strengths but produces a coordinated linear-light parameter set.
- Preserve one common technical baseline across modes; vary finishing and regional opportunity within safety limits.
- Use neutral-evidence confidence, warm/mixed-light intent, and highlight headroom before applying RGB gains.
- Evaluate regional residual defects after the global baseline.
- Extend face midtone correction with a soft face/neck falloff inside the reliable person mask.
- Move Smart Magic comparison to one canvas and one shared image transform.
- Reuse analysis, segmentation, and immutable plans across modes and export.

## Proposed processing order

Decode/orient → global and regional evidence → adaptive technical baseline → bounded candidate validation → residual face/subject fill → scene-aware background finishing → final quality validation → shared preview/export plan.

Scene finishing must use the original evidence and may not repeat baseline exposure or white-balance correction.
