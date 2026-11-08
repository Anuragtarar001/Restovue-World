# Milestone 6.2.1 — Complete Adjustment Diagnostics

## Implementation

Smart Magic now publishes a fixed 29-category `AdjustmentDiagnosticReport` for every completed mode. Each entry contains:

- stable adjustment identifier and group;
- `APPLIED`, `NOT_REQUIRED`, `REJECTED`, `SKIPPED`, `NOT_EVALUATED`, or `UNSUPPORTED`;
- neutral, proposed, and applied values where they genuinely exist;
- native engine unit and affected region;
- evidence-backed decision reason;
- source generation, render generation, session identity, and mode.

`AdjustmentDiagnosticBuilder` derives entries from the real `SmartMagicProductionSession`, optimizer candidate evaluations, regional plan, applied strength, quality failures, and final `SmartMagicProductionRender`. A neutral field is not automatically called `NOT_REQUIRED`:

- invalid non-neutral candidates become `REJECTED`;
- global proposals bypassed by an accepted regional path become `SKIPPED`;
- absent production analysis becomes `NOT_EVALUATED`;
- missing production operators become `UNSUPPORTED`.

The UI control is named **Adjustment Details**. Its grouped, vertically scrollable panel shows every inventory row. Tapping a row reveals raw neutral/proposed/applied values, region, mode, status, and reason. Expanding it performs no image processing. A report is displayed only if its source generation, render generation, and mode match the currently displayed preview.

## Exact-device evidence

The following reports were generated on Samsung SM-S926B from the same private sunrise photograph (`orig1.jpeg`) through the production coordinator:

- session: `sm45-61ec9b0e8c6c5c0201206029-g0`;
- source generation: 42;
- render generations: Natural 100, Enhanced 101, Extreme 102;
- machine-readable evidence: `research/photographic-intelligence/milestone6-2-1/complete_adjustment_reports.json` (Git-ignored because it is derived from a private photograph).

Reason abbreviations below:

- `REGIONAL_PATH`: a reliable person plan was selected, so the proposed global operation was deliberately not rendered.
- `ACCEPTED`: the exact regional operation rendered at accepted strength 1.000.
- `BELOW_THRESHOLD`: measured regional evidence did not justify the operation.
- `NO_RULE`: production planning has no decision rule for this category.
- `NO_OPERATOR`: the production renderer cannot perform this category with the named semantics.

## Natural — complete report

| Adjustment | Status | Applied value | Region | Reason |
|---|---|---:|---|---|
| Exposure | SKIPPED | — | Global | REGIONAL_PATH |
| Brightness | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Contrast | SKIPPED | — | Global | REGIONAL_PATH |
| Highlights | SKIPPED | — | Global | REGIONAL_PATH |
| Shadows | APPLIED | +0.059219 normalized | Person | ACCEPTED |
| Whites | SKIPPED | — | Global | REGIONAL_PATH |
| Blacks | SKIPPED | — | Global | REGIONAL_PATH |
| Gamma / midtone curve | SKIPPED | — | Global | REGIONAL_PATH |
| Local contrast | NOT_REQUIRED | — | Background | BELOW_THRESHOLD |
| Texture / clarity | SKIPPED | — | Global | REGIONAL_PATH |
| White balance temperature | UNSUPPORTED | — | N/A | RGB gains exist, calibrated temperature does not |
| Tint | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Red channel | SKIPPED | — | Global | REGIONAL_PATH |
| Green channel | SKIPPED | — | Global | REGIONAL_PATH |
| Blue channel | SKIPPED | — | Global | REGIONAL_PATH |
| Vibrance | SKIPPED | — | Global | REGIONAL_PATH |
| Saturation | SKIPPED | — | Global | REGIONAL_PATH |
| Hue correction | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Selective color adjustments | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Luminance noise reduction | SKIPPED | — | Global | REGIONAL_PATH |
| Color noise reduction | SKIPPED | — | Global | REGIONAL_PATH |
| Sharpening | SKIPPED | — | Global | REGIONAL_PATH |
| Detail recovery | NOT_REQUIRED | — | Background | BELOW_THRESHOLD/noise protection |
| Dehaze | SKIPPED | — | Global | REGIONAL_PATH |
| Face illumination | APPLIED | +0.131407 gamma delta | Person/face influence | ACCEPTED |
| Subject exposure | APPLIED | +0.060725 EV | Person | ACCEPTED |
| Background exposure | NOT_EVALUATED | — | Background | NO_RULE |
| Regional contrast | NOT_REQUIRED | — | Background | BELOW_THRESHOLD |
| Local midtone correction | APPLIED | +0.131407 gamma delta | Person | ACCEPTED |

## Enhanced — complete report

