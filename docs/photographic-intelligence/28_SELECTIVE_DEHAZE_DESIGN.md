# Selective Dehaze Design

Eligibility combines low local contrast, compressed robust luminance range, weak chroma, and a
dark-channel cue. It is reduced by high/low-key, warm-light, clipping, and explicit photographic-
intent evidence. Low contrast alone is never sufficient.

Regional execution uses a bounded atmospheric-scattering inversion:

`J = (I - A) / t + A`

`A` is a robust bright-background linear-RGB estimate; `t` is bounded to `[0.78, 1]`. Highlight,
flat-bright, soft-mask, and subject-protection guards reduce the operation locally. This is not a
depth map and is not advertised as one. Mode caps are 0.02/0.04/0.06 for regional dehaze. If intent
or mask evidence is inadequate the operation is omitted.

The cream portrait did not meet the dehaze evidence threshold. Its safe improvement came from
mid-frequency background local contrast instead, avoiding fake atmospheric removal.

