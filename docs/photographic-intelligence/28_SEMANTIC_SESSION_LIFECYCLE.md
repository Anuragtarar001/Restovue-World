# Semantic Session Lifecycle

`SmartMagicProductionSession` is immutable and Android-free. It contains source identity and sampled SHA-256 fingerprint, source geometry, geometry version, global and v2 photometric evidence, diagnoses, semantic result, compact confidence mask, scene graph, regional evidence, subject decision, and all three mode plans. It retains no full-resolution source bitmap.

`SmartMagicViewModel` owns one active session and a small cache of rendered preview results. Every photo selection increments a generation token, cancels prior processing/export jobs, clears the preview cache, and creates a new session. Completion callbacks check both generation and session identity. Rapid mode changes cancel obsolete renders; cached completed modes switch immediately.

Export requires the exact source identity stored by the session. The coordinator rejects aspect-ratio/geometry mismatches. Crop, 90-degree rotation, or mirroring therefore cannot silently reuse a stale mask; a new session is required. Normalized mask coordinates allow resolution changes with the same oriented geometry.

Activity recreation does not serialize the model session in this milestone. The selected URI can be reprocessed, but in-flight work is cancelled with the ViewModel. Process-death restoration remains partial.
