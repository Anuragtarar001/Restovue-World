# Edge-Aware Mask Refinement

`EdgeAwareMaskRefiner` runs a bounded 5x5 joint-bilateral refinement at the model mask resolution.
Spatial weight uses sigma 1.45 mask pixels and range weight uses source linear luminance sigma
0.075. Source luminance is sampled in oriented normalized coordinates. High-confidence model
samples remain dominant; uncertain samples use edge-supported consensus and are attenuated rather
than expanded.

`RegionalMaskSampler` bilinearly samples the compact mask and suppresses correction strength in the
0.2-0.8 uncertainty band. Background is the confidence complement, not a separately claimed
semantic class. The implementation does not invent missing hair. It preserves fine hair only where
the bundled model supplies evidence.

The three supplied masks were 256x256. Measured boundary-uncertainty fractions were 0.01785,
0.02771 and 0.03099. All were accepted by the existing M3 quality rules. They are generated
fixtures without ground-truth masks, so these values are diagnostics, not IoU accuracy.

