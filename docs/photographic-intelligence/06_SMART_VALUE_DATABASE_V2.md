# Smart Value Database v2

Milestone 2 separates knowledge into three enforceable layers. The original Milestone 1 files remain unchanged.

## Layers

1. **Research knowledge** is copied to `research/photographic-intelligence/v2/research_observations.json`. Every Lightroom statistic remains `SOURCE_OBSERVED`, `RESEARCH_ONLY`, and `executable=false`.
2. **Photographic decision knowledge** is in `decision_knowledge.json` and typed by `KnowledgeV2.kt`. It defines measurable condition IDs, confidence requirements, protections, and conflicts.
3. **Executable plan knowledge** is in `executable_knowledge.json`. Its formulas are `ENGINEERING_HYPOTHESIS` / `UNCALIBRATED`; the database explicitly states that Milestone 2 is not wired to the renderer.

`build_v2.py` is the reproducible migration. Its manifest hashes all ten source artifacts and records a copy-and-classify transformation. No XMP value becomes a Restovue parameter.

## Validation

`SmartValueKnowledgeValidator` rejects duplicate/blank IDs, invalid references, missing units/confidence/evidence, non-finite or reversed ranges, unsupported semantic requirements on executable recipes, source observations marked executable, false calibration claims, self-conflicts, invalid precedence, and cyclic dependencies. Invalid production knowledge causes construction of `CorrectionDecisionEngine` to fail.

The generated validation report confirms 21/21 Milestone 1 records migrated, 451 XMP matrix rows, 516 mapping rows, zero parse errors, zero duplicate preset IDs, 140 taxonomy records, 22 regional records, and 15 source conflict rules. Sixteen executable photometric condition definitions are implemented. All six parameter formulas remain explicitly uncalibrated.

## Contract limits

- JSON is the reviewable knowledge contract; Kotlin is the runtime typed contract.
- `CorrectionPlanCodec` is a deterministic versioned compact persistence format with collection-size and schema guards.
- Semantic-mask recipes are non-executable and declare their missing capability.
- Production validation requires a later real-photo calibration milestone.
