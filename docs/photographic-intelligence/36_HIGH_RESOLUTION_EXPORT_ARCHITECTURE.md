# High-Resolution Export Architecture

The existing safe policy rejects a 48 MP full-frame decode/render when heap headroom is insufficient.
That check remains unchanged. `TiledExportPlanner` now proves complete, non-overlapping core coverage
for 8192×6144 with bounded 48-pixel decode overlap; tests verify total area and maximum 1120×1120
decode tiles for 1024-pixel cores.

A complete Android path still requires:

1. a region decoder that preserves EXIF/oriented coordinates;
2. shared reduced-resolution global analysis and immutable plan;
3. operator-specific halo overlap (dehaze/local contrast need neighbors);
4. consistent atmospheric/tone parameters across every tile;
5. a streaming encoder/container that does not rebuild a full Bitmap;
6. seam and reopen verification on a real 48 MP source.

Android `ImageDecoder` plus `Bitmap.compress` currently materializes full frames, so geometry alone
does not make export memory-safe. Status: BLOCKED/PARTIAL. The app continues to fail clearly rather
than silently downscale or increase heap limits.

