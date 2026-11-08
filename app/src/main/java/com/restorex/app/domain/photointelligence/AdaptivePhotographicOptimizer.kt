package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicParameters
import kotlin.math.abs
import kotlin.math.ln

data class OptimizerCandidateEvaluation(
    val id: String,
    val score: Float,
    val valid: Boolean,
    val rejectionReasons: List<String>,
    val parameters: SmartMagicParameters,
    val impact: PerceptualImpactMetrics,
    val renderNanos: Long,
)

data class AdaptiveOptimizationResult(
    val selectedParameters: SmartMagicParameters,
    val selectedCandidateId: String,
    val evaluations: List<OptimizerCandidateEvaluation>,
    val candidateBudget: Int,
    val stopReason: String,
)

/**
 * Deterministic bounded block-coordinate optimizer. It evaluates actual rendered preview candidates;
 * research preset values are never treated as objectives.
 */
class AdaptivePhotographicOptimizer(
    private val analyzer: PhotometricAnalyzerV2 = PhotometricAnalyzerV2(65_536, intArrayOf(4)),
    private val impactEstimator: PerceptualImpactEstimator = PerceptualImpactEstimator(65_536),
) {
    fun optimize(
        source: RasterImage,
        sourceEvidence: PhotometricEvidence,
        advanced: AdvancedPhotometricEvidence,
        level: EnhancementLevel,
        base: SmartMagicParameters,
        candidateBudget: Int,
        shouldCancel: () -> Boolean = { false },
        onCandidateEvaluated: (completed: Int, budget: Int) -> Unit = { _, _ -> },
        render: (SmartMagicParameters, () -> Boolean) -> RasterImage,
    ): AdaptiveOptimizationResult {
        require(candidateBudget in 2..8)
        val evaluations = mutableListOf<OptimizerCandidateEvaluation>()
        var current = base
        var selected = evaluate("baseline", source, sourceEvidence, advanced, base, shouldCancel, render)
        evaluations += selected
        onCandidateEvaluated(evaluations.size, candidateBudget)

        val candidates = listOf(
            "exposure-white-balance" to { p: SmartMagicParameters -> exposureCandidate(p, sourceEvidence, advanced, level) },
            "tone-structure" to { p: SmartMagicParameters -> toneCandidate(p, sourceEvidence, advanced, level) },
            "color-finish" to { p: SmartMagicParameters -> colorCandidate(p, sourceEvidence, advanced, level) },
            "detail-finish" to { p: SmartMagicParameters -> detailCandidate(p, sourceEvidence, advanced, level) },
        )
        for ((id, transform) in candidates) {
            if (evaluations.size >= candidateBudget || shouldCancel()) break
            val proposed = transform(current)
            if (proposed == current) continue
            val evaluation = evaluate(id, source, sourceEvidence, advanced, proposed, shouldCancel, render)
            evaluations += evaluation
            onCandidateEvaluated(evaluations.size, candidateBudget)
            if (evaluation.valid && (!selected.valid || evaluation.score + .0001f < selected.score)) {
                current = proposed
                selected = evaluation
            }
        }
        if (!selected.valid) {
            val neutral = SmartMagicParameters(overallStrength = base.overallStrength)
            val safe = if (evaluations.size < candidateBudget)
                evaluate("safe-no-op", source, sourceEvidence, advanced, neutral, shouldCancel, render) else null
            if (safe != null) evaluations += safe
            if (safe != null) onCandidateEvaluated(evaluations.size, candidateBudget)
            if (safe?.valid == true) selected = safe
        }
        return AdaptiveOptimizationResult(selected.parameters, selected.id, evaluations, candidateBudget,
            if (evaluations.size >= candidateBudget) "CANDIDATE_BUDGET_REACHED" else "NO_FURTHER_IMPROVEMENT")
    }

    private fun evaluate(
        id: String,
        source: RasterImage,
        sourceEvidence: PhotometricEvidence,
        advanced: AdvancedPhotometricEvidence,
        parameters: SmartMagicParameters,
        shouldCancel: () -> Boolean,
        render: (SmartMagicParameters, () -> Boolean) -> RasterImage,
    ): OptimizerCandidateEvaluation {
        val started = System.nanoTime()
        val output = render(parameters, shouldCancel)
        val evidence = analyzer.analyze(output, shouldCancel)
        val impact = impactEstimator.estimate(source, output)
        val newWhiteClip = (evidence.luminance.whiteClipping - sourceEvidence.luminance.whiteClipping).coerceAtLeast(0f)
        val newBlackClip = (evidence.luminance.blackClipping - sourceEvidence.luminance.blackClipping).coerceAtLeast(0f)
        val failures = buildList {
            if (newWhiteClip > .008f) add("NEW_WHITE_CLIPPING")
            if (newBlackClip > .010f) add("NEW_BLACK_CLIPPING")
            if (impact.saturationAnomalyFraction > .006f) add("SATURATION_ANOMALY")
            if (impact.haloRisk > .008f) add("HALO_RISK")
            if (!impact.meanOklabDelta.isFinite()) add("NON_FINITE_IMPACT")
        }
        val preserveIntent = maxOf(advanced.lowKeyIntent, advanced.highKeyIntent, advanced.warmLightIntent)
        val targetMedian = if (preserveIntent > .55f) sourceEvidence.luminance.median else when {
            advanced.shadowRecoveryConfidence > .45f -> .205f
            else -> .19f
        }
        val toneError = abs(evidence.luminance.median - targetMedian)
        val rangeTarget = sourceEvidence.luminance.robustRange.coerceIn(.30f, .66f)
        val rangeError = abs(evidence.luminance.robustRange - rangeTarget) *
            (if (advanced.localContrastDeficit > .4f) .55f else .25f)
        val colorDrift = abs(evidence.color.meanSaturation - sourceEvidence.color.meanSaturation)
        val score = toneError * (1f - preserveIntent * .65f) + rangeError +
            newWhiteClip * 8f + newBlackClip * 6f +
            (colorDrift - .025f).coerceAtLeast(0f) * 1.8f + impact.haloRisk * 5f +
            impact.saturationAnomalyFraction * 7f + impact.meanOklabDelta * .08f
        return OptimizerCandidateEvaluation(id, score, failures.isEmpty(), failures, parameters, impact,
            System.nanoTime() - started)
    }

    private fun exposureCandidate(
        p: SmartMagicParameters, evidence: PhotometricEvidence, advanced: AdvancedPhotometricEvidence,
        level: EnhancementLevel,
    ): SmartMagicParameters {
        val intent = maxOf(advanced.lowKeyIntent, advanced.highKeyIntent)
        if (intent > .65f) return p
        val target = if (advanced.shadowRecoveryConfidence > .45f) .205f else .19f
        val ev = (ln(target / evidence.luminance.median.coerceAtLeast(.02f)) / LN2).toFloat()
        val cap = when (level) { EnhancementLevel.NATURAL -> .08f; EnhancementLevel.ENHANCED -> .14f; EnhancementLevel.EXTREME -> .20f }
        val adjustment = ev.coerceIn(-cap, cap) * (1f - advanced.noiseConstraint * .55f)
        return p.copy(exposureEv = (p.exposureEv + adjustment).coerceIn(-.6f, .8f))
    }

    private fun toneCandidate(
        p: SmartMagicParameters, evidence: PhotometricEvidence, advanced: AdvancedPhotometricEvidence,
        level: EnhancementLevel,
    ): SmartMagicParameters {
        val gammaCap = when (level) { EnhancementLevel.NATURAL -> .08f; EnhancementLevel.ENHANCED -> .18f; EnhancementLevel.EXTREME -> .28f }
        val deficit = ((.20f - evidence.luminance.median) / .18f).coerceIn(0f, 1f)
        val gamma = 1f + gammaCap * deficit * advanced.shadowRecoveryConfidence *
            (1f - advanced.lowKeyIntent) * (1f - advanced.noiseConstraint * .6f)
        val shadowCap = when (level) { EnhancementLevel.NATURAL -> .16f; EnhancementLevel.ENHANCED -> .28f; EnhancementLevel.EXTREME -> .36f }
        val shadows = maxOf(p.shadows, advanced.shadowRecoveryConfidence * shadowCap).coerceIn(0f, .4f)
        val highlights = minOf(p.highlights, -advanced.recoverableHighlightConfidence * .22f).coerceIn(-.6f, 0f)
        val brightnessCap = when (level) { EnhancementLevel.NATURAL -> .035f; EnhancementLevel.ENHANCED -> .075f; EnhancementLevel.EXTREME -> .12f }
        val brightness = (brightnessCap * deficit * advanced.shadowRecoveryConfidence *
            (1f - advanced.lowKeyIntent) * (1f - advanced.noiseConstraint * .7f)).coerceIn(0f, brightnessCap)
        val contrastCap = when (level) { EnhancementLevel.NATURAL -> .055f; EnhancementLevel.ENHANCED -> .10f; EnhancementLevel.EXTREME -> .15f }
        val contrast = maxOf(p.contrast,
            advanced.localContrastDeficit * contrastCap * (1f - advanced.atmosphericIntentRisk * .65f))
            .coerceIn(-.1f, .22f)
        val whites = when {
            evidence.luminance.whiteClipping > .01f -> minOf(p.whites, -.04f)
            evidence.luminance.p95 < .72f -> maxOf(p.whites, .035f * (1f - advanced.highKeyIntent))
            else -> p.whites
        }.coerceIn(-.1f, .15f)
        val blacks = when {
            evidence.luminance.blackClipping > .015f -> maxOf(p.blacks, .025f)
            evidence.luminance.p05 > .075f && advanced.lowKeyIntent < .45f -> minOf(p.blacks, -.025f)
            else -> p.blacks
        }.coerceIn(-.15f, .1f)
        return p.copy(brightness = maxOf(p.brightness, brightness), midtoneGamma = gamma.coerceIn(.8f, 1.5f),
            shadows = shadows, highlights = highlights, contrast = contrast, whites = whites, blacks = blacks)
    }

    private fun colorCandidate(
        p: SmartMagicParameters, evidence: PhotometricEvidence, advanced: AdvancedPhotometricEvidence,
        level: EnhancementLevel,
    ): SmartMagicParameters {
        val cap = when (level) { EnhancementLevel.NATURAL -> .10f; EnhancementLevel.ENHANCED -> .17f; EnhancementLevel.EXTREME -> .22f }
        val room = ((.30f - evidence.color.meanSaturation) / .25f).coerceIn(0f, 1f)
        return p.copy(vibrance = minOf(p.vibrance, cap * room * (1f - advanced.warmLightIntent * .35f)))
    }

    private fun detailCandidate(
        p: SmartMagicParameters, evidence: PhotometricEvidence, advanced: AdvancedPhotometricEvidence,
        level: EnhancementLevel,
    ): SmartMagicParameters {
        val modeCap = when (level) { EnhancementLevel.NATURAL -> .65f; EnhancementLevel.ENHANCED -> .85f; EnhancementLevel.EXTREME -> 1f }
        val safety = (1f - advanced.noiseConstraint * .85f) * advanced.detailConfidence * modeCap
        return p.copy(clarity = p.clarity * safety, texture = p.texture * safety,
            sharpening = p.sharpening * safety,
            luminanceNoiseReduction = maxOf(p.luminanceNoiseReduction,
                evidence.detail.noiseRisk * .18f).coerceIn(0f, .35f),
            chromaNoiseReduction = maxOf(p.chromaNoiseReduction,
                evidence.detail.noiseRisk * .16f).coerceIn(0f, .3f))
    }

    private companion object { const val LN2 = .6931471805599453 }
}
