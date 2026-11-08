# Mask coordinates and refinement

## Canonical space

Semantic geometry is stored in normalized **oriented-source** coordinates. Existing `ImageDecoder` output is orientation-normalized, and exports write EXIF orientation `NORMAL`. `ExifTransform` documents all eight encoded-to-oriented mappings. `LetterboxTransform` documents model padding and rejects coordinates inside padding.

Coordinate spaces are: encoded file -> EXIF-oriented source -> bounded analysis bitmap -> model output -> normalized oriented source -> preview/export transform. Because normalized geometry is used, current MediaPipe output may have a different raster size without shifting the mask.

User crop, quarter-turns, fine rotation, flips, and perspective remain the responsibility of the existing `SourceCoordinateMapper`; Milestone 4 must sample the canonical semantic mask through that mapper. Semantic masks are never assumed to match canvas pixels.

## Representation

`ConfidenceMask` stores unsigned 8-bit scores, capped at 1,048,576 pixels. A 256x256 mask uses 64 KiB. It provides bilinear sampling, inversion, union, intersection, exclusion, bounded feathering, and defensive copies. It does not live in Compose state.

## Quality and rejection

Quality includes weighted area, thresholded area, conditional mean score, uncertain-score area, connected components, and isolated area. Empty, near-full, weak, fragmented, or highly uncertain masks are rejected. A face outside the combined person mask adds a rejection reason. Thresholds are engineering hypotheses, not calibrated model probabilities.

The one-pixel separable box feather is intentionally conservative; it reduces quantization stair steps without pretending to be an edge-aware matting algorithm. Edge-aware upsampling remains a Milestone 4 calibration task.
