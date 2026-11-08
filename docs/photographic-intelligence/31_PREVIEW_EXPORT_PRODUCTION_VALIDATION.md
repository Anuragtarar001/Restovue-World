# Preview and Export Production Validation

Preview and export now share one `SmartMagicProductionSession`, one mode-specific plan, and one coordinator. Export receives the actual cached preview result. For regional corrections it locks the accepted preview strength and reruns the same renderer at source resolution. No re-diagnosis or mode reselection occurs.

Android test `Milestone45ProductionValidationTest.selectedProductionLevelIsUsedByMediaStoreExport`:

- Runs bundled face/person inference.
- Produces a Natural production preview.
- Exports through `SmartMagicExportManager` and MediaStore.
- Reopens the gallery output.
- Verifies source dimensions.
- Compares decoded JPEG RGB against a direct full-resolution production render with a mean absolute channel tolerance below 4.0.
- Verifies a changed preview does not become the original after export.

The filtered device class passed both tests on Samsung SM-S926B. Diagnostic PNGs are produced by instrumentation only and cannot enter `SmartMagicExportManager`.

JPEG is lossy, so byte/pixel identity is not expected. Transparent sources use PNG. The normal export never writes the comparison split or a semantic overlay.
