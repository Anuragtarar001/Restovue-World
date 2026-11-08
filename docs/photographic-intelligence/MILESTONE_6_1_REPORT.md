# Restovue Milestone 6.1 report

## Status

**PARTIAL** despite a successful sunrise correction. The mandatory pair was analyzed and the verified gating defect was fixed. The exact sunrise result is visibly and measurably improved. The milestone remains partial because the indoor four-woman source is unavailable, no physical mid-range device was tested, and broad real-camera calibration remains incomplete.

## Verified root cause and fix

The supplied person mask was excellent, but full-frame face detection failed. A hard face-association gate produced `INSUFFICIENT_EVIDENCE`; the global optimizer was bypassed because a human mask existed; all regional plans were empty; every mode returned the exact source.

M6.1 adds model-confirmed person-guided face detection, a conservative reliable-person fallback, independently bounded local midtone plans, and regional-importance perceptual acceptance. It does not globally increase Extreme multipliers.

## Measured result

- Correct face detected at score 0.9158.
- Face luminance: 0.09175 original, 0.09154 old Extreme, 0.10570 Natural, 0.11347 Enhanced, 0.12012 new Extreme.
- Whole subject: 0.08296 → 0.09813 in Extreme.
- Background: 0.18472 → 0.18486.
- Extreme saturation anomaly: 0; halo risk: 0.0000025.
- Exact source-resolution export: 4.161 s on SM-S926B.

## Acceptance

| Requirement | Status |
|---|---|
| Original optimizer decision explained | PASS |
| Exact source/export compared | PASS |
| Subject-aware correction tested | PASS |
| Sunrise reprocessed and visually reviewed | PASS |
| Background atmosphere preserved | PASS |
| Existing UI/manual editor preserved | PASS |
| Indoor four-woman case | BLOCKED — source unavailable |
| Physical mid-range device | NOT TESTED |
| Broad real-photo regression dataset | PARTIAL |

## Artifacts

Private, Git-ignored visual artifacts and traces are in `research/photographic-intelligence/milestone6-1/`. Source photographs were not added to version control.

## Verification

- Clean JVM/build/lint gate: PASS; 186 JVM tests.
- Connected Samsung suite: PASS; 23 tests.
- Lint: 0 errors, 33 warnings, 2 hints.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 48,819,785 bytes, SHA-256 `1EFF40E2376F124A2F764B6FC69EDAC201C8F57367A7A3DC076A8B7B7D2DF2BF`.

## Recommendation

Use the new sunrise calibration for owner acceptance testing. Obtain the original indoor-celebration image and a representative 4–6 GB Android device before calling M6.1 complete.

