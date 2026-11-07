package com.restorex.app.domain.smartmagic

import com.restorex.app.domain.model.RasterImage
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SmartMagicAnalyzer {
    fun analyze(source: RasterImage, shouldCancel: () -> Boolean = { false }): SmartMagicAnalysis {
        val step = sqrt((source.argb.size / MAX_SAMPLES.toDouble()).coerceAtLeast(1.0)).toInt().coerceAtLeast(1)
        val histogram = IntArray(HISTOGRAM_BINS)
        var count = 0
        var sumY = 0.0
        var sumR = 0.0; var sumG = 0.0; var sumB = 0.0
        var sumSaturation = 0.0; var sumChroma = 0.0
        var saturated = 0; var shadowClipped = 0; var highlightClipped = 0
        var neutralCount = 0; var neutralR = 0.0; var neutralG = 0.0; var neutralB = 0.0
        var skin = 0; var centralSkin = 0; var centralSamples = 0
        var blueUpper = 0; var upperSamples = 0; var greenLower = 0; var lowerSamples = 0
        var sumRg = 0.0; var sumYb = 0.0; var sumRg2 = 0.0; var sumYb2 = 0.0
        var edgeCount = 0; var edgeSamples = 0; var edgeEnergy = 0.0; var noiseEnergy = 0.0; var smoothSamples = 0
        var localDifference = 0.0; var localDifference2 = 0.0
        var centerY = 0.0; var centerCount = 0; var borderY = 0.0; var borderCount = 0

        for (y in 0 until source.height step step) {
            if (shouldCancel()) throw CancellationException("Smart Magic analysis cancelled")
            for (x in 0 until source.width step step) {
                val color = source.argb[y * source.width + x]
                val alpha = color ushr 24 and 0xFF
                if (alpha < 16) continue
                val sr = (color ushr 16 and 0xFF) / 255f
                val sg = (color ushr 8 and 0xFF) / 255f
                val sb = (color and 0xFF) / 255f
                val r = SmartMagicColorMath.srgbToLinear(sr)
                val g = SmartMagicColorMath.srgbToLinear(sg)
                val b = SmartMagicColorMath.srgbToLinear(sb)
                val luminance = SmartMagicColorMath.luminance(r, g, b).coerceIn(0f, 1f)
                histogram[(luminance * (HISTOGRAM_BINS - 1)).roundToInt()]++
                count++; sumY += luminance; sumR += r; sumG += g; sumB += b
                val maximum = max(sr, max(sg, sb)); val minimum = min(sr, min(sg, sb))
                val chroma = maximum - minimum
                val saturation = if (maximum <= 1e-6f) 0f else chroma / maximum
                sumSaturation += saturation; sumChroma += chroma
                if (saturation > .78f) saturated++
                if (maximum <= 4f / 255f) shadowClipped++
                if (maximum >= 251f / 255f) highlightClipped++
                if (saturation < .12f && luminance in .04f..0.92f) {
                    neutralCount++; neutralR += r; neutralG += g; neutralB += b
                }
                val skinLike = isSkinLike(sr, sg, sb, saturation, luminance)
                if (skinLike) skin++
                val nx = (x + .5f) / source.width; val ny = (y + .5f) / source.height
                if (nx in .2f..0.8f && ny in .14f..0.82f) {
                    centralSamples++
                    if (skinLike) centralSkin++
                    centerY += luminance; centerCount++
                } else { borderY += luminance; borderCount++ }
                if (ny < .48f) { upperSamples++; if (sb > sr * 1.08f && sb > sg * 1.04f && saturation > .12f) blueUpper++ }
                if (ny > .48f) { lowerSamples++; if (sg > sr * 1.04f && sg > sb * 1.02f && saturation > .12f) greenLower++ }
                val rg = sr - sg; val yb = .5f * (sr + sg) - sb
                sumRg += rg; sumYb += yb; sumRg2 += rg * rg; sumYb2 += yb * yb

                if (x >= step && x + step < source.width && y >= step && y + step < source.height) {
                    val left = linearLuminance(source.argb[y * source.width + x - step])
                    val right = linearLuminance(source.argb[y * source.width + x + step])
                    val top = linearLuminance(source.argb[(y - step) * source.width + x])
                    val bottom = linearLuminance(source.argb[(y + step) * source.width + x])
                    val gx = right - left; val gy = bottom - top
                    val gradient = sqrt(gx * gx + gy * gy)
                    val laplacian = abs(4f * luminance - left - right - top - bottom)
                    edgeSamples++; edgeEnergy += gradient; if (gradient > .055f) edgeCount++
                    localDifference += laplacian; localDifference2 += laplacian * laplacian
                    if (gradient < .025f) { noiseEnergy += laplacian; smoothSamples++ }
                }
            }
        }
        require(count > 0) { "The image contains no visible pixels" }
        val luma = SmartMagicLuminanceStatistics(
            mean = (sumY / count).toFloat(), median = percentile(histogram, count, .5f),
            p01 = percentile(histogram, count, .01f), p05 = percentile(histogram, count, .05f),
            p25 = percentile(histogram, count, .25f), p50 = percentile(histogram, count, .5f),
            p75 = percentile(histogram, count, .75f), p95 = percentile(histogram, count, .95f),
            p99 = percentile(histogram, count, .99f),
            shadowClippingFraction = shadowClipped.toFloat() / count,
            highlightClippingFraction = highlightClipped.toFloat() / count,
            effectiveRange = percentile(histogram, count, .95f) - percentile(histogram, count, .05f),
            globalContrast = percentile(histogram, count, .75f) - percentile(histogram, count, .25f),
            localContrast = (localDifference / edgeSamples.coerceAtLeast(1)).toFloat(),
        )
        val rgMean = sumRg / count; val ybMean = sumYb / count
        val rgStd = sqrt((sumRg2 / count - rgMean * rgMean).coerceAtLeast(0.0))
        val ybStd = sqrt((sumYb2 / count - ybMean * ybMean).coerceAtLeast(0.0))
        val neutralFraction = neutralCount.toFloat() / count
        val neutralDenominator = neutralCount.coerceAtLeast(1)
        val nr = (neutralR / neutralDenominator).toFloat(); val ng = (neutralG / neutralDenominator).toFloat(); val nb = (neutralB / neutralDenominator).toFloat()
        val neutralSpread = if (neutralCount == 0) 1f else (max(nr, max(ng, nb)) - min(nr, min(ng, nb))) / max(nr, max(ng, nb)).coerceAtLeast(.01f)
        val warmConfidence = smoothStep(.025f, .16f, ((sumR - sumB) / count).toFloat()) * (1f - neutralFraction).coerceIn(.25f, 1f)
        val color = SmartMagicColorStatistics(
            meanRed = (sumR / count).toFloat(), meanGreen = (sumG / count).toFloat(), meanBlue = (sumB / count).toFloat(),
            colorfulness = (sqrt(rgStd * rgStd + ybStd * ybStd) + .3 * sqrt(rgMean * rgMean + ybMean * ybMean)).toFloat(),
            meanChroma = (sumChroma / count).toFloat(), meanSaturation = (sumSaturation / count).toFloat(),
            highlySaturatedFraction = saturated.toFloat() / count, neutralFraction = neutralFraction,
            neutralRed = nr, neutralGreen = ng, neutralBlue = nb,
            whiteBalanceConfidence = (neutralFraction * 4f).coerceIn(0f, 1f) * (1f - smoothStep(.28f, .65f, neutralSpread)),
            skinLikeFraction = skin.toFloat() / count,
            centralSkinLikeFraction = centralSkin.toFloat() / centralSamples.coerceAtLeast(1),
            warmLightConfidence = warmConfidence,
        )
        val meanLocal = localDifference / edgeSamples.coerceAtLeast(1)
        val localVariance = (localDifference2 / edgeSamples.coerceAtLeast(1) - meanLocal * meanLocal).coerceAtLeast(0.0)
        val detail = SmartMagicDetailStatistics(
            edgeDensity = edgeCount.toFloat() / edgeSamples.coerceAtLeast(1),
            localVariance = localVariance.toFloat(),
            fineDetail = (meanLocal * 4.5).toFloat().coerceIn(0f, 1f),
            estimatedNoise = ((noiseEnergy / smoothSamples.coerceAtLeast(1) - .003) / .055).toFloat().coerceIn(0f, 1f),
            highFrequencyEnergy = (edgeEnergy / edgeSamples.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f),
            haloRisk = smoothStep(.14f, .32f, (edgeEnergy / edgeSamples.coerceAtLeast(1)).toFloat()),
        )
        val spatial = SpatialEvidence(
            upperBlue = blueUpper.toFloat() / upperSamples.coerceAtLeast(1),
            lowerGreen = greenLower.toFloat() / lowerSamples.coerceAtLeast(1),
            centerLuma = (centerY / centerCount.coerceAtLeast(1)).toFloat(),
            borderLuma = (borderY / borderCount.coerceAtLeast(1)).toFloat(),
        )
        return SmartMagicAnalysis(source.width, source.height, count, luma, color, detail,
            SmartMagicSceneClassifier.classify(luma, color, detail, spatial))
    }

    private fun percentile(histogram: IntArray, count: Int, fraction: Float): Float {
        val target = ceil(count * fraction).toInt().coerceAtLeast(1)
        var cumulative = 0
        histogram.forEachIndexed { index, amount ->
            cumulative += amount
            if (cumulative >= target) return index.toFloat() / (HISTOGRAM_BINS - 1)
        }
        return 1f
    }

    private fun linearLuminance(color: Int): Float = SmartMagicColorMath.luminance(
        SmartMagicColorMath.srgbToLinear((color ushr 16 and 0xFF) / 255f),
        SmartMagicColorMath.srgbToLinear((color ushr 8 and 0xFF) / 255f),
        SmartMagicColorMath.srgbToLinear((color and 0xFF) / 255f),
    )

    private fun isSkinLike(r: Float, g: Float, b: Float, saturation: Float, luminance: Float): Boolean {
        if (luminance !in .035f..0.88f || saturation !in .10f..0.72f || r <= g || g <= b * .82f) return false
        val maximum = max(r, max(g, b)); val minimum = min(r, min(g, b)); val delta = maximum - minimum
        val hue = when {
            delta < 1e-5f -> 0f
            maximum == r -> 60f * (((g - b) / delta) % 6f)
            maximum == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return hue <= 52f || hue >= 350f
    }

    internal data class SpatialEvidence(val upperBlue: Float, val lowerGreen: Float, val centerLuma: Float, val borderLuma: Float)

    private companion object { const val MAX_SAMPLES = 260_000; const val HISTOGRAM_BINS = 1024 }
}

object SmartMagicSceneClassifier {
    internal fun classify(
        luma: SmartMagicLuminanceStatistics,
        color: SmartMagicColorStatistics,
        detail: SmartMagicDetailStatistics,
        spatial: SmartMagicAnalyzer.SpatialEvidence,
    ): SmartMagicSceneScores {
        val portrait = (smoothStep(.025f, .13f, color.skinLikeFraction) * .45f +
            smoothStep(.04f, .18f, color.centralSkinLikeFraction) * .55f).coerceIn(0f, 1f)
        val landscape = (smoothStep(.04f, .28f, spatial.upperBlue) * .45f +
            smoothStep(.05f, .32f, spatial.lowerGreen) * .4f + smoothStep(.08f, .28f, detail.edgeDensity) * .15f).coerceIn(0f, 1f)
        val lowLight = (smoothStep(.22f, .045f, luma.median) * .7f + smoothStep(.30f, .09f, luma.p75) * .3f).coerceIn(0f, 1f)
        val centerDeficit = spatial.borderLuma - spatial.centerLuma
        val backlit = (smoothStep(.035f, .22f, centerDeficit) * .7f + smoothStep(.005f, .08f, luma.highlightClippingFraction) * .3f).coerceIn(0f, 1f)
        val document = (smoothStep(.72f, .94f, luma.mean) * .35f + smoothStep(.10f, .025f, color.meanSaturation) * .35f +
            smoothStep(.09f, .28f, detail.edgeDensity) * .3f).coerceIn(0f, 1f)
        val balancedExposure = 1f - max(smoothStep(.28f, .08f, luma.median), smoothStep(.55f, .78f, luma.median))
        val balanced = (balancedExposure * (1f - smoothStep(.012f, .08f, luma.highlightClippingFraction)) *
            (1f - smoothStep(.02f, .11f, luma.shadowClippingFraction)) * smoothStep(.32f, .7f, luma.effectiveRange)).coerceIn(0f, 1f)
        val highKey = (smoothStep(.48f, .75f, luma.mean) * (1f - smoothStep(.015f, .1f, luma.highlightClippingFraction))).coerceIn(0f, 1f)
        val lowKey = (smoothStep(.23f, .07f, luma.mean) * (1f - smoothStep(.08f, .32f, luma.p95))).coerceIn(0f, 1f)
        val general = (1f - max(max(portrait, landscape), document) * .6f).coerceIn(.25f, 1f)
        return SmartMagicSceneScores(general, portrait, landscape, lowLight, backlit, document, balanced, highKey, lowKey)
    }
}

internal fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge0 == edge1) return if (value < edge0) 0f else 1f
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
