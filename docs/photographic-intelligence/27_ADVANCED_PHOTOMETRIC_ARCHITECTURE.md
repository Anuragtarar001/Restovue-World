# Advanced Photometric Architecture (Milestone 5)

`AdvancedPhotometricAnalyzer` is Android-independent and consumes the bounded analysis raster,
Milestone 2 `PhotometricEvidence`, and diagnoses. It measures atmosphere-related evidence without
claiming semantic sky, fog, or water detection. The versioned result records haze evidence,
atmospheric-intent risk, glare evidence, recoverable-highlight confidence, shadow-recovery
confidence, local-contrast deficit, detail confidence, noise constraint, warm-light evidence,
high/low-key evidence, dark-channel mean, and bright-area fraction.

The production flow is:

`decode -> global/photometric analysis -> advanced evidence -> bundled semantic inference ->
regional/global plans -> bounded render search -> perceptual/quality validation -> preview/export`.

The same immutable production session and selected plan are reused for export. The analyzer uses
representative sampling (maximum 131,072 samples), linear-light luminance, and transparent-pixel
exclusion. Scores remain engineering hypotheses pending a larger real-camera calibration set.

