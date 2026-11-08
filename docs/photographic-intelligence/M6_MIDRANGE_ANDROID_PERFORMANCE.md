# Milestone 6 mainstream and mid-range performance

## Workload policy

`SmartMagicProcessingBudgetPolicy` combines runtime max heap and processor count. It does not classify hardware by RAM alone and never changes export dimensions.

| Tier | Analysis | Preview | Optimizer | Optimizer candidates | Regional candidates |
|---|---:|---:|---:|---:|---:|
| constrained | 896 | 560 | 256 | 3 | 2 |
| mainstream | 1080 | 640 | 288 | 4 | 3 |
| high-throughput | 1280 | 720 | 320 | 5 | 4 |

Dimensions are longest-side pixels. Quality protections are identical across tiers. Lower tiers reduce search work and temporary pixels, not final export resolution.

## Device measurement

Samsung SM-S926B selected the `mainstream` budget under the process heap/runtime reported to the app. At 640x427:

- session/semantic work: 2.961 s;
- first Natural render: 3.911 s;
- warmed Enhanced: 1.592 s;
- warmed Extreme: 1.586 s.

Large fixture diagnostics ranged from 0.94 s no-op to 23.05 s for a cold, multi-operation Natural render. Those are larger renders and are not UI-preview measurements.

## Limitations

- No representative physical mid-range phone was available; mid-range performance is NOT TESTED.
- The policy is deterministic and unit-tested, but thermal throttling and battery effects were not measured.
- Full-resolution export continues to use the Milestone 5 safe memory policy; 48 MP remains blocked rather than weakening that guard.

