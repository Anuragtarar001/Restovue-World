# Photographic Intent Protection

Intent is evidence-weighted and explicitly uncertain. The engine considers strict low-key and
high-key distributions, warm-light evidence, clipping, and named low-key/high-key/silhouette
diagnoses. It does not infer sunset, fog, sky, or night objects from color alone.

An early Milestone 5 calibration treated every `potentiallyIntentional` diagnosis as an atmosphere
veto. Device traces showed this incorrectly suppressed an ordinary portrait. The final rule accepts
only explicit intent diagnoses and tightened low-key evidence to require a low median plus dominant
shadow occupancy. This retained protection for ambiguous dark scenes without blocking ordinary
background corrections.

Protection rules remain authoritative in every mode. Extreme cannot override face, clipping,
mask-quality, atmospheric-intent, or shadow-noise safety constraints.

