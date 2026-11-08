# Regional Renderer Architecture

Milestone 4 adds an Android-independent execution path under
`domain/photointelligence`. `RegionalPlanBuilder` translates the existing Milestone 2/3
semantic supplement into a versioned `RegionalCorrectionPlan`; it does not invent a second
recipe database. `RegionalCorrectionRenderer` validates source identity, transformation
version, mask identity, mask quality, parameter units and protection decisions before touching
pixels.

The execution sequence is: validate plan -> refine mask -> resolve protection vetoes -> execute
typed operations -> composite in linear sRGB -> validate local quality -> accept, retry once at
60%, or return the original. A failed regional operation is never converted into a global one.

Supported typed operators are exposure (EV), shadows, highlights, contrast, vibrance and
white-balance gain delta. Current evidence-driven planning emits subject exposure/shadows and
background contrast/vibrance. Other operator types are implemented for controlled tests but are
not emitted without a matching validated decision rule.

The same pure renderer accepts any `RasterImage`, so preview and export callers can use identical
mathematics at different resolutions. Production Smart Magic has not yet been switched to this
path; doing so without persisting the semantic session and transform version would be unsafe.

