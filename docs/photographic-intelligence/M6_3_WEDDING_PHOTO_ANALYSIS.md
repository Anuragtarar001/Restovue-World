# Milestone 6.3 wedding photograph analysis

## Input and execution

- Source geometry: 739 × 1600.
- Faces detected by the current offline model: 2 of the 4 visible people.
- Analysis/inference: 7.47 s on Samsung SM-S926B.
- All modes rendered as `COMBINED`.
- Full-resolution render times: 9.35 s, 10.87 s, and 10.82 s.
- Mean OKLab differences: 0.0173, 0.0266, and 0.0332.

The darker detected face increased in mean linear luminance from 0.0534 to 0.0614, 0.0694, and 0.0761 for Natural, Enhanced, and Extreme: approximately +15%, +30%, and +42%. The other detected face increased more conservatively. Vivid stage decoration and clothing remained saturated without an obvious full-frame neutralization.

## Limitations

Only two faces were detected. The combined person mask cannot supply independent person instances, and no claim is made that all four faces were individually optimized. The contact sheet shows progressive improvement without obvious mask seams at its review scale, but it is not a manually labeled halo benchmark.

## Status

Regional group correction is **PARTIAL but materially improved**. The underlit detected face receives a measurable and visible mode-dependent correction; full four-face analysis remains blocked by detector coverage.

Evidence: `research/photographic-intelligence/milestone6-3/wedding/`.

