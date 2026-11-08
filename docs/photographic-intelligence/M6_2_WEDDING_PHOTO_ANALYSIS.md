# Milestone 6.2 Wedding Photo Analysis

## Status: BLOCKED

The requested original four-woman wedding photograph and its Natural, Enhanced, and Extreme exports are not present in the repository. A search of project files found only the separate sunrise pair (`orig1.jpeg`, `origfixed.jpeg`) and previously generated Milestone 4 group fixtures.

The exact wedding case therefore was not measured, reprocessed, or visually judged. No synthetic image is presented as that scene and no claim is made about per-face luminance, detector reliability, plan rejection, output differences, or export parity for it.

To complete this report, place the four exact files in a private/Git-ignored validation directory or attach them again:

1. original source;
2. Restovue Natural export;
3. Restovue Enhanced export;
4. Restovue Extreme export.

Once available, the implemented diagnostic path can record individual face boxes, box luminance, combined person-mask quality, regional plan operations, candidate rejection reasons, mode differences, and aligned crops.

## Evidence that is available

The current renderer already computes independent face midtones inside a reliable combined person mask. Its deterministic group test uses one dark and one well-exposed face and verifies that local midtone recovery lifts the darker face by more than the bright face while creating no excessive new clipping. This establishes mechanism correctness only; it does not validate the real wedding photograph.
