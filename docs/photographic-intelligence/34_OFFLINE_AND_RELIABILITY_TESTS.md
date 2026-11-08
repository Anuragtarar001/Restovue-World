# Offline and Reliability Tests

Code/dependency audit confirms:

- No `INTERNET` or `ACCESS_NETWORK_STATE` permission.
- No cloud, HTTP, analytics, ads, telemetry, or remote model dependency.
- Face and person TFLite assets are bundled under `app/src/main/assets/models/`.
- Photo selection remains Android Photo Picker based; export uses MediaStore.

Reliability coverage includes cancellation, deterministic rendering, stale geometry rejection, source identity checking, session fingerprint determinism, invalid-mask rejection, no-person global fallback, mode caching, exact selected-plan export, and explicit memory preflight.

The existing installed app was not uninstalled or data-cleared. Tests used replacement installation and cleaned their temporary MediaStore entry. Consequently a true fresh-install airplane-mode test was not performed because it would require removing existing app data and changing device connectivity. Offline first-run remains PARTIAL, supported by bundled assets and permission/dependency audit but not destructive device proof.

Process death, low-storage injection, HEIC production export, repeated exports under thermal load, and deliberate model-initialization failure were not physically exercised in this milestone.
