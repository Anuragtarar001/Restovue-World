# Smart Magic preliminary quality evaluation

## Method

`SmartMagicEvaluationTest` constructs 13 deterministic offline cases: portrait-like fields,
underexposure, backlight, daylight landscape, sunset, night, high and low contrast, saturated,
washed-out, noisy, document-like and transparent imagery. Each case is analyzed, enhanced and
reanalyzed. The test verifies dimensions and limits newly introduced clipping. This is an
engineering calibration set, not a substitute for human evaluation on real photographs.

## Selected measured decisions

Values below are actual outputs from the deterministic test run. They demonstrate adaptation,
not fixed preset values.

| Case | EV | Contrast | Highlights | Shadows | Vibrance | Clarity | Luma/chroma NR | Sharpen |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Underexposed portrait-like | +0.484 | +0.123 | 0.000 | +0.078 | 0.000 | 0.038 | 0.000 / 0.000 | 0.171 |
| Backlit portrait-like | -0.420 | 0.000 | -0.044 | +0.242 | +0.036 | 0.027 | 0.000 / 0.000 | 0.171 |
| Daylight landscape | -0.101 | +0.093 | 0.000 | 0.000 | 0.000 | 0.077 | 0.000 / 0.000 | 0.238 |
| Night | +0.560 | +0.126 | 0.000 | +0.059 | 0.000 | 0.064 | 0.091 / 0.076 | 0.207 |
| High contrast | +0.011 | -0.022 | -0.101 | +0.171 | +0.105 | 0.052 | 0.000 / 0.000 | 0.238 |
| Low contrast | +0.080 | +0.082 | 0.000 | 0.000 | +0.105 | 0.068 | 0.000 / 0.000 | 0.238 |
| Already saturated | +0.008 | 0.000 | 0.000 | +0.120 | 0.000 | 0.058 | 0.000 / 0.000 | 0.238 |
| Noisy | +0.215 | +0.126 | 0.000 | +0.046 | +0.046 | 0.070 | 0.064 / 0.052 | 0.216 |

The saturated case received zero vibrance. The noisy case received more noise reduction and less
sharpening than clean general imagery. High contrast selected negative contrast, highlight
compression and shadow recovery, while low contrast selected positive contrast.

On the connected Samsung SM-S926B, a measured 1080×720 synthetic analysis+render took 3.008
seconds. A 4000×3000 (12 MP) full spatial pipeline, JPEG encode, MediaStore finalize and reopen test
took 40.262 seconds. Managed heap was about 18.4 MB before and 195.0 MB immediately after the
export while the test still retained results; the device-reported app heap limit was 268.4 MB.
These are measured synthetic-workload results, not universal performance promises.

## Clipping and stability

The final renderer reserves a small encoding headroom. Across the synthetic set, new highlight
clipping remained within the test's eight-percentage-point bound and new shadow clipping within ten
percentage points. Most cases introduced neither. The document-like case can make deliberately
dark synthetic text reach black; the allowed shadow bound accounts for this while the source
strokes remain geometrically unchanged.

Tone-curve tests scan 4097 input levels across neutral and extreme allowed settings and verify
monotonic output. Determinism and preview/export consistency are checked byte-for-byte at equal
resolution. Alpha values and image dimensions are preserved.

## Assessment status

The computational checks are passing. Subjective image quality is **preliminary** because no
licensed human-rated photographic reference corpus is present in the repository. Device export
and workflow results are recorded in the final implementation report after connected testing.
No contact sheet is presented as photographic evidence because the available evaluation images are
synthetic calibration patterns rather than real photographs.
