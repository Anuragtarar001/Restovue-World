# Milestone 6.2 Sunrise Regression

The exact `orig1.jpeg`/`origfixed.jpeg` calibration test was rerun on Samsung SM-S926B after the UI and progress changes.

Result: PASS.

- Face detector score: 0.9158.
- Person mask accepted; weighted area 0.07394; boundary uncertainty 0.03639.
- Subject assessment: `REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE`.
- Original face luminance: 0.09175.
- Old Extreme face luminance: 0.09154.
- New Natural/Enhanced/Extreme: 0.10570 / 0.11347 / 0.12012.
- Extreme person mean: 0.08296 -> 0.09813.
- Extreme background mean: 0.18472 -> 0.18486.
- Saturation anomaly fraction: 0.
- Halo risk: 0.00000254.

The same production coordinator and renderer produced the contact sheet and metrics at:

`research/photographic-intelligence/milestone6-1/final-result/M6_1_final_CONTACT_SHEET.png`

The comparison/progress changes do not alter these pixel-processing decisions.