| Adjustment | Status | Applied value | Region | Reason |
|---|---|---:|---|---|
| Exposure | SKIPPED | — | Global | REGIONAL_PATH |
| Brightness | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Contrast | SKIPPED | — | Global | REGIONAL_PATH |
| Highlights | SKIPPED | — | Global | REGIONAL_PATH |
| Shadows | APPLIED | +0.101518 normalized | Person | ACCEPTED |
| Whites | SKIPPED | — | Global | REGIONAL_PATH |
| Blacks | SKIPPED | — | Global | REGIONAL_PATH |
| Gamma / midtone curve | SKIPPED | — | Global | REGIONAL_PATH |
| Local contrast | NOT_REQUIRED | — | Background | BELOW_THRESHOLD |
| Texture / clarity | SKIPPED | — | Global | REGIONAL_PATH |
| White balance temperature | UNSUPPORTED | — | N/A | RGB gains exist, calibrated temperature does not |
| Tint | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Red channel | SKIPPED | — | Global | REGIONAL_PATH |
| Green channel | SKIPPED | — | Global | REGIONAL_PATH |
| Blue channel | SKIPPED | — | Global | REGIONAL_PATH |
| Vibrance | SKIPPED | — | Global | REGIONAL_PATH |
| Saturation | SKIPPED | — | Global | REGIONAL_PATH |
| Hue correction | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Selective color adjustments | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Luminance noise reduction | SKIPPED | — | Global | REGIONAL_PATH |
| Color noise reduction | SKIPPED | — | Global | REGIONAL_PATH |
| Sharpening | SKIPPED | — | Global | REGIONAL_PATH |
| Detail recovery | NOT_REQUIRED | — | Background | BELOW_THRESHOLD/noise protection |
| Dehaze | SKIPPED | — | Global | REGIONAL_PATH |
| Face illumination | APPLIED | +0.182275 gamma delta | Person/face influence | ACCEPTED |
| Subject exposure | APPLIED | +0.104101 EV | Person | ACCEPTED |
| Background exposure | NOT_EVALUATED | — | Background | NO_RULE |
| Regional contrast | NOT_REQUIRED | — | Background | BELOW_THRESHOLD |
| Local midtone correction | APPLIED | +0.182275 gamma delta | Person | ACCEPTED |

## Extreme — complete report

| Adjustment | Status | Applied value | Region | Reason |
|---|---|---:|---|---|
| Exposure | SKIPPED | — | Global | REGIONAL_PATH |
| Brightness | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Contrast | SKIPPED | — | Global | REGIONAL_PATH |
| Highlights | SKIPPED | — | Global | REGIONAL_PATH |
| Shadows | APPLIED | +0.143818 normalized | Person | ACCEPTED |
| Whites | SKIPPED | — | Global | REGIONAL_PATH |
| Blacks | SKIPPED | — | Global | REGIONAL_PATH |
| Gamma / midtone curve | SKIPPED | — | Global | REGIONAL_PATH |
| Local contrast | NOT_REQUIRED | — | Background | BELOW_THRESHOLD |
| Texture / clarity | SKIPPED | — | Global | REGIONAL_PATH |
| White balance temperature | UNSUPPORTED | — | N/A | RGB gains exist, calibrated temperature does not |
| Tint | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Red channel | SKIPPED | — | Global | REGIONAL_PATH |
| Green channel | SKIPPED | — | Global | REGIONAL_PATH |
| Blue channel | SKIPPED | — | Global | REGIONAL_PATH |
| Vibrance | SKIPPED | — | Global | REGIONAL_PATH |
| Saturation | SKIPPED | — | Global | REGIONAL_PATH |
| Hue correction | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Selective color adjustments | UNSUPPORTED | — | N/A | NO_OPERATOR |
| Luminance noise reduction | SKIPPED | — | Global | REGIONAL_PATH |
| Color noise reduction | SKIPPED | — | Global | REGIONAL_PATH |
| Sharpening | SKIPPED | — | Global | REGIONAL_PATH |
| Detail recovery | NOT_REQUIRED | — | Background | BELOW_THRESHOLD/noise protection |
| Dehaze | SKIPPED | — | Global | REGIONAL_PATH |
| Face illumination | APPLIED | +0.211947 gamma delta | Person/face influence | ACCEPTED |
| Subject exposure | APPLIED | +0.147476 EV | Person | ACCEPTED |
| Background exposure | NOT_EVALUATED | — | Background | NO_RULE |
| Regional contrast | NOT_REQUIRED | — | Background | BELOW_THRESHOLD |
| Local midtone correction | APPLIED | +0.211947 gamma delta | Person | ACCEPTED |

## Verification

- Fixed inventory completeness and uniqueness are enforced by `AdjustmentDiagnosticReport` construction.
- Accepted regional values are asserted equal to `operation.amount * appliedStrength`.
- Tests cover all six statuses, including a rejected regional proposal and a genuinely evaluated neutral exposure.
- Samsung UI tests verify all 29 rows, exact applied display value, mode switching, and stale report rejection.
- No photographic correction strength was changed for this diagnostic milestone.

### Executed test evidence

- Required clean gate: `gradlew.bat clean testDebugUnitTest assembleDebug lintDebug` — **PASS** in 2m55s.
- JVM tests: **193 passed, 0 failed, 0 skipped** across 41 suites.
- Lint: **0 errors, 33 warnings, 5 hints**.
- Exact Samsung diagnostic execution: `Milestone621AdjustmentDiagnosticsTest` — **PASS**; all three reports were produced from the same private sunrise source through the production coordinator.
- Focused Samsung Smart Magic UI class: **6 passed** on an unlocked device. This includes complete inventory display, exact applied-value formatting, per-mode replacement, and stale-generation suppression.
- Full connected run: 26 discovered; **19 passed, 1 skipped, 6 UI failures**. The phone's secure lock screen became active during the 9m37s run (`mCurrentFocus=Bouncer`), preventing Compose test activities from launching. All non-UI tests, including the new exact diagnostic test, passed. A repeat UI run while the phone remained locked reproduced the launch failure; production assertions did not fail.

The full connected aggregate is therefore not reported as a clean pass. Re-running the UI portion requires the owner to unlock the secure device; the implementation is not modified to bypass device security.

### Final artifact

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 48,950,857 bytes
- SHA-256: `09BB618CA5AAE8283468AD14A9019A17655FBBD9C3116FE1EA800AD499A12DC7`
- Installation: `adb install -r` succeeded on Samsung SM-S926B; installed version is `0.9.0-rc1` (`versionCode 9`).
