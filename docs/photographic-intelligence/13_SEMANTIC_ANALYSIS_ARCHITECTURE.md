# Semantic analysis architecture

The Android-free contract is `SemanticAnalyzer` in `SemanticModels.kt`; its input is `RasterImage` and its result contains model identity, oriented-source dimensions, faces, one optional combined person mask, associations, supported/unsupported classes, duration, and explicit failure state. Android/TensorFlow Lite types remain in `data/LiteRtSemanticAnalyzer.kt`.

The adapter resamples only to each model's fixed input (128x128 face, 256x256 person). It uses four CPU threads, lazy synchronized interpreter creation, coroutine cancellation checks between models, deterministic stable face IDs, and closes both native interpreters. Model failures return an explicit failure instead of a guessed mask.

Supported semantic facts are only `FACE`, `PERSON`, and complement `BACKGROUND`. Unsupported classes are listed in every result. Detector model scores and Restovue's geometry-based quality score are separate fields.

Data flow:

1. Decode/orient through the existing Android repository.
2. Create a bounded, aspect-preserving inference bitmap.
3. Run bundled BlazeFace and Selfie Segmenter directly through TensorFlow Lite.
4. Normalize face geometry to oriented-source coordinates.
5. Quantize the soft confidence mask to one byte/pixel.
6. Validate mask quality and associate faces geometrically.
7. Calculate person/background photometry with the soft mask.
8. Extend the Milestone 2 scene graph and create deferred planner supplements.

No production UI or rendering pipeline consumes these results yet.
