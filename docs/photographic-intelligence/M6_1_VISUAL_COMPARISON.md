# Milestone 6.1 visual comparison

The final production-path images are under `research/photographic-intelligence/milestone6-1/final-result/`.

- `M6_1_final_CONTACT_SHEET.png`: Original | previous Extreme | Natural | Enhanced | new Extreme.
- `M6_1_final_SUBJECT_CLOSEUP.png`: subject-scale comparison.
- `person_mask_overlay.png`: actual segmentation alignment.
- `difference_*.png`: amplified differences.
- `execution_trace.json`: plan, measurements, face, mask and performance evidence.

Human visual review:

- Previous Extreme is effectively indistinguishable from the original and leaves the face dark.
- Natural provides a restrained subject lift.
- Enhanced makes the face and clothing more readable without flattening the field.
- Extreme is visibly stronger at subject scale while the sunrise, mist, tree silhouettes and field hierarchy remain intact.
- No obvious person outline, sky shift, false dehaze or saturation artifact is visible in the full sheet or close-up.

The result is visibly better for the stated goal, but the face remains consistent with backlit sunrise illumination rather than being normalized to studio brightness.

