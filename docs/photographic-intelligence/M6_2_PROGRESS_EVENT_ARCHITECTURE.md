# Milestone 6.2 Progress Event Architecture

`SmartMagicWorkUpdate` is an Android-free, bitmap-free domain event. It contains a stage, state, optional real work-unit counts, and a short detail. Its constructor rejects negative counts and progress beyond the total.

`SmartMagicProgressEvent` binds that update to the current source generation, render generation, enhancement level, and user-facing label. `SmartMagicViewModel.publishProgress` accepts an update only when both the source and (when present) render generations remain current.

The event path is:

`Coordinator/export boundary -> SmartMagicWorkUpdate -> generation gate -> immutable SmartMagicUiState -> Compose monitor`

Only the latest event is stored. There is no unbounded queue and no bitmap reference. Optimizer updates occur per candidate (maximum device-budgeted candidates), not per pixel. Renderer and encoder stages remain indeterminate because they do not expose defensible total work units.

Mode switching cancels the old render and advances the render generation. An old callback therefore cannot alter the visible stage or preview. Export uses the already-selected preview/session and reports separate full-resolution stages.
