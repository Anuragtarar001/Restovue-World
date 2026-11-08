# Detail and Local Contrast

`LOCAL_CONTRAST` uses a dimension-scaled, 25-sample mid-frequency neighborhood. A fixed 5x5 kernel
was rejected because it measures texture rather than photographic tonal separation on modern phone
images. The radius is `min(width,height)/48`, bounded to 2–24 pixels. Linear luminance detail is
bounded and highlight-gated before soft-mask compositing.

`DETAIL` uses the same neighborhood with an edge threshold and highlight/noise guards. It is not
deblurring or super-resolution. Face/person masks, uncertain boundaries, smooth bright regions,
and the quality validator constrain both operations. No new denoising model or network dependency
was introduced.

On the cream fixture, Natural remained negligible; Enhanced and Extreme produced measurable,
background-only separation without visible subject change or obvious haloing in the contact sheet.

