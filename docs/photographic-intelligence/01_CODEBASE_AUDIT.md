# Restovue photographic-intelligence codebase audit

Audit date: 2026-11-07  
Source revision: `2425c7aa780e0487399627873081a19249fc7034`  
Scope: read-only audit; no Android application source was changed.

## Architecture and build

- Single `:app` native Android module, package/namespace `com.restorex.app`, Kotlin 2.4.21, AGP 9.5.0, Java 17, `minSdk 28`, `targetSdk/compileSdk 36`.
- Jetpack Compose + Material 3 UI. `MainActivity` creates `EditorViewModel` and `SmartMagicViewModel`; `EditorScreen.kt` uses Compose state rather than a navigation library. Home, editor, settings, Smart Magic and utility dialogs are state-selected destinations in one screen.
- `domain` contains Android-free models, history, engine/repository ports and use cases. `data` contains Android codecs and deterministic packed-ARGB CPU processing. `presentation` contains `StateFlow` ViewModels, render scheduling and caches.
- Dependencies are AndroidX/Compose/coroutines/ExifInterface only. There is no OpenCV, GPU image framework, ML runtime, face detector, semantic segmenter or bundled model asset. The manifest declares no permissions and no network capability.

## Capability trace

| Capability | Path / symbol | Actual behavior, input/output and scale | Color domain / dependencies | Limits, reuse and verification |
|---|---|---|---|---|
| Image load/orientation | `data/AndroidImageRepository.kt::decode/load` | `ImageDecoder` software bitmap; target-size decode; returns `RasterImage(IntArray ARGB, scaleFromOriginal)`. ImageDecoder normalizes encoded orientation. | Android ImageDecoder, ARGB_8888 | Bounded by requested dimension/pixels. Tested by Android repository/processor integration indirectly; no malformed-input fuzz suite. Reusable. |
| Editor state | `domain/model/EditState.kt` | Immutable parameter state; adjustments, geometry, filter/LUT, compact local edits, creative layers. | Android-free | Validated ranges in constructors; history stores state, not bitmaps. Strong reuse point. |
| Preview scheduling | `presentation/EditorViewModel.kt::scheduleRender`; `PreviewSizing.kt` | Latest job cancelled; generation ID rejects stale output; interactive source can be reduced to 720 max dimension; settled preview viewport/pixel budget default 1.5 MP. | Coroutines/Dispatchers.Default | Good bounded architecture; CPU algorithms still allocate several full-frame buffers. Covered by scheduler and preview policy tests. |
| Preview cache | `presentation/RasterMemoryCache.kt` | Access-ordered byte- and entry-bounded `RasterImage` cache. | Kotlin collections | No disk cache; safe reusable component. Unit-tested. |
| Full export | `AndroidImageRepository.export`; `AndroidImageProcessor.renderForExport` | Re-decodes source with memory preflight, renders `EditState`, writes JPEG/PNG/WebP via SAF URI, optionally copies recognized EXIF and normalizes orientation. | Android bitmap encoder, ExifInterface | Not tiled. `ExportMemoryPolicy` may reject/downsample only when user dimensions demand it; explicitly errors when unsafe. Preview and export share `CpuImagePipeline`, but source scale changes spatial radii. Tests cover policy/processor; device fidelity remains necessary. |
| Memory policy | `ExportMemoryPolicy.kt`; `MemoryPressureRegistry`; `PerformanceDiagnostics` | Safe pixels use 65% heap and estimated 12 or 20 bytes/pixel; cache clearing on trim; debug logging. | Runtime heap | Native/driver memory not fully modeled. No GPU allocations exist. Unit-tested policy. |
| Processing order | `AndroidImageProcessor.render` | local edits → geometry → global/filter/LUT pipeline → background fill → creative layers/frame. Export reuses owned source pixel buffer where possible. | CPU | Local operations precede geometry and global correction; future semantic masks need coordinate/version discipline. |
| Exposure (editor) | `CpuImagePipeline.transformPixel` | `gain = 2^exposure`, UI `-2..+2 EV`; applied after heuristic auto-mode and temperature/tint. | **Gamma-encoded channel values** | EV formula is physically shaped but applied to sRGB-encoded values, not linear light; not equivalent to scene-linear exposure. Unit tests check behavior, not colorimetric equivalence. Requires replacement/calibration for intelligence engine. |
| Exposure (Smart Magic) | `SmartMagicPipeline.applyGlobalColorInPlace` | Converts sRGB→linear, multiplies by `2^exposureEv` and RGB WB gains; range `-.6..+.8 EV`. | Linear sRGB then OkLab | Mathematically preferable and reusable. Unit tests cover exposure/tone behavior. |
| Highlights/shadows (editor) | `CpuImagePipeline.transformPixel` | Additive RGB delta using `luma²` highlight and `(1-luma)²` shadow masks; UI `-1..1`, constants `.30/.34`. | Gamma-encoded luma/channels | Can shift/chop channels after additive correction; global only. Fixed coefficients. Tested directionally. |
| Highlights/shadows (Smart Magic) | `SmartMagicPipeline.toneMapLuminance` | Smooth basis functions on linear luminance; highlights `-.6..0`, shadows `0..+.4`; rational shoulder above `.72`. Chroma restored through luminance ratio. | Linear sRGB luminance | Monotonicity is tested. Global only; no real subject/background masks. Reusable foundation. |
| Whites/blacks | Editor additive endpoint masks (`.62..1`, `0.. .38`); Smart Magic polynomial endpoint bases | Editor UI `-1..1`; Smart Magic whites `-.1..+.15`, blacks `-.15..+.1`. | Editor gamma; Smart Magic linear | Not equivalent to Lightroom endpoints. Hardcoded masks and gains require calibration. |
| Contrast/tone curve | Editor center contrast `(v-.5)*contrast+.5`, `contrast .5..1.5`; scalar smooth S-curve `toneCurve -1..1`. Smart Magic adds `contrast*(smoothstep(y)-y)`, `-.1..+.22`. | Editor gamma; Smart Magic linear luminance | Editor may clip before final clamp. Smart Magic curve has monotonic tests. No arbitrary point-curve engine in `EditState`. |
| White balance | Editor fixed opposing red/blue and green/magenta multipliers (`temperature/tint -1..1`). Smart Magic robust neutral-pixel gains, per-channel `.85..1.15`, confidence- and warm-light-gated. | Editor gamma; Smart Magic linear | Kelvin is not estimated. Smart method is conservative and reusable; neither performs formal chromatic adaptation. |
| Saturation/vibrance | Editor HSL saturation multiplier `0..2`; vibrance adds to under-saturated colors (`-1..1`). Smart Magic modifies OkLab chroma with low-chroma and heuristic skin protection. | HSL gamma values; Smart Magic OkLab after linear conversion | Smart Magic is more perceptual; skin detection is color heuristic, not face detection. Tests cover saturation protection/stability. |
| HSL mixer | `CpuImagePipeline.transformPixel`; `HslAdjustments` | Eight hue bands, hue `-60..60`, saturation/luminance `-1..1`; triangular/circular hue weights. | HSL from working gamma RGB | Useful but not Lightroom-equivalent; no perceptual gamut mapping until final clamp. Unit-tested through pipeline/filter tests. |
| Color balance/split toning | `Adjustments.colorBalance/splitToning`; `transformPixel` | Luminance-weighted RGB deltas; shadow/highlight HSL tint blend. | Gamma luma/RGB | Fixed masks/strength `.28`; reusable only after calibration. |
| Auto modes | `AutoEnhanceMode`; `CpuImagePipeline.transformPixel` | percentile levels, grey-world gains, and fixed `AUTO_ENHANCE` blend constants (`.38`, `.55`, target luma `.46`, EV cap `.7`). | Gamma RGB/statistics | These are fixed heuristics, distinct from Smart Magic. Good regression baseline, not scene understanding. |
| Smart analysis | `domain/smartmagic/SmartMagicAnalyzer.kt` | ≤260k samples; 1024-bin **linear-light** luminance histogram; percentiles, clipping, colorfulness, neutral/skin-like fractions, spatial upper-blue/lower-green, edge/noise proxies. | Linear sRGB stats plus gamma HSV-like color stats | Real dynamic measurements. Scene classifier is heuristic: general, portrait, landscape, low light, backlit, document, balanced/high/low-key. No objects or masks. Unit-tested with synthetic images. |
| Smart parameter selection | `SmartMagicParameterEngine.kt` | Smooth evidence mappings; one parameter set at default strength `.7`; exposure target median `.145.. .29`; guards for balanced/low-key/document/noise/skin. | Analysis-derived | Dynamic, not fixed per image, but one global look only. Hardcoded thresholds/calibration constants are documented in code. Reusable starting point. |
| Smart render | `data/SmartMagicPipeline.kt` | linear exposure/tone/WB → OkLab color/gamut mapping → dark-channel dehaze → 3×3 edge-aware NR → clarity → fine detail/sharpen → output headroom 250/255. | Mixed linear sRGB, OkLab, packed 8-bit intermediates | Deterministic and shared by Smart preview/export. Repeated 8-bit quantization between stages; whole-frame buffers; no semantic regions. Substantial unit/instrumentation coverage. |
| Noise reduction | `CpuImagePipeline.reduceNoise`; Smart `applyEdgeAwareNoiseReduction` | Luma/chroma separation; small edge-aware neighborhood; controls `0..1` editor, `0.. .35/.30` Smart. | Y/Cb/Cr-like values derived from gamma RGB | Conventional, bounded, not NLM; may miss coarse/structured noise. Directionally tested. |
| Sharpening | `CpuImagePipeline.applySharpening`; Smart `applyFineDetail` | luminance unsharp mask; editor amount `0..2`, radius `.3..3`, threshold `0..1`, masking `0..1`; Smart edge-gated 3×3 detail. | Gamma-derived luminance | Scale-aware editor radius uses `scaleFromOriginal`; Smart radius parameter is stored but current fine-detail stage uses fixed neighborhood, a material mismatch. Tests cover response/threshold/mask. |
| Structure/clarity/texture | Gaussian/Laplacian bands in `CpuImagePipeline`; box/neighbor methods in Smart pipeline | Structure `0..1`, clarity/texture `-1..1` editor; Smart positive bounded values. | Gamma luma | Real distinct frequency operations, but packed 8-bit and heuristic radii. Details tests exist. |
| Dehaze | Both CPU pipelines | Approximate dark-channel transmission with 3×3 dark estimate and sky protection; editor `0..1`, Smart `0.. .1`. | Gamma RGB | Conventional approximation, global atmospheric-light estimate; susceptible to sky/edge artifacts. Tests are synthetic/directional. |
| Face detection | None | `isSkinLike`/`skinProtection` are pixel-color heuristics only. | No model | Must not be represented as face detection. |
| Subject detection | None | Central-vs-border luminance contributes to “backlit”; no subject instance. | No model | Missing. |
| Semantic segmentation | None | Manual background strokes and masks exist; no automatic region classes. | No model | Missing. |
| Manual masks | `LocalEdits.kt`, `CpuLocalEditPipeline`, background/liquify models | Normalized brush strokes, sparse light-mask tiles, radial/linear masks, manual background mask. | CPU | Useful non-destructive regional rendering infrastructure. Brush/mask tests exist. |
| GPU acceleration | None | Compose displays bitmaps; processing is CPU. | No OpenGL/Vulkan/RenderEffect processing | Missing; avoids transfer/lifecycle complexity but limits speed. |
| Geometry | `CpuGeometryPipeline`, `CropCalculator`, coordinate mappers | crop/rotate/flip/fine rotation/perspective, normalized coordinates. | CPU | Tested; reusable for semantic mask coordinate mapping. |
| LUT/filter engine | `CubeLut`, `DefaultFilterCatalog` | 3D `.cube` trilinear sampling; 50 curated parameter presets; intensity blend. | CPU | Filter parameters share editor gamma pipeline. Catalog/LUT tests exist. |
| Local retouch | `CpuLocalEditPipeline` | clone/heal/smooth/dodge/burn/lighting/background/liquify from compact operations. | CPU | Conventional, non-AI; useful masks/stroke serialization. Not semantic. |
| Utilities | `CpuUtilityPipeline`, `AndroidUtilityProcessor`, batch/target encoder | collage, exposure-fusion HDR, translation panorama, passport, document/signature, batch conversion/compression. | CPU/Android codecs | Panorama/HDR are limited as previously documented; separate from intelligence engine. |
| Offline models | `OfflineRestorationEngine` port only | Interface declares unavailable future NCNN capability. No implementation/assets. | None | Do not claim restoration/segmentation. |

