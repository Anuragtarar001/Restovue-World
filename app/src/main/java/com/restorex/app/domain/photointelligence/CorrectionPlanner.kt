package com.restorex.app.domain.photointelligence

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class CorrectionDecisionEngine(
    private val knowledge: SmartValueKnowledgeV2 = Milestone2Knowledge.create(),
    private val capabilities: Set<CorrectionOperation> = CorrectionOperation.entries.toSet(),
) {
    init {
        val validation = SmartValueKnowledgeValidator().validate(knowledge)
        require(validation.isValid) { "Invalid Milestone 2 knowledge: ${validation.issues}" }
    }

    fun plan(
        sourceIdentity: String,
        evidence: PhotometricEvidence,
        diagnoses: List<ConditionDiagnosis>,
        sceneGraph: SceneGraph,
    ): CorrectionPlanSet = CorrectionPlanSet(
        natural = planLevel(EnhancementLevel.NATURAL, sourceIdentity, evidence, diagnoses, sceneGraph),
        enhanced = planLevel(EnhancementLevel.ENHANCED, sourceIdentity, evidence, diagnoses, sceneGraph),
        extreme = planLevel(EnhancementLevel.EXTREME, sourceIdentity, evidence, diagnoses, sceneGraph),
    )

    private fun planLevel(
        level: EnhancementLevel, sourceIdentity: String, e: PhotometricEvidence,
        diagnoses: List<ConditionDiagnosis>, graph: SceneGraph,
    ): CorrectionPlan {
        val byId = diagnoses.associateBy { it.conditionId }
        val operations = mutableListOf<TypedCorrection>(); val protections = mutableListOf<ProtectionDecision>()
        val deferred = mutableListOf<DeferredCorrection>(); val rejected = mutableListOf<RejectedCorrection>()
        val reasons = mutableListOf<DecisionReason>()
        val confidenceGate = when (level) { EnhancementLevel.NATURAL -> .60f; EnhancementLevel.ENHANCED -> .43f; EnhancementLevel.EXTREME -> .32f }
        val highNoise = byId["PHT_HIGH_NOISE_RISK"]?.severity ?: 0f
        val severeClipping = byId["PHT_SEVERE_CLIPPING"]?.severity ?: 0f
        val balanced = byId["PHT_ALREADY_BALANCED_EXPOSURE"]?.confidence ?: 0f
        if (graph.unresolvedPrimarySubject) reasons += DecisionReason("SEMANTIC_PRIMARY_SUBJECT_UNKNOWN", "Photometric salience cannot establish a semantic subject")
        if (balanced >= .65f) protections += protection("PRT_BALANCED", "ALREADY_BALANCED", balanced, CorrectionOperation.GLOBAL_EXPOSURE, CorrectionOperation.TONE_CONTRAST)
        if (severeClipping >= .35f) protections += protection("PRT_CLIPPED_ENDPOINTS", "ENDPOINT_HEADROOM_LIMITED", severeClipping, CorrectionOperation.GLOBAL_EXPOSURE, CorrectionOperation.WHITE_POINT, CorrectionOperation.BLACK_POINT)
        if (highNoise >= .35f) protections += protection("PRT_HIGH_NOISE", "NOISE_AMPLIFICATION_RISK", highNoise, CorrectionOperation.SHADOW_RECOVERY, CorrectionOperation.DETAIL_ENHANCEMENT, CorrectionOperation.CLARITY)
        if (e.color.whiteBalanceConfidence < .35f) protections += protection("PRT_WB_UNRELIABLE", "INSUFFICIENT_NEUTRAL_EVIDENCE", 1f - e.color.whiteBalanceConfidence, CorrectionOperation.WHITE_BALANCE)
        if (e.color.neutralFraction >= .80f && e.color.meanSaturation <= .03f)
            protections += protection("PRT_POTENTIAL_MONOCHROME", "POTENTIAL_INTENTIONAL_MONOCHROME", e.color.neutralFraction,
                CorrectionOperation.WHITE_BALANCE, CorrectionOperation.VIBRANCE, CorrectionOperation.SATURATION)

        val under = byId["PHT_GLOBAL_UNDEREXPOSED"]; val over = byId["PHT_GLOBAL_OVEREXPOSED"]
        if (under != null || over != null) {
            val d = under ?: over!!; val sign = if (under != null) 1f else -1f
            val target = when (level) { EnhancementLevel.NATURAL -> .27f; EnhancementLevel.ENHANCED -> .32f; EnhancementLevel.EXTREME -> .36f }
            val rawEv = (ln(target / e.luminance.median.coerceAtLeast(.01f).toDouble()) / ln(2.0)).toFloat()
            val highlightHeadroom = (1f - smooth(.01f, .16f, e.luminance.whiteClipping + e.luminance.highlightOccupancy * .22f))
            val intentCaution = if (d.potentiallyIntentional) when (level) { EnhancementLevel.NATURAL -> .38f; EnhancementLevel.ENHANCED -> .62f; EnhancementLevel.EXTREME -> .78f } else 1f
            val value = if (sign > 0) min(rawEv.coerceAtLeast(0f), .8f * highlightHeadroom) else max(rawEv.coerceAtMost(0f), -.6f)
            addOrReject(operations, rejected, d, CorrectionOperation.GLOBAL_EXPOSURE, confidenceGate,
                parameter("exposureEv", value * d.confidence * intentCaution, "EV", -.6f, .8f, "exposure-v2"))
        }
        byId["PHT_EXCESSIVE_HIGHLIGHT_OCCUPANCY"]?.let { d ->
            val maxAmount = when (level) { EnhancementLevel.NATURAL -> -.22f; EnhancementLevel.ENHANCED -> -.42f; EnhancementLevel.EXTREME -> -.60f }
            addOrReject(operations, rejected, d, CorrectionOperation.HIGHLIGHT_COMPRESSION, confidenceGate,
                parameter("highlights", maxAmount * d.severity, "normalized", -.6f, 0f, "highlights-v2"))
        }
        byId["PHT_EXCESSIVE_SHADOW_OCCUPANCY"]?.let { d ->
            val maxAmount = when (level) { EnhancementLevel.NATURAL -> .14f; EnhancementLevel.ENHANCED -> .28f; EnhancementLevel.EXTREME -> .40f }
            val noiseLimit = (1f - highNoise * when (level) { EnhancementLevel.NATURAL -> .9f; EnhancementLevel.ENHANCED -> .75f; EnhancementLevel.EXTREME -> .6f })
            addOrReject(operations, rejected, d, CorrectionOperation.SHADOW_RECOVERY, confidenceGate,
                parameter("shadows", maxAmount * d.severity * noiseLimit, "normalized", 0f, .4f, "shadows-noise-aware-v2"))
        }
        byId["PHT_LOW_GLOBAL_CONTRAST"]?.let { d ->
            val maxAmount = when (level) { EnhancementLevel.NATURAL -> .055f; EnhancementLevel.ENHANCED -> .13f; EnhancementLevel.EXTREME -> .20f }
            addOrReject(operations, rejected, d, CorrectionOperation.TONE_CONTRAST, confidenceGate,
                parameter("contrast", maxAmount * d.severity * (1f - severeClipping * .65f), "normalized", -.1f, .22f, "contrast-v2"))
        }
        byId["PHT_RELIABLE_COLOR_CAST"]?.let { d ->
            if (e.color.whiteBalanceConfidence >= confidenceGate) {
                val strength = when (level) { EnhancementLevel.NATURAL -> .55f; EnhancementLevel.ENHANCED -> .78f; EnhancementLevel.EXTREME -> .92f }
                operations += correction(CorrectionOperation.WHITE_BALANCE, d, listOf(
                    parameter("redGain", 1f + (e.color.proposedRedGain - 1f) * strength, "linear_gain", .85f, 1.15f, "neutral-gain-v2"),
                    parameter("greenGain", 1f + (e.color.proposedGreenGain - 1f) * strength, "linear_gain", .85f, 1.15f, "neutral-gain-v2"),
                    parameter("blueGain", 1f + (e.color.proposedBlueGain - 1f) * strength, "linear_gain", .85f, 1.15f, "neutral-gain-v2")))
            } else rejected += RejectedCorrection(CorrectionOperation.WHITE_BALANCE, "UNRELIABLE_NEUTRAL_EVIDENCE")
        }
        byId["PHT_WEAK_CHROMA"]?.let { d ->
            val maxValue = when (level) { EnhancementLevel.NATURAL -> .06f; EnhancementLevel.ENHANCED -> .14f; EnhancementLevel.EXTREME -> .22f }
            val saturationHeadroom = (1f - e.color.highSaturationFraction * 2f).coerceIn(0f, 1f)
            addOrReject(operations, rejected, d, CorrectionOperation.VIBRANCE, confidenceGate,
                parameter("vibrance", maxValue * d.severity * saturationHeadroom, "normalized", 0f, .25f, "vibrance-headroom-v2"))
        }
        byId["PHT_HIGH_NOISE_RISK"]?.let { d ->
            val maxValue = when (level) { EnhancementLevel.NATURAL -> .12f; EnhancementLevel.ENHANCED -> .23f; EnhancementLevel.EXTREME -> .32f }
            addOrReject(operations, rejected, d, CorrectionOperation.NOISE_REDUCTION, confidenceGate,
                parameter("luminanceNoiseReduction", maxValue * d.severity, "normalized", 0f, .35f, "noise-v2"))
        }
        byId["PHT_LOW_LOCAL_CONTRAST"]?.let { d ->
            val maxValue = when (level) { EnhancementLevel.NATURAL -> .035f; EnhancementLevel.ENHANCED -> .085f; EnhancementLevel.EXTREME -> .13f }
            val noiseLimit = (1f - highNoise * .8f).coerceIn(0f, 1f)
            addOrReject(operations, rejected, d, CorrectionOperation.CLARITY, confidenceGate,
                parameter("clarity", maxValue * d.severity * noiseLimit, "normalized", 0f, .15f, "clarity-noise-aware-v2"))
        }
        byId["PHT_STRONG_BACKLIGHT_PATTERN"]?.let {
            deferred += DeferredCorrection(CorrectionOperation.SHADOW_RECOVERY, "DEFERRED_UNTIL_MILESTONE_3_OR_4", "SEMANTIC_PRIMARY_SUBJECT_MASK")
            reasons += DecisionReason("REGIONAL_NOT_GLOBALIZED", "Backlighting suggests a regional operation, but no semantic subject mask exists", listOf(it.conditionId))
        }
        val resolved = applyProtectionVeto(resolveConflicts(operations, e), protections, rejected)
        if (resolved.isEmpty()) reasons += DecisionReason("NO_OP", noOpReason(diagnoses, protections))
        return CorrectionPlan(
            level = level, sourceIdentity = sourceIdentity, analysisVersion = e.analysisVersion,
            knowledgeVersion = knowledge.knowledgeVersion, operations = resolved, protections = protections,
            deferredOperations = deferred.distinct(), rejectedOperations = rejected.distinct(),
            validationRequirements = validationRequirements(), explanation = reasons,
        )
    }

    private fun addOrReject(target: MutableList<TypedCorrection>, rejected: MutableList<RejectedCorrection>, d: ConditionDiagnosis,
                            operation: CorrectionOperation, gate: Float, parameter: ParameterValue) {
        when {
            operation !in capabilities -> rejected += RejectedCorrection(operation, "UNSUPPORTED_RENDERER_CAPABILITY")
            d.confidence < gate -> rejected += RejectedCorrection(operation, "CONFIDENCE_BELOW_LEVEL_GATE")
            abs(parameter.value) < .003f -> rejected += RejectedCorrection(operation, "EFFECT_BELOW_MEANINGFUL_THRESHOLD")
            else -> target += correction(operation, d, listOf(parameter))
        }
    }
    private fun correction(operation: CorrectionOperation, d: ConditionDiagnosis, parameters: List<ParameterValue>) = TypedCorrection(
        "${operation.name.lowercase()}-v2", operation, "GLOBAL", parameters, listOf(d.conditionId), d.confidence,
        listOf("EXISTING_SMART_MAGIC_GLOBAL_RENDERER"), listOf("NO_NEW_CLIPPING", "NO_NON_FINITE_OUTPUT", "PREVIEW_EXPORT_PLAN_EQUALITY"))
    private fun parameter(name: String, value: Float, unit: String, min: Float, max: Float, formula: String) = ParameterValue(
        name, value.coerceIn(min, max), unit, min, max, formula, ValueProvenance.ENGINEERING_HYPOTHESIS, CalibrationStatus.UNCALIBRATED)
    private fun protection(id: String, reason: String, confidence: Float, vararg operations: CorrectionOperation) = ProtectionDecision(
        id, DecisionDisposition.PROTECT, reason, confidence.coerceIn(0f, 1f), operations.map { it.name })

    private fun resolveConflicts(input: List<TypedCorrection>, e: PhotometricEvidence): List<TypedCorrection> {
        var result = input.distinctBy { it.operation }
        if (result.any { it.operation == CorrectionOperation.SHADOW_RECOVERY } && result.any { it.operation == CorrectionOperation.NOISE_REDUCTION }) {
            result = result.map { c -> if (c.operation != CorrectionOperation.SHADOW_RECOVERY) c else c.copy(parameters = c.parameters.map {
                if (it.name == "shadows") it.copy(value = (it.value * (1f - e.detail.noiseRisk * .35f)).coerceIn(it.minimum, it.maximum)) else it
            }, qualityChecks = c.qualityChecks + "SHADOW_NOISE_REVALIDATION") }
        }
        if (result.any { it.operation == CorrectionOperation.GLOBAL_EXPOSURE }) {
            result = result.map { c -> if (c.operation != CorrectionOperation.GLOBAL_EXPOSURE) c else c.copy(parameters = c.parameters.map {
                if (it.name == "exposureEv" && it.value > 0f) it.copy(value = (it.value * (1f - e.luminance.highlightOccupancy)).coerceIn(it.minimum, it.maximum)) else it
            }, qualityChecks = c.qualityChecks + "HIGHLIGHT_HEADROOM") }
        }
        return result.filterNot { c -> c.parameters.all { abs(it.value) < .003f || (it.unit == "linear_gain" && abs(it.value - 1f) < .003f) } }
    }
    private fun applyProtectionVeto(
        input: List<TypedCorrection>, protections: List<ProtectionDecision>, rejected: MutableList<RejectedCorrection>,
    ): List<TypedCorrection> = input.filter { correction ->
        val veto = protections.firstOrNull { protection ->
            correction.operation.name in protection.affectedOperationIds && when (protection.policyId) {
                "PRT_CLIPPED_ENDPOINTS" -> correction.operation != CorrectionOperation.GLOBAL_EXPOSURE ||
                    correction.parameters.any { it.name == "exposureEv" && it.value > 0f }
                "PRT_HIGH_NOISE" -> protection.confidence >= .70f
                "PRT_BALANCED" -> protection.confidence >= .75f
                else -> true
            }
        }
        if (veto != null) rejected += RejectedCorrection(correction.operation, "VETOED_${veto.reasonCode}")
        veto == null
    }
    private fun noOpReason(diagnoses: List<ConditionDiagnosis>, protections: List<ProtectionDecision>): String = when {
        diagnoses.isEmpty() -> "No measurable condition exceeded the evidence threshold"
        protections.any { it.reasonCode == "ALREADY_BALANCED" } -> "Measured tonal distribution is already balanced"
        else -> "No supported correction passed confidence, protection, and capability checks"
    }
    private fun validationRequirements() = listOf(
        ValidationRequirement("VAL_FINITE", "all_output_channels", "finite and in renderer range"),
        ValidationRequirement("VAL_CLIPPING", "new_endpoint_clipping", "must not increase beyond uncalibrated safety gate"),
        ValidationRequirement("VAL_PLAN_EQUALITY", "plan_hash", "preview and export must use the same plan"),
    )
    private fun smooth(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t)
    }
}

