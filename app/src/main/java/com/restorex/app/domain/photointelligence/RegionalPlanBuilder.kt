package com.restorex.app.domain.photointelligence

import kotlin.math.abs

/** Converts the Milestone 2/3 evidence and plans into executable, bounded regional operations. */
class RegionalPlanBuilder {
    fun build(
        sourceIdentity: String,
        transformationVersion: Long,
        level: EnhancementLevel,
        supplement: SemanticPlanningSupplement,
        regional: SemanticRegionalEvidence,
        maskId: String,
        advanced: AdvancedPhotometricEvidence? = null,
        faces: List<FaceEvidence> = emptyList(),
        faceIlluminationDeficit: Float = 0f,
    ): RegionalCorrectionPlan {
        val base = when (level) {
            EnhancementLevel.NATURAL -> supplement.basePlans.natural
            EnhancementLevel.ENHANCED -> supplement.basePlans.enhanced
            EnhancementLevel.EXTREME -> supplement.basePlans.extreme
        }
        val operations = mutableListOf<RegionalOperation>()
        val assessment = supplement.subjectAssessment
        when (assessment.decision) {
            SemanticSubjectDecision.REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE -> {
                val deficit = (regional.background.meanLinearLuminance - regional.person.meanLinearLuminance).coerceAtLeast(0f)
                val headroom = (1f - regional.person.highlightOccupancy * 3f - regional.person.whiteClipping * 6f).coerceIn(0f, 1f)
                val noiseGuard = (1f - regional.person.noiseRisk * 1.35f).coerceIn(.25f, 1f)
                val levelTarget = when (level) { EnhancementLevel.NATURAL -> .28f; EnhancementLevel.ENHANCED -> .48f; EnhancementLevel.EXTREME -> .68f }
                val ev = (deficit * 2.2f * levelTarget * headroom * noiseGuard).coerceIn(0f, when (level) {
                    EnhancementLevel.NATURAL -> .28f; EnhancementLevel.ENHANCED -> .50f; EnhancementLevel.EXTREME -> .72f
                })
                if (ev >= .025f) operations += operation("subject-exposure", RegionalOperationType.EXPOSURE, RegionalTarget.PERSON, ev, "EV", assessment.confidence)
                val shadow = (regional.person.shadowOccupancy * levelTarget * noiseGuard * .45f).coerceIn(0f, .30f)
                if (shadow >= .015f) operations += operation("subject-shadows", RegionalOperationType.SHADOWS, RegionalTarget.PERSON, shadow, "normalized", assessment.confidence)
                val hasLocalizedFace = faces.isNotEmpty()
                if (hasLocalizedFace || assessment.confidence >= .42f) {
                    val gammaCap = if (hasLocalizedFace) when (level) {
                        EnhancementLevel.NATURAL -> .14f
                        EnhancementLevel.ENHANCED -> .27f
                        EnhancementLevel.EXTREME -> .38f
                    } else when (level) {
                        EnhancementLevel.NATURAL -> .08f
                        EnhancementLevel.ENHANCED -> .14f
                        EnhancementLevel.EXTREME -> .22f
                    }
                    val levelGamma = when (level) {
                        EnhancementLevel.NATURAL -> .62f
                        EnhancementLevel.ENHANCED -> .86f
                        EnhancementLevel.EXTREME -> 1f
                    }
                    val gamma = (deficit * 2.15f * headroom * noiseGuard * levelGamma).coerceIn(0f, gammaCap)
                    if (gamma >= .04f) operations += operation(
                        if (hasLocalizedFace) "face-midtone-gamma" else "subject-midtone-gamma",
                        RegionalOperationType.MIDTONE_GAMMA, RegionalTarget.PERSON, gamma,
                        "gamma_delta", assessment.confidence)
                }
                val highlight = (advanced?.recoverableHighlightConfidence ?: 0f) * levelTarget * .13f
                if (highlight >= .015f) operations += operation("subject-highlights", RegionalOperationType.HIGHLIGHTS,
                    RegionalTarget.PERSON, highlight.coerceAtMost(.10f), "normalized", assessment.confidence)
            }
            SemanticSubjectDecision.PROTECT_CORRECTLY_EXPOSED_SUBJECT -> {
                val contrastNeed = (0.20f - abs(regional.background.meanLinearLuminance - .32f)).coerceIn(0f, .20f)
                val levelStrength = when (level) { EnhancementLevel.NATURAL -> .30f; EnhancementLevel.ENHANCED -> .55f; EnhancementLevel.EXTREME -> .72f }
                val contrast = (contrastNeed * levelStrength).coerceIn(0f, .12f)
                if (contrast >= .01f) operations += operation("background-contrast", RegionalOperationType.CONTRAST, RegionalTarget.BACKGROUND, contrast, "normalized", assessment.confidence)
                val baseVibrance = base.operations.firstOrNull { it.operation == CorrectionOperation.VIBRANCE }
                    ?.parameters?.firstOrNull { it.name == "vibrance" }?.value ?: 0f
                val vibrance = (baseVibrance * levelStrength).coerceIn(0f, .10f)
                if (vibrance >= .01f) operations += operation("background-vibrance", RegionalOperationType.VIBRANCE, RegionalTarget.BACKGROUND, vibrance, "normalized", assessment.confidence)
                if (advanced != null) {
                    val localCaps = when (level) {
                        EnhancementLevel.NATURAL -> .040f
                        EnhancementLevel.ENHANCED -> .080f
                        EnhancementLevel.EXTREME -> .110f
                    }
                    val local = (advanced.localContrastDeficit * advanced.detailConfidence * localCaps)
                        .coerceIn(0f, localCaps)
                    if (local >= .008f) operations += operation("background-local-contrast",
                        RegionalOperationType.LOCAL_CONTRAST, RegionalTarget.BACKGROUND, local,
                        "normalized", assessment.confidence * (1f - advanced.atmosphericIntentRisk * .55f))

                    val dehazeCaps = when (level) {
                        EnhancementLevel.NATURAL -> .020f
                        EnhancementLevel.ENHANCED -> .040f
                        EnhancementLevel.EXTREME -> .060f
                    }
                    val dehaze = (advanced.hazeConfidence * (1f - advanced.atmosphericIntentRisk) * dehazeCaps)
                        .coerceIn(0f, dehazeCaps)
                    if (dehaze >= .006f) operations += operation("background-selective-dehaze",
                        RegionalOperationType.SELECTIVE_DEHAZE, RegionalTarget.BACKGROUND, dehaze,
                        "normalized", assessment.confidence * advanced.hazeConfidence)

                    val detailCaps = when (level) {
                        EnhancementLevel.NATURAL -> .012f
                        EnhancementLevel.ENHANCED -> .024f
                        EnhancementLevel.EXTREME -> .034f
                    }
                    val detail = advanced.detailConfidence * (1f - advanced.noiseConstraint) * detailCaps
                    if (detail >= .007f) operations += operation("background-detail",
                        RegionalOperationType.DETAIL, RegionalTarget.BACKGROUND, detail,
                        "normalized", assessment.confidence * advanced.detailConfidence)
                }
                if (faces.isNotEmpty() && faceIlluminationDeficit >= .018f) {
                    val mode = when (level) {
                        EnhancementLevel.NATURAL -> .75f
                        EnhancementLevel.ENHANCED -> 1.15f
                        EnhancementLevel.EXTREME -> 1.55f
                    }
                    val gammaCap = if (faces.size == 1) when (level) {
                        EnhancementLevel.NATURAL -> .10f
                        EnhancementLevel.ENHANCED -> .16f
                        EnhancementLevel.EXTREME -> .22f
                    } else when (level) {
                        EnhancementLevel.NATURAL -> .13f
                        EnhancementLevel.ENHANCED -> .23f
                        EnhancementLevel.EXTREME -> .32f
                    }
                    val gamma = (faceIlluminationDeficit * 2.35f * mode).coerceIn(0f, gammaCap)
                    operations += operation(if (faces.size > 1) "group-face-midtone-gamma" else "protected-face-midtone-gamma",
                        RegionalOperationType.MIDTONE_GAMMA,
                        RegionalTarget.PERSON, gamma, "gamma_delta", assessment.confidence)
                }
            }
            else -> Unit
        }
        return RegionalCorrectionPlan(sourceIdentity = sourceIdentity, transformationVersion = transformationVersion,
            maskId = maskId, level = level, operations = operations, protections = supplement.protections,
            explanation = base.explanation + DecisionReason(if (operations.isEmpty()) "REGIONAL_NO_OP" else "REGIONAL_PLAN_READY",
                if (operations.isEmpty()) "No safe regional correction was justified" else "Regional operations derived from semantic and photometric evidence"))
    }

    private fun operation(id: String, type: RegionalOperationType, target: RegionalTarget, amount: Float, unit: String, confidence: Float) =
        RegionalOperation(id, type, target, amount, unit, confidence, listOf("SEMANTIC_SUBJECT_ASSESSMENT", "MASK_WEIGHTED_REGIONAL_PHOTOMETRY"),
            listOf("NO_NEW_CLIPPING", "PROTECTED_INTERIOR", "BOUNDARY_DISCONTINUITY", "FINITE_OUTPUT"))
}
