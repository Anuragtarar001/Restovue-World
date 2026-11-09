package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.smartmagic.SmartMagicAnalysis
import com.restorex.app.domain.smartmagic.SmartMagicParameterEngine
import com.restorex.app.domain.smartmagic.SmartMagicParameters
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * Target-based technical baseline. The three Auto strengths are diagnostics describing how much
 * endpoint, contrast, and general correction evidence exists; they are not sequential manual
 * filters. One coordinated linear-light parameter set is emitted to avoid repeated stretching.
 */
data class AdaptiveAutoPlan(
    val schemaVersion: Int = 1,
    val autoEnhanceStrength: Float,
    val autoContrastStrength: Float,
    val autoLevelsStrength: Float,
    val targetMedianLinear: Float,
    val measuredMedianLinear: Float,
    val neutralEvidenceConfidence: Float,
    val baseline: SmartMagicParameters,
    val explanation: List<DecisionReason>,
) {
    init {
        require(schemaVersion == 1)
        listOf(autoEnhanceStrength, autoContrastStrength, autoLevelsStrength,
            neutralEvidenceConfidence).forEach { require(it.isFinite() && it in 0f..1f) }
        require(targetMedianLinear.isFinite() && targetMedianLinear in .05f.. .5f)
        require(measuredMedianLinear.isFinite() && measuredMedianLinear in 0f..1f)
    }
}

class AdaptiveAutoController {
    fun plan(analysis: SmartMagicAnalysis, advanced: AdvancedPhotometricEvidence): AdaptiveAutoPlan {
        val l = analysis.luminance
        val c = analysis.color
        val s = analysis.scenes
        val base = SmartMagicParameterEngine(.72f).parametersFor(analysis)

        val intentGuard = (1f - max(advanced.lowKeyIntent, advanced.highKeyIntent) * .78f)
            .coerceIn(.18f, 1f)
        val rangeNeed = ((.64f - l.effectiveRange) / .42f).coerceIn(0f, 1f)
        val unusedBlackRange = ((l.p05 - .018f) / .09f).coerceIn(0f, 1f)
        val unusedWhiteRange = ((.91f - l.p95) / .38f).coerceIn(0f, 1f)
        val clippingGuard = (1f - l.highlightClippingFraction * 8f - l.shadowClippingFraction * 3f)
            .coerceIn(.15f, 1f)
        val levelsStrength = (rangeNeed * (.35f + .65f * max(unusedBlackRange, unusedWhiteRange)) *
            clippingGuard * intentGuard).coerceIn(0f, 1f)

        val globalContrastNeed = ((.29f - l.globalContrast) / .24f).coerceIn(0f, 1f)
        val localContrastNeed = ((.025f - l.localContrast) / .022f).coerceIn(0f, 1f)
        val rawContrastNeed = max(globalContrastNeed, localContrastNeed * .82f)
        // Endpoint expansion already contributes contrast; only retain the residual need.
        val contrastStrength = ((rawContrastNeed - levelsStrength * .30f) * intentGuard *
            (1f - advanced.atmosphericIntentRisk * .50f)).coerceIn(0f, 1f)

        val targetMedian = (.19f + s.portrait * .025f + s.backlit * .015f -
            advanced.lowKeyIntent * .055f + advanced.highKeyIntent * .055f)
            .coerceIn(.12f, .29f)
        val medianDeficit = ((targetMedian - l.median) / targetMedian.coerceAtLeast(.05f)).coerceIn(0f, 1f)
        val exposureError = abs((ln(targetMedian / l.median.coerceAtLeast(.012f)) / LN_2).toFloat())
            .coerceIn(0f, 1f)
        val shadowNeed = ((.16f - l.p25) / .145f).coerceIn(0f, 1f) *
            (1f - advanced.noiseConstraint * .72f)
        val chromaNeed = ((.14f - c.meanSaturation) / .14f).coerceIn(0f, 1f) *
            (1f - c.highlySaturatedFraction * 2.5f).coerceIn(0f, 1f)
        val enhanceStrength = ((medianDeficit * .38f + exposureError * .20f + shadowNeed * .24f +
            chromaNeed * .08f + advanced.localContrastDeficit * .10f) * intentGuard)
            .coerceIn(0f, 1f)

        val neutralPeak = max(c.neutralRed, max(c.neutralGreen, c.neutralBlue)).coerceAtLeast(.01f)
        val neutralSpread = (neutralPeak - minOf(c.neutralRed, c.neutralGreen, c.neutralBlue)) / neutralPeak
        val neutralConfidence = (c.whiteBalanceConfidence *
            (1f - advanced.warmLightIntent * .88f) *
            (1f - advanced.atmosphericIntentRisk * .35f) *
            (1f - ((neutralSpread - .20f) / .35f).coerceIn(0f, 1f)))
            .coerceIn(0f, 1f)
        fun safeGain(value: Float): Float {
            if (neutralConfidence < .16f || c.neutralFraction < .025f) return 1f
            return (1f + (value - 1f) * neutralConfidence * (.45f + enhanceStrength * .35f))
                .coerceIn(.955f, 1.045f)
        }

        val gammaLift = (medianDeficit * (.11f + s.portrait * .08f + s.backlit * .06f) *
            (1f - advanced.noiseConstraint * .55f) * intentGuard).coerceIn(0f, .24f)
        val brightness = (medianDeficit * .055f * (1f - advanced.noiseConstraint * .6f) * intentGuard)
            .coerceIn(0f, .07f)
        val baseline = base.copy(
            exposureEv = (base.exposureEv * (.58f + enhanceStrength * .42f)).coerceIn(-.45f, .58f),
            brightness = max(base.brightness, brightness),
            contrast = (base.contrast * (.38f + contrastStrength * .62f) + contrastStrength * .025f)
                .coerceIn(-.08f, .16f),
            highlights = (base.highlights * (.65f + enhanceStrength * .35f)).coerceIn(-.48f, 0f),
            shadows = (base.shadows * (.52f + enhanceStrength * .48f)).coerceIn(0f, .32f),
            whites = (base.whites + unusedWhiteRange * levelsStrength * .038f).coerceIn(-.08f, .11f),
            blacks = (base.blacks - unusedBlackRange * levelsStrength * .022f).coerceIn(-.11f, .08f),
            midtoneGamma = max(base.midtoneGamma, 1f + gammaLift).coerceIn(.86f, 1.32f),
            redGain = safeGain(base.redGain),
            greenGain = safeGain(base.greenGain),
            blueGain = safeGain(base.blueGain),
            whiteBalanceConfidence = neutralConfidence,
            saturation = (base.saturation * (.45f + enhanceStrength * .35f)).coerceIn(-.035f, .045f),
            vibrance = (base.vibrance * (.55f + enhanceStrength * .35f)).coerceIn(0f, .16f),
            overallStrength = .72f,
        )
        return AdaptiveAutoPlan(
            autoEnhanceStrength = enhanceStrength,
            autoContrastStrength = contrastStrength,
            autoLevelsStrength = levelsStrength,
            targetMedianLinear = targetMedian,
            measuredMedianLinear = l.median,
            neutralEvidenceConfidence = neutralConfidence,
            baseline = baseline,
            explanation = listOf(
                DecisionReason("ADAPTIVE_AUTO_TARGET", "Technical baseline targets measured tonal defects rather than a universal histogram"),
                DecisionReason("AUTO_OPERATIONS_COORDINATED", "Levels contribution is subtracted from contrast need; one linear-light plan is rendered"),
                DecisionReason(if (neutralConfidence >= .16f) "WHITE_BALANCE_ELIGIBLE" else "WHITE_BALANCE_PROTECTED",
                    if (neutralConfidence >= .16f) "Reliable neutral evidence permitted bounded RGB gains" else "Weak or intentional-light evidence suppressed global neutralization"),
            ),
        )
    }

