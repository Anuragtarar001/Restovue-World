# Milestone 6.2 Performance Report

## Device

Samsung SM-S926B, Android 17/API 37. This is flagship hardware; no representative 4-8 GB mid-range physical device was available, so mid-range latency remains NOT TESTED.

## Measured processing

The exact sunrise rerun (1600 x 740 diagnostic output) measured:

- session: 2.413 s;
- global analysis: 1.064 s;
- photometric/advanced analysis: 0.507 s;
- semantic inference: 0.467 s;
- reasoning/planning: 0.343 s;
- Natural render: 10.905 s on its cold diagnostic pass;
- Enhanced render: 3.973 s;
- Extreme render: 3.978 s.

The complete device suite also measured the production preview timing test at 3.040 s. Existing 12 MP production export tests completed in 52.592 s and 40.600 s; these remain well above the desired sub-10-second goal and are not hidden by the new monitor.

## Milestone 6.2 overhead

- Divider movement updates one float and Compose clipping only. It emits no engine event and performs no decode, allocation of image rasters, or reprocessing.
- The progress channel retains one bitmap-free event. Candidate progress is bounded by the optimizer candidate budget; no pixel/tile events are emitted.
- Cached mode display publishes one completed cached event and reuses the existing render.
- The adjustment summary is constructed once when a render is accepted.

`SmartMagicScreenTest` (four Compose screen cases including endpoint dragging and zoom) took 5.69 s in the complete device suite; this is instrumentation wall time, not frame latency.

## Gaps

CPU utilization, thermal behavior, peak native memory, and interaction frame timing were not collected during a user-driven wedding workflow. Mid-range physical testing is BLOCKED by unavailable hardware and the wedding performance case is BLOCKED by unavailable files.
