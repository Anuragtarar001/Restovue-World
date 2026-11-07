package com.restorex.app.data

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicAnalysis
import com.restorex.app.domain.smartmagic.SmartMagicAnalyzer
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import com.restorex.app.domain.smartmagic.SmartMagicEngine
import com.restorex.app.domain.smartmagic.SmartMagicParameters
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic Smart Magic renderer. Global exposure/tone/color work in linear sRGB; spatial
 * stages operate on luminance/chroma and use bounded packed buffers rather than full float images.
 */
class SmartMagicPipeline(
    private val analyzer: SmartMagicAnalyzer = SmartMagicAnalyzer(),
) : SmartMagicEngine {
    override fun analyze(source: RasterImage, shouldCancel: () -> Boolean): SmartMagicAnalysis =
        analyzer.analyze(source, shouldCancel)

    override fun enhance(
        source: RasterImage,
        parameters: SmartMagicParameters,
        shouldCancel: () -> Boolean,
        reuseSourcePixels: Boolean,
    ): RasterImage {
        var pixels = if (reuseSourcePixels) source.argb else source.argb.copyOf()
        applyGlobalColorInPlace(pixels, parameters, shouldCancel)
        if (parameters.dehaze > EPSILON) pixels = applyDarkChannelDehaze(pixels, source.width, source.height, parameters.dehaze, shouldCancel)
        if (parameters.luminanceNoiseReduction > EPSILON || parameters.chromaNoiseReduction > EPSILON) {
            pixels = applyEdgeAwareNoiseReduction(
                pixels, source.width, source.height,
                parameters.luminanceNoiseReduction, parameters.chromaNoiseReduction, shouldCancel,
            )
        }
        if (parameters.clarity > EPSILON) applyClarityInPlace(pixels, source.width, source.height, parameters.clarity, source.scaleFromOriginal, shouldCancel)
        if (parameters.texture > EPSILON || parameters.sharpening > EPSILON) {
            pixels = applyFineDetail(
                pixels, source.width, source.height, parameters.texture, parameters.sharpening,
                parameters.sharpeningThreshold, parameters.sharpeningMasking, shouldCancel,
            )
        }
        protectOutputHeadroomInPlace(pixels)
        return RasterImage(source.width, source.height, pixels, source.scaleFromOriginal)
    }

    private fun applyGlobalColorInPlace(pixels: IntArray, p: SmartMagicParameters, shouldCancel: () -> Boolean) {
        val gain = 2f.pow(p.exposureEv)
        val lab = FloatArray(3)
        val mapped = FloatArray(3)
        val gamutScratch = FloatArray(3)
        pixels.indices.forEach { index ->
            if (index and 0x3FFF == 0) checkCancelled(shouldCancel)
            val color = pixels[index]
            val alpha = color ushr 24 and 0xFF
            if (alpha == 0) return@forEach
            val sourceR = (color ushr 16 and 0xFF) / 255f
            val sourceG = (color ushr 8 and 0xFF) / 255f
            val sourceB = (color and 0xFF) / 255f
            var r = SmartMagicColorMath.srgbToLinear(sourceR) * p.redGain * gain
            var g = SmartMagicColorMath.srgbToLinear(sourceG) * p.greenGain * gain
            var b = SmartMagicColorMath.srgbToLinear(sourceB) * p.blueGain * gain
            val oldY = SmartMagicColorMath.luminance(r, g, b).coerceAtLeast(1e-7f)
            val newY = toneMapLuminance(oldY, p)
            val ratio = (newY / oldY).coerceIn(0f, 8f)
            r *= ratio; g *= ratio; b *= ratio

            SmartMagicColorMath.linearRgbToOklab(r, g, b, lab)
            val chroma = sqrt(lab[1] * lab[1] + lab[2] * lab[2])
            val normalizedChroma = (chroma / .24f).coerceIn(0f, 1f)
            val skinProtection = skinProtection(sourceR, sourceG, sourceB) * p.portraitProtection
            val colorBoost = 1f + p.saturation * (1f - skinProtection * .7f) +
                p.vibrance * (1f - normalizedChroma).pow(1.45f) * (1f - skinProtection * .78f)
            gamutMapOklab(lab[0], lab[1] * colorBoost, lab[2] * colorBoost, mapped, gamutScratch)
            r = mapped[0]; g = mapped[1]; b = mapped[2]
            // Reserve a small encoding headroom so a tiny adaptive correction cannot turn vivid,
            // non-clipped source channels into flat 255 plateaus.
            val peak = max(r, max(g, b))
            if (peak > OUTPUT_HEADROOM_LINEAR) {
                val headroomScale = OUTPUT_HEADROOM_LINEAR / peak
                r *= headroomScale; g *= headroomScale; b *= headroomScale
            }
            pixels[index] = pack(alpha, SmartMagicColorMath.linearToSrgb(r), SmartMagicColorMath.linearToSrgb(g), SmartMagicColorMath.linearToSrgb(b))
        }
    }

    internal fun toneMapLuminance(value: Float, p: SmartMagicParameters): Float {
        var y = value.coerceAtLeast(0f)
        // A rational shoulder handles exposure-created headroom without clipping abruptly.
        val shoulderStrength = p.exposureEv.coerceAtLeast(0f) * .75f + -p.highlights * 1.2f
        if (y > .72f && shoulderStrength > EPSILON) {
            y = .72f + (y - .72f) / (1f + (y - .72f) * shoulderStrength)
        }
        y = y.coerceIn(0f, 1f)
        val shadowBasis = y * (1f - y) * (1f - y)
        val highlightBasis = y * y * (1f - y)
        y += p.shadows * shadowBasis * 1.35f
        y += p.highlights * highlightBasis * .9f
        y += p.whites * y * y * y * (1f - y) * .55f
        y += p.blacks * y * (1f - y).pow(3) * .7f
        val smooth = y * y * (3f - 2f * y)
        y += p.contrast * (smooth - y)
        return y.coerceIn(0f, 1f)
    }

    private fun gamutMapOklab(lightness: Float, a: Float, b: Float, output: FloatArray, scratch: FloatArray) {
        SmartMagicColorMath.oklabToLinearRgb(lightness, a, b, output)
        if (inGamut(output)) return
        var low = 0f; var high = 1f
        repeat(7) {
            val middle = (low + high) * .5f
            SmartMagicColorMath.oklabToLinearRgb(lightness, a * middle, b * middle, scratch)
            if (inGamut(scratch)) low = middle else high = middle
        }
        SmartMagicColorMath.oklabToLinearRgb(lightness.coerceIn(0f, 1f), a * low, b * low, output)
        output.indices.forEach { output[it] = output[it].coerceIn(0f, 1f) }
    }

    private fun inGamut(values: FloatArray) = values[0] in 0f..1f && values[1] in 0f..1f && values[2] in 0f..1f

    private fun applyDarkChannelDehaze(
        source: IntArray, width: Int, height: Int, amount: Float, shouldCancel: () -> Boolean,
    ): IntArray {
        var atmosphereR = .82f; var atmosphereG = .82f; var atmosphereB = .82f; var bestScore = -1f
        val sampleStep = max(1, sqrt(source.size / 120_000.0).toInt())
        for (y in 0 until height step sampleStep) for (x in 0 until width step sampleStep) {
            val color = source[y * width + x]
            val cr = red(color); val cg = green(color); val cb = blue(color)
            val dark = min(cr, min(cg, cb)); val luminance = luma(color)
            val score = dark * .7f + luminance * .3f
            if (score > bestScore) { bestScore = score; atmosphereR = cr; atmosphereG = cg; atmosphereB = cb }
        }
        val output = IntArray(source.size)
        for (index in source.indices) {
            if (index and 0x1FFF == 0) checkCancelled(shouldCancel)
            val x = index % width; val y = index / width
            var dark = 1f
            for (dy in -1..1) for (dx in -1..1) {
                val sample = source[(y + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)]
                dark = min(dark, min(red(sample) / atmosphereR.coerceAtLeast(.2f), min(green(sample) / atmosphereG.coerceAtLeast(.2f), blue(sample) / atmosphereB.coerceAtLeast(.2f))))
            }
            val original = source[index]; val saturation = saturation(original)
            val skyProtection = if (luma(original) > .62f && saturation < .16f) .32f else 1f
            val effective = amount * skyProtection
            val transmission = (1f - effective * 5.8f * dark).coerceIn(.58f, 1f)
            fun restore(channel: Float, atmosphere: Float) = mix(channel, (channel - atmosphere) / transmission + atmosphere, effective).coerceIn(0f, 1f)
            output[index] = pack(source[index] ushr 24 and 0xFF,
                restore(red(original), atmosphereR), restore(green(original), atmosphereG), restore(blue(original), atmosphereB))
        }
        return output
    }

    private fun applyEdgeAwareNoiseReduction(
        source: IntArray, width: Int, height: Int, luminanceAmount: Float, chromaAmount: Float,
        shouldCancel: () -> Boolean,
    ): IntArray {
        val output = IntArray(source.size)
        val rangeSigma = .028f + max(luminanceAmount, chromaAmount) * .08f
        val denominator = 2f * rangeSigma * rangeSigma
        for (index in source.indices) {
            if (index and 0x1FFF == 0) checkCancelled(shouldCancel)
            val x = index % width; val y = index / width
            val centerColor = source[index]
            val centerY = luma(centerColor)
            val centerCb = (blue(centerColor) - centerY) * .564f
            val centerCr = (red(centerColor) - centerY) * .713f
            var weightSum = 0f
            var sumY = 0f; var sumCb = 0f; var sumCr = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val sample = source[(y + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)]
                val sampleY = luma(sample)
                val sampleCb = (blue(sample) - sampleY) * .564f
                val sampleCr = (red(sample) - sampleY) * .713f
                val difference = sampleY - centerY
                val spatial = if (dx == 0 && dy == 0) 1f else if (dx == 0 || dy == 0) .78f else .58f
                val weight = spatial * exp(-(difference * difference) / denominator)
                weightSum += weight; sumY += sampleY * weight; sumCb += sampleCb * weight; sumCr += sampleCr * weight
            }
            val yValue = mix(centerY, sumY / weightSum, luminanceAmount * .78f)
            val cb = mix(centerCb, sumCb / weightSum, chromaAmount * .92f)
            val cr = mix(centerCr, sumCr / weightSum, chromaAmount * .92f)
            output[index] = pack(source[index] ushr 24 and 0xFF, yValue + 1.403f * cr,
                yValue - .344f * cb - .714f * cr, yValue + 1.773f * cb)
        }
        return output
    }

    private fun applyClarityInPlace(
        pixels: IntArray, width: Int, height: Int, amount: Float, scale: Float, shouldCancel: () -> Boolean,
    ) {
        val radius = (3.5f * scale).roundToInt().coerceIn(1, 6)
        val blurred = boxBlur(pixels, width, height, radius, shouldCancel)
        for (index in pixels.indices) {
            if (index and 0x3FFF == 0) checkCancelled(shouldCancel)
            val original = pixels[index]
            val difference = (luma(original) - luma(blurred[index])).coerceIn(-.14f, .14f)
            val protection = smoothStep(.005f, .035f, abs(difference))
            val delta = difference * amount * 4.8f * protection
            pixels[index] = pack(pixels[index] ushr 24 and 0xFF, red(original) + delta, green(original) + delta, blue(original) + delta)
        }
    }

    private fun applyFineDetail(
        source: IntArray, width: Int, height: Int, texture: Float, sharpening: Float,
        threshold: Float, masking: Float, shouldCancel: () -> Boolean,
    ): IntArray {
        val output = IntArray(source.size)
        for (index in source.indices) {
            if (index and 0x1FFF == 0) checkCancelled(shouldCancel)
            val x = index % width; val y = index / width
            val original = source[index]; val originalY = luma(original)
            var neighborY = 0f
            for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) {
                neighborY += luma(source[(y + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)])
            }
            neighborY /= 8f
            val detail = (originalY - neighborY).coerceIn(-.08f, .08f)
            val edge = abs(detail)
            val textureGate = smoothStep(.003f, .018f, edge) * (1f - smoothStep(.16f, .34f, edge) * .65f)
            val sharpenGate = if (edge < threshold) 0f else smoothStep(.006f + masking * .04f, .08f + masking * .17f, edge)
            val delta = detail * (texture * 5.4f * textureGate + sharpening * 2.1f * sharpenGate)
            output[index] = pack(source[index] ushr 24 and 0xFF, red(original) + delta, green(original) + delta, blue(original) + delta)
        }
        return output
    }

    private fun boxBlur(source: IntArray, width: Int, height: Int, radius: Int, shouldCancel: () -> Boolean): IntArray {
        val horizontal = IntArray(source.size); val count = radius * 2 + 1
        for (y in 0 until height) {
            if (y and 15 == 0) checkCancelled(shouldCancel)
            var sumR = 0; var sumG = 0; var sumB = 0
            for (x in -radius..radius) { val c = source[y * width + x.coerceIn(0, width - 1)]; sumR += c ushr 16 and 255; sumG += c ushr 8 and 255; sumB += c and 255 }
            for (x in 0 until width) {
                horizontal[y * width + x] = (source[y * width + x] and -0x1000000) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                val remove = source[y * width + (x - radius).coerceIn(0, width - 1)]; val add = source[y * width + (x + radius + 1).coerceIn(0, width - 1)]
                sumR += (add ushr 16 and 255) - (remove ushr 16 and 255); sumG += (add ushr 8 and 255) - (remove ushr 8 and 255); sumB += (add and 255) - (remove and 255)
            }
        }
        val output = IntArray(source.size)
        for (x in 0 until width) {
            var sumR = 0; var sumG = 0; var sumB = 0
            for (y in -radius..radius) { val c = horizontal[y.coerceIn(0, height - 1) * width + x]; sumR += c ushr 16 and 255; sumG += c ushr 8 and 255; sumB += c and 255 }
            for (y in 0 until height) {
                output[y * width + x] = (source[y * width + x] and -0x1000000) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                val remove = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]; val add = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                sumR += (add ushr 16 and 255) - (remove ushr 16 and 255); sumG += (add ushr 8 and 255) - (remove ushr 8 and 255); sumB += (add and 255) - (remove and 255)
            }
        }
        return output
    }

    private fun skinProtection(r: Float, g: Float, b: Float): Float {
        if (r <= g || g < b * .78f) return 0f
        val warmth = ((r - b) / .48f).coerceIn(0f, 1f)
        val balance = (1f - abs(g - (r * .62f + b * .38f)) / .24f).coerceIn(0f, 1f)
        return warmth * balance
    }

    private fun protectOutputHeadroomInPlace(pixels: IntArray) {
        pixels.indices.forEach { index ->
            val color = pixels[index]
            if (color ushr 24 == 0) return@forEach
            val red = color ushr 16 and 255; val green = color ushr 8 and 255; val blue = color and 255
            val peak = max(red, max(green, blue))
            if (peak > 250) {
                val scale = 250f / peak
                pixels[index] = (color and -0x1000000) or ((red * scale).roundToInt() shl 16) or
                    ((green * scale).roundToInt() shl 8) or (blue * scale).roundToInt()
            }
        }
    }

    private fun red(color: Int) = (color ushr 16 and 255) / 255f
    private fun green(color: Int) = (color ushr 8 and 255) / 255f
    private fun blue(color: Int) = (color and 255) / 255f
    private fun luma(color: Int) = .2126f * red(color) + .7152f * green(color) + .0722f * blue(color)
    private fun saturation(color: Int): Float { val maximum = max(red(color), max(green(color), blue(color))); return if (maximum < EPSILON) 0f else (maximum - min(red(color), min(green(color), blue(color)))) / maximum }
    private fun mix(a: Float, b: Float, amount: Float) = a + (b - a) * amount.coerceIn(0f, 1f)
    private fun smoothStep(low: Float, high: Float, value: Float): Float { val t = ((value - low) / (high - low)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    private fun pack(alpha: Int, r: Float, g: Float, b: Float): Int = (alpha shl 24) or
        ((r.coerceIn(0f, 1f) * 255f).roundToInt() shl 16) or ((g.coerceIn(0f, 1f) * 255f).roundToInt() shl 8) or (b.coerceIn(0f, 1f) * 255f).roundToInt()
    private fun checkCancelled(shouldCancel: () -> Boolean) { if (shouldCancel()) throw CancellationException("Smart Magic processing cancelled") }
    private companion object {
        const val EPSILON = .0001f
        val OUTPUT_HEADROOM_LINEAR = SmartMagicColorMath.srgbToLinear(.982f)
    }
}
