# Milestone 6.2 Before/After UI

## Implemented behavior

`SmartMagicScreen` now renders the selected processed preview as the base layer and the real original preview as a second, identically transformed layer clipped to the left of a vertical divider.

- Divider fraction `0.0`: original clip width is zero; the canvas is entirely AFTER.
- Divider fraction `0.5`: equal BEFORE/AFTER split.
- Divider fraction `1.0`: original covers the canvas; it is entirely BEFORE.
- The divider has a two-pixel line and a 48 dp circular drag target with left/right chevrons.
- BEFORE is labelled on the left and AFTER on the right.
- Compare is an explicit toggle. Exiting comparison shows the complete processed preview.
- Both layers share the same `ContentScale.Fit`, scale, translation, transform origin, and viewport.
- Pinch zoom, pan, double-tap zoom/reset, and divider position survive mode recomposition. Values are saveable across activity recreation for the same source generation.

Divider movement changes only Compose clipping state. It does not call the ViewModel, decoder, analyzer, optimizer, or renderer. Save continues to use the selected cached production render; the comparison overlay cannot be exported.

## Gesture and accessibility behavior

The movable handle owns horizontal drag gestures while the surrounding canvas owns transform gestures. The divider exposes an accessibility progress action from 0 to 1 and a state description such as `50 percent before`.

On-device `SmartMagicScreenTest` verified 0%, 50%, and 100%, double-tap zoom, all three mode controls, and the real screen's comparison semantics on Samsung SM-S926B.

## Known limitation

The existing Compose transformable API is deprecated in favor of its centroid-aware replacement. It remains functional and lint reports no error; migration is a future cleanup rather than a Milestone 6.2 correctness blocker.
