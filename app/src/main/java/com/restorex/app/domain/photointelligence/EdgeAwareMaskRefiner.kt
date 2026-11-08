package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import java.util.concurrent.CancellationException
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Small joint-bilateral refinement at model-mask resolution with uncertainty suppression. */
class EdgeAwareMaskRefiner(
    private val radius: Int = 2,
    private val spatialSigma: Float = 1.45f,
    private val luminanceSigma: Float = .075f,
) {
    init { require(radius in 1..4 && spatialSigma > 0f && luminanceSigma > 0f) }

    fun refine(source: RasterImage, raw: ConfidenceMask, shouldCancel: () -> Boolean = { false }): ConfidenceMask {
        val luma = FloatArray(raw.width * raw.height)
        for (y in 0 until raw.height) {
            if (y and 15 == 0 && shouldCancel()) throw CancellationException("Mask refinement cancelled")
            for (x in 0 until raw.width) {
                val sx = ((x + .5f) * source.width / raw.width).toInt().coerceIn(0, source.width - 1)
                val sy = ((y + .5f) * source.height / raw.height).toInt().coerceIn(0, source.height - 1)
                val c = source.argb[sy * source.width + sx]
                luma[y * raw.width + x] = SmartMagicColorMath.luminance(
                    SmartMagicColorMath.srgbToLinear((c ushr 16 and 255) / 255f),
                    SmartMagicColorMath.srgbToLinear((c ushr 8 and 255) / 255f),
                    SmartMagicColorMath.srgbToLinear((c and 255) / 255f))
            }
        }
        val result = FloatArray(luma.size)
        val spatialDenominator = 2f * spatialSigma * spatialSigma
        val rangeDenominator = 2f * luminanceSigma * luminanceSigma
        for (y in 0 until raw.height) {
            if (y and 7 == 0 && shouldCancel()) throw CancellationException("Mask refinement cancelled")
            for (x in 0 until raw.width) {
                val centerLuma = luma[y * raw.width + x]
                var sum = 0f; var weightSum = 0f
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, raw.width - 1); val ny = (y + dy).coerceIn(0, raw.height - 1)
                    val deltaLuma = luma[ny * raw.width + nx] - centerLuma
                    val spatial = exp(-(dx * dx + dy * dy) / spatialDenominator)
                    val range = exp(-(deltaLuma * deltaLuma) / rangeDenominator)
                    val weight = spatial * range
                    sum += raw.score(nx, ny) * weight; weightSum += weight
                }
                val filtered = sum / weightSum.coerceAtLeast(1e-6f)
                val original = raw.score(x, y)
                // Keep high-confidence fine structure; uncertain pixels require edge-supported consensus.
                val certainty = (kotlin.math.abs(original - .5f) * 2f).coerceIn(0f, 1f)
                var refined = original * certainty + filtered * (1f - certainty)
                if (original in .20f.. .80f) {
                    val localEdge = localEdge(luma, raw.width, raw.height, x, y)
                    val uncertaintyAttenuation = (.72f + localEdge.coerceIn(0f, .15f) * 1.5f).coerceIn(.72f, .94f)
                    refined = .5f + (refined - .5f) * uncertaintyAttenuation
                }
                result[y * raw.width + x] = refined.coerceIn(0f, 1f)
            }
        }
        return ConfidenceMask.fromFloats("${raw.id}-edge-v1", raw.width, raw.height, result)
    }

    private fun localEdge(values: FloatArray, width: Int, height: Int, x: Int, y: Int): Float {
        val left = values[y * width + max(0, x - 1)]; val right = values[y * width + min(width - 1, x + 1)]
        val top = values[max(0, y - 1) * width + x]; val bottom = values[min(height - 1, y + 1) * width + x]
        return kotlin.math.abs(right - left) + kotlin.math.abs(bottom - top)
    }
}

class RegionalMaskSampler(private val mask: ConfidenceMask, private val target: RegionalTarget) {
    fun sample(x: Int, y: Int, width: Int, height: Int): Float {
        val person = mask.sample((x + .5f) / width, (y + .5f) / height)
        val base = if (target == RegionalTarget.PERSON) person else 1f - person
        // Suppress both effects in the most uncertain boundary band instead of expanding it.
        val certainty = (kotlin.math.abs(person - .5f) * 2f).coerceIn(0f, 1f)
        return (base * (.45f + .55f * certainty)).coerceIn(0f, 1f)
    }
}
