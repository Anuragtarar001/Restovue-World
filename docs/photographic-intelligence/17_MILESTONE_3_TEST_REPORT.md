# Milestone 3 test report

## Automated coverage

Domain tests cover soft-mask construction/composition/inversion, bounded geometry, bilinear sampling, mask rejection, EXIF transforms, letterbox round-trip/padding, face/person association, soft-mask regional photometry, and the rule that a person is not automatically P1.

Android tests execute both bundled models, not mocks. The public-domain U.S.-government portrait fixture is instrumentation-only and has provenance in `TEST_ASSET_LICENSE.md`. Assertions cover a real face, a non-empty soft person mask, mask bounds, supported classes, regional statistics, warm repeat inference, and a no-person uniform raster that must not fabricate a face and whose mask is rejected.

Final results: 148 JVM unit tests across 31 suites, 0 failures/errors; 13 connected Android tests on SM-S926B, 0 failures/errors (four are new semantic tests); clean debug APK/AAB assembly successful; Android lint 0 errors, 33 warnings, 1 hint. The new Android tests also cover missing-model failure and cancellation before inference.

## Not tested / limitations

The requested 20-category private/real-photo matrix was not available. Profile, occlusion, groups, distant people, snow, waterfall, night, and printed-face cases therefore remain **NOT TESTED** on real photographs. Synthetic tests validate math, not photographic accuracy. No manually labeled IoU/boundary benchmark exists yet.
