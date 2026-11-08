# Real-Photo Visual Validation

The final production-path visual set is stored under `research/photographic-intelligence/milestone4-5-validation/`.

The established three images are generated fixtures, not real-camera ground truth. Human review found:

- Cream portrait: no useful visible change; preserving the original is appropriate.
- Backlit fine-hair portrait: face and shirt visibility improve progressively. Sky/water remain stable. No hard silhouette halo is visible in the Natural boundary close-up.
- Group portrait: all faces remain below clipping and the group brightens together. An intermediate implementation exposed dark/blue chroma speckles; the final output adds a smooth deep-shadow chroma guard. Some source chroma/compression outliers remain visible at extreme zoom, so calibration is not considered complete.

The contact sheet is `M45_VISUAL_CONTACT_SHEET.png`, with Original/Natural/Enhanced/Extreme columns and explicit no-op/rejection labels. Difference images are amplified eight times for diagnosis and are not photographic outputs.

No private user photographs were copied or committed. The requested broader real-camera matrix was unavailable, so real-camera coverage is NOT TESTED rather than inferred from generated images.
