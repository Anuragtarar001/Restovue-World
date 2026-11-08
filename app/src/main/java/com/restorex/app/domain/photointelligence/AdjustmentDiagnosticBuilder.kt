package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.smartmagic.SmartMagicParameters
import kotlin.math.abs

/** Builds a complete report from actual analysis, plans, candidates and accepted render output. */
class AdjustmentDiagnosticBuilder {
    fun build(
        session: SmartMagicProductionSession,
        render: SmartMagicProductionRender,
        sourceGeneration: Long,
        renderGeneration: Long,
    ): AdjustmentDiagnosticReport {
        val parameters = session.globalParameters.getValue(render.level)
        val regionalOperations = residualRegionalOperations(
            session.regionalPlans[render.level]?.operations.orEmpty(), parameters,
            session.subjectAssessment?.decision == SemanticSubjectDecision.PROTECT_CORRECTLY_EXPOSED_SUBJECT)
        val appliedRegionalStrength = render.regionalResult?.appliedStrength ?: 0f
        val regionalAccepted = render.strategy in setOf(ProductionRenderStrategy.REGIONAL_ONLY, ProductionRenderStrategy.COMBINED) &&
            render.outcome in setOf(ProductionRenderOutcome.APPLIED, ProductionRenderOutcome.REDUCED_FOR_QUALITY)
        val regionalFailure = render.regionalResult?.metrics?.failureReasons.orEmpty().joinToString().ifBlank {
            render.explanation.joinToString { it.code }.ifBlank { "Regional quality validation did not accept the proposal" }
        }
        val entries = AdjustmentDiagnosticId.entries.map { id ->
            when (id) {
                AdjustmentDiagnosticId.WHITE_BALANCE_TEMPERATURE -> unsupported(id, "Smart Magic uses measured RGB gains and has no calibrated temperature unit")
                AdjustmentDiagnosticId.TINT -> unsupported(id, "No production tint operator is implemented")
                AdjustmentDiagnosticId.HUE_CORRECTION -> unsupported(id, "No production hue-rotation operator is implemented")
                AdjustmentDiagnosticId.SELECTIVE_COLOR -> unsupported(id, "No semantic selective-color operator is implemented")
                AdjustmentDiagnosticId.BACKGROUND_EXPOSURE -> notEvaluated(id, "The current production planner has no background-exposure decision rule", "Background")

                AdjustmentDiagnosticId.SUBJECT_EXPOSURE -> regional(
                    id, regionalOperations, RegionalOperationType.EXPOSURE, RegionalTarget.PERSON,
                    appliedRegionalStrength, regionalAccepted, regionalFailure, session,
                    "Subject/background exposure relationship did not justify local exposure", "Person")
                AdjustmentDiagnosticId.FACE_ILLUMINATION -> regionalById(
                    id, regionalOperations, "face-midtone-gamma", appliedRegionalStrength,
                    regionalAccepted, regionalFailure, session,
                    if (session.semanticResult.faces.isEmpty()) "No reliable face evidence was available" else "Detected faces did not require local illumination",
                    "Detected faces")
                AdjustmentDiagnosticId.LOCAL_MIDTONE_CORRECTION -> regional(
                    id, regionalOperations, RegionalOperationType.MIDTONE_GAMMA, RegionalTarget.PERSON,
                    appliedRegionalStrength, regionalAccepted, regionalFailure, session,
                    "Regional midtone deficit was below the planner threshold", "Person")
                AdjustmentDiagnosticId.REGIONAL_CONTRAST -> regional(
                    id, regionalOperations, RegionalOperationType.CONTRAST, RegionalTarget.BACKGROUND,
                    appliedRegionalStrength, regionalAccepted, regionalFailure, session,
                    "Background contrast did not justify a regional correction", "Background")
                AdjustmentDiagnosticId.LOCAL_CONTRAST -> regional(
                    id, regionalOperations, RegionalOperationType.LOCAL_CONTRAST, RegionalTarget.BACKGROUND,
                    appliedRegionalStrength, regionalAccepted, regionalFailure, session,
                    "Measured local-contrast deficit did not pass regional eligibility", "Background")
                AdjustmentDiagnosticId.DETAIL_RECOVERY -> regional(
                    id, regionalOperations, RegionalOperationType.DETAIL, RegionalTarget.BACKGROUND,
                    appliedRegionalStrength, regionalAccepted, regionalFailure, session,
                    "Detail evidence or noise protection did not justify regional recovery", "Background")

                AdjustmentDiagnosticId.HIGHLIGHTS -> regionalOrGlobal(id, RegionalOperationType.HIGHLIGHTS,
                    regionalOperations, appliedRegionalStrength, regionalAccepted, regionalFailure, session, render,
                    components(id, parameters))
                AdjustmentDiagnosticId.SHADOWS -> regionalOrGlobal(id, RegionalOperationType.SHADOWS,
                    regionalOperations, appliedRegionalStrength, regionalAccepted, regionalFailure, session, render,
                    components(id, parameters))
                AdjustmentDiagnosticId.VIBRANCE -> regionalOrGlobal(id, RegionalOperationType.VIBRANCE,
                    regionalOperations, appliedRegionalStrength, regionalAccepted, regionalFailure, session, render,
                    components(id, parameters))
                AdjustmentDiagnosticId.DEHAZE -> regionalOrGlobal(id, RegionalOperationType.SELECTIVE_DEHAZE,
                    regionalOperations, appliedRegionalStrength, regionalAccepted, regionalFailure, session, render,
                    components(id, parameters))

                else -> global(id, components(id, parameters), session, render)
            }
        }
        return AdjustmentDiagnosticReport(sourceGeneration, renderGeneration, render.level, session.sessionId, entries)
    }

