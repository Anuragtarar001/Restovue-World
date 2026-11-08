# Quality Search and Rejection Analysis

`RegionalCorrectionRenderer` evaluates a bounded deterministic set of strengths: 1.00, 0.70, 0.45, and 0.30. A preview-selected strength can be locked for export, in which case exactly one candidate is evaluated.

Each candidate records strength, runtime, failure reasons, and perceptual impact. Search stops at the strongest passing candidate. There is no unbounded loop. Failed candidates preserve the source.

Validators cover finite output, new clipping, protected interiors, boundary discontinuity, shadow noise, normalized shadow-saturation amplification, and output gamut. The former absolute RGB-chroma metric falsely interpreted correct linear exposure scaling as chroma amplification. It is now normalized by channel peak, so exposure that preserves saturation passes while actual saturation growth remains measurable.

Outcomes are mapped explicitly to `APPLIED`, `REDUCED_FOR_QUALITY`, `NO_CORRECTION_NEEDED`, `QUANTIZED_NO_OP`, `PERCEPTUALLY_NEGLIGIBLE`, `REJECTED_FOR_QUALITY`, or `UNSUPPORTED`.

The final three-fixture run accepted the requested full strength for every backlit/group mode. The cream portrait remained no-op/negligible. Search still exists and is covered by unit tests; no mode bypasses protection.
