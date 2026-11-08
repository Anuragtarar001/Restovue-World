# Composition intelligence V3

`SemanticCompositionEngine` adds confirmed person, face, and background nodes to the existing photometric graph while retaining every Milestone 2 salience node. Face-to-person containment and foreground/background relations are explicit edges.

P1 is assigned only when the accepted person mask has sufficient combined evidence: face prominence, subject area, mask confidence, and subject/background contrast. Person presence alone does not establish a portrait. When those cues are inadequate, `semanticPrimarySubjectId` remains null and priority is `UNRESOLVED`. If a person becomes P1, its complement may be P3 context. P2 and P4 are not fabricated without semantic or intentionality evidence.

Multiple faces can attach to the single combined person mask. This association does not create person instances. Overlap, groups, different exposure per person, and individuals without faces remain ambiguous and must defer individual correction.

The engine cannot currently identify sky, snow, roads, vehicles, waterfalls, vegetation, buildings, lights, hair, clothing, or skin. Bright photometric components are still non-semantic and are not automatically distractions.
