package com.restorex.app.domain.photointelligence

enum class ValueProvenance {
    SOURCE_OBSERVED, MATHEMATICALLY_DERIVED, ENGINEERING_HYPOTHESIS,
    EXPERIMENTALLY_CALIBRATED, PRODUCTION_VALIDATED,
}

enum class CalibrationStatus { RESEARCH_ONLY, UNCALIBRATED, CALIBRATED, PRODUCTION_VALIDATED }
enum class EnhancementLevel { NATURAL, ENHANCED, EXTREME }
enum class DecisionDisposition { APPLY, APPLY_CONSERVATIVELY, PROTECT, SKIP, UNSUPPORTED, INSUFFICIENT_EVIDENCE }
enum class CompositionPriority { P1, P2, P3, P4, UNRESOLVED }
enum class RegionKind { PHOTOMETRIC_GRID, PHOTOMETRIC_SALIENCE_CANDIDATE, BRIGHT_COMPONENT, SEMANTIC_PLACEHOLDER, SEMANTIC_CONFIRMED }
enum class BrightRegionKind { POINT_SOURCE, BLOOM, BROAD_BRIGHT_SURFACE, DIFFUSE_HIGHLIGHT, SPECULAR_CANDIDATE }

data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right >= left && bottom >= top)
    }
    val area: Float get() = (right - left) * (bottom - top)
}

data class ChannelFractions(val red: Float, val green: Float, val blue: Float) {
    init { require(listOf(red, green, blue).all { it.isFinite() && it in 0f..1f }) }
}

data class LuminanceDistribution(
    val mean: Float, val median: Float,
    val p01: Float, val p05: Float, val p25: Float, val p50: Float,
    val p75: Float, val p95: Float, val p99: Float,
    val shadowOccupancy: Float, val highlightOccupancy: Float,
    val blackClipping: Float, val whiteClipping: Float,
    val robustRange: Float, val globalContrast: Float,
)

data class ColorEvidence(
    val meanLinearRed: Float, val meanLinearGreen: Float, val meanLinearBlue: Float,
    val meanSaturation: Float, val medianSaturation: Float, val meanChroma: Float,
    val highSaturationFraction: Float, val neutralFraction: Float,
    val neutralRed: Float?, val neutralGreen: Float?, val neutralBlue: Float?,
    val whiteBalanceConfidence: Float, val proposedRedGain: Float,
    val proposedGreenGain: Float, val proposedBlueGain: Float,
    val channelBlackClipping: ChannelFractions, val channelWhiteClipping: ChannelFractions,
)

data class DetailEvidence(
    val edgeDensity: Float, val meanGradient: Float, val localContrast: Float,
    val noiseRisk: Float, val fineDetailEnergy: Float,
)

data class SpatialRegionEvidence(
    val id: String, val scale: Int, val row: Int, val column: Int, val bounds: NormalizedRect,
    val visibleWeight: Float, val luminance: LuminanceDistribution,
    val meanSaturation: Float, val localContrast: Float, val edgeDensity: Float,
    val noiseRisk: Float, val relativeBrightness: Float,
)

data class PhotometricEvidence(
    val analysisVersion: String,
    val sourceWidth: Int, val sourceHeight: Int,
    val sampledPixels: Int, val effectiveVisibleWeight: Float,
    val histogramBins: Int,
    val luminance: LuminanceDistribution,
    val color: ColorEvidence,
    val detail: DetailEvidence,
    val regions: List<SpatialRegionEvidence>,
    val analysisDurationNanos: Long,
) {
    init {
        require(sourceWidth > 0 && sourceHeight > 0 && sampledPixels >= 0 && effectiveVisibleWeight >= 0f)
        require(histogramBins >= 16 && analysisDurationNanos >= 0)
    }
}

data class MetricEvidence(val metric: String, val value: Float, val unit: String, val rationale: String) {
    init { require(value.isFinite()); require(metric.isNotBlank() && unit.isNotBlank()) }
}

