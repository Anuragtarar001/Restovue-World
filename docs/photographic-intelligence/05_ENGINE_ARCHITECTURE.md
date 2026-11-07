# Photographic Intelligence Engine 2.0 — proposed architecture

This is a Milestone 1 design, not production code. It integrates with the existing Android-free domain / Android data / presentation split without replacing the proven renderer, repository or scheduler.

## Data flow

```text
Photo Picker URI
  → bounded oriented analysis raster + immutable source identity
  → photometric analysis ───────────────┐
  → face/person/semantic analysis ──────┤
  → scene labels + confidence ──────────┤
  → region graph + P1/P2/P3/P4 ────────┤
  → defects/opportunities/protections ──┤
  → Smart Value DB + conflict rules ────┤
  → eligible recipe candidates ─────────┤
  → Natural/Enhanced/Extreme plans ─────┤
  → preview renderer → validation ──────┤→ cached accepted previews
  → selected accepted plan + source URI → full-resolution renderer → validation/export
```

The three levels share analysis/scene graph but calculate their own bounded parameters and must each pass validation. They are not opacity blends of one filtered image.

## Domain proposals

```kotlin
interface PhotometricAnalyzer {
    suspend fun analyze(image: AnalysisRaster, cancel: CancellationSignal): PhotometricEvidence
}

interface SemanticAnalyzer { // implementation and runtime live in data
    suspend fun analyze(image: AnalysisRaster, cancel: CancellationSignal): SemanticEvidence
}

data class SemanticEvidence(
    val model: ModelIdentity,
    val regions: List<SemanticRegion>, // normalized confidence mask refs, never Android Bitmap
    val unsupportedClasses: Set<RegionClass>,
)

data class SceneGraph(
    val labels: List<ConfidenceTag>,
    val regions: List<RegionNode>,
    val relationships: List<RegionEdge>,
    val priorities: Map<RegionId, CompositionPriority>,
)

data class Diagnosis(
    val defects: List<EvidencedTag>,
    val opportunities: List<EvidencedTag>,
    val protections: List<Protection>,
)

data class CorrectionPlan(
    val level: EnhancementLevel,
    val analysisVersion: String,
    val globalOperations: List<TypedOperation>,
    val regionalOperations: List<RegionalOperation>,
    val expectedChecks: List<ValidationRule>,
)

interface SmartValueRepository {
    fun candidates(context: DecisionContext): List<KnowledgeRecord>
}

interface PlanValidator {
    fun validate(source: AnalysisRaster, candidate: RenderedCandidate, plan: CorrectionPlan): ValidationReport
}
```

Masks should be immutable, normalized to oriented source coordinates, versioned with geometry, and stored as bounded low-resolution confidence tiles or compressed runs. Full-resolution rendering upsamples masks edge-aware with tile overlap; UI state stores references/operations rather than full bitmaps.

## Responsibilities

1. **Image analysis:** extend current `SmartMagicAnalyzer` concepts: linear-light luminance/channel statistics, robust percentiles, local contrast/noise/detail at multiple scales, clipping and color distributions. It must not classify semantics.
2. **Scene classification:** multi-label scores from photometric + semantic evidence. Taxonomy IDs come from `scene_taxonomy.json`; unsupported labels remain unavailable, not guessed.
3. **Semantic segmentation:** new `SemanticAnalyzer` domain port, likely bundled face + person first, one evaluated general parser later. Return confidence and boundary quality.
4. **Scene graph:** regions with containment/overlap/adjacency/depth hints, faces-to-person association, light source/bloom components and foreground/background relationships.
5. **Composition:** combine faces, subject masks, prominence, focus, placement and relationships. Brightness/size/center are weak independent cues. Assign P1–P4 with explanation/confidence.
6. **Diagnosis/opportunity:** every tag stores evidence, counter-evidence, region, severity and confidence. Intentional low/high key, fog, flame and decorative-light tags can suppress apparent defects.
7. **Protection policies:** attached before recipe selection; P1 identity/skin and atmosphere protections have veto authority.
8. **Smart Value DB:** load a versioned packaged JSON (eventually compiled to a compact indexed format). Source observations, hypotheses and validated values stay distinct.
9. **Eligibility:** a recipe requires its measurements/masks and minimum confidence; missing prerequisites reject it.
10. **Recipe selection:** score compatible candidates, resolve `correction_conflicts.json`, establish order, and choose no-op when benefit is uncertain.
11. **Adaptive parameters:** continuous functions of severity/confidence/recovery headroom and level-specific constraints. No scene-name constants. Store units and provenance per parameter.
12. **Regional processing:** reuse `CpuLocalEditPipeline` concepts and source coordinate mapper, but add float/linear tiles and confidence masks. Protect boundaries with overlap/guided refinement.
13. **Validation:** render bounded analysis candidates; reject/relax/replan before displaying. Export reuses the selected plan and mask/model outputs, never reclassifies into a new look.
14. **Outputs:** Natural prioritizes minimal safe corrections; Enhanced maximizes clearly visible natural benefit; Extreme uses the strongest candidate that still passes identical safety gates.

## Integration with current code

- Keep `RasterImage`, `LoadImage`, `ImageRepository`, `SmartMagicExportManager` pattern, ViewModel cancellation generation, memory cache and `MediaStore` pending/finalize behavior.
- Evolve `SmartMagicEngine` behind a new session/plan contract rather than adding semantic decisions inside Compose.
- Keep model runtime, Android bitmap conversion and codecs in `data`. Keep scene/knowledge models and decision logic Android-free.
- Reuse `SmartMagicColorMath`, linear exposure/tone/OkLab gamut mapping; calibrate or replace gamma-domain manual transforms only in a later implementation milestone.
- Preview analysis longest side target should be benchmarked (current Smart analysis decode is 1280; prior requirement suggested up to 1536). Semantic models receive their native bounded input.
- Full-resolution neighborhood operations require tiling with halo overlap. Global statistics are computed once; geometry/masks use oriented source coordinates. Algorithms that cannot tile correctly remain bounded whole-image with a truthful size limit.
- Add model assets only after license/size/device gate. The app must remain functional and conservative without semantic inference.

## Persistence and reproducibility

Each session should record source identity, analysis/DB/model versions, evidence, chosen recipes, exact parameters, mask coordinate metadata, quality report and deterministic seeds. No source image is copied into logs. A plan hash makes preview/export decisions auditable.

## Milestone 2 implementation sequence

1. Freeze domain schemas and JSON loader/validator.
2. Add regression tests around current Smart pipeline.
3. Add photometric evidence/diagnosis and no-op/protection decisions without models.
4. Integrate bundled face/person inference behind ports after license/size approval.
5. Build scene graph/composition and regional face/subject exposure recipe.
6. Add plan validator and three independently calculated levels.
7. Calibrate on real photographs, then integrate UI/export.

Gate each step on deterministic tests, offline install, memory/latency measurements and visual review. Broad semantic parsing and GPU acceleration are separate evidence-based decisions, not prerequisites for the first trustworthy portrait-aware engine.
