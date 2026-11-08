# Full-Resolution Performance

Measured on Samsung SM-S926B:

- Three production sessions and nine 1536x1024 renders plus diagnostic PNG/contact-sheet generation: 80.677 seconds in the retained instrumentation run.
- Session creation per fixture: 1.40-2.45 seconds.
- Bundled semantic inference after warm-up: 46-48 ms; first measured fixture: 313 ms.
- Individual 1536x1024 regional render plus validation/impact sampling: approximately 4.3-9.5 seconds in the final traces.
- Complete 12 MP production export instrumentation test: 30.889 seconds. This includes generating/encoding the synthetic 12 MP input, semantic session creation, preview selection, full decode/render/encode, and reopen verification; it is not a pure export-only timing.

Normal UI previews are bounded to 720 pixels on the longest side; analysis is bounded to 1280. Semantic inference never receives 12/48 MP input. Completed mode previews are cached, and mode switches cancel stale work.

12 MP output dimensions were verified at 4000x3000. The original source was not downscaled.

48 MP export is blocked by `ExportMemoryPolicy` on the device's heap budget before allocation. A safe tiled/streaming renderer has not been implemented, so acceptance criterion 17 is not met. This is preferable to an OOM or silent downscale.
