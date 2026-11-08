# Original versus previous Extreme analysis

Inputs:

- `orig1.jpeg`: SHA-256 `0EE707657753839EEC960AFE1FD978551E23F8521B2F6AFF0D560F87430A221E`.
- `origfixed.jpeg`: SHA-256 `A83E9600E02C97208C4132AF0D062B7DB4E2909D5738D7D4AAD815F5AF75869C`.
- Both decode to 1600x740, sRGB, no alpha. Geometry matches; no resampling alignment was required.

## Exact pair measurements

| Linear luminance | Original | Previous Extreme |
|---|---:|---:|
| p1 | 0.002760 | 0.002761 |
| p5 | 0.006053 | 0.006044 |
| p25 | 0.023924 | 0.023924 |
| p50 | 0.088703 | 0.088682 |
| p75 | 0.258673 | 0.258574 |
| p95 | 0.622974 | 0.622974 |
| p99 | 0.771431 | 0.770687 |

- Shadow occupancy: 21.5954% → 21.5909%.
- Highlight occupancy: 0.14105% → 0.14029%.
- Black clipping: 0.01343% → 0.01520%.
- White clipping: 0.000084% → 0.000169%.
- Mean adjacent linear contrast: 0.0071495 → 0.0071874.
- Changed pixels: 68.71%, primarily JPEG-level differences.
- Mean OKLab delta: 0.00446; p95: 0.01338.
- Mean linear luminance delta: 0.0000205.

The old export altered many pixels by very small values but did not make a meaningful tonal correction. With the final model-confirmed face region, face mean linear luminance was 0.091746 in the original and 0.091540 in the old Extreme result: a slight decrease.

The exact RGB histograms, regional measurements and metadata are stored in `research/photographic-intelligence/milestone6-1/final-result/execution_trace.json`. Difference image: `difference_old_extreme.png` in the same directory.

