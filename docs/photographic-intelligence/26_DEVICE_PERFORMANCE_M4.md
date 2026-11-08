# Milestone 4 Device Performance

Device: Samsung Galaxy S24+ SM-S926B (`[redacted]`), Android 17 / API 37 as reported by ADB.
Input fixtures were 1448x1086, 1536x1024 and 1536x1024;
semantic masks were 256x256.

The first complete diagnostic run took 70.212 seconds for all three images. The final complete
connected diagnostic test took 136.024 seconds. Later runs took longer
because additional quality measurements and lossless PNG diagnostics were added. These totals
include analysis, three full-size CPU renders, mask images, difference images, boundary crops and
PNG compression; they are not interactive preview latency.

Recorded per-fixture semantic inference was approximately 46-374 ms depending on cold/warm state.
Observed managed-heap deltas in diagnostic JSON were roughly 21-43 MB and native deltas roughly
12-38 MB. These are point deltas, not profiler-confirmed peaks. No OOM or ANR occurred.

Current clean debug APK is 48,639,561 bytes (46.39 MiB). Milestone 4 adds no dependency or model; size change is code and
normal build variation. Thermal/battery profiling, 12 MP/48 MP full-resolution rendering and a
lower-tier device were not measured.

