# Milestone 6 tone-curve engine

Smart Magic now carries `midtoneGamma` in its typed parameter model, range 0.8–1.5 and neutral 1.0.

For normalized tone luminance `y`:

`y_gamma = clamp(y, 0, 1)^(1 / midtoneGamma)`

The result then passes through the existing bounded contrast/highlight/shadow shaping. Values above 1 lift midtones while preserving endpoints. Unit tests verify monotonicity and the exact Gamma +38 mapping (`gamma = 1.38`).

The adaptive optimizer caps proposed changes independently by mode rather than applying one global mode multiplier. Regional face midtone operations use `gamma_delta`, with conservative per-mode ceilings and local headroom/noise guards.

No inverted gradients or non-finite values were observed in the automated suite.

