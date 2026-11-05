# RestoreX engineering rules

## Product constraints

- RestoreX is a 100% offline Android app. Never add `INTERNET`, network state, or cloud permissions.
- Do not add cloud SDKs, analytics, ad SDKs, remote configuration, telemetry, HTTP clients, or API calls.
- Image operations must be real and deterministic. Do not ship placeholder or mock processing behind visible controls.
- Imported photos stay on device. Use the system Photo Picker and Storage Access Framework destinations.

## Architecture

- Package root is `com.restorex.app`; `minSdk` is 28.
- Keep domain models, history, engine ports, and repository ports Android-free.
- Put Android codecs and bitmap processing in `data`; UI state and ViewModels in `presentation`; Compose in the app/UI layer.
- Edits are non-destructive `EditState` values. Never mutate or overwrite the source URI.
- Add presets through `FilterCatalog` with stable IDs. The registry is designed for at most 50 curated presets.
- NCNN integrations implement `OfflineRestorationEngine`, bundle models locally, and must expose capability truthfully. Never simulate restoration.
- Use native C++/OpenCV only for a measured need that Android/Kotlin cannot meet cleanly. Include tests and ABI-size notes when introducing native code.

## Delivery

- Pin stable dependency versions; do not use `+`, snapshots, alphas, or betas in production dependencies.
- Do not commit `local.properties`, signing keys, build outputs, IDE state, or source photos.
- Run `./gradlew.bat testDebugUnitTest assembleDebug lintDebug` before claiming a build succeeds.
- Run connected tests when a compatible emulator/device is available; report honestly when none is available.
- Preserve accessibility labels and verify new controls have meaningful content descriptions or text.
