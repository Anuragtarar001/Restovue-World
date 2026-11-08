# Real-Photo Validation

The retained validation set contains three generated regression photographs and one documented
public-domain real camera portrait (`public_domain_portrait.jpg`, PD-USGov; provenance and SHA-256
are in `app/src/androidTest/assets/TEST_ASSET_LICENSE.md`). The real portrait exercised bundled
face/person inference but produced `INSUFFICIENT_EVIDENCE`; all modes correctly stayed no-op rather
than applying an unsafe global fallback.

Outputs are under `research/photographic-intelligence/milestone5-validation/`. Each directory has
original, three production renders, amplified difference maps, mask overlay, execution trace, and
quality report. `M5_VISUAL_CONTACT_SHEET.png` is the human-review sheet. Fine-hair and group boundary
close-ups are retained.

Visual review found no obvious subject outline, sky damage, group seam, black pinhole, or colored
shadow speckle. This is preliminary: the requested 12-category real-photo set was unavailable, so
sunset, fog, night, noisy low-light, snow/beach, and real hazy-landscape coverage remains incomplete.

