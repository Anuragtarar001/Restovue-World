# Samsung SM-S926B owner acceptance plan

Use copies or non-sensitive sample images. Keep the phone offline for the offline check. Record Android version, source dimensions, output dimensions, elapsed time and any crash/ANR.

1. Launch: cold-start twice; expect home logo, no editor-only toolbar, no crash.
2. Branding: verify launcher R, full logo, app name and clickable developer website.
3. Selection: open JPEG, transparent PNG, WebP and HEIC input through Photo Picker; deny/cancel once.
4. Geometry: test each ratio, free crop, custom dimensions, flips, 90/180 rotation, fine rotation and perspective; export and verify dimensions.
5. Adjustments/filters/details: drag rapidly; latest value must win without queue buildup; compare preview/export.
6. Retouch: heal, clone, smooth, dodge/burn and local light at Fit, Fill and 3× zoom.
7. Liquify: test all seven modes on a grid image at Fit, Fill, zoom and pan; exported deformation must match.
8. Background: erase/restore edges, transparent PNG, solid, gradient and imported replacement; check alpha in another viewer.
9. Creative: add Unicode text, emoji, every shape, PNG sticker and each frame; reorder/delete and export.
10. Collage: use 2, 4 and 9 images of mixed orientation; verify spacing and requested output size. Note missing per-cell manipulation.
11. Batch: use three copied images plus one intentionally invalid URI/file; verify progress, continuation, names, metadata option and exact counts.
12. Compression: request 120 KB and 500 KB JPEGs; confirm actual file sizes do not exceed targets.
13. Passport: verify editable 35×45 mm at 300 DPI and 4×6 sheet copy layout; do not treat as acceptance guarantee.
14. Document/signature: test bright, shadowed and lined paper; ensure text/strokes are not lost; verify signature alpha.
15. HDR: use 3 aligned bracketed exposures; compare Natural and Dramatic and check alignment artifacts.
16. Panorama: use 3 ordered horizontal images; then insufficient-overlap and repeated-texture sets; failures must be explicit.
17. Export: JPEG/PNG/WebP, metadata on/off, share sheet, cancellation and low-storage behavior.
18. History: perform 30 mixed edits, undo to original, redo, branch after undo and confirm redo clears.
19. Comparison/full screen: hold original, split divider, Fit/Fill, enter/exit full screen; Export/Back must remain accessible.
20. Lifecycle: rotate, background/foreground, return from Photo Picker/SAF, and force process removal. Record any lost state.
21. Large files: try representative 12 MP, 24 MP and 50 MP images plus an extreme panorama; expect bounded processing or a clear size error, never silent downscaling.
22. Offline: disable Wi-Fi/mobile data; editing and export must still work. The developer link may require the external browser/network.
23. Accessibility: test TalkBack labels, large font, focus order, sliders, contrast and 48 dp touch targets.
24. Reopen exported outputs in Gallery and verify orientation, alpha, dimensions, format signature and visual alignment.
25. Repeat open/edit/export cycles ten times and inspect for rising memory, crashes or stale previews.
