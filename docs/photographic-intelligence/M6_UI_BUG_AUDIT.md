# Milestone 6 UI bug audit

Date: 2026-11-08

## Confirmed flow

`SmartMagicScreen` calls `SmartMagicViewModel.selectLevel`. The ViewModel owns source analysis and render jobs, invokes `SmartMagicProcessingCoordinator`, and publishes a `SmartMagicUiState` consumed by Compose.

## Findings

| Symptom | Confirmed cause | Evidence |
|---|---|---|
| Mode appears selected but image does not reliably change | Mode renders shared only the source generation. A cancelled older coroutine could still publish READY/image state; rendering also hid mode controls while leaving an older bitmap visible. | Pre-M6 ViewModel had no unique mode-render generation or completed-level identity. |
| Initial Enhanced can be mislabeled | Original/previous pixels could remain visible while the selected label changed and the new render was pending. | State did not distinguish original-ready, analyzing, rendering, and selected-preview-ready. |
| Before/After seems inert | Press-and-hold existed but had weak discoverability and competed with the small image surface. | Screen inspection and UI test coverage. |
| Image is too small | Header/status/control areas consumed canvas height; there was no zoom/pan transform. | Screen layout inspection. |
| Extreme feels disproportionately slow | Larger regional renders repeatedly sampled 5x5 neighborhoods, converted samples to linear light for each operator, allocated temporary arrays in validation, and ran bounded quality candidates serially. Segmentation/session work could also be repeated for a new source. | Source profiling and device traces. |

The fast first visible image is now explicitly an **original preview**, not a completed Enhanced result. Completion requires matching source, session, selected level, plan and render generation.

## Controls audited

- Natural / Enhanced / Extreme: always available after original preview, latest selection wins.
- Compare: hold image or the visible comparison control; release restores the processed image without re-rendering.
- Save: enabled only when selected level equals completed level and generations match.
- Back / Choose another: cancel source, analysis, render and export jobs through lifecycle-aware ViewModel jobs.

