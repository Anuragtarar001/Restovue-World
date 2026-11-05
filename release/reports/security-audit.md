# Security and privacy audit

## Findings

- Only `MainActivity` is exported, solely for the launcher intent.
- The merged manifest also contains AndroidX ProfileInstaller's standard exported receiver, protected by the platform signature-level `android.permission.DUMP` permission; its initialization provider is not exported.
- No permissions are declared; in particular there is no network or broad-storage permission.
- No services, receivers, providers, WebView, command execution, archive extraction or native processing integration exists.
- Photo input uses system Photo Picker content URIs; output uses user-selected SAF URIs.
- Streams and file descriptors inspected in codecs/export use scoped `use` blocks.
- Batch filenames are restricted by UI to letters, digits, `_` and `-`; DocumentsProvider creates outputs without source overwrites.
- Decoding and export include dimension/pixel limits, bounded caches and background cancellation.
- Release signing secrets are read only from environment variables and are never committed or printed.
- Backup is disabled and data extraction rules are supplied.

## Residual risks

- Malformed-image resilience ultimately depends partly on Android platform decoders.
- Whole-image algorithms can reject large inputs at the safe memory boundary; tiled export is not complete.
- Imported sticker/background URIs may become unavailable after provider permission expiry because persistent access is not guaranteed for Photo Picker results.
- Full process-death restoration of an active edit session is not implemented.
- A final dependency/SBOM scan of the signed artifact should be performed in the owner's release environment.

No critical security defect or source-overwrite path was identified in this source audit. This is not a penetration test.