data class ConditionDiagnosis(
    val conditionId: String,
    val evidence: List<MetricEvidence>,
    val counterEvidence: List<MetricEvidence>,
    val severity: Float,
    val confidence: Float,
    val affectedRegionIds: List<String>,
    val potentiallyIntentional: Boolean,
    val limitations: List<String>,
) {
    init { require(severity in 0f..1f && confidence in 0f..1f); require(conditionId.isNotBlank()) }
}

data class BrightRegionEvidence(
    val id: String, val kind: BrightRegionKind, val bounds: NormalizedRect,
    val areaFraction: Float, val coreFraction: Float, val peakLuminance: Float,
    val meanLuminance: Float, val localDominance: Float, val surroundingContrast: Float,
    val saturatedChannelFraction: Float, val confidence: Float,
)

data class RegionNode(
    val id: String, val kind: RegionKind, val bounds: NormalizedRect,
    val salience: Float, val confidence: Float,
    val priority: CompositionPriority = CompositionPriority.UNRESOLVED,
    val semanticClass: String? = null,
)
data class RegionEdge(val from: String, val to: String, val relation: String, val confidence: Float)
data class SceneGraph(
    val nodes: List<RegionNode>, val edges: List<RegionEdge>,
    val semanticPrimarySubjectId: String?, val unresolvedPrimarySubject: Boolean,
)

data class ProtectionDecision(
    val policyId: String, val disposition: DecisionDisposition, val reasonCode: String,
    val confidence: Float, val affectedOperationIds: List<String>,
)

enum class CorrectionOperation {
    GLOBAL_EXPOSURE, HIGHLIGHT_COMPRESSION, SHADOW_RECOVERY, WHITE_POINT, BLACK_POINT,
    TONE_CONTRAST, WHITE_BALANCE, VIBRANCE, SATURATION, NOISE_REDUCTION,
    DETAIL_ENHANCEMENT, CLARITY, DEHAZE,
}

data class ParameterValue(
    val name: String, val value: Float, val unit: String, val minimum: Float, val maximum: Float,
    val formulaVersion: String, val provenance: ValueProvenance, val calibrationStatus: CalibrationStatus,
) {
    init {
        require(listOf(value, minimum, maximum).all { it.isFinite() }); require(minimum <= maximum)
        require(value in minimum..maximum); require(unit.isNotBlank() && formulaVersion.isNotBlank())
    }
}

data class TypedCorrection(
    val operationId: String, val operation: CorrectionOperation, val targetScope: String,
    val parameters: List<ParameterValue>, val evidenceReferences: List<String>,
    val confidence: Float, val requiredCapabilities: List<String>, val qualityChecks: List<String>,
) { init { require(operationId.isNotBlank() && confidence in 0f..1f) } }

data class DeferredCorrection(val operation: CorrectionOperation, val reasonCode: String, val requiredCapability: String)
data class RejectedCorrection(val operation: CorrectionOperation, val reasonCode: String)
data class ValidationRequirement(val id: String, val metric: String, val rule: String)
data class DecisionReason(val code: String, val explanation: String, val evidenceReferences: List<String> = emptyList())

data class CorrectionPlan(
    val schemaVersion: Int = 2,
    val level: EnhancementLevel,
    val sourceIdentity: String,
    val analysisVersion: String,
    val knowledgeVersion: String,
    val operations: List<TypedCorrection>,
    val protections: List<ProtectionDecision>,
    val deferredOperations: List<DeferredCorrection>,
    val rejectedOperations: List<RejectedCorrection>,
    val validationRequirements: List<ValidationRequirement>,
    val explanation: List<DecisionReason>,
) { init { require(schemaVersion == 2 && sourceIdentity.isNotBlank()) } }

data class CorrectionPlanSet(val natural: CorrectionPlan, val enhanced: CorrectionPlan, val extreme: CorrectionPlan)
