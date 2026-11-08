# Milestone 6.2 Live Processing Monitor

The generic modal spinner was replaced by a compact panel below the image canvas. The panel presents the latest generation-valid engine event, an operation detail, and a slim horizontal progress indicator.

## Truthful stage mapping

The production path emits only stages it actually executes:

| Engine boundary | User label | Progress type |
|---|---|---|
| preview decode | Loading photograph | indeterminate |
| global/photometric/advanced analysis | Analyzing lighting | indeterminate |
| bundled semantic inference | Detecting faces and subjects | indeterminate |
| regional reasoning and plan construction | Evaluating exposure and tone | indeterminate |
| global optimizer candidate evaluation, only for non-person paths | Optimizing tonal balance | determinate candidate work units |
| selected renderer | Rendering corrected preview | indeterminate |
| full-resolution decode | Preparing full-resolution image | indeterminate |
| locked preview plan at source resolution | Applying selected corrections | indeterminate |
| encoder and MediaStore write | Encoding and saving image | indeterminate |
| completed preview/export | Finalizing image | completed |

Regional photographs do not display the global optimizer stage because that work is not executed. Cached modes publish `Cached preview ready`; they do not replay processing stages.

## Selected adjustments

After a preview is ready, `View adjustments` expands a summary generated from the exact accepted plan and applied regional strength. Units are preserved:

- exposure: EV;
- midtone curve: gamma delta;
- gains: gain delta;
- remaining regional operations: normalized engine units.

Global summaries likewise report the actual `SmartMagicParameters`. No Adobe/manual-slider equivalence is claimed. A no-op reports that the original was preserved and includes the production outcome.

## Failure and cancellation

Cancellation marks the active event cancelled and restores READY, ORIGINAL_READY, or IDLE according to the last valid image. Errors do not replace the original. Generation checks discard stale source, mode, and progress events.
