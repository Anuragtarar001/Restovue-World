# Milestone 6.3 photometric operators

| Operator | Mathematical behavior | Neutral | Production status |
|---|---|---:|---|
| Exposure | linear RGB multiplied by `2^EV` | 0 EV | Implemented |
| Brightness | endpoint-protected perceptual luminance lift `b*sqrt(Y)*(1-Y)` | 0 | Implemented in 6.3 |
| Contrast | monotonic pivoted tonal curve with bounded strength | 0 | Implemented |
| Highlights | smooth upper-tone compression; no clipped-detail claim | 0 | Implemented |
| Shadows | luminance/noise-aware lower-tone lift | 0 | Implemented |
| Whites | bounded upper endpoint placement | 0 | Implemented |
| Blacks | bounded lower endpoint placement | 0 | Implemented |
| Gamma/midtone | monotonic endpoint-preserving midtone curve | 1 gamma | Implemented globally and regionally |
| Local contrast | bounded neighborhood contrast with edge/mask safeguards | 0 | Implemented |
| Clarity/texture | bounded detail operators | 0 | Implemented |

Brightness is deliberately not an alias for exposure. Exposure changes scene-linear energy uniformly. Brightness preferentially changes perceptual midtones while preserving exact black and white endpoints. Tests verify neutral identity, endpoint preservation, and behavior distinct from exposure.

All parameter types enforce finite values and bounded ranges. Global output is validated before regional composition. Regional output uses a soft confidence mask in linear light and rejects unsafe clipping, shadow chroma amplification, saturation anomalies, and boundary artifacts.

The optimizer now derives brightness, contrast, whites, and blacks from measured percentile deficits, local contrast, clipping/headroom, low-key intent, haze/atmosphere evidence, and noise constraints. These formulas remain engineering-calibrated rather than experimentally universal.

