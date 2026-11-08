# Perceptual Mode Calibration

Natural, Enhanced, and Extreme are independently planned. Operation-specific envelopes differ:
regional local contrast uses caps 0.04/0.08/0.11; dehaze 0.02/0.04/0.06; detail
0.012/0.024/0.034. Global dehaze, clarity, texture, shadow, highlight, and white-balance parameters
also receive evidence-specific caps and intent guards.

The production estimator records changed fraction, mean/p95 OKLab delta, luminance/chroma change,
local-edge contrast change, saturation anomalies, and halo risk. A parameter change is not enough:
quantized and perceptually negligible output is returned as the original.

Final device results (mean OKLab delta):

| Fixture | Natural | Enhanced | Extreme |
|---|---:|---:|---:|
| Cream portrait | negligible (0.00011) | applied (0.00044) | applied (0.00077) |
| Backlit portrait | 0.02478 | 0.03908 | 0.05160 |
| Group portrait | 0.01157 | 0.01847 | 0.02481 |
| Public-domain portrait | no-op | no-op | no-op |

