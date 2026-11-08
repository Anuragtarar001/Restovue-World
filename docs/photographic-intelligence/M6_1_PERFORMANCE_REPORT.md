# Milestone 6.1 performance report

Device: Samsung Galaxy S24+ SM-S926B. Exact source: 1600x740 (1.184 MP).

## Bounded production preview

- Preview: 640x296.
- Session/analysis/semantic/planning: 2,389 ms.
- Natural render: 2,967 ms cold.
- Enhanced render: 1,555 ms warm.
- Extreme render: 1,555 ms warm.
- Source-resolution Extreme export plus MediaStore encoding: 4,161 ms.

Full diagnostic rendering at 1600x740 was 10.9 s for the first cold mode and about 4.0 s for warm Enhanced/Extreme; it also produced quality metrics and lossless artifacts and is not normal preview latency.

The second face pass runs only when full-frame face detection fails and a reliable person mask is present. Candidate optimization remains bounded to preview resolution; export reuses the chosen session/plan.

Peak Java/native memory and thermal behavior were not measured. No physical mid-range phone was available, so mid-range performance remains NOT TESTED rather than inferred from the S24+.

