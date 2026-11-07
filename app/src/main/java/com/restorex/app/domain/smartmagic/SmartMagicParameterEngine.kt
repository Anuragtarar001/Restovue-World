package com.restorex.app.domain.smartmagic

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

class SmartMagicParameterEngine(private val strength: Float = .7f) {
    init { require(strength in 0f..1f) }

    fun parametersFor(analysis: SmartMagicAnalysis): SmartMagicParameters {
        val l = analysis.luminance; val c = analysis.color; val d = analysis.detail; val s = analysis.scenes
        val targetMedian = (0.205f - s.lowLight * .035f + s.document * .07f + s.highKey * .04f - s.lowKey * .035f)
            .coerceIn(.145f, .29f)
        val portraitMidtoneWeight = s.portrait * .55f
        val measuredMidtone = l.median + (l.p75 - l.median) * portraitMidtoneWeight
        var exposure = (ln(targetMedian / measuredMidtone.coerceAtLeast(.012f)) / LN_2).toFloat()
        exposure -= smoothStep(.01f, .10f, l.highlightClippingFraction) * .22f
        exposure = exposure.coerceIn(-.6f, .8f)
        exposure *= (1f - s.alreadyBalanced * .82f)
        exposure *= (1f - s.lowKey * .68f * (1f - s.portrait * .8f))
        exposure *= (1f - s.document * .9f)
        exposure *= strength

        val lowContrast = smoothStep(.18f, .58f, .58f - l.effectiveRange)
        val contrast = ((lowContrast * .18f - smoothStep(.72f, .92f, l.effectiveRange) * .08f) *
            (1f - s.document * .35f) * strength).coerceIn(-.1f, .22f)
        val highlights = (-(smoothStep(.68f, .96f, l.p95) * .28f + smoothStep(.004f, .10f, l.highlightClippingFraction) * .24f) *
            (1f - s.highKey * .35f) * strength).coerceIn(-.6f, 0f)
        val noisyShadowLimit = 1f - d.estimatedNoise * .65f
        val shadows = ((smoothStep(.16f, .025f, l.p25) * .28f + s.backlit * .13f) * noisyShadowLimit *
            (1f - s.lowKey * .6f) * strength).coerceIn(0f, .4f)
        val whites = ((.52f - l.p95) * .25f * lowContrast * strength).coerceIn(-.1f, .15f)
        val blacks = ((.035f - l.p05) * 1.1f * strength - lowContrast * .025f).coerceIn(-.15f, .1f)

        val gains = whiteBalanceGains(c)
        val vividProtection = 1f - smoothStep(.18f, .48f, c.highlySaturatedFraction)
        val portraitProtection = s.portrait.coerceIn(0f, 1f)
        val saturation = ((.10f - c.meanSaturation) * .22f * vividProtection * (1f - portraitProtection * .55f) * strength).coerceIn(-.05f, .08f)
        val vibrance = (smoothStep(.42f, .08f, c.meanSaturation) * .22f * vividProtection *
            (1f - portraitProtection * .45f) * (1f - s.document * .9f) * strength).coerceIn(0f, .25f)

        val detailRoom = (1f - d.haloRisk * .7f) * (1f - d.estimatedNoise * .65f)
        val clarity = ((.085f + lowContrast * .035f) * detailRoom * (1f - portraitProtection * .55f) *
            (1f - s.document * .35f) * strength).coerceIn(0f, .15f)
        val texture = ((.07f - d.fineDetail * .025f) * detailRoom * (1f - portraitProtection * .5f) * strength).coerceIn(0f, .12f)
        val hazeEvidence = lowContrast * s.landscape * (1f - s.highKey) * (1f - s.lowLight) *
            smoothStep(.035f, .18f, l.p05)
        val dehaze = (hazeEvidence * .09f * strength).coerceIn(0f, .1f)
        val lumaNr = (d.estimatedNoise * (.25f + s.lowLight * .1f) * strength).coerceIn(0f, .35f)
        val chromaNr = (d.estimatedNoise * (.19f + s.lowLight * .1f) * strength).coerceIn(0f, .3f)
        val sharpen = ((.34f - d.haloRisk * .16f - d.estimatedNoise * .12f) *
            (1f - portraitProtection * .28f) * strength).coerceIn(0f, .4f)
        return SmartMagicParameters(
            exposureEv = exposure, contrast = contrast, highlights = highlights, shadows = shadows,
            whites = whites, blacks = blacks,
            redGain = gains[0], greenGain = gains[1], blueGain = gains[2], whiteBalanceConfidence = gains[3],
            saturation = saturation, vibrance = vibrance, clarity = clarity, texture = texture, dehaze = dehaze,
            luminanceNoiseReduction = lumaNr, chromaNoiseReduction = chromaNr,
            sharpening = sharpen, sharpeningRadius = (.75f + d.fineDetail * .25f).coerceIn(.6f, 1f),
            sharpeningThreshold = (3f / 255f + d.estimatedNoise * .018f).coerceIn(0f, .1f),
            sharpeningMasking = (.28f + d.estimatedNoise * .5f + portraitProtection * .15f).coerceIn(0f, 1f),
            portraitProtection = portraitProtection, overallStrength = strength,
        )
    }

    private fun whiteBalanceGains(c: SmartMagicColorStatistics): FloatArray {
        if (c.whiteBalanceConfidence < .08f) return floatArrayOf(1f, 1f, 1f, 0f)
        val gray = (c.neutralRed + c.neutralGreen + c.neutralBlue) / 3f
        val confidence = c.whiteBalanceConfidence * (1f - c.warmLightConfidence * .72f)
        fun gain(channel: Float): Float {
            val raw = gray / channel.coerceAtLeast(.015f)
            return (1f + (raw - 1f).coerceIn(-.1f, .1f) * confidence * strength).coerceIn(.85f, 1.15f)
        }
        val red = gain(c.neutralRed); val green = gain(c.neutralGreen); val blue = gain(c.neutralBlue)
        val normalization = (red * .2126f + green * .7152f + blue * .0722f).coerceAtLeast(.01f)
        return floatArrayOf(
            (red / normalization).coerceIn(.85f, 1.15f),
            (green / normalization).coerceIn(.85f, 1.15f),
            (blue / normalization).coerceIn(.85f, 1.15f),
            confidence,
        )
    }

    private companion object { val LN_2 = ln(2.0) }
}
