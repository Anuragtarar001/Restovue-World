# Device performance report

Device: Samsung SM-S926B, connected Gradle target `SM-S926B - 17`. Portrait source after bounded test decode: 672x839 pixels. Model inputs are 128x128 and 256x256; person output is a 256x256 compact confidence mask.

Measured focused run:

| Measurement | Result |
|---|---:|
| Cold combined face + person analysis | 362 ms |
| Warm combined analysis | 46 ms |
| Faces | 1 |
| Person weighted area | 0.5068 |
| Mean foreground model score | 0.9914 |
| Boundary uncertainty | 0.0196 |
| Components | 1 |
| Managed-heap delta during two analyses | 22,838,336 bytes |
| Native-heap delta during two analyses | 17,874,384 bytes |

Values come from instrumentation logcat and are single-run observations, not performance guarantees. CPU utilization, battery/thermal behavior, 12 MP/48 MP repeated cycles, and a lower-tier device were not measured. The adapter bounds inference to 1024 pixels on the longest side, so model input does not grow to 48 MP.

The universal debug APK grew from 32,701,857 bytes to 48,590,409 bytes after the direct TensorFlow Lite runtime and models. The signed debug AAB is 18,728,759 bytes. The model files themselves total 479,283 bytes.

- APK SHA-256: `ae5ad54c5335a5209add5187c847913f5fcd123a32ceb3b30c96e5b7b4fb044f`
- AAB SHA-256: `398f1faf4caa5da0a1c1022eee86426b325d3422ef1319f0c1aed4ef3877b69f`

Connected tests prove local model assets execute after installation and the final manifest has no network permission. A destructive fresh-install airplane-mode test was not performed because it would erase the owner's existing debug-app data; this acceptance item remains PARTIAL.
