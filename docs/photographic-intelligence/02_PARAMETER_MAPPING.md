# Lightroom XMP to Restovue parameter mapping

Source XMP engine: Camera Raw Process Version 15.4 in EmulsionPresets commit `0cdb71c60603867a046bc92c63617feec7894dd1`. Numeric equality does **not** imply visual equality. Adobe slider transfer functions are proprietary; mappings below are calibration proposals, not conversions implemented in Milestone 1.

| Lightroom XMP field | LR scale / observed range in 451 presets | Restovue field | Restovue scale and current behavior | Equivalent? | Proposed calibration |
|---|---|---|---|---|---|
| `Exposure2012` | EV; observed -1.0..+2.0 | `Adjustments.exposure`; Smart `exposureEv` | editor -2..+2, `2^EV` on gamma RGB; Smart -.6..+.8 on linear RGB | Smart exposure formula yes before tone mapping; editor no | Use Smart linear operator. Fit preview/export against neutral ramps and color patches; retain EV identity only before shoulder. |
| `Contrast2012` | -100..+100 nominal; observed -45..80 | `contrast` | editor .5..1.5 center slope; Smart -.1..+.22 smoothstep delta | No | Fit monotonic tone response at several input luminances; a scalar affine map is insufficient. |
| `Highlights2012` | -100..+100; observed -100..30 | `highlights` | editor -1..1 additive `Y²`; Smart -.6..0 polynomial/shoulder | No | Measure LR ramp response by process version, then fit bounded shoulder basis. |
| `Shadows2012` | -100..+100; observed -48..90 | `shadows` | editor -1..1 additive `(1-Y)²`; Smart 0..+.4 basis | No | Calibrate toe response and noise guard; support negative Smart shadows before mapping full scale. |
| `Whites2012` | -100..+100; observed -43..30 | `whites` | editor -1..1 mask above .62; Smart -.1..+.15 endpoint basis | No | Calibrate endpoint movement separately from exposure/highlights. |
| `Blacks2012` | -100..+100; observed -50..40 | `blacks` | editor -1..1 low mask; Smart -.15..+.1 endpoint basis | No | Fit black-point/toe response with clipping constraint. |
| `Temperature` | Kelvin-like value for raw WB; observed 2850..7700 where explicit | `temperature` | -1..1 fixed red/blue gain | No | For JPEG input, estimate chromatic-adaptation gains rather than claim Kelvin. Preserve raw field as source metadata only. |
| `Tint` | Adobe green-magenta scale; observed -10..21 | `tint` | -1..1 fixed green vs red/blue gain | No | Calibrate in a chromatic-adaptation space with neutral-patch targets. |
| `Vibrance` | -100..+100; observed -45..64 | `vibrance` | editor -1..1 low-saturation HSL boost; Smart 0..+.25 OkLab chroma boost with skin heuristic | Conceptually similar, not equivalent | Prefer Smart OkLab operator; fit low/medium/high-chroma patches and skin protection independently. |
| `Saturation` | -100..+100; observed -50..30 | `saturation` | editor multiplier 0..2; Smart -.05..+.08 OkLab chroma scale | No | Fit chroma response in OkLab and gamut-map; do not apply direct `/100` mapping without tests. |
| `Clarity2012` | -100..+100; observed -40..55 | `clarity` | -1..1 multiscale luminance local contrast; Smart 0..+.15 | Conceptually similar | Calibrate by image scale and spatial-frequency target; protect halos/skin. |
| `Texture` | -100..+100; observed -25..25 | `texture` | -1..1 fine luminance band; Smart 0..+.12 | Conceptually similar | Calibrate at reference megapixel density; verify preview/export radius scaling. |
| `Dehaze` | -100..+100; observed -22..45 | `dehaze` | editor 0..1 / Smart 0..+.1 approximate dark channel | Partial; Restovue lacks negative haze | No numeric conversion until atmospheric model and sky tests are calibrated. |
| `Sharpness` | nominal 0..150; observed 10..60 | `sharpening` | 0..2 luminance unsharp mask; Smart 0..+.4 fixed-neighborhood detail | No | Jointly calibrate amount with radius/detail/masking. Smart radius is currently not used by renderer. |
| `SharpenRadius` | nominal .5..3 | `sharpeningRadius` | .3..3 pixels at source scale | Potentially close in units | Verify Gaussian definition and scale normalization; radius identity is not assumed. |
| `SharpenDetail` | 0..100 | no direct model field | Restovue threshold/masking change eligibility, not LR “detail” behavior | Missing | Add only after defining frequency response. |
| `SharpenEdgeMasking` | 0..100 | `sharpeningMasking` | 0..1 gradient eligibility | Conceptually similar | Fit edge-mask percentile curve. |
| `LuminanceSmoothing` | 0..100; observed 0..60 | `luminanceNoiseReduction` | 0..1 3×3 edge-aware Y filter | No | Map from measured noise, not preset number; benchmark texture retention. |
| `ColorNoiseReduction` | 0..100; explicit in only 13 presets, observed 10..60 | `colorNoiseReduction` | 0..1 chroma filter | No | Calibrate with synthetic/real chroma noise and boundary bleeding. |
| `HueAdjustment*` | eight bands, -100..+100 | `HslAdjustments.*.hue` | eight bands, degrees -60..60 | No | Fit hue-wheel movement per band; Lightroom band centers/overlap are not established here. |
| `SaturationAdjustment*` | eight bands, -100..+100 | per-band saturation -1..1 | HSL additive weighted adjustment | No | Patch-chart regression required. |
| `LuminanceAdjustment*` | eight bands, -100..+100 | per-band luminance -1..1 | HSL lightness additive adjustment | No | Patch-chart regression required. |
| `ColorGrade*` | hue 0..360, sat/lum/balance/blending Adobe scales | `ColorBalance`, `SplitToning` | RGB deltas plus two-way HSL split tone | Incomplete | Restovue lacks equivalent three-way/global grading and blending semantics. Mark missing for automatic import. |
| `ToneCurvePV2012*` | point curves 0..255, composite + RGB channels | scalar `toneCurve` only | one symmetric curve control; no point/per-channel curves in `EditState` | Missing | Add monotonic point-curve model only after pipeline/color-mode decision. Do not reduce arbitrary curves to one scalar. |
| Lens/profile/calibration fields | camera/profile dependent | no equivalent | none | Missing / intentionally excluded | Never import without matching camera/profile semantics. |
| Grain/vignette | Adobe effects | `FilmEffects.grain`, `vignette` | deterministic grain, radial vignette | Not equivalent | Treat as artistic, excluded from automatic correction database. |

## Key findings

- All 451 shipped presets contain a composite tone curve, but many are neutral boilerplate; presence is not evidence of a useful correction curve.
- Missing XMP fields remain `null` in the research database. Adobe application defaults were not inferred.
- The source presets are predominantly film/color looks (358 classified primarily artistic); their values are observations and risk constraints, not automatic-correction truth.
- Restovue's Smart global exposure/color path is a better mapping target than the gamma-domain manual path, but highlight/shadow/endpoint/detail behavior still requires empirical calibration.
