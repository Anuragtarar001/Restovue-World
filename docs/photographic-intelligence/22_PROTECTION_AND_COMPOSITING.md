# Protection and Compositing

Protection decisions from Milestone 2 are authoritative. A matching `PROTECT` disposition vetoes
the regional operation. Invalid/rejected masks, stale source identity, stale transformation version,
or unsupported parameters preserve the input and return an explicit status.

Face boxes are conservative elliptical constraints, not skin masks. Positive person correction is
reduced where aggregate person highlights reduce headroom. With multiple faces, all subject
operations receive a 0.78 group guard; no instance masks are fabricated.

For source linear RGB `I`, corrected RGB `C`, and refined confidence `M`, compositing is
`I + (C-I)*M`. Background uses `1-M` plus uncertainty attenuation. Protected interior leakage,
boundary change, clipping, shadow chroma and shadow high-frequency amplification are measured.
One retry at 60% is allowed. A second failure returns the exact original.

