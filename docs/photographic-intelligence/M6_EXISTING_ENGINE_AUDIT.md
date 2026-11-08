# Milestone 6 existing engine audit

## Manual adjustment mathematics

The editor stores non-destructive `AdjustmentState` values. `CpuImagePipeline` performs deterministic bitmap processing.

- Exposure: multiplicative light scaling.
- Gamma: range 0.4–2.5, neutral 1.0; each normalized encoded RGB channel is transformed with `channel^(1/gamma)` after exposure/tonal deltas and before contrast.
- Contrast/highlights/shadows/white/black: bounded tone operations in the existing CPU pipeline.
- Temperature/tint, saturation/vibrance and detail controls are independent operations.

The UI displays gamma relative to neutral. Therefore user-observed **Gamma +38** maps to stored gamma `1.38`, not exponent `38` and not an Adobe slider. The exact transform is `c_out = c_in^(1/1.38)`.

## Smart Magic pipeline

- `SmartMagicProcessingCoordinator`: source-bound evidence, semantic inference, plans and rendering.
- `SmartMagicPipeline`: deterministic global correction.
- `RegionalPlanBuilder` / `RegionalCorrectionRenderer`: soft-mask subject/background operations and quality search.
- `PerceptualImpactEstimator`: OKLab magnitude and artifact metrics.
- `SmartMagicExportManager`: reuses the selected production session/plan at source dimensions.

## Color-space note

Smart Magic exposure and regional compositing use linear-light calculations. The legacy manual gamma operation deliberately follows its existing encoded-RGB behavior. M6's Smart Magic midtone gamma is instead applied to luminance within the Smart Magic tone mapping path, so the two are numerically documented but not falsely claimed equivalent.

