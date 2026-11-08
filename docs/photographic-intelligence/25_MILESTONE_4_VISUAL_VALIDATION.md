# Milestone 4 Visual Validation

Outputs are under `research/photographic-intelligence/milestone4-diagnostics/device-sm-s926b/`.
They are real model/renderer outputs, not hand-painted demonstrations.

## Fixture 1 — subject protection

One face and a reliable combined person mask were found. Person mean linear luminance was 0.3541
versus background 0.5577. The decision protected the correctly exposed person and allowed only
bounded background vibrance. Visual review found no subject recoloring or broad boundary halo.

## Fixture 2 — backlit fine hair

One face and a reliable fine-hair mask were found. Person/background mean luminance was
0.1290/0.7083. Subject exposure was eligible. Only reduced Natural recovery passed the final
shadow-chroma gate; Enhanced and Extreme preserved the original. Natural improved face and shirt
visibility while the bright water/sky remained protected. Fine outer curls remain limited
by the 256x256 model confidence; no detail is invented. Unsafe stronger output is rejected when the
shadow-chroma check fails.

## Fixture 3 — group

Three faces share one honest combined mask. Person/background mean luminance was 0.1396/0.3272.
Early review found amplified blue/red shadow samples, and a subsequent per-pixel suppression attempt
created black pinholes. Both were rejected. The final chroma-outlier cleanup is visually clean.
Natural passed only after the 60% retry. Enhanced and Extreme preserved the original when strict
multi-face shadow-chroma validation failed. No individual-person correction is claimed.

Visual assessment is preliminary: no independent photographer panel or ground-truth mask exists.

