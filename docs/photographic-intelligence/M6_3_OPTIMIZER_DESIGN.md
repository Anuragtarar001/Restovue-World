# Milestone 6.3 optimizer design

`AdaptivePhotographicOptimizer` performs deterministic bounded candidate generation from actual photometric and semantic evidence. It does not select a fixed preset.

Relevant measurements include robust linear-luminance percentiles, clipping, shadow/highlight occupancy, local contrast, chroma and saturation distributions, noise risk, atmospheric/low-key intent, subject/background contrast, face luminance, and mask reliability.

Candidate families include baseline, tone recovery, color/detail, and balanced combinations. Device budgets bound the number of candidates. Every rendered candidate is measured by the existing perceptual impact and quality validators. Rejection reasons are retained.

Milestone 6.3 adds evidence-derived brightness, contrast, whites, and blacks and always runs the global optimizer. For a protected person, the global plan cannot introduce negative exposure or brightness, positive global contrast is removed, and black darkening is suppressed. The background complement may still receive contrast. Regional residuals are calculated only after global parameters are known.

Mode envelopes are independently bounded:

- Natural: lowest tonal and regional limits; strongest similarity/intent preference.
- Enhanced: greater recoverable midtone and subject opportunity.
- Extreme: strongest defensible correction, with the same hard clipping, chroma, face, and boundary validators.

Multiple faces are not forced to the same luminance. The face-guided gamma operation weights darker detected faces more strongly relative to the brightest detected face, within mode-specific caps. This is not a complexion classifier and does not infer skin pixels.