object DiagnosticJson {
    fun report(sourceIdentity: String, evidence: PhotometricEvidence, diagnoses: List<ConditionDiagnosis>,
               graph: SceneGraph, bright: List<BrightRegionEvidence>, plans: CorrectionPlanSet): String {
        val safeIdentity = sha256(sourceIdentity)
        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        fun plan(p: CorrectionPlan) = "{\"level\":${q(p.level.name)},\"operationCount\":${p.operations.size}," +
            "\"operations\":[${p.operations.joinToString(",") { q(it.operation.name) }}]," +
            "\"deferred\":[${p.deferredOperations.joinToString(",") { q(it.reasonCode) }}]," +
            "\"noOp\":${p.operations.isEmpty()}}"
        return "{" +
            "\"schemaVersion\":2,\"sourceHash\":${q(safeIdentity)}," +
            "\"sourceDimensions\":{\"width\":${evidence.sourceWidth},\"height\":${evidence.sourceHeight}}," +
            "\"analysisVersion\":${q(evidence.analysisVersion)},\"knowledgeVersion\":${q(plans.enhanced.knowledgeVersion)}," +
            "\"sampledPixels\":${evidence.sampledPixels},\"regionCount\":${evidence.regions.size}," +
            "\"diagnoses\":[${diagnoses.joinToString(",") { "{\"id\":${q(it.conditionId)},\"severity\":${it.severity},\"confidence\":${it.confidence}}" }}]," +
            "\"brightRegions\":[${bright.joinToString(",") { "{\"id\":${q(it.id)},\"kind\":${q(it.kind.name)},\"confidence\":${it.confidence}}" }}]," +
            "\"semanticPrimarySubject\":null,\"salienceCandidateCount\":${graph.nodes.size}," +
            "\"plans\":{\"natural\":${plan(plans.natural)},\"enhanced\":${plan(plans.enhanced)},\"extreme\":${plan(plans.extreme)}}," +
            "\"analysisNanos\":${evidence.analysisDurationNanos}}"
    }
    fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
