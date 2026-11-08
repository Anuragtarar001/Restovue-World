# Milestone 6 face-regional correction

## Implementation

`RegionalPlanBuilder` can add `MIDTONE_GAMMA` when a reliable person mask and semantic-photometric underexposure decision exist. Amount is derived from subject/background deficit, subject highlight headroom, noise risk and the selected mode.

The renderer does not treat a face bounding box as skin segmentation. It combines:

- the real soft person mask;
- smooth elliptical weighting within each detected face box;
- measured face luminance;
- a stronger lift only for darker faces;
- deep-black reliability and highlight guards.

For groups, detected face regions constrain one combined person mask. The engine does not invent individual person-instance masks and therefore remains conservative when faces have mixed exposure.

## Verification

`AdvancedRegionalCorrectionTest` verifies that a darker face changes more than a brighter face without new highlight clipping. Device fixtures show bounded progression:

- Backlit fine-hair: OKLab mean delta 0.02688 / 0.04358 / 0.05800.
- Three-person group: 0.01299 / 0.02121 / 0.02813.

Visual review found improved face/foreground visibility with no obvious mask outline in the generated contact sheet. This is fixture evidence, not a complete human skin-tone study.

The requested four-woman mixed-light wedding photograph is not present in the repository. Matching Gamma +38 against that exact image is therefore BLOCKED; no success is claimed.

