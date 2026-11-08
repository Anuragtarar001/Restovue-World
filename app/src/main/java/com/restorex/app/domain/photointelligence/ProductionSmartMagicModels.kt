package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicAnalysis
import com.restorex.app.domain.smartmagic.SmartMagicParameters

const val SMART_MAGIC_PRODUCTION_SCHEMA = 2

data class SmartMagicStageTimings(
    val globalAnalysisNanos: Long,
    val photometricAnalysisNanos: Long,
    val semanticInferenceNanos: Long,
    val reasoningAndPlanningNanos: Long,
) {
    init { require(listOf(globalAnalysisNanos, photometricAnalysisNanos, semanticInferenceNanos,
        reasoningAndPlanningNanos).all { it >= 0 }) }
}

enum class ProductionRenderStrategy { GLOBAL_ONLY, REGIONAL_ONLY, COMBINED, NO_OP }

enum class ProductionRenderOutcome {
    APPLIED,
    REDUCED_FOR_QUALITY,
    NO_CORRECTION_NEEDED,
    QUANTIZED_NO_OP,
    PERCEPTUALLY_NEGLIGIBLE,
    REJECTED_FOR_QUALITY,
    UNSUPPORTED,
}

data class PerceptualImpactMetrics(
    val changedPixels: Int,
    val changedFraction: Float,
    val meanOklabDelta: Float,
    val p95OklabDelta: Float,
    val meanLinearLuminanceDelta: Float,
    val p95AbsoluteLuminanceDelta: Float,
    val meanChromaDelta: Float,
    val perceptuallyNegligible: Boolean,
    val localContrastDelta: Float = 0f,
    val saturationAnomalyFraction: Float = 0f,
    val haloRisk: Float = 0f,
)

data class RegionalCandidateEvaluation(
    val strength: Float,
    val accepted: Boolean,
    val failureReasons: List<String>,
    val impact: PerceptualImpactMetrics,
    val runtimeNanos: Long,
)

/** Immutable decisions shared by preview and full-resolution export. No source bitmap is retained. */
data class SmartMagicProductionSession(
    val schemaVersion: Int = SMART_MAGIC_PRODUCTION_SCHEMA,
    val sessionId: String,
    val sourceIdentity: String,
    val sourceFingerprint: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val geometryVersion: Long,
    val smartAnalysis: SmartMagicAnalysis,
    val photometricEvidence: PhotometricEvidence,
    val diagnoses: List<ConditionDiagnosis>,
    val advancedEvidence: AdvancedPhotometricEvidence,
    val semanticResult: SemanticAnalysisResult,
    val sceneGraph: SceneGraph,
    val regionalEvidence: SemanticRegionalEvidence?,
    val subjectAssessment: SemanticSubjectAssessment?,
    val globalParameters: Map<EnhancementLevel, SmartMagicParameters>,
    val regionalPlans: Map<EnhancementLevel, RegionalCorrectionPlan>,
    val optimizationResults: Map<EnhancementLevel, AdaptiveOptimizationResult>,
    val stageTimings: SmartMagicStageTimings,
    val createdElapsedNanos: Long,
) {
    init {
        require(schemaVersion == SMART_MAGIC_PRODUCTION_SCHEMA)
        require(sessionId.isNotBlank() && sourceIdentity.isNotBlank() && sourceFingerprint.isNotBlank())
        require(sourceWidth > 0 && sourceHeight > 0 && geometryVersion >= 0 && createdElapsedNanos >= 0)
        require(EnhancementLevel.entries.all { it in globalParameters })
    }
}

data class SmartMagicProductionRender(
    val image: RasterImage,
    val level: EnhancementLevel,
    val strategy: ProductionRenderStrategy,
    val outcome: ProductionRenderOutcome,
    val impact: PerceptualImpactMetrics,
    val regionalResult: RegionalRenderResult?,
    val explanation: List<DecisionReason>,
    val elapsedNanos: Long,
)
