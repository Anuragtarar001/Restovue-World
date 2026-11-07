# Offline semantic segmentation research

Status: architecture research only; no dependency or model was added.

## Capability reality

No single reviewed Android API supplies reliable masks for people, face/skin/hair/clothing, sky, vegetation, water, roads, vehicles, buildings and light sources. A practical first implementation should combine a small number of complementary components, retain confidence maps, and make every regional recipe fall back to global conservative processing when confidence or boundary quality is poor.

| Candidate | Actual output/classes | Delivery, size and runtime | License/status | Suitability and limitations |
|---|---|---|---|---|
| Bundled ML Kit face detection | Face boxes; optional landmarks, contours and classification. It detects faces, not identity, skin/hair/clothing masks. | Bundled artifact adds about 6.9 MB per official guide; API 23+; CPU/on-device. Unbundled Play Services variant is not acceptable for guaranteed first-run offline behavior. | Google ML Kit SDK terms; exact transitive/model terms require release legal review. | Strong first-stage face localization; contours can seed soft protection masks but are not full head/person masks. Small/distant/occluded/profile faces remain difficult. |
| ML Kit Selfie Segmentation | One confidence mask: human foreground vs background; supports single/multiple people, full/half body. | Bundled, about 4.5 MB; raw model mask commonly 256×256; documented Pixel 4 latency 25–65 ms depending on input; beta. | SDK terms; underlying published lightweight model card is Apache-2.0, but shipping artifact terms must be verified. | Best low-integration person mask candidate. It cannot distinguish individuals, skin, hair or clothing, and fine hair/edges can shift or disappear. |
| MediaPipe Tasks Image Segmenter + custom TFLite | Category or per-class confidence masks dictated entirely by bundled model metadata. | Android-compatible; CPU and supported acceleration depend on task/runtime/model. Model size is model-specific and must be measured from an exact artifact. | MediaPipe framework Apache-2.0; each model/dataset requires separate license verification. | Good runtime abstraction. It does not provide desired classes by itself. |
| DeepLabV3 MobileNet (Pascal VOC example) | 20 foreground VOC classes plus background: includes person, car/bus/train/bike, animals and objects; **does not separate sky, vegetation, water, road or building from background**. | TFLite example exists; exact selected artifact size/latency must be benchmarked, not assumed. | TensorFlow/model artifact license must be recorded from selected download; VOC dataset/model provenance review required. | Useful proof of pipeline, poor class coverage for photographic regional correction. Not recommended as final general model. |
| LR-ASPP MobileNetV3 trained on Cityscapes | Typical 19 urban classes: road, sidewalk, building, wall/fence/pole, traffic light/sign, vegetation, terrain, sky, person/rider, car/truck/bus/train/motorcycle/bicycle. | Mobile-oriented architecture. Exact Android artifact, quantization, size and Snapdragon/older-device latency are not yet selected/measured. | Model/code and Cityscapes data terms must be checked; Cityscapes dataset has non-commercial restrictions, so do not ship a derived weight without legal clearance. | Valuable class set for urban scenes but misses water, snow, hair/clothing, food/product and many natural categories. Licensing is a blocker until resolved. |
| ADE20K-trained lightweight model (e.g. mobile SegFormer/TopFormer/PP-MobileSeg candidate) | Broad ~150-class scene parsing can include sky, vegetation, water, road, building and objects depending on label map. | Potentially the best single general parser, but no reviewed officially packaged Android artifact with confirmed commercial license, model size and benchmark is approved yet. | Must audit model weights, code and ADE20K terms separately. | Research candidate only. Broad labels trade speed/memory and boundary quality; hair/clothing and light sources still need specialized handling. |
| ONNX Runtime Mobile + NNAPI | Runtime, not a model. Class support follows the chosen ONNX model. | Prebuilt Android mobile package can use NNAPI (Android 8.1+, Android 9+ recommended); package/ABI size must be measured. | MIT runtime; model license separate. | Viable if an ONNX model materially outperforms LiteRT. Adds another native runtime and maintenance surface; not first choice. |
| LiteRT/TFLite runtime | Runtime, not a model; CPU/XNNPACK and compatible delegates. | Most natural fit for bundled mobile models; delegate compatibility varies by operators/device. | Apache-2.0 runtime; model license separate. | Recommended runtime direction if MediaPipe Tasks abstraction is insufficient. Benchmark CPU first; GPU setup/transfer can lose on small analysis images. |
| ML Kit object detector | Object boxes and coarse categories, not semantic masks. | Bundled on-device; exact artifact impact requires dependency report. | SDK terms. | Could support salience/scene graph, but coarse classification does not cover the requested taxonomy and cannot replace segmentation. |

## Recommended staged strategy

1. **Milestone 2 baseline:** bundled face detection for face boxes/contours plus bundled person/selfie segmentation for human confidence. This directly supports portrait priority/protection while keeping inference count small. Use analysis images, never full-resolution model input.
2. Define an Android-free `SemanticAnalyzer` port returning versioned normalized confidence masks, model ID/version, source dimensions and per-class confidence. Android model/runtime stays in `data`.
3. Derive skin/hair/clothing only when supported by a separately licensed model or strongly bounded sub-classifier inside a confident person/head region. Do not relabel a person mask as skin/hair segmentation.
4. Evaluate one broad scene parser on the exact target devices. Require classes at least person, sky, vegetation, water, road, vehicle and building; record model SHA-256, license, quantization, input shape, mask resolution, latency, native heap and APK/AAB impact.
5. Bright-light sources should initially use photometric connected-region analysis (bright core + bloom) rather than a semantic model. Snow/fog/fire/water still require scene/color evidence because class masks alone do not establish intended treatment.
6. Feather/refine masks using confidence-aware guided upsampling. Reject masks with low mean confidence, implausible area, fragmentation or high boundary disagreement. All rejected masks route to conservative global recipes.

## Acceptance benchmark before adoption

- Test on Samsung SM-S926B plus one lower-memory API-28+ device; use portrait, group, backlit, fine hair, snow, water, forest, urban/night, occlusion and edge-subject sets.
- Report per-class IoU/boundary F-score where labels exist, inference latency (cold/warm), Java/native peak memory, model and ABI size, and correction-quality impact.
- Verify offline behavior on a fresh install with airplane mode. Any Play-Services-downloaded model fails the product's guaranteed-offline requirement.
- No model has been approved in Milestone 1. Hair/clothing/sky/vegetation/water semantic support remains **missing**, not simulated.

## Primary references

- MediaPipe Image Segmenter API: https://ai.google.dev/edge/api/mediapipe/python/mp/tasks/vision/ImageSegmenter
- ML Kit face detection: https://developers.google.com/ml-kit/vision/face-detection/android
- ML Kit selfie segmentation: https://developers.google.com/ml-kit/vision/selfie-segmentation/android
- Selfie model card: https://developers.google.com/static/ml-kit/images/vision/selfie-segmentation/selfie-model-card.pdf
- ONNX Runtime NNAPI provider: https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider
