package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class PerceptualImpactEstimator(private val maximumSamples: Int = 262_144) {
    init { require(maximumSamples >= 256) }

    fun estimate(source: RasterImage, output: RasterImage): PerceptualImpactMetrics {
        require(source.width == output.width && source.height == output.height)
        val step = ceil(sqrt((source.argb.size / maximumSamples.toDouble()).coerceAtLeast(1.0))).toInt()
        val colorDeltas = FloatArray((source.height + step - 1) / step * ((source.width + step - 1) / step))
        val lumaDeltas = FloatArray(colorDeltas.size)
        val beforeLab = FloatArray(3); val afterLab = FloatArray(3)
        var samples = 0; var changed = 0; var lumaSigned = 0.0; var chromaSigned = 0.0
        var contrastDelta = 0.0; var contrastSamples = 0; var saturationAnomalies = 0; var halo = 0.0
        for (y in 0 until source.height step step) for (x in 0 until source.width step step) {
            val index = y * source.width + x
            val before = source.argb[index]; val after = output.argb[index]
            if (before != after) changed++
            toLab(before, beforeLab); toLab(after, afterLab)
            val dl = afterLab[0] - beforeLab[0]; val da = afterLab[1] - beforeLab[1]; val db = afterLab[2] - beforeLab[2]
            colorDeltas[samples] = sqrt(dl * dl + da * da + db * db)
            val beforeLuma = linearLuma(before); val afterLuma = linearLuma(after)
            val lumaDelta = afterLuma - beforeLuma
            lumaDeltas[samples] = abs(lumaDelta); lumaSigned += lumaDelta
            val beforeChroma = sqrt(beforeLab[1] * beforeLab[1] + beforeLab[2] * beforeLab[2])
            val afterChroma = sqrt(afterLab[1] * afterLab[1] + afterLab[2] * afterLab[2])
            chromaSigned += afterChroma - beforeChroma
            val beforePeak = maxOf(
                SmartMagicColorMath.srgbToLinear((before ushr 16 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((before ushr 8 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((before and 255) / 255f),
            ).coerceAtLeast(1e-5f)
            val afterPeak = maxOf(
                SmartMagicColorMath.srgbToLinear((after ushr 16 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((after ushr 8 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((after and 255) / 255f),
            ).coerceAtLeast(1e-5f)
            val beforeSat = (beforePeak - minOf(
                SmartMagicColorMath.srgbToLinear((before ushr 16 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((before ushr 8 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((before and 255) / 255f))) / beforePeak
            val afterSat = (afterPeak - minOf(
                SmartMagicColorMath.srgbToLinear((after ushr 16 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((after ushr 8 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((after and 255) / 255f))) / afterPeak
            if (afterSat - beforeSat > .18f) saturationAnomalies++
            if (x >= step) {
                val beforeNeighbor = linearLuma(source.argb[index - step])
                val afterNeighbor = linearLuma(output.argb[index - step])
                val beforeEdge = abs(beforeLuma - beforeNeighbor)
                val afterEdge = abs(afterLuma - afterNeighbor)
                contrastDelta += afterEdge - beforeEdge; contrastSamples++
                halo += (afterEdge - beforeEdge - .025f).coerceAtLeast(0f)
            }
            samples++
        }
        val colors = colorDeltas.copyOf(samples).sortedArray(); val lumas = lumaDeltas.copyOf(samples).sortedArray()
        val meanColor = if (samples == 0) 0f else colors.average().toFloat()
        val p95Color = percentile(colors, .95f); val p95Luma = percentile(lumas, .95f)
        val fraction = if (samples == 0) 0f else changed / samples.toFloat()
        // A one-code-value change in a small minority of pixels is not a useful photographic result.
        val negligible = changed == 0 || (meanColor < .00045f && p95Color < .0025f) ||
            (fraction < .01f && p95Color < .004f)
        return PerceptualImpactMetrics(changed, fraction, meanColor, p95Color,
            if (samples == 0) 0f else (lumaSigned / samples).toFloat(), p95Luma,
            if (samples == 0) 0f else (chromaSigned / samples).toFloat(), negligible,
            if (contrastSamples == 0) 0f else (contrastDelta / contrastSamples).toFloat(),
            if (samples == 0) 0f else saturationAnomalies / samples.toFloat(),
            if (contrastSamples == 0) 0f else (halo / contrastSamples).toFloat())
    }

    private fun toLab(color: Int, output: FloatArray) = SmartMagicColorMath.linearRgbToOklab(
        SmartMagicColorMath.srgbToLinear((color ushr 16 and 255) / 255f),
        SmartMagicColorMath.srgbToLinear((color ushr 8 and 255) / 255f),
        SmartMagicColorMath.srgbToLinear((color and 255) / 255f), output)

    private fun linearLuma(color: Int) = SmartMagicColorMath.luminance(
        SmartMagicColorMath.srgbToLinear((color ushr 16 and 255) / 255f),
        SmartMagicColorMath.srgbToLinear((color ushr 8 and 255) / 255f),
        SmartMagicColorMath.srgbToLinear((color and 255) / 255f))

    private fun percentile(sorted: FloatArray, fraction: Float): Float =
        if (sorted.isEmpty()) 0f else sorted[((sorted.lastIndex) * fraction).toInt().coerceIn(0, sorted.lastIndex)]
}
