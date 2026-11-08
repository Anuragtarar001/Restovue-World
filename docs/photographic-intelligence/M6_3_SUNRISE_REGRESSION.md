# Milestone 6.3 sunrise regression

- Source geometry: 1600 × 740.
- Faces detected: 1.
- Analysis/inference: 7.50 s on Samsung SM-S926B.
- All modes rendered as `COMBINED`.
- Full-resolution render times: 8.98 s, 9.19 s, and 8.97 s.
- Mean OKLab differences: 0.0123, 0.0284, and 0.0254.

The detected face/subject focus region increased from mean linear luminance 0.0917 to 0.1052, 0.1216, and 0.1269: approximately +15%, +33%, and +38%. The previous Extreme export measured 0.0915 in the same region.

Visual inspection of the aligned contact sheet confirms improved subject visibility while retaining warm sunlight, mist, distant-tree softness, and foreground depth. No forced dehaze or daylight neutralization was selected. Extreme is not simply the largest full-frame OKLab delta because its independently bounded plan prioritizes subject recovery and safety.

Status: **PASS for the available supplied fixture**, with the qualification that one real photograph does not establish universal photographic quality.

Evidence: `research/photographic-intelligence/milestone6-3/sunrise/`.

