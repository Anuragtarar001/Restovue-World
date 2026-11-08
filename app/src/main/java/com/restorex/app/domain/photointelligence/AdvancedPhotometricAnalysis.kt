package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/** Measured evidence used by Milestone 5 planning. Scores are evidence, not semantic labels. */
data class AdvancedPhotometricEvidence(
    val analysisVersion: String = "m5-advanced-1",
    val hazeConfidence: Float,
    val atmosphericIntentRisk: Float,
    val veilingGlareConfidence: Float,
    val recoverableHighlightConfidence: Float,
    val shadowRecoveryConfidence: Float,
    val localContrastDeficit: Float,
    val detailConfidence: Float,
    val noiseConstraint: Float,
    val warmLightIntent: Float,
    val lowKeyIntent: Float,
    val highKeyIntent: Float,
    val darkChannelMean: Float,
    val brightAreaFraction: Float,
    val analysisDurationNanos: Long,
) {
    init {
        listOf(hazeConfidence, atmosphericIntentRisk, veilingGlareConfidence,
            recoverableHighlightConfidence, shadowRecoveryConfidence, localContrastDeficit,
            detailConfidence, noiseConstraint, warmLightIntent, lowKeyIntent, highKeyIntent,
            darkChannelMean, brightAreaFraction).forEach { require(it.isFinite() && it in 0f..1f) }
        require(analysisDurationNanos >= 0)
    }
}

/**
 * Bounded, Android-independent atmospheric/tonal evidence. It deliberately does not infer sky,
 * fog, water, or scene objects from color. Ambiguous atmosphere increases the intent-risk score.
 */
class AdvancedPhotometricAnalyzer(private val maximumSamples: Int = 131_072) {
    init { require(maximumSamples >= 1024) }

    fun analyze(
        source: RasterImage,
        evidence: PhotometricEvidence,
        diagnoses: List<ConditionDiagnosis>,
        shouldCancel: () -> Boolean = { false },
    ): AdvancedPhotometricEvidence {
        val started = System.nanoTime()
        val step = ceil(sqrt((source.argb.size / maximumSamples.toDouble()).coerceAtLeast(1.0))).toInt()
        var darkSum = 0.0; var visible = 0; var bright = 0; var warm = 0; var neutralBright = 0
        for (y in 0 until source.height step step) {
            if (y and 31 == 0 && shouldCancel()) throw CancellationException("Advanced analysis cancelled")
            for (x in 0 until source.width step step) {
                val argb = source.argb[y * source.width + x]
                if ((argb ushr 24 and 255) == 0) continue
                val r = SmartMagicColorMath.srgbToLinear((argb ushr 16 and 255) / 255f)
                val g = SmartMagicColorMath.srgbToLinear((argb ushr 8 and 255) / 255f)
                val b = SmartMagicColorMath.srgbToLinear((argb and 255) / 255f)
                val luma = SmartMagicColorMath.luminance(r, g, b)
                val peak = max(r, max(g, b)).coerceAtLeast(1e-5f)
                val saturation = (peak - minOf(r, g, b)) / peak
                darkSum += minOf(r, g, b).toDouble(); visible++
                if (luma > .72f) bright++
                if (r > b * 1.18f && r > g * 1.04f && luma > .05f) warm++
                if (luma > .55f && saturation < .16f) neutralBright++
            }
        }
        val l = evidence.luminance; val c = evidence.color; val d = evidence.detail
        val lowContrast = smoothStep(.20f, .035f, d.localContrast)
        val compressedRange = smoothStep(.62f, .24f, l.robustRange)
        val weakColor = smoothStep(.24f, .055f, c.meanSaturation)
        val darkChannel = if (visible == 0) 0f else (darkSum / visible).toFloat().coerceIn(0f, 1f)
        val brightFraction = if (visible == 0) 0f else bright / visible.toFloat()
        val warmFraction = if (visible == 0) 0f else warm / visible.toFloat()
        val neutralBrightFraction = if (visible == 0) 0f else neutralBright / visible.toFloat()
        val warmIntent = (smoothStep(.08f, .30f, warmFraction) *
            (1f - smoothStep(.05f, .20f, c.neutralFraction))).coerceIn(0f, 1f)
        val lowKey = (smoothStep(.09f, .018f, l.median) *
            smoothStep(.52f, .82f, l.shadowOccupancy) *
            (1f - smoothStep(.02f, .12f, l.highlightOccupancy))).coerceIn(0f, 1f)
        val highKey = (smoothStep(.46f, .72f, l.median) * smoothStep(.34f, .72f, brightFraction)).coerceIn(0f, 1f)
        val severeClipping = smoothStep(.006f, .08f, l.whiteClipping)
        val atmosphericAmbiguity = lowContrast * weakColor *
            (smoothStep(.45f, .78f, compressedRange) + warmIntent * .55f).coerceIn(0f, 1f)
        val intentionalDiagnosis = diagnoses.filter {
            it.potentiallyIntentional && (it.conditionId.contains("LOW_KEY") ||
                it.conditionId.contains("HIGH_KEY") || it.conditionId.contains("SILHOUETTE"))
        }.maxOfOrNull { it.confidence } ?: 0f
        val intentRisk = max(max(warmIntent, max(lowKey, highKey)), max(intentionalDiagnosis, atmosphericAmbiguity * .55f))
            .coerceIn(0f, 1f)
        // Dark-channel evidence is useful only as one cue. Highlight and intent guards prevent the
        // bright-sky/soft-light failure modes of indiscriminate dark-channel dehazing.
        val haze = (lowContrast * compressedRange * (.35f + weakColor * .45f + darkChannel * .35f) *
            (1f - intentRisk * .82f) * (1f - severeClipping * .65f)).coerceIn(0f, 1f)
        val glare = (lowContrast * smoothStep(.06f, .38f, brightFraction) *
            smoothStep(.02f, .24f, neutralBrightFraction) * (1f - highKey * .65f)).coerceIn(0f, 1f)
        val recoverableHighlights = (smoothStep(.08f, .38f, l.highlightOccupancy) *
            (1f - severeClipping) * (1f - highKey * .45f)).coerceIn(0f, 1f)
        val noiseConstraint = (d.noiseRisk * (1f + l.shadowOccupancy * .35f)).coerceIn(0f, 1f)
        val shadowRecovery = (smoothStep(.18f, .55f, l.shadowOccupancy) *
            (1f - noiseConstraint * .72f) * (1f - lowKey * .78f)).coerceIn(0f, 1f)
        val detailConfidence = ((d.edgeDensity * 2.2f + d.fineDetailEnergy * 2.8f) *
            (1f - noiseConstraint * .75f)).coerceIn(0f, 1f)
        return AdvancedPhotometricEvidence(
            hazeConfidence = haze,
            atmosphericIntentRisk = intentRisk,
            veilingGlareConfidence = glare,
            recoverableHighlightConfidence = recoverableHighlights,
            shadowRecoveryConfidence = shadowRecovery,
            localContrastDeficit = (lowContrast * (1f - intentRisk * .45f)).coerceIn(0f, 1f),
            detailConfidence = detailConfidence,
            noiseConstraint = noiseConstraint,
            warmLightIntent = warmIntent,
            lowKeyIntent = lowKey,
            highKeyIntent = highKey,
            darkChannelMean = darkChannel,
            brightAreaFraction = brightFraction,
            analysisDurationNanos = System.nanoTime() - started,
        )
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = if (edge0 < edge1) ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        else ((edge0 - value) / (edge0 - edge1)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
