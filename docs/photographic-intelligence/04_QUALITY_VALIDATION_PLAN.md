# Smart Image Fixer quality-validation plan

Metrics are rejection/diagnostic signals, not proof that an edit is aesthetically better. Every release candidate requires blinded real-photo review in addition to numerical checks.

## Measurement protocol

- Preserve one analysis/session record: source hash (not the source photo in repository), decode/orientation metadata, masks/model versions, scene graph, diagnosis, parameters and seed.
- Compare source, Natural, Enhanced and Extreme in a common color space and aligned geometry. Measure preview and export separately after resampling the export to preview dimensions with a documented filter.
- Compute global metrics and per-region metrics for P1–P4. A global improvement cannot compensate for damage to P1 or a protected region.
- Record confidence and `NOT_APPLICABLE` instead of zero when a semantic region is unavailable.

| Quality concern | Objective candidate metric | Required guard / interpretation | Human review question |
|---|---|---|---|
| Primary-subject visibility | P1 median/percentile luminance, local contrast, edge SNR relative to background | Only valid with a trustworthy P1 mask; cap new clipping/noise | Is the subject easier to read without looking unnaturally lifted? |
| Supporting-subject visibility | P2 luminance/detail gain and P1/P2 salience ratio | P2 must not overtake P1 unintentionally | Does context improve without competing? |
| Highlight dominance | area and connected-component size above linear/sRGB thresholds; local salience | Separate light-source cores/speculars from diffuse surfaces | Are lights/snow/windows still believable? |
| Shadow detail | gradient/entropy gain in recoverable dark regions | Reject gains dominated by estimated noise | Is real detail visible or only noisy grey? |
| Skin preservation | region OkLab hue/chroma/lightness delta; clipped-skin fraction; texture-energy ratio | Confidence-gated face/skin mask; ΔE thresholds need calibration | Does skin remain natural and identity unchanged? |
| Vegetation color | hue-family transition matrix and chroma change | Dry yellow/brown must not be counted as failed green | Are plants richer but still botanically plausible? |
| Snow texture | highlight clipping, local variance and neutral/cool cast shift | Keep high-key brightness; avoid forcing mid-grey | Is snow bright with visible recoverable texture? |
| Night atmosphere | sky/background median, black occupancy, light-source contrast | Scene-intent label may override shadow target | Does it still look like night? |
| Noise amplification | smooth-region robust high-frequency MAD/SNR before/after | Exclude real edges/texture; compare after tone normalization | Did recovery reveal objectionable luminance/chroma noise? |
| New clipping | fraction and connected area at 0/255 plus linear working-space overflow | Existing clipped pixels tracked separately; no false recovery claim | Are gradients and bright colors intact? |
| Halos | signed overshoot/undershoot profiles across strong edges; boundary ring energy | Measure both global detail edges and mask boundaries | Are bright/dark outlines visible around trees/buildings/people? |
| Mask seams | gradient discontinuity aligned with mask boundary; inside/outside delta | Compare against natural source edge; feather width recorded | Can the correction boundary be seen? |
| Over-sharpening | edge overshoot, double-edge/ringing ratio, smooth-region HF gain | Scale-normalize by output resolution | Does detail look crisp rather than brittle? |
| Banding | gradient quantization level count, flat-region second-derivative spikes | Test 8-bit encoded output, not only float stage | Are skies/skin gradients smooth? |
| Preview/export consistency | aligned per-pixel MAE/PSNR/SSIM plus region parameter/log equality | Allow documented resolution-scale effects; same plan must be reused | Does saved output match the chosen preview? |

## Hard validation gates (thresholds not yet calibrated)

1. No NaN/infinity, out-of-range mask, invalid geometry or non-monotonic global tone curve.
2. Output dimensions/orientation/alpha match the declared export contract.
3. No new broad channel clipping unless the recipe explicitly permits a small calibrated increase and passes visual review.
4. Protected P1 regions cannot undergo a correction whose eligibility mask is absent or rejected.
5. Extreme may be stronger but must still pass the same identity, gamut, clipping, halo and boundary gates.
6. A failed candidate plan falls back one level or to no-op; it is never silently saved.

Numeric limits are deliberately absent in Milestone 1. They must be calibrated on labeled real photographs; hardcoding plausible-looking percentages now would be fabricated evidence.

## Calibration dataset and review

- Minimum strata: the 15 requested scenarios, multiple devices/cameras/skin tones/weather states, JPEG/PNG/HEIC where supported, low/high resolution and prior-edited images.
- Preserve licensing/provenance and consent. Do not commit user photographs.
- At least two photographic reviewers score paired randomized outputs for naturalness, subject visibility, atmosphere retention, color, detail and artifacts on a 5-point scale plus “prefer source.”
- Use inter-rater agreement and retain failure comments. Tune on training/calibration split; lock thresholds; report results on a held-out set.
- Generate contact sheets from local licensed inputs; synthetic ramps/noise/edges validate math only and must be labeled synthetic.

## Automated test layers

- Unit: transfer functions, percentile/mask math, monotonicity, bounded parameter calculations, conflict resolution.
- Property/fuzz: random pixels and malformed metadata; cancellation; no overflow/NaN/index errors.
- Golden synthetic: ramps, step edges, color patches, noise fields, alpha edges.
- Integration: same plan in preview/export, lifecycle cancellation, MediaStore finalization.
- Device: latency/memory/thermal behavior and visual review on target and lower-tier phones.
