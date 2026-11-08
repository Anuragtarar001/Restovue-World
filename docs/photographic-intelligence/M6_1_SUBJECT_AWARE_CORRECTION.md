# Subject-aware correction

## Implemented changes

- Added a bounded second face-detection pass only when full-frame detection fails and a reliable person mask exists.
- The focused crop is centered on the torso-derived body axis, avoiding outstretched arms/fabric.
- The face model must still confirm the face; segmentation never fabricates a face box.
- Added a conservative no-face fallback for a reliable moderate-underexposure person mask. Deep silhouettes remain protected.
- Added whole-subject midtone gamma when no face is localized; face-localized weighting is used when detection succeeds.
- Added region-aware perceptual significance so small safe P1 corrections are not erased by global averaging.

## Final sunrise plans

| Mode | Exposure | Shadows | Face midtone gamma delta |
|---|---:|---:|---:|
| Natural | +0.061 EV | 0.0599 | 0.1321 |
| Enhanced | +0.105 EV | 0.1028 | 0.1832 |
| Extreme | +0.148 EV | 0.1456 | 0.2130 |

The corrections operate through the real soft person mask. Extreme raised whole-subject mean luminance from 0.08296 to 0.09813 while the background changed from 0.18472 to 0.18486. Halo risk was 0.0000025 and saturation anomaly fraction was zero.

Safety regression tests cover intentional silhouette protection, already-correct subject protection, no-face whole-subject recovery, face-localized recovery and small-subject significance.

