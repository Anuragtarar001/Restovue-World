# Milestone 6.3 performance report

Measurements below are physical-device instrumentation results from Samsung SM-S926B. They include production full-resolution rendering and quality search, not only preview display.

| Case | Geometry | Analysis/inference | Natural render | Enhanced render | Extreme render |
|---|---:|---:|---:|---:|---:|
| Sunrise | 1600×740 | 7.50 s | 8.98 s | 9.19 s | 8.97 s |
| Indoor portrait | 899×1599 | 8.83 s | 19.90 s | 20.35 s | 48.21 s |
| Wedding | 739×1600 | 7.47 s | 9.35 s | 10.87 s | 10.82 s |

The indoor Extreme path is the confirmed bottleneck. Its regional candidate reaches the shadow-chroma validator after expensive global detail/noise and regional candidate passes, is retried within a bounded search, and is ultimately rejected. Repeated full-frame neighborhood operations and intermediate bitmap passes dominate; there is no unbounded search.

Milestone 6.3 avoids the former wasted mutually exclusive flow and reuses session analysis/masks across modes and export. It does not yet fuse the expensive detail/noise/neighborhood passes. The 40–53 second target problem is therefore improved for two supplied images but not solved for the indoor Extreme case.

No mid-range physical device, peak-memory profiler capture, or 48 MP export was available in this run. Those items are **NOT TESTED** and no mid-range claim is made.

