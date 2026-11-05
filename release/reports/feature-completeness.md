# Phase 7.5 feature matrix

| Feature | Implementation evidence | Tests | Device verification | Status | Limitation |
|---|---|---|---|---|---|
| Full-resolution editor export | Original-source decode, shared state pipeline, dimension verification, memory preflight | Preview/export consistency | Processor tests on SM-S926B | PARTIAL | Whole-image memory limit; no general tiled neighborhood pipeline. |
| Liquify | Seven deterministic normalized-vector modes | Push/restore plus all-mode deterministic tests | Pipeline only | PARTIAL | Gesture matrix and large-image hands-on testing pending. |
| Background masks/replacement | Erase/restore alpha masks; transparent/solid/gradient/import modes | Alpha and solid compositing tests | Not exercised manually | PARTIAL | Inversion, edge refinement and color/brightness matching incomplete. |
| Creative layers | Text/shapes/stickers/frames, ordering and shared export renderer | Preview/export instrumentation | Renderer tested | PARTIAL | Direct canvas handles, pinch manipulation, duplicate and complete color UI missing. |
| Collage | 2–9 bounded center-crop grid | Dimension/cell test | NOT TESTED | PARTIAL | Per-cell zoom/reposition/corners/history missing. |
| Passport | Editable mm/DPI/copies/sheet output | Conversion and sheet test | NOT TESTED | PARTIAL | Head guides, alignment UI, A4 preset and sourced country rules incomplete. |
| Document cleanup | Grayscale/adaptive threshold | Ink/paper separation | NOT TESTED | PARTIAL | Deskew, shadows and independent controls incomplete. |
| Signature cleanup | Isolation, line suppression, transparency, crop | Bounds/alpha test | NOT TESTED | PARTIAL | Dedicated manual crop/resize/target-size workflow incomplete. |
| HDR | Genuine multi-image exposure fusion and translation alignment | Tonal recovery test | NOT TESTED | PARTIAL | Bounded 2.5 MP input; no radiance-map reconstruction/ghost removal. |
| Panorama | Ordered translation overlap, rejection and blend | Synthetic overlap test | NOT TESTED | PARTIAL | No feature homography/RANSAC; bounded input resolution. |
| HEIF export | Intentionally unavailable | N/A | N/A | FAIL | No reliable encoder integrated; never writes JPEG with HEIC extension. |
| Batch | Sequential SAF output, formats, size, rotate, metadata, filter/preset state, cancellation | Progress/failure and size tests | Encoder tested | PARTIAL | Full multi-file SAF owner workflow not completed; local overlays excluded. |