    private fun global(
        id: AdjustmentDiagnosticId,
        values: List<AdjustmentDiagnosticValue>,
        session: SmartMagicProductionSession,
        render: SmartMagicProductionRender,
    ): AdjustmentDiagnosticEntry {
        if (render.strategy == ProductionRenderStrategy.REGIONAL_ONLY) return AdjustmentDiagnosticEntry(
            id, AdjustmentDiagnosticStatus.SKIPPED, values, "Global",
            "A reliable regional person plan was selected; the global operation was not rendered",
        )
        val nonNeutral = values.any { value -> value.proposedValue?.let { abs(it - value.neutralValue) > .0005f } == true }
        val invalidCandidates = rejectedCandidateReasons(id, session, render.level)
        if (render.strategy == ProductionRenderStrategy.NO_OP && nonNeutral) return AdjustmentDiagnosticEntry(
            id, AdjustmentDiagnosticStatus.REJECTED, values, "Global",
            invalidCandidates.ifBlank { "The proposed global result was not accepted: ${render.outcome.name.lowercase().replace('_', ' ')}" },
        )
        if (!nonNeutral && invalidCandidates.isNotBlank()) return AdjustmentDiagnosticEntry(
            id, AdjustmentDiagnosticStatus.REJECTED, values, "Global", invalidCandidates,
        )
        if (!nonNeutral) return AdjustmentDiagnosticEntry(
            id, AdjustmentDiagnosticStatus.NOT_REQUIRED, values, "Global", neutralReason(id),
        )
        val applied = values.map { it.copy(appliedValue = it.proposedValue) }
        return AdjustmentDiagnosticEntry(id, AdjustmentDiagnosticStatus.APPLIED, applied, "Global",
            "The accepted ${render.level.name.lowercase()} global plan rendered this non-neutral value")
    }

    private fun regionalOrGlobal(
        id: AdjustmentDiagnosticId,
        type: RegionalOperationType,
        operations: List<RegionalOperation>,
        strength: Float,
        accepted: Boolean,
        failure: String,
        session: SmartMagicProductionSession,
        render: SmartMagicProductionRender,
        globalValues: List<AdjustmentDiagnosticValue>,
    ): AdjustmentDiagnosticEntry {
        val operation = operations.firstOrNull { it.type == type }
        if (operation == null) return global(id, globalValues, session, render)
        return regionalEntry(id, operation, strength, accepted, failure)
    }

    private fun regional(
        id: AdjustmentDiagnosticId,
        operations: List<RegionalOperation>,
        type: RegionalOperationType,
        target: RegionalTarget,
        strength: Float,
        accepted: Boolean,
        failure: String,
        session: SmartMagicProductionSession,
        notRequiredReason: String,
        region: String,
    ): AdjustmentDiagnosticEntry {
        val operation = operations.firstOrNull { it.type == type && it.target == target }
        if (operation != null) return regionalEntry(id, operation, strength, accepted, failure)
        if (session.semanticResult.personMaskReference?.quality?.accepted != true) return AdjustmentDiagnosticEntry(
            id, AdjustmentDiagnosticStatus.SKIPPED, emptyList(), region,
            "A reliable person mask was required but unavailable",
        )
        return AdjustmentDiagnosticEntry(id, AdjustmentDiagnosticStatus.NOT_REQUIRED, emptyList(), region, notRequiredReason)
    }

