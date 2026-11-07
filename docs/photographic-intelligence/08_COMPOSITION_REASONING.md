# Composition reasoning in Milestone 2

`CompositionReasoningEngine` converts measured regions into a future-compatible `SceneGraph`. It deliberately does **not** identify people, cars, roads, skies, snow, vegetation, or waterfalls.

## Current evidence

4×4 regions can become `PHOTOMETRIC_SALIENCE_CANDIDATE` nodes using three weak cues: relative luminance (45%), edge density/focus proxy (35%), and position (20%). Confidence is capped at 0.65. Bright connected components are separate nodes. Overlap/adjacency relationships are recorded.

All nodes retain `UNRESOLVED` composition priority, `semanticClass=null`, and the graph sets `semanticPrimarySubjectId=null`. A bright, sharp, central candidate is not promoted to P1. This is the required boundary between salience and semantic subject identity.

## Bright-region groundwork

`BrightRegionAnalyzer` works on a bounded raster up to 256 pixels on the longest side. It uses a relative 90th-percentile threshold with a linear 0.72 floor, eight-connected components, core fraction, peak/mean luminance, boundary falloff, local dominance, compactness, and area. It classifies point-source, bloom, specular-candidate, diffuse-highlight, and broad-bright-surface photometric shapes. A broad bright field is not classified as a small point light.

These labels do not assert object identity. “Headlight”, “window”, and “snow” remain fixture assumptions for future semantic tests.

## Milestone 3 contract

Future semantic evidence can attach a class, confidence, normalized mask reference, and model identity to existing nodes. Only then may composition logic justify P1–P4 assignments or regional subject operations. Snow portrait, night road, waterfall portrait, and sunset silhouette remain documented scenario fixtures rather than claimed detections.
