# Model selection and licensing

## Decision

Milestone 3 uses the telemetry-free direct interpreter `org.tensorflow:tensorflow-lite:2.17.0`, pinned from Maven. TensorFlow Lite is Apache-2.0. The two local assets are official MediaPipe float16 models and are listed in `app/src/main/assets/models/model_manifest.json` with source URLs, byte counts, and SHA-256 hashes.

| Capability | Asset | Bytes | SHA-256 | Output |
|---|---|---:|---|---|
| Face detection | BlazeFace short range v1 | 229,746 | `b4578f...0152f` | box, model score, six keypoints |
| Human foreground | Selfie Segmenter v1 | 249,537 | `191ac9...6658b` | one soft human confidence mask |

The face model is not recognition and exposes no identity, demographic, age, or emotion inference. The selfie model is not instance segmentation: multiple similarly scaled people may be represented by one combined mask. It does not expose separate skin, hair, or clothing classes.

## Alternatives rejected

Bundled ML Kit face detection (`face-detection:16.1.7`) is stable, but ML Kit's terms permit service telemetry. ML Kit Selfie Segmentation is still beta (`16.0.0-beta6`), conflicting with the project's stable-dependency rule. Downloaded Play Services models were rejected because first-run offline operation is mandatory. MediaPipe/LiteRT broad segmentation was not packaged because Milestone 3 approval covers people and faces only.

## Runtime and redistribution

The MediaPipe Tasks Android runtime was evaluated and rejected: every available stable version declares Google DataTransport and the official privacy notice says Tasks sends utilization/performance metrics with no opt-out. Removing the transport makes task initialization fail. Restovue therefore directly executes the same verified `.tflite` assets with `libtensorflowlite_jni.so`; no downloader, metrics transport, Firebase component, or network permission is present.

TensorFlow Lite and both model cards use Apache-2.0 terms. Direct BlazeFace anchor decoding and non-maximum suppression are implemented in Restovue and verified against the on-device portrait overlay.

## Known model limitations

BlazeFace short range favors prominent, relatively frontal faces. Small, profile, occluded, edge, backlit, and low-light faces can be missed. Selfie Segmenter is designed for selfie/video-conference scale; thin limbs, hair boundaries, severe occlusion, noise, and people at different scales can be imperfect. Scores are model scores, not calibrated probabilities.

## Broad segmentation research

Future sky/vegetation/water/road/vehicle/building support requires a separately approved class map, license review, APK/memory benchmark, and real-photo validation. `BACKGROUND` is deliberately not relabeled as any of those classes. Cityscapes-trained weights must not be used commercially until model and dataset rights are resolved.