    private fun regionalById(
        id: AdjustmentDiagnosticId,
        operations: List<RegionalOperation>,
        operationId: String,
        strength: Float,
        accepted: Boolean,
        failure: String,
        session: SmartMagicProductionSession,
        notRequiredReason: String,
        region: String,
    ): AdjustmentDiagnosticEntry {
        val operation = operations.firstOrNull { it.id == operationId ||
            operationId == "face-midtone-gamma" && it.id in setOf(
                "group-face-midtone-gamma", "protected-face-midtone-gamma") }
        if (operation != null) return regionalEntry(id, operation, strength, accepted, failure)
        if (session.semanticResult.faces.isEmpty()) return AdjustmentDiagnosticEntry(
            id, AdjustmentDiagnosticStatus.SKIPPED, emptyList(), region, notRequiredReason,
        )
        return AdjustmentDiagnosticEntry(id, AdjustmentDiagnosticStatus.NOT_REQUIRED, emptyList(), region, notRequiredReason)
    }

    private fun regionalEntry(
        id: AdjustmentDiagnosticId,
        operation: RegionalOperation,
        strength: Float,
        accepted: Boolean,
        failure: String,
    ): AdjustmentDiagnosticEntry {
        val proposed = AdjustmentDiagnosticValue(operation.type.name.lowercase().replace('_', ' '), 0f,
            operation.amount, if (accepted) operation.amount * strength else null, operation.unit)
        return AdjustmentDiagnosticEntry(id,
            if (accepted) AdjustmentDiagnosticStatus.APPLIED else AdjustmentDiagnosticStatus.REJECTED,
            listOf(proposed), operation.target.name.lowercase().replaceFirstChar { it.uppercase() },
            if (accepted) "Accepted regional operation rendered at strength %.3f".format(strength) else failure)
    }

    private fun components(id: AdjustmentDiagnosticId, p: SmartMagicParameters): List<AdjustmentDiagnosticValue> = when (id) {
        AdjustmentDiagnosticId.EXPOSURE -> component("exposure", 0f, p.exposureEv, "EV")
        AdjustmentDiagnosticId.BRIGHTNESS -> component("brightness", 0f, p.brightness, "normalized_luminance")
        AdjustmentDiagnosticId.CONTRAST -> component("contrast", 0f, p.contrast, "normalized")
        AdjustmentDiagnosticId.HIGHLIGHTS -> component("highlights", 0f, p.highlights, "normalized")
        AdjustmentDiagnosticId.SHADOWS -> component("shadows", 0f, p.shadows, "normalized")
        AdjustmentDiagnosticId.WHITES -> component("whites", 0f, p.whites, "normalized")
        AdjustmentDiagnosticId.BLACKS -> component("blacks", 0f, p.blacks, "normalized")
        AdjustmentDiagnosticId.GAMMA_MIDTONE_CURVE -> component("midtone gamma", 1f, p.midtoneGamma, "gamma")
        AdjustmentDiagnosticId.TEXTURE_CLARITY -> listOf(
            value("clarity", 0f, p.clarity, "normalized"), value("texture", 0f, p.texture, "normalized"))
        AdjustmentDiagnosticId.RED_CHANNEL -> component("red gain", 1f, p.redGain, "gain")
        AdjustmentDiagnosticId.GREEN_CHANNEL -> component("green gain", 1f, p.greenGain, "gain")
        AdjustmentDiagnosticId.BLUE_CHANNEL -> component("blue gain", 1f, p.blueGain, "gain")
        AdjustmentDiagnosticId.VIBRANCE -> component("vibrance", 0f, p.vibrance, "normalized")
        AdjustmentDiagnosticId.SATURATION -> component("saturation", 0f, p.saturation, "normalized")
        AdjustmentDiagnosticId.LUMINANCE_NOISE_REDUCTION -> component("luminance noise reduction", 0f, p.luminanceNoiseReduction, "normalized")
        AdjustmentDiagnosticId.COLOR_NOISE_REDUCTION -> component("color noise reduction", 0f, p.chromaNoiseReduction, "normalized")
        AdjustmentDiagnosticId.SHARPENING -> component("sharpening amount", 0f, p.sharpening, "normalized")
        AdjustmentDiagnosticId.DEHAZE -> component("dehaze", 0f, p.dehaze, "normalized")
        else -> emptyList()
    }

