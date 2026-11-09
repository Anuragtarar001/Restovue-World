# Adaptive Smart Magic milestone report

## Status

**PARTIAL**

The adaptive technical baseline, soft virtual-fill extension, and single-canvas comparison repair
are implemented and covered by JVM tests. Three owner photographs were rendered with the exact
global production math and visually reviewed. Full semantic/regional and physical-device acceptance
are blocked because the Samsung SM-S926B is not connected.

## Implemented

- Target-based AdaptiveAutoPlan with independent diagnostic Auto strengths.
- Coordinated linear-light exposure, brightness, contrast, endpoints, gamma, shadow/highlight,
  bounded RGB gain, and color/detail parameters.
- One technical baseline shared across Natural/Enhanced/Extreme; mode finishing remains bounded.
- Existing bounded optimizer continues to evaluate rendered candidates.
- Face illumination extended into neck/upper torso with a weaker soft lobe, intersected with the
  person confidence mask.
- Smart Magic original/processed comparison rendered through one Canvas and one transform.
- Real-photo host review generator, contact sheet, per-photo JSON, aggregate JSON, HTML gallery.

## Verified root causes

The manual Auto operations overlap mathematically. Auto Enhance already combines contrast,
gray-world balance, exposure, and vibrance; Auto Levels stretches RGB channels separately. The
manual Auto Enhance color path can create a mixed-light white-clothing cast because full-image
encoded-sRGB gray-world gains are followed by exposure/clipping and vibrance. The exact reported
shoulder pixels have not been isolated, so the artifact mechanism is verified in code while the
specific ROI reproduction remains pending.

The Smart Magic comparison had two image nodes with nominally equal transforms. It now uses one
draw pass and one destination rectangle, removing that alignment/state ambiguity.

## Real-photo observations

| Photo | Adaptive evidence | Host visual result | Limitation |
|---|---|---|---|
| Sunrise | median 0.089; target 0.215; neutral confidence 0.039 | field/subject visibly opened; warmth and mist retained | face mask not run |
| Father | median 0.367; target 0.215; neutral confidence 1.0 | restrained redistribution; white clothing remains neutral in visual review | shoulder ROI/device reference unavailable |
| Wedding | median 0.110; target 0.215; neutral confidence 0.014 | useful global tonal lift; colored lighting retained | left face still needs Android per-face pass |

Host full-image global render times ranged from 0.86 to 1.23 seconds for 1.18–1.44 MP inputs.
This is not a Samsung or mid-range performance result.

## Validation

- Focused adaptive controller tests: passed.
- Advanced regional renderer tests including virtual neck fill: passed.
- Real-photo host generator: passed and produced all 3 × 3 mode outputs.
- Required Gradle gate (`testDebugUnitTest assembleDebug lintDebug`): PASS; 198 JVM tests,
  0 failures, 0 errors, 0 skipped; debug assembly and lint completed successfully.
- Connected Android/physical tests: BLOCKED. The SM-S926B briefly appeared over ADB, then
  disconnected before `connectedDebugAndroidTest` began; Gradle reported no online devices.

## Not implemented or not validated

- No new cloud, network, telemetry, or model dependency was added.
- No physical-device measurement was available.
- No claim is made that global host renders equal production regional Smart Magic.
- No exact manual 50%/75% sequential reference was fabricated; the UI's Auto mode is an enum and
  cannot represent three simultaneously active Auto filters in one edit state.

## Deliverables

- Audit: docs/smart-magic/ADAPTIVE_AUTO_AUDIT.md
- Gallery: docs/smart-magic/review/SMART_MAGIC_VISUAL_REVIEW.html
- Presentation: docs/smart-magic/review/SMART_MAGIC_MILESTONE_PRESENTATION.md
- Aggregate diagnostics: docs/smart-magic/review/VALIDATION_SUMMARY.json
- Private local pixels: docs/smart-magic/review/assets/private/ (Git-ignored)

## Recommendation

**PARTIAL.** Install the generated debug APK on the Samsung device and rerun the three photographs
through the complete semantic path before declaring the pipeline ready for user review.
