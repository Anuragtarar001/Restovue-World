# Preview / Export Parity

`RegionalCorrectionRenderer` is resolution-independent and deterministic. Calling it twice with the
same source, plan, mask and transform version is pixel-identical (covered by a JVM test). Normalized
mask sampling allows the same semantic session to be used at preview and source resolution.

Every plan carries source identity, transformation version and mask ID. A mismatch returns
`SKIPPED_STALE_SESSION` or `SKIPPED_INVALID_MASK`; stale results cannot silently apply.

Current limitation: the production `SmartMagicViewModel` and `SmartMagicExportManager` still use
the pre-existing global Smart Magic path. Milestone 4 provides and device-tests the shared regional
renderer, but does not replace production behavior until a persistent semantic session can be
passed atomically to both preview and export. Therefore application-level preview/export regional
parity is PARTIAL, not claimed complete.

