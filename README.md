# Restovue : Photo Editor

Restovue is an offline-first native Android photo editor by NextGen TechEdge Solutions. It supports non-destructive tonal, color, detail, crop, rotation, filter, comparison, and export workflows through Android Photo Picker and the Storage Access Framework.

No internet permission, network client, cloud SDK, analytics, or remote model is included.

## Features

- Photo Picker import without broad storage permission
- Center crop presets: original, 1:1, 4:3, and 16:9
- 90-degree rotation
- Brightness, contrast, exposure, and saturation controls
- Non-destructive state history with undo and redo
- Press-and-hold original view and draggable split before/after comparison
- Fit preview with pinch zoom, pan, and double-tap zoom
- Premium dark editor UI with contextual tool panels
- Filter intensity and generated on-device filter thumbnails
- Fit and Fill canvas modes with synchronized zoom, pan, and before/after coordinates
- True Full Screen canvas mode with a persistent floating toolbar, Export, comparison, history, and exit controls
- 50 curated filters across Natural, Portrait, Cinematic, Film, B&amp;W, Landscape, and Creative categories
- On-demand 80 × 96 filter previews center-cropped from the current photo
- Private `.cube` 3D LUT import with trilinear interpolation
- Deterministic grain, fade, film tone, roll-off, shadow tint, procedural light leak, and chromatic aberration
- Luminance-oriented Structure, Clarity, Texture, and conservative dark-channel Dehaze
- Luminance unsharp masking with amount, original-pixel radius, threshold, edge masking, and a preview-only mask view
- Independent edge-preserving luminance and chroma noise reduction
- Viewport-derived 1.5 MP settled previews and a separate 720 px interactive slider path
- Byte-budgeted preview/filter caches, cooperative latest-state-wins rendering, memory-pressure cache release, and safe export preflight
- Manual Heal, source-assisted Heal, Clone, Smooth, Dodge, Burn, brush lighting, radial lighting, and linear lighting
- Sparse tiled brush masks with hardness, opacity, pressure, feathering, erasing, mask overlays, and compact undo/redo history
- Versioned local-edit serialization using source-normalized coordinates that remain aligned through crop and rotation
- JPEG (95 quality) and lossless PNG export
- Extensible filter registry capped at 50 curated presets
- Clean boundary for future bundled, offline NCNN restoration engines

Preview decoding is bounded to 2048 px on the longest edge. Export re-decodes from the original URI at its original dimensions and applies the edit state off the main thread. Source content is never modified.

## Architecture

The single app module is split by responsibility:

- `domain`: pure edit models, draft/commit history, stable editor coordinates, raster representation, engine/repository ports, and use cases
- `data`: Android `ImageDecoder`, bitmap/`ColorMatrix` renderer, exporter, and filter catalog
- `presentation`: lifecycle ViewModel, immutable UI state, cancellable preview scheduling, and a small preview cache
- app/UI: dependency container, Compose Material 3 editor workspace, Activity Result contracts

OpenCV and C++ are not used in this slice: crop, rotate, and color transforms are fully supported by the platform bitmap pipeline. `OfflineRestorationEngine` is the integration boundary for a future bundled NCNN implementation.

The render order is source-normalized local retouching and selective lighting, geometry (rotation and crop), tonal and color adjustments plus interpolated preset parameters, optional 3D LUT and deterministic film effects, Dehaze, luminance/chroma denoising, Structure, Clarity, Texture, luminance sharpening, and final chromatic aberration. Preview and export share the same deterministic algorithms; scale-dependent detail radii are derived from each raster's scale relative to the original image.

The Details workspace separates Structure, Sharpening, and Noise Reduction controls. Slider drafts render continuously, while release creates one history entry. Category resets preserve unrelated edits, and resetting the whole image requires confirmation.

The editor uses two preview resolutions. Rapid slider drafts render from a 720 px working raster and are never inserted into the preview cache. Settled renders use the actual viewport, a 1.5 MP pixel budget, and optional bounded zoom refinement. Preview cache retention is capped at 14 MiB and filter thumbnails at 8 MiB. Full-resolution export owns and reuses its decoded pixel buffer, performs a heap-aware preflight before allocation, and asks for explicit smaller dimensions when a photograph is unsafe for the current device heap.

GPU acceleration was evaluated for Phase 5 but not introduced: the project had no reusable OpenGL context, no connected device was available for defensible GPU/CPU profiling, and a second renderer would duplicate textures during the memory-repair phase. The bounded CPU path remains the single preview/export reference implementation and avoids CPU/GPU transfer overhead.

## Local toolchain baseline

- Android SDK Platform 36 (Android 16), target/compile SDK 36
- Android SDK Build Tools 36.1.0
- Android Gradle Plugin 9.5.0
- Gradle 9.6.0 wrapper
- Kotlin / Compose compiler plugin 2.4.21
- Android Studio bundled JDK 25 (project bytecode target 17)
- NDK r30 and CMake 4.1.2 are installed but intentionally unused

## Build and test

Set `JAVA_HOME` to Android Studio's bundled JDK (or another compatible JDK 17+) and ensure `ANDROID_HOME` points at the SDK, then run on Windows:

```powershell
./gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

For instrumentation tests, start a compatible emulator or connect an API 28+ device:

```powershell
./gradlew.bat connectedDebugAndroidTest
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## License and branding

Restovue's original source code is available under the [Apache License 2.0](LICENSE).
Bundled models and third-party materials retain their own terms and notices. See the
[model manifest](app/src/main/assets/models/model_manifest.json) and
[test asset notices](app/src/androidTest/assets/TEST_ASSET_LICENSE.md).

NextGen TechEdge Solutions reserves the **Restovue** and **Restovue World** names,
including the **Restovue-World** repository name, and the project logos and branding
artwork. The source code license excludes the project logos and branding artwork,
including app icons and graphics that incorporate those marks. Forks should use
their own names and branding. Factual attribution is allowed as described in
Section 6 of the license.
