# Milestone 6 research/model comparison

No model, network API, dataset, or new runtime was added.

## Findings

- [Adobe Lightroom Auto Tone](https://developer.adobe.com/firefly-services/docs/lightroom/guides/auto-tone/) is content-aware and adjusts a group of tonal/color controls, but the documented Firefly service is network-hosted and conflicts with Restovue's offline requirement. It is a behavioral reference only.
- [MIT-Adobe FiveK](https://people.csail.mit.edu/vladb/photoadjust/) demonstrates expert-retouched targets and global/local photographic adjustment research. Dataset image rights and redistribution constraints make it unsuitable for silently bundling as app test content.
- [DeepLPF](https://github.com/sjmoran/deeplpf-image-enhancement) demonstrates interpretable global and local parametric filters and is useful architecture research. Its pretrained weights, dataset provenance, Android latency and commercial distribution need separate diligence; it was not integrated.
- [Image-Adaptive 3D LUT](https://github.com/HuiZeng/Image-Adaptive-3DLUT) supports the value of fast weighted LUT rendering, but its old PyTorch/CUDA research implementation is not a drop-in Android production component. Checkpoint/data rights and mobile benchmarks remain unresolved.
- [Unsplash datasets](https://github.com/unsplash/datasets) are research metadata/resources with terms that must be reviewed per asset. They were not copied into this repository.

## Decision

The production path remains deterministic: bounded measurements, typed correction plans, LUT-friendly tone math, real person masks, rendered-candidate evaluation and explicit artifact rejection. A learned model should be reconsidered only after licensing, offline size, mid-range latency and comparative image-quality evidence are available.

