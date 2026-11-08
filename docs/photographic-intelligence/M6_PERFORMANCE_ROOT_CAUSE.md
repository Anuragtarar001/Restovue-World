# Milestone 6 performance root-cause report

## Confirmed costs

1. Semantic session creation: decoding-independent analysis, face/person inference, evidence and plans.
2. Regional rendering: per-pixel linear conversions, soft-mask sampling and up to four quality candidates.
3. Neighborhood operations: local contrast/detail operations previously recomputed 25 samples for each operator.
4. Quality validation: temporary `FloatArray` objects were allocated inside hot pixel loops.
5. Diagnostic tests: larger-than-UI renders for three modes plus PNGs, masks, differences and contact sheets. These totals are not normal preview latency.

## Changes

- Replaced hot-loop temporary arrays with scalar linear RGB.
- A bounded luminance cache is reused for preview-sized neighborhood sampling.
- One neighborhood mean is shared among compatible local operations per output pixel.
- Regional and optimizer candidate counts are supplied by a device/workload budget.
- Analysis, segmentation, plans and masks are cached in an immutable source session.
- Mode render jobs are cancellable; stale results cannot publish.
- Optimizer search runs on a 256–320 px representation, never on 12/48 MP input.

## S24+ measurement

Fixture: backlit portrait, 640x427 preview, mainstream budget.

- Session/semantic analysis: 2,961 ms.
- first Natural render: 3,911 ms.
- Enhanced render after warm-up: 1,592 ms.
- Extreme render after warm-up: 1,586 ms.
- Cached mode publication: no renderer invocation; UI-state update only.

The first correction remains above the ideal 2–4 second target when session and rendering are combined. Milestone 6 therefore improves and explains the behavior but does not claim the flagship target is fully achieved. A physical mid-range device was unavailable.