## Fixed thresholds and hardcoded values requiring calibration

The code contains deliberate bounds, but the next engine must not treat them as source-grounded universal values. Important examples include:

- Editor Auto Enhance: luma target `.46`, EV ±`.7`, level blend `.38`, WB blend `.55`.
- Smart target median starts at `.205`; scene shifts/caps `.145.. .29`; EV `-.6..+.8`; highlight clipping thresholds `.004.. .10`; neutral confidence threshold `.08`.
- Scene heuristics: skin fraction thresholds, upper-blue/lower-green thresholds, document/high/low-key ranges in `SmartMagicSceneClassifier`.
- Dehaze: fixed sky thresholds and minimum transmission; atmospheric estimate is global.
- Detail: fixed blur radii/band clamps/noise gates. Smart `sharpeningRadius` is calculated but not consumed by its fixed 3×3 detail renderer.
- Output is repeatedly packed to 8-bit ARGB; only some Smart global operations use floating-point linear RGB/OkLab.

## Preview versus export

- Editor: identical processing classes and `EditState`; preview uses reduced decode and export re-decodes to a heap-safe resolution. Spatial radii scale through `scaleFromOriginal` for editor structure/clarity/texture/sharpening, but not every local algorithm is scale-normalized.
- Smart Magic: preview analyzes a decode capped at 1280 and renders a 1080 preview. Export re-decodes according to a safe pixel budget and reuses the **same stored parameter set**, so it does not silently reanalyze. The renderer is shared; fixed-neighborhood detail/dehaze/NR can look resolution-dependent.
- Neither path exports a screen capture. Original URI is not overwritten.

## Reuse recommendation

Reuse `RasterImage`, immutable `EditState` patterns, repository/use-case ports, cancellation/generation scheduling, bounded caches, memory preflight, linear/OkLab Smart color math, coordinate mappers, compact manual masks and MediaStore/SAF output. Replace or calibrate gamma-domain editor tone/color math for the new engine. Add semantic inference behind a new Android-free port; do not put model runtime types in domain models.

## Regression baseline

`gradlew.bat testDebugUnitTest` completed successfully on 2026-11-07: 24 suites, 111 tests, 0 failures, 0 errors and 0 skipped. This validates the existing synthetic/unit contracts; it is not a physical-device or real-photo quality result.