    fun parametersForMode(plan: AdaptiveAutoPlan, level: EnhancementLevel): SmartMagicParameters {
        val p = plan.baseline
        val finish = when (level) {
            EnhancementLevel.NATURAL -> .72f
            EnhancementLevel.ENHANCED -> 1f
            EnhancementLevel.EXTREME -> 1.16f
        }
        // Technical exposure and color balance stay coherent. Modes primarily vary finishing;
        // independently evaluated regional recovery remains mode-specific.
        return p.copy(
            contrast = (p.contrast * (.82f + .18f * finish)).coerceIn(-.1f, .18f),
            saturation = (p.saturation * finish).coerceIn(-.05f, .06f),
            vibrance = (p.vibrance * finish).coerceIn(0f, .20f),
            clarity = (p.clarity * finish).coerceIn(0f, .14f),
            texture = (p.texture * finish).coerceIn(0f, .11f),
            dehaze = (p.dehaze * when (level) {
                EnhancementLevel.NATURAL -> .50f
                EnhancementLevel.ENHANCED -> .82f
                EnhancementLevel.EXTREME -> 1f
            }).coerceIn(0f, .075f),
            sharpening = (p.sharpening * (.82f + .18f * finish)).coerceIn(0f, .36f),
            overallStrength = when (level) {
                EnhancementLevel.NATURAL -> .58f
                EnhancementLevel.ENHANCED -> .72f
                EnhancementLevel.EXTREME -> .84f
            },
        )
    }

    private companion object { val LN_2 = ln(2.0) }
}
