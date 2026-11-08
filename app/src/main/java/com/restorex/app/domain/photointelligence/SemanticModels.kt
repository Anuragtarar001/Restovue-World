package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage

enum class SemanticClass { FACE, PERSON, BACKGROUND }
enum class SemanticFailureReason { MODEL_UNAVAILABLE, INITIALIZATION_FAILED, INFERENCE_FAILED, CANCELLED, INVALID_OUTPUT, MEMORY_LIMIT }
enum class MaskRejectionReason { EMPTY, NEARLY_FULL_FRAME, LOW_CONFIDENCE, EXCESSIVE_FRAGMENTATION, UNCERTAIN_BOUNDARY, FACE_OUTSIDE_PERSON, INVALID_GEOMETRY }
enum class FaceAssociationStatus { ASSOCIATED_WITH_COMBINED_PERSON_MASK, OUTSIDE_PERSON_MASK, AMBIGUOUS, NO_PERSON_MASK }
enum class SemanticSubjectDecision { REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE, PROTECT_CORRECTLY_EXPOSED_SUBJECT, PROTECT_INTENTIONAL_SILHOUETTE, MASK_UNRELIABLE, INSUFFICIENT_EVIDENCE, NO_PERSON }

data class NormalizedPoint(val x: Float, val y: Float) {
    init { require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) }
}

data class SemanticModelIdentity(
    val name: String,
    val runtime: String,
    val runtimeVersion: String,
    val assetSha256: String,
)

data class FaceLandmarkEvidence(val type: String, val position: NormalizedPoint)

data class FaceEvidence(
    val id: String,
    val bounds: NormalizedRect,
    val detectorScore: Float?,
    val heuristicQuality: Float,
    val landmarks: List<FaceLandmarkEvidence>,
) {
    init {
        require(detectorScore == null || detectorScore.isFinite() && detectorScore in 0f..1f)
        require(heuristicQuality.isFinite() && heuristicQuality in 0f..1f)
    }
}

data class MaskQuality(
    val weightedAreaFraction: Float,
    val foregroundAreaFraction: Float,
    val meanForegroundScore: Float,
    val boundaryUncertainty: Float,
    val componentCount: Int,
    val isolatedAreaFraction: Float,
    val accepted: Boolean,
    val rejectionReasons: List<MaskRejectionReason>,
)

data class SemanticMaskReference(
    val id: String,
    val width: Int,
    val height: Int,
    val coordinateSpace: String = "ORIENTED_SOURCE_NORMALIZED",
    val quality: MaskQuality,
)

data class FacePersonAssociation(
    val faceId: String,
    val maskId: String?,
    val status: FaceAssociationStatus,
    val personCoverageWithinFace: Float,
)

data class SemanticAnalysisResult(
    val faceModel: SemanticModelIdentity?,
    val personModel: SemanticModelIdentity?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val faces: List<FaceEvidence>,
    val personMask: ConfidenceMask?,
    val personMaskReference: SemanticMaskReference?,
    val associations: List<FacePersonAssociation>,
    val supportedClasses: Set<SemanticClass>,
    val unsupportedClasses: Set<String>,
    val inferenceDurationNanos: Long,
    val failureReason: SemanticFailureReason? = null,
    val failureDetail: String? = null,
)

fun interface SemanticAnalyzer {
    suspend fun analyze(image: RasterImage, shouldCancel: () -> Boolean): SemanticAnalysisResult
}

data class WeightedRegionStatistics(
    val effectiveWeight: Float,
    val meanLinearLuminance: Float,
    val shadowOccupancy: Float,
    val highlightOccupancy: Float,
    val blackClipping: Float,
    val whiteClipping: Float,
    val meanSaturation: Float,
    val noiseRisk: Float,
)

data class SemanticRegionalEvidence(
    val person: WeightedRegionStatistics,
    val background: WeightedRegionStatistics,
    val personMinusBackgroundLuminance: Float,
    val maskId: String,
)

data class SemanticSubjectAssessment(
    val decision: SemanticSubjectDecision,
    val confidence: Float,
    val evidence: List<MetricEvidence>,
    val counterEvidence: List<MetricEvidence>,
    val explanation: String,
)

data class RegionalPlanDecision(
    val targetMaskId: String,
    val operation: CorrectionOperation,
    val status: String,
    val reasonCode: String,
    val protections: List<String>,
)

data class SemanticPlanningSupplement(
    val basePlans: CorrectionPlanSet,
    val subjectAssessment: SemanticSubjectAssessment,
    val regionalDecisions: List<RegionalPlanDecision>,
    val protections: List<ProtectionDecision>,
)
