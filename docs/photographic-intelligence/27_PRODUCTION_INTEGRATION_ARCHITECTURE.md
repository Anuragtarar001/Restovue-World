# Production Integration Architecture

Milestone 4.5 replaces the two production bypasses with `SmartMagicProcessingCoordinator`.

## Actual pipeline

`Photo Picker -> LoadImage (oriented raster) -> SmartMagicViewModel.process -> SmartMagicProcessingCoordinator.createSession -> SmartMagicProcessingCoordinator.render -> SmartMagicScreen -> SmartMagicExportManager.export -> SmartMagicProcessingCoordinator.renderForExport -> MediaStore`

`createSession` executes the existing `SmartMagicPipeline` analysis, `PhotometricAnalyzerV2`, diagnosis, bright-region and composition reasoning, bundled `LiteRtSemanticAnalyzer`, mask-weighted person/background photometry, semantic subject assessment, and independent Natural/Enhanced/Extreme planning. The coordinator does not duplicate renderer mathematics.

Production policy is deterministic:

- Reliable semantic mask plus executable plan: regional-only correction.
- No reliable human evidence: existing global Smart Magic correction.
- Human evidence without a safe regional plan: preserve the source; never substitute global exposure.
- Quality rejection or perceptual no-op: preserve the source.

This deliberately avoids applying global exposure and regional exposure twice. A future combined strategy requires separate calibrated operation ownership and is not claimed here.

`SmartMagicExportManager` receives the selected production preview result. Regional export locks to the preview's accepted strength and plan. If full-resolution validation cannot reproduce that decision, export fails explicitly instead of silently changing the look or exporting an analysis-sized image.

Diagnostics remain instrumentation-only. No mask, heatmap, or difference image is reachable from the production Save Photo path.
