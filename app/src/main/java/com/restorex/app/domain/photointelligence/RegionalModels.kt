package com.restorex.app.domain.photointelligence

enum class RegionalTarget { PERSON, BACKGROUND }
enum class RegionalOperationType {
    EXPOSURE, SHADOWS, HIGHLIGHTS, CONTRAST, VIBRANCE, WHITE_BALANCE,
    SELECTIVE_DEHAZE, LOCAL_CONTRAST, DETAIL, MIDTONE_GAMMA,
}
enum class RegionalRenderStatus {
    APPLIED, REDUCED, NO_OP, PERCEPTUALLY_NEGLIGIBLE,
    SKIPPED_INVALID_MASK, SKIPPED_STALE_SESSION, SKIPPED_PROTECTION, SKIPPED_UNSUPPORTED,
}

data class RegionalOperation(
    val id: String,
    val type: RegionalOperationType,
    val target: RegionalTarget,
    val amount: Float,
    val unit: String,
    val confidence: Float,
    val evidenceReferences: List<String>,
    val qualityChecks: List<String>,
) {
    init {
        require(id.isNotBlank() && amount.isFinite() && confidence in 0f..1f)
        require(unit.isNotBlank())
        when (type) {
            RegionalOperationType.EXPOSURE -> require(amount in -.6f.. .8f && unit == "EV")
            RegionalOperationType.WHITE_BALANCE -> require(amount in -.15f.. .15f && unit == "gain_delta")
            RegionalOperationType.MIDTONE_GAMMA -> require(amount in 0f.. .5f && unit == "gamma_delta")
            else -> require(amount in -1f..1f && unit == "normalized")
        }
    }
}

data class RegionalCorrectionPlan(
    val schemaVersion: Int = 1,
    val sourceIdentity: String,
    val transformationVersion: Long,
    val maskId: String,
    val level: EnhancementLevel,
    val operations: List<RegionalOperation>,
    val protections: List<ProtectionDecision>,
    val explanation: List<DecisionReason>,
) {
    init { require(schemaVersion == 1 && sourceIdentity.isNotBlank() && maskId.isNotBlank() && transformationVersion >= 0) }
}

data class RegionalQualityMetrics(
    val newHighlightClipping: Float,
    val newShadowClipping: Float,
    val targetMeanLuminanceChange: Float,
    val protectedInteriorMeanDifference: Float,
    val boundaryDiscontinuity: Float,
    val shadowChromaAmplification: Float,
    val shadowNoiseAmplification: Float,
    val maskLeakage: Float,
    val maximumLinearChannel: Float,
    val finiteOutput: Boolean,
    val passed: Boolean,
    val failureReasons: List<String>,
)

data class RegionalRenderResult(
    val image: com.restorex.app.domain.model.RasterImage,
    val status: RegionalRenderStatus,
    val appliedStrength: Float,
    val refinedMask: ConfidenceMask?,
    val metrics: RegionalQualityMetrics,
    val decisions: List<DecisionReason>,
    val candidateEvaluations: List<RegionalCandidateEvaluation> = emptyList(),
)

data class RegionalRenderRequest(
    val source: com.restorex.app.domain.model.RasterImage,
    val sourceIdentity: String,
    val transformationVersion: Long,
    val plan: RegionalCorrectionPlan,
    val personMask: ConfidenceMask,
    val maskQuality: MaskQuality,
    val faces: List<FaceEvidence>,
    val regionalEvidence: SemanticRegionalEvidence,
    val preferredStrength: Float? = null,
    val maxCandidateAttempts: Int = 4,
)
