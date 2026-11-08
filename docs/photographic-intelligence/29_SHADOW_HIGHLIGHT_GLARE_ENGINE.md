# Shadow, Highlight, and Glare Engine

Subject recovery remains linear-light, mask-weighted, and bounded by person highlight headroom,
noise risk, group mode, and face-region constraints. Highly chromatic deep-shadow outliers receive
a smooth local strength suppression rather than global desaturation. This preserves black hair and
clothing while avoiding amplification of JPEG/chroma speckles.

Highlight compression is eligible only where bright information exists; white-clipped pixels are
not described as recovered. `AdvancedPhotometricAnalyzer` records veiling-glare evidence but
Milestone 5 does not perform object removal, inpainting, or source-light removal. Legitimate bright
sky, water reflection, rim light, and high-key evidence reduce correction strength.

Device validation found zero saturation-anomaly samples in the four retained cases. New highlight
and shadow clipping remained within the regional quality validator's limits.

