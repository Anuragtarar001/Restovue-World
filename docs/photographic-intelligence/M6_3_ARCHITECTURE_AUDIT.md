# Milestone 6.3 architecture audit

## Baseline defect

The production path was traced from `SmartMagicViewModel` through `SmartMagicProcessingCoordinator`, `AdaptivePhotographicOptimizer`, `RegionalPlanBuilder`, `RegionalCorrectionRenderer`, preview state, and `SmartMagicExportManager`.

The confirmed defect was in `SmartMagicProcessingCoordinator.createSession` and `render`: the global optimizer ran only when no regional person plan existed, and an accepted regional plan returned immediately as `REGIONAL_ONLY`. Consequently a reliable person mask suppressed exposure, gamma, shadows, highlights, whites, blacks, RGB gains, denoise, sharpening, and other global operations. The diagnostic statement that the global operation was not rendered was accurate.

## Operator inventory before Milestone 6.3

The global `SmartMagicPipeline` already implemented linear-light exposure, contrast, highlights, shadows, white/black placement, monotonic midtone gamma, RGB gains, vibrance, saturation, clarity/texture, bounded dehaze, denoise, and sharpening. Regional rendering supported exposure, shadow recovery, highlight compression, gamma/midtone, contrast, vibrance, white balance, and detail through soft masks. Brightness had no independent production operator and was correctly shown as `UNSUPPORTED`. Temperature, tint, hue rotation, and semantic selective color were not calibrated production operations.

## Implemented correction

- Global analysis and optimization now run for all images.
- The session contains global parameters and an optional regional plan.
- Rendering validates the global result, computes a residual regional plan, then validates regional compositing.
- `COMBINED` is a first-class render strategy.
- A rejected regional candidate no longer discards a valid global result.
- Export reuses the preview session, plan, strategy, and accepted regional strength.
- The adjustment diagnostic builder reports the combined operations actually rendered.

## Remaining architectural limitations

- Person segmentation is a combined foreground mask, not per-person instance segmentation.
- Face boxes guide local weighting but are not skin masks.
- Background exposure has no independent planner rule.
- White balance is expressed as measured RGB gains, not calibrated Kelvin/tint.
- Hue rotation and selective color remain unsupported.
- Some neighborhood/detail passes remain expensive at export resolution.

