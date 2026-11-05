# Release-candidate build report

Build date: 6 November 2026

## Identity

- Application ID: `com.restorex.app`
- Application label: Restovue : Photo Editor
- Version code: 9
- Version name: 0.9.0-rc1
- minSdk: 28
- targetSdk / compileSdk: 36 / 36
- Runtime permissions requested: no user-facing permissions; the merged manifest contains only AndroidX's app-signature dynamic-receiver permission.

## Quality gates

- `clean testDebugUnitTest assembleDebug lintDebug`: PASS
- Unit tests: 91 passed, 0 failed, 0 skipped
- `connectedDebugAndroidTest` on Samsung SM-S926B: 4 passed, 0 failed, 0 skipped
- `assembleRelease`: PASS with R8 and resource shrinking
- `bundleRelease`: PASS
- Debug lint: 0 errors, 30 warnings, 1 hint
- Release lint-vital: PASS

Lint warnings are non-blocking update suggestions, approved-branding icon-shape notices, API-26 adaptive-resource redundancy, KTX style suggestions and the intentionally retained API-28-compatible WebP path. Target API 36 satisfies the stated release target; no dependency was upgraded solely to silence a warning.

## Artifacts

| Artifact | Bytes | SHA-256 | Signing |
|---|---:|---|---|
| `restovue-0.9.0-rc1-debug.apk` | 32,259,430 | `63A7AF6B3BD2D7DD06FCAB9AB8F44E6F5E87683AF3143F21D41D708FE9BADF7C` | Android debug key; owner testing only |
| `restovue-0.9.0-rc1-release-unsigned.apk` | 3,561,957 | `FDB324229FDA0C62E2A90E903C162E5941901512FD0A90F36E9FC4AFDE8ECEF5` | Unsigned |
| `restovue-0.9.0-rc1-release-unsigned.aab` | 4,679,339 | `CA7194DC29584A5E685B5D3592A8D8955C3D452A24D73580C98A2E4B15AA46DE` | Unsigned; not upload-ready |

`apksigner` rejects the unsigned release APK and `jarsigner` reports the AAB as unsigned, as intended without owner credentials. The AAB contains arm64-v8a, armeabi-v7a, x86 and x86_64 variants of the AndroidX graphics path library.

## Device result

The 0.9.0-rc1 debug APK installed successfully on Samsung SM-S926B and cold-launched in 560 ms during this run. Actual home and About screenshots were captured from that build. This single debug observation is not a universal launch-performance guarantee.

## Release blocker

The unsigned AAB cannot be uploaded to Google Play. The owner must configure an upload key securely and rebuild. Phase 7.5 also retains the PARTIAL/FAIL items in `feature-completeness.md`; this is an owner-acceptance candidate, not a claim that all advanced features are complete.