    private fun component(name: String, neutral: Float, proposed: Float, unit: String) = listOf(value(name, neutral, proposed, unit))
    private fun value(name: String, neutral: Float, proposed: Float, unit: String) =
        AdjustmentDiagnosticValue(name, neutral, proposed, null, unit)

    private fun unsupported(id: AdjustmentDiagnosticId, reason: String) = AdjustmentDiagnosticEntry(
        id, AdjustmentDiagnosticStatus.UNSUPPORTED, emptyList(), "Not applicable", reason)
    private fun notEvaluated(id: AdjustmentDiagnosticId, reason: String, region: String) = AdjustmentDiagnosticEntry(
        id, AdjustmentDiagnosticStatus.NOT_EVALUATED, emptyList(), region, reason)

    private fun neutralReason(id: AdjustmentDiagnosticId): String = when (id) {
        AdjustmentDiagnosticId.EXPOSURE -> "Measured global exposure did not justify a non-neutral correction"
        AdjustmentDiagnosticId.HIGHLIGHTS -> "Recoverable highlight evidence did not justify compression"
        AdjustmentDiagnosticId.SHADOWS -> "Shadow occupancy and noise constraints did not justify recovery"
        AdjustmentDiagnosticId.RED_CHANNEL, AdjustmentDiagnosticId.GREEN_CHANNEL, AdjustmentDiagnosticId.BLUE_CHANNEL ->
            "Neutral-region evidence did not justify a channel gain change"
        AdjustmentDiagnosticId.LUMINANCE_NOISE_REDUCTION, AdjustmentDiagnosticId.COLOR_NOISE_REDUCTION ->
            "Measured noise risk did not justify denoising"
        else -> "The measured evidence produced a neutral value for this evaluated operation"
    }

    private fun rejectedCandidateReasons(
        id: AdjustmentDiagnosticId,
        session: SmartMagicProductionSession,
        level: EnhancementLevel,
    ): String {
        val result = session.optimizationResults[level] ?: return ""
        val rejected = result.evaluations.filter { !it.valid && candidateChanges(id, it.parameters) }
        return rejected.flatMap { it.rejectionReasons }.distinct().joinToString().let { reasons ->
            if (reasons.isBlank()) "" else "Candidate rejected: $reasons"
        }
    }

    private fun candidateChanges(id: AdjustmentDiagnosticId, p: SmartMagicParameters): Boolean = components(id, p).any {
        it.proposedValue?.let { proposed -> abs(proposed - it.neutralValue) > .0005f } == true
    }

    private fun residualRegionalOperations(
        operations: List<RegionalOperation>, global: SmartMagicParameters, protectCorrectSubject: Boolean,
    ): List<RegionalOperation> {
        val residual = operations.mapNotNull { operation ->
        val residual = when (operation.type) {
            RegionalOperationType.EXPOSURE -> operation.amount - global.exposureEv.coerceAtLeast(0f) * .65f -
                global.brightness.coerceAtLeast(0f) * .35f
            RegionalOperationType.SHADOWS -> operation.amount - global.shadows.coerceAtLeast(0f) * .50f
            RegionalOperationType.MIDTONE_GAMMA -> operation.amount -
                (global.midtoneGamma - 1f).coerceAtLeast(0f) * .55f - global.brightness.coerceAtLeast(0f) * .25f
            RegionalOperationType.HIGHLIGHTS -> operation.amount - (-global.highlights).coerceAtLeast(0f) * .35f
            else -> operation.amount
        }.coerceAtLeast(0f)
            if (residual < .004f) null else operation.copy(amount = residual)
        }.toMutableList()
        val protectiveExposure = if (protectCorrectSubject)
            (-global.exposureEv).coerceAtLeast(0f) * .82f + (-global.brightness).coerceAtLeast(0f) * .35f else 0f
        if (protectiveExposure >= .015f && residual.none {
                it.type == RegionalOperationType.EXPOSURE && it.target == RegionalTarget.PERSON
            }) {
            residual += RegionalOperation("protect-subject-from-global-darkening", RegionalOperationType.EXPOSURE,
                RegionalTarget.PERSON, protectiveExposure.coerceAtMost(.42f), "EV", .92f,
                listOf("CORRECTLY_EXPOSED_SUBJECT", "GLOBAL_NEGATIVE_EXPOSURE"),
                listOf("NO_NEW_CLIPPING", "PROTECTED_INTERIOR", "BOUNDARY_DISCONTINUITY", "FINITE_OUTPUT"))
        }
        return residual
    }
}
