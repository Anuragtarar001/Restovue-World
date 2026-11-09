# Smart Magic fast export — beta 11

## Problem

A 12 MP Smart Magic export could spend roughly 149 seconds in
`RegionalCorrectionRenderer`. That renderer evaluated every full-resolution pixel on one thread,
could try four strengths, and could recursively retry person-only and face-only plans. Export could
then render the global result again. JPEG encoding was not the bottleneck.

## Implemented path

1. Preview continues to run the existing deterministic global and regional correction plus its
   quality checks.
2. When a regional preview is accepted, the coordinator fits a low-resolution bilateral grid of
   3x4 sRGB affine transforms from the global preview to the approved final preview.
3. Each grid cell is fitted from the 22 sufficient statistics used by Bilateral Guided Upsampling:
   ten values for the symmetric 4x4 Gram matrix and twelve RGB cross-products.
4. Statistics are blurred with the existing `[1 4 6 4 1] / 16` bilateral-grid kernel before the
   cells are solved.
5. A coarse whole-image affine transform is used as the regularization prior. Sparse cells approach
   that correction instead of zero/black.
6. Export renders the existing photographic plan once using its parallel tiles. The cached affine
   finish is sliced and applied immediately before the renderer's single dithered quantization.
7. Full-resolution regional candidate search is no longer used by export. If no preview fit exists,
   export uses the safe global plan.

The grid contains only coefficients and retains no photograph pixels.

## S24+ measurements (debug build)

| Test | Result |
| --- | ---: |
| Preview-grid fit, 800x600 | 411 ms |
| 12 MP render with cached regional finish | 5,058 ms |
| 12 MP end-to-end gallery export | 5,006 ms |
| 12 MP JPEG encoding in photographic profile | 214 ms |
| Previous standalone 12 MP regional stage | 149,152 ms |

The two 5-second tests use different fixtures; they demonstrate that both the regional-integrated
renderer and full gallery path are inside the 15-second target on the attached Galaxy S24+.

## Validation

- Identity/sparse-grid protection: no black-cell regression.
- Smooth regional correction reproduction.
- Distant-background mask-leakage bound.
- Preview-session/export integration.
- Smart Magic comparison divider reaches the extreme left and preserves before-left/after-right.
- `testDebugUnitTest`, `assembleDebug`, and `lintDebug` pass.
- Relevant connected Android tests pass on model `SM-S926B`.

Real photographs remain the final acceptance test, particularly face/background boundaries and
large same-luminance regions.
