# Smart Magic adaptive photographic intelligence

## What was wrong

- Manual Auto Enhance, Auto Contrast, and Auto Levels overlap; they are mutually exclusive in one edit state, not three independent corrections.
- Manual Auto Enhance uses whole-image gray-world gains and several encoded-sRGB operations. In mixed lighting this can bias whites; unequal highlight clipping followed by vibrance can make residual magenta/purple contamination more visible.
- The production global baseline used independently scaled parameter-engine outputs rather than one coordinated tonal target.
- The Smart Magic comparison used two separately laid-out Compose images, making exact fit/transform identity harder to guarantee.
- Face recovery stopped at a face ellipse and could look disconnected from the neck and upper subject.

## What was reused

The linear-light SmartMagicPipeline, photometric analyzers, bounded optimizer, semantic inference,
confidence masks, quality validator, immutable production session, export coordinator, progress
events, and complete Adjustment Details reporting remain the foundation.

## What changed

AdaptiveAutoController now measures endpoint utilization, global/local contrast, median deficit,
shadow/noise constraints, neutral evidence, and scene-intent guards. It reports independent Auto
diagnostic strengths but renders one coordinated linear-light plan. The contribution of endpoint
expansion is subtracted from residual contrast need.

The regional renderer now uses a weaker feathered neck/upper-torso lobe below each underlit face.
It remains intersected with the actual person confidence mask, so it cannot invent a body mask.

Smart Magic comparison now draws original and processed pixels on one Canvas, with one fit rectangle
and one zoom/pan transform. Divider movement only changes clipping and does not decode or process.

## Photographic safeguards

- Warm and atmospheric intent suppress global neutralization.
- RGB gains require neutral evidence and are capped at ±4.5%.
- Low/high-key intent guards reduce target-seeking.
- Shadow lift is reduced by measured noise risk.
- Face fill is per-face, midtone-dependent, highlight-protected, and person-mask constrained.
- Natural, Enhanced, and Extreme share the technical baseline; finishing and residual regional
  opportunities may differ, but safety limits remain authoritative.

## Visual results

See [SMART_MAGIC_VISUAL_REVIEW.html](SMART_MAGIC_VISUAL_REVIEW.html). The host review shows a useful
global lift on the sunrise and wedding photographs and a restrained father-portrait result. It does
not validate Android semantic inference or the final per-face result.

## Performance and test boundary

On this Windows host, a single full-image global render of the supplied 1.18–1.44 MP images measured
about 0.86–1.23 seconds. These are JVM host measurements, not Android device claims. The Samsung
S24+ was disconnected; physical preview/export, peak memory, and mask performance are not tested.

## Remaining limitations

- The supplied real photos have not passed through the current Android semantic models this session.
- The underlit wedding face therefore remains visually unvalidated.
- Exact purple-shoulder reproduction needs a documented ROI or device/manual reference render.
- Mode differences in the host-only global baseline are deliberately subtle; regional and
  scene-finishing validation is still required.

## Recommendation

**PARTIAL — requires user/device review.** The implementation is suitable for build and device
validation, but professional-quality claims would be premature without the connected semantic pass.
