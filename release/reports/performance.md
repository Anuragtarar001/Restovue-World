# Performance report

The editor uses viewport-sized previews, a reduced interactive source, conflated latest-state rendering, cancellation checks, bounded raster/thumbnail caches, sequential batch processing and memory preflight for export.

Measured during prior connected-device verification on Samsung SM-S926B:

- Cold launch observed: approximately 383–500 ms across two debug launches.
- Target-size JPEG device test: 1200×900 encoded under a 120 KB limit in 0.113 s as reported by the instrumentation suite.

No controlled before/after measurements exist for slider frame rate, peak heap, HDR throughput, panorama throughput or 50 MP export. Those remain owner acceptance tests. Debug-build launch timing is not a release performance guarantee.
