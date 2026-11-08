# Milestone 6.2 Group Portrait Correction

## Current implementation

Group handling remains deliberately compatible with the real model's capabilities:

- face detection supplies individual normalized face boxes;
- person segmentation supplies one combined soft foreground mask, not invented person instances;
- `estimateFaceMidtones` measures each face box using only pixels supported by that person mask;
- `faceRecoveryInfluence` creates a bounded, smooth elliptical influence inside each detected face and scales recovery by its measured midtone deficit;
- the darkest recoverable faces receive more midtone lift, while brighter faces receive little or none;
- a group guard reduces broad subject exposure/shadow operations;
- highlight, deep-shadow chroma, noise, clipping, leakage, and boundary validators remain authoritative.

This does not normalize faces to a universal luminance and makes no skin-color classification.

## Tests

`AdvancedRegionalCorrectionTest.face midtone gamma lifts darker face more than well exposed face` verifies differential face correction and clipping limits on a deterministic fixture. Existing group protection tests verify that multi-face corrections are more conservative than an unprotected subject correction. The exact four-woman wedding photograph remains blocked as documented in `M6_2_WEDDING_PHOTO_ANALYSIS.md`.

## Remaining limitation

Because segmentation is combined, clothing/body operations cannot be assigned to individual overlapping people. Per-face midtone correction remains spatially bounded within face regions; independent whole-person correction is deferred until a licensed offline instance model is justified.
