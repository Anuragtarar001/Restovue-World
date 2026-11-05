package com.restorex.app.data

import com.restorex.app.domain.model.Adjustments
import com.restorex.app.domain.model.AutoEnhanceMode
import com.restorex.app.domain.model.ChannelBalance
import com.restorex.app.domain.model.HslColorRange
import com.restorex.app.domain.model.RasterImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Deterministic, offline CPU renderer shared by preview and full-resolution export. */
object CpuImagePipeline {
    fun apply(source: RasterImage, adjustments: Adjustments, lut: CubeLut? = null, lutIntensity: Float = 0f): RasterImage {
        if (adjustments.isNeutral && (lut == null || lutIntensity <= EPSILON)) return source
        val stats = ImageStatistics.from(source)
        var pixels = IntArray(source.argb.size)
        for (index in source.argb.indices) {
            val x = index % source.width
            val y = index / source.width
            pixels[index] = transformPixel(source.argb[index], x, y, source.width, source.height, adjustments, stats, lut, lutIntensity)
        }

        if (adjustments.dehaze > EPSILON) {
            pixels = applyDehaze(pixels, source.width, source.height, adjustments.dehaze)
        }
        if (adjustments.luminanceNoiseReduction > EPSILON || adjustments.colorNoiseReduction > EPSILON) {
            pixels = reduceNoise(
                pixels,
                source.width,
                source.height,
                adjustments.luminanceNoiseReduction,
                adjustments.colorNoiseReduction,
            )
        }
        if (adjustments.structure > EPSILON) {
            pixels = applyStructure(pixels, source.width, source.height, adjustments.structure, source.scaleFromOriginal)
        }
        if (abs(adjustments.clarity) > EPSILON) {
            pixels = applyClarity(pixels, source.width, source.height, adjustments.clarity, source.scaleFromOriginal)
        }
        if (abs(adjustments.texture) > EPSILON) {
            pixels = applyTexture(pixels, source.width, source.height, adjustments.texture, source.scaleFromOriginal)
        }
        if (adjustments.sharpening > EPSILON) {
            pixels = applySharpening(
                pixels,
                source.width,
                source.height,
                adjustments.sharpening,
                adjustments.sharpeningRadius * source.scaleFromOriginal,
                adjustments.sharpeningThreshold,
                adjustments.sharpeningMasking,
            )
        }
        if (adjustments.film.chromaticAberration > EPSILON) {
            pixels = applyChromaticAberration(pixels, source.width, source.height, adjustments.film.chromaticAberration)
        }
        return RasterImage(source.width, source.height, pixels, source.scaleFromOriginal)
    }

    fun sharpeningMask(source: RasterImage, adjustments: Adjustments): RasterImage {
        val luminance = luminanceArray(source.argb)
        val mask = edgeMask(luminance, source.width, source.height, adjustments.sharpeningMasking)
        val pixels = IntArray(mask.size) { index ->
            val value = (mask[index].coerceIn(0f, 1f) * 255f).roundToInt()
            (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }
        return RasterImage(source.width, source.height, pixels, source.scaleFromOriginal)
    }

    private fun applyDehaze(pixels: IntArray, width: Int, height: Int, amount: Float): IntArray {
        var atmosphere = .7f
        var brightest = 0f
        pixels.forEach { color ->
            val c = channels(color)
            val y = luma(c[0], c[1], c[2])
            if (y > brightest) { brightest = y; atmosphere = max(c[0], max(c[1], c[2])).coerceAtLeast(.7f) }
        }
        return IntArray(pixels.size) { index ->
            val x = index % width; val yPos = index / width
            var dark = 1f
            for (dy in -1..1) for (dx in -1..1) {
                val sample = channels(pixels[(yPos + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)])
                dark = min(dark, min(sample[0], min(sample[1], sample[2])))
            }
            val c = channels(pixels[index])
            val maxC = max(c[0], max(c[1], c[2])); val minC = min(c[0], min(c[1], c[2]))
            val saturation = if (maxC < EPSILON) 0f else (maxC - minC) / maxC
            val skyProtection = if (luma(c[0], c[1], c[2]) > .68f && saturation < .16f) .42f else 1f
            val effective = amount * skyProtection
            val transmission = (1f - .72f * effective * dark / atmosphere).coerceIn(.45f, 1f)
            fun restore(value: Float) = mix(value, ((value - atmosphere) / transmission + atmosphere).coerceIn(0f, 1f), effective)
            pack(pixels[index] ushr 24 and 0xFF, restore(c[0]), restore(c[1]), restore(c[2]))
        }
    }

    private fun applyStructure(pixels: IntArray, width: Int, height: Int, amount: Float, scale: Float): IntArray {
        val y = luminanceArray(pixels)
        val fine = gaussianBlur(y, width, height, (.8f * scale).coerceAtLeast(.3f))
        val medium = gaussianBlur(y, width, height, (2.4f * scale).coerceAtLeast(.65f))
        val target = FloatArray(y.size) { index ->
            val fineBand = (y[index] - fine[index]).coerceIn(-.08f, .08f)
            val mediumBand = (y[index] - medium[index]).coerceIn(-.12f, .12f)
            val noiseGate = smoothStep(.004f, .025f, abs(fineBand))
            y[index] + amount * (fineBand * .48f * noiseGate + mediumBand * .34f)
        }
        return replaceLuminance(pixels, y, target)
    }

    private fun applyClarity(pixels: IntArray, width: Int, height: Int, amount: Float, scale: Float): IntArray {
        val y = luminanceArray(pixels)
        val blur = gaussianBlur(y, width, height, (4.5f * scale).coerceAtLeast(1f))
        val target = FloatArray(y.size) { index ->
            val band = (y[index] - blur[index]).coerceIn(-.16f, .16f)
            val smoothProtection = smoothStep(.006f, .04f, abs(band))
            y[index] + band * amount * .62f * smoothProtection
        }
        return replaceLuminance(pixels, y, target)
    }

    private fun applyTexture(pixels: IntArray, width: Int, height: Int, amount: Float, scale: Float): IntArray {
        val y = luminanceArray(pixels)
        val blur = gaussianBlur(y, width, height, (.65f * scale).coerceAtLeast(.3f))
        val gradient = gradientMagnitude(y, width, height)
        val target = FloatArray(y.size) { index ->
            val band = (y[index] - blur[index]).coerceIn(-.07f, .07f)
            val edgeProtection = 1f - smoothStep(.12f, .35f, gradient[index]) * .72f
            val noiseGate = smoothStep(.003f, .018f, abs(band))
            y[index] + band * amount * .78f * edgeProtection * noiseGate
        }
        return replaceLuminance(pixels, y, target)
    }

    private fun applySharpening(
        pixels: IntArray,
        width: Int,
        height: Int,
        amount: Float,
        radius: Float,
        threshold: Float,
        masking: Float,
    ): IntArray {
        val y = luminanceArray(pixels)
        val blur = gaussianBlur(y, width, height, radius.coerceIn(.3f, 3f))
        val eligibility = edgeMask(y, width, height, masking)
        val pixelThreshold = threshold * .12f // UI 0..100 maps to 0..30.6 levels in 8-bit space.
        val target = FloatArray(y.size) { index ->
            val detail = y[index] - blur[index]
            if (abs(detail) <= pixelThreshold) y[index]
            else y[index] + detail.coerceIn(-.20f, .20f) * amount * eligibility[index]
        }
        return replaceLuminance(pixels, y, target)
    }

    private fun transformPixel(
        color: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        a: Adjustments,
        stats: ImageStatistics,
        lut: CubeLut?,
        lutIntensity: Float,
    ): Int {
        val alpha = color ushr 24 and 0xFF
        var r = (color ushr 16 and 0xFF) / 255f
        var g = (color ushr 8 and 0xFF) / 255f
        var b = (color and 0xFF) / 255f
        val strength = a.enhanceStrength

        when (a.autoMode) {
            AutoEnhanceMode.AUTO_LEVELS -> {
                r = mix(r, levels(r, stats.redLow, stats.redHigh), strength)
                g = mix(g, levels(g, stats.greenLow, stats.greenHigh), strength)
                b = mix(b, levels(b, stats.blueLow, stats.blueHigh), strength)
            }
            AutoEnhanceMode.AUTO_CONTRAST -> {
                r = mix(r, levels(r, stats.lumaLow, stats.lumaHigh), strength)
                g = mix(g, levels(g, stats.lumaLow, stats.lumaHigh), strength)
                b = mix(b, levels(b, stats.lumaLow, stats.lumaHigh), strength)
            }
            AutoEnhanceMode.AUTO_WHITE_BALANCE -> {
                val gains = stats.whiteBalanceGains()
                r *= mix(1f, gains[0], strength)
                g *= mix(1f, gains[1], strength)
                b *= mix(1f, gains[2], strength)
            }
            AutoEnhanceMode.AUTO_ENHANCE -> {
                val levelStrength = strength * 0.38f
                r = mix(r, levels(r, stats.lumaLow, stats.lumaHigh), levelStrength)
                g = mix(g, levels(g, stats.lumaLow, stats.lumaHigh), levelStrength)
                b = mix(b, levels(b, stats.lumaLow, stats.lumaHigh), levelStrength)
                val gains = stats.whiteBalanceGains(limit = 0.09f)
                r *= mix(1f, gains[0], strength * 0.55f)
                g *= mix(1f, gains[1], strength * 0.55f)
                b *= mix(1f, gains[2], strength * 0.55f)
                val autoEv = (ln(0.46f / stats.averageLuma.coerceAtLeast(0.04f)) / LN_2)
                    .coerceIn(-0.7f, 0.7f) * strength
                val gain = 2f.pow(autoEv)
                r *= gain; g *= gain; b *= gain
            }
            AutoEnhanceMode.NONE -> Unit
        }

        // Temperature uses opposing red/blue gains; tint separates green from magenta.
        r *= 1f + a.temperature * 0.16f + a.tint * 0.045f
        g *= 1f - a.tint * 0.09f
        b *= 1f - a.temperature * 0.16f + a.tint * 0.045f

        val exposureGain = 2f.pow(a.exposure)
        r *= exposureGain; g *= exposureGain; b *= exposureGain

        val luma = luma(r, g, b)
        val shadowMask = (1f - luma.coerceIn(0f, 1f)).pow(2)
        val highlightMask = luma.coerceIn(0f, 1f).pow(2)
        val whiteMask = smoothStep(0.62f, 1f, luma)
        val blackMask = 1f - smoothStep(0f, 0.38f, luma)
        val tonalDelta = a.brightness * 0.28f + a.shadows * shadowMask * 0.34f +
            a.highlights * highlightMask * 0.30f + a.whites * whiteMask * 0.22f + a.blacks * blackMask * 0.22f
        r += tonalDelta; g += tonalDelta; b += tonalDelta

        if (abs(a.toneCurve) > EPSILON) {
            r = toneCurve(r, a.toneCurve); g = toneCurve(g, a.toneCurve); b = toneCurve(b, a.toneCurve)
        }
        if (abs(a.gamma - 1f) > EPSILON) {
            val inverseGamma = 1f / a.gamma
            r = r.coerceAtLeast(0f).pow(inverseGamma)
            g = g.coerceAtLeast(0f).pow(inverseGamma)
            b = b.coerceAtLeast(0f).pow(inverseGamma)
        }
        r = (r - 0.5f) * a.contrast + 0.5f
        g = (g - 0.5f) * a.contrast + 0.5f
        b = (b - 0.5f) * a.contrast + 0.5f

        r *= 1f + a.redChannel * 0.5f
        g *= 1f + a.greenChannel * 0.5f
        b *= 1f + a.blueChannel * 0.5f

        var hsl = rgbToHsl(r, g, b)
        var hue = wrapHue(hsl[0] + a.hue)
        var saturation = hsl[1]
        var lightness = hsl[2]
        val autoVibrance = if (a.autoMode == AutoEnhanceMode.AUTO_ENHANCE) 0.12f * strength else 0f
        val vibrance = (a.vibrance + autoVibrance).coerceIn(-1f, 1f)
        saturation *= a.saturation
        saturation += vibrance * (1f - saturation.coerceIn(0f, 1f)) * 0.65f

        for (range in HslColorRange.entries) {
            val value = a.hsl[range]
            if (!value.isNeutral) {
                val weight = hueWeight(hue, range.centerHue)
                hue = wrapHue(hue + value.hue * weight)
                saturation += value.saturation * weight * (1f - saturation.coerceIn(0f, 1f) * 0.35f)
                lightness += value.luminance * weight * 0.32f
            }
        }
        hsl = hslToRgb(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
        r = hsl[0]; g = hsl[1]; b = hsl[2]

        val adjustedLuma = luma(r, g, b).coerceIn(0f, 1f)
        val shadowWeight = (1f - adjustedLuma).pow(2)
        val highlightWeight = adjustedLuma.pow(2)
        val midWeight = (1f - abs(adjustedLuma - 0.5f) * 2f).coerceIn(0f, 1f)
        val balance = a.colorBalance
        r += channelDelta(balance.shadows, balance.midtones, balance.highlights, shadowWeight, midWeight, highlightWeight) { it.red }
        g += channelDelta(balance.shadows, balance.midtones, balance.highlights, shadowWeight, midWeight, highlightWeight) { it.green }
        b += channelDelta(balance.shadows, balance.midtones, balance.highlights, shadowWeight, midWeight, highlightWeight) { it.blue }

        if (!a.splitToning.isNeutral) {
            val split = a.splitToning
            val shifted = (adjustedLuma + split.balance * 0.2f).coerceIn(0f, 1f)
            val shadowTone = hslToRgb(split.shadowHue, 1f, 0.5f)
            val highlightTone = hslToRgb(split.highlightHue, 1f, 0.5f)
            val sw = (1f - shifted).pow(2) * split.shadowSaturation * 0.28f
            val hw = shifted.pow(2) * split.highlightSaturation * 0.28f
            r = mix(r, shadowTone[0], sw); g = mix(g, shadowTone[1], sw); b = mix(b, shadowTone[2], sw)
            r = mix(r, highlightTone[0], hw); g = mix(g, highlightTone[1], hw); b = mix(b, highlightTone[2], hw)
        }

        if (lut != null && lutIntensity > EPSILON) {
            val mapped = lut.sample(r, g, b)
            r = mix(r, mapped[0], lutIntensity); g = mix(g, mapped[1], lutIntensity); b = mix(b, mapped[2], lutIntensity)
        }

        val film = a.film
        if (!film.isNeutral) {
            if (film.highlightRolloff > EPSILON) {
                fun roll(value: Float): Float {
                    val c = value.coerceAtLeast(0f)
                    val shoulder = smoothStep(.58f, 1f, c)
                    return c - shoulder * film.highlightRolloff * (c - .58f) * .45f
                }
                r = roll(r); g = roll(g); b = roll(b)
            }
            if (abs(film.tone) > EPSILON) {
                r *= 1f + film.tone * .14f
                b *= 1f - film.tone * .14f
            }
            if (film.colorFade > EPSILON) {
                val yValue = luma(r, g, b)
                val amount = film.colorFade * .42f
                r = mix(r, yValue, amount); g = mix(g, yValue, amount); b = mix(b, yValue, amount)
            }
            if (film.fade > EPSILON) {
                val amount = film.fade
                r = r * (1f - amount * .18f) + amount * .085f
                g = g * (1f - amount * .18f) + amount * .082f
                b = b * (1f - amount * .18f) + amount * .078f
            }
            if (film.shadowTint > EPSILON) {
                val yValue = luma(r, g, b).coerceIn(0f, 1f)
                val tone = hslToRgb(film.shadowTintHue, 1f, .5f)
                val amount = (1f - yValue).pow(2) * film.shadowTint * .28f
                r = mix(r, tone[0], amount); g = mix(g, tone[1], amount); b = mix(b, tone[2], amount)
            }
            if (film.lightLeak > EPSILON && width > 1 && height > 1) {
                val nx = x / (width - 1f); val ny = y / (height - 1f)
                val fromRight = sqrt((1f - nx) * (1f - nx) + ny * ny)
                val leak = (1f - smoothStep(.05f, .92f, fromRight)) * film.lightLeak
                r += leak * .48f; g += leak * .16f; b += leak * .035f
            }
            if (film.grain > EPSILON) {
                val hash = pixelHash(x, y, film.seed)
                val noise = ((hash ushr 8 and 0xFFFF) / 65535f - .5f) * film.grain * .18f
                val yValue = luma(r, g, b).coerceIn(0f, 1f)
                val grainMask = .45f + (1f - abs(yValue - .5f) * 2f) * .55f
                r += noise * grainMask; g += noise * grainMask; b += noise * grainMask
            }
        }

        if (abs(a.vignette) > EPSILON && width > 1 && height > 1) {
            val nx = (x / (width - 1f) - 0.5f) * 2f
            val ny = (y / (height - 1f) - 0.5f) * 2f
            val edge = smoothStep(0.25f, 1.15f, sqrt(nx * nx + ny * ny))
            val factor = 1f - a.vignette * edge * 0.55f
            r *= factor; g *= factor; b *= factor
        }
        return pack(alpha, r, g, b)
    }

    private fun applyChromaticAberration(pixels: IntArray, width: Int, height: Int, amount: Float): IntArray {
        val shift = (1 + amount * 3f).roundToInt().coerceIn(1, 4)
        return IntArray(pixels.size) { index ->
            val x = index % width; val y = index / width
            val left = pixels[y * width + (x - shift).coerceAtLeast(0)]
            val center = pixels[index]
            val right = pixels[y * width + (x + shift).coerceAtMost(width - 1)]
            val blend = amount.coerceIn(0f, 1f) * .7f
            val red = mix((center ushr 16 and 0xFF) / 255f, (left ushr 16 and 0xFF) / 255f, blend)
            val green = (center ushr 8 and 0xFF) / 255f
            val blue = mix((center and 0xFF) / 255f, (right and 0xFF) / 255f, blend)
            pack(center ushr 24 and 0xFF, red, green, blue)
        }
    }

    private fun pixelHash(x: Int, y: Int, seed: Int): Int {
        var value = x * 0x1f1f1f1f xor y * 0x45d9f3b xor seed
        value = (value xor (value ushr 16)) * 0x45d9f3b
        return value xor (value ushr 16)
    }

    private fun reduceNoise(pixels: IntArray, width: Int, height: Int, luminanceAmount: Float, colorAmount: Float): IntArray {
        val count = pixels.size
        val yValues = FloatArray(count)
        val cbValues = FloatArray(count)
        val crValues = FloatArray(count)
        pixels.forEachIndexed { index, color ->
            val c = channels(color)
            val y = luma(c[0], c[1], c[2])
            yValues[index] = y
            cbValues[index] = (c[2] - y) * .564f
            crValues[index] = (c[0] - y) * .713f
        }
        val output = IntArray(count)
        val sigmaRange = .035f + max(luminanceAmount, colorAmount) * .09f
        val denominator = 2f * sigmaRange * sigmaRange
        for (index in pixels.indices) {
            val x = index % width; val yPos = index / width
            var sumWeight = 0f; var sumY = 0f; var sumCb = 0f; var sumCr = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val sampleIndex = (yPos + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)
                val difference = yValues[sampleIndex] - yValues[index]
                val spatial = if (dx == 0 && dy == 0) 1f else if (dx == 0 || dy == 0) .78f else .58f
                val weight = spatial * exp(-(difference * difference) / denominator)
                sumWeight += weight
                sumY += yValues[sampleIndex] * weight
                sumCb += cbValues[sampleIndex] * weight
                sumCr += crValues[sampleIndex] * weight
            }
            val filteredY = sumY / sumWeight
            val filteredCb = sumCb / sumWeight
            val filteredCr = sumCr / sumWeight
            val y = mix(yValues[index], filteredY, luminanceAmount * .9f)
            val cb = mix(cbValues[index], filteredCb, colorAmount * .92f)
            val cr = mix(crValues[index], filteredCr, colorAmount * .92f)
            output[index] = pack(
                pixels[index] ushr 24 and 0xFF,
                y + 1.403f * cr,
                y - .344f * cb - .714f * cr,
                y + 1.773f * cb,
            )
        }
        return output
    }

    private fun luminanceArray(pixels: IntArray) = FloatArray(pixels.size) { index ->
        val color = pixels[index]
        luma((color ushr 16 and 0xFF) / 255f, (color ushr 8 and 0xFF) / 255f, (color and 0xFF) / 255f)
    }

    private fun replaceLuminance(pixels: IntArray, original: FloatArray, target: FloatArray): IntArray =
        IntArray(pixels.size) { index ->
            val color = pixels[index]
            val delta = target[index].coerceIn(0f, 1f) - original[index]
            pack(
                color ushr 24 and 0xFF,
                (color ushr 16 and 0xFF) / 255f + delta,
                (color ushr 8 and 0xFF) / 255f + delta,
                (color and 0xFF) / 255f + delta,
            )
        }

    private fun gaussianBlur(source: FloatArray, width: Int, height: Int, sigma: Float): FloatArray {
        val safeSigma = sigma.coerceAtLeast(.3f)
        val radius = ceil(safeSigma * 2.5f).toInt().coerceIn(1, 8)
        val kernel = FloatArray(radius * 2 + 1)
        var kernelSum = 0f
        for (offset in -radius..radius) {
            val value = exp(-(offset * offset) / (2f * safeSigma * safeSigma))
            kernel[offset + radius] = value
            kernelSum += value
        }
        kernel.indices.forEach { kernel[it] /= kernelSum }
        val horizontal = FloatArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f
            for (offset in -radius..radius) sum += source[y * width + (x + offset).coerceIn(0, width - 1)] * kernel[offset + radius]
            horizontal[y * width + x] = sum
        }
        val output = FloatArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f
            for (offset in -radius..radius) sum += horizontal[(y + offset).coerceIn(0, height - 1) * width + x] * kernel[offset + radius]
            output[y * width + x] = sum
        }
        return output
    }

    private fun gradientMagnitude(values: FloatArray, width: Int, height: Int): FloatArray = FloatArray(values.size) { index ->
        val x = index % width; val y = index / width
        val left = values[y * width + (x - 1).coerceAtLeast(0)]
        val right = values[y * width + (x + 1).coerceAtMost(width - 1)]
        val top = values[(y - 1).coerceAtLeast(0) * width + x]
        val bottom = values[(y + 1).coerceAtMost(height - 1) * width + x]
        sqrt((right - left) * (right - left) + (bottom - top) * (bottom - top))
    }

    private fun edgeMask(values: FloatArray, width: Int, height: Int, masking: Float): FloatArray {
        if (masking <= EPSILON) return FloatArray(values.size) { 1f }
        val gradient = gradientMagnitude(values, width, height)
        val low = .008f + masking * .055f
        val high = .045f + masking * .20f
        return FloatArray(values.size) { index -> smoothStep(low, high, gradient[index]) }
    }

    private fun applyLocalContrast(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        amount: Float,
        threshold: Float,
    ): IntArray {
        val blurred = boxBlur(pixels, width, height, radius)
        return IntArray(pixels.size) { index ->
            val original = channels(pixels[index])
            val smooth = channels(blurred[index])
            val difference = luma(original[0], original[1], original[2]) - luma(smooth[0], smooth[1], smooth[2])
            val applied = if (abs(difference) >= threshold) amount else 0f
            pack(
                pixels[index] ushr 24 and 0xFF,
                original[0] + (original[0] - smooth[0]) * applied,
                original[1] + (original[1] - smooth[1]) * applied,
                original[2] + (original[2] - smooth[2]) * applied,
            )
        }
    }

    /** Separable box blur: O(pixel count), two packed buffers, and no native allocation. */
    private fun boxBlur(source: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius <= 0) return source.copyOf()
        val horizontal = IntArray(source.size)
        for (y in 0 until height) {
            var sumR = 0; var sumG = 0; var sumB = 0
            for (x in -radius..radius) {
                val color = source[y * width + x.coerceIn(0, width - 1)]
                sumR += color ushr 16 and 0xFF; sumG += color ushr 8 and 0xFF; sumB += color and 0xFF
            }
            val count = radius * 2 + 1
            for (x in 0 until width) {
                val alpha = source[y * width + x] ushr 24 and 0xFF
                horizontal[y * width + x] = (alpha shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                val remove = source[y * width + (x - radius).coerceIn(0, width - 1)]
                val add = source[y * width + (x + radius + 1).coerceIn(0, width - 1)]
                sumR += (add ushr 16 and 0xFF) - (remove ushr 16 and 0xFF)
                sumG += (add ushr 8 and 0xFF) - (remove ushr 8 and 0xFF)
                sumB += (add and 0xFF) - (remove and 0xFF)
            }
        }
        val output = IntArray(source.size)
        for (x in 0 until width) {
            var sumR = 0; var sumG = 0; var sumB = 0
            for (y in -radius..radius) {
                val color = horizontal[y.coerceIn(0, height - 1) * width + x]
                sumR += color ushr 16 and 0xFF; sumG += color ushr 8 and 0xFF; sumB += color and 0xFF
            }
            val count = radius * 2 + 1
            for (y in 0 until height) {
                val alpha = source[y * width + x] ushr 24 and 0xFF
                output[y * width + x] = (alpha shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                val remove = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                val add = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                sumR += (add ushr 16 and 0xFF) - (remove ushr 16 and 0xFF)
                sumG += (add ushr 8 and 0xFF) - (remove ushr 8 and 0xFF)
                sumB += (add and 0xFF) - (remove and 0xFF)
            }
        }
        return output
    }

    private data class ImageStatistics(
        val averageLuma: Float,
        val averageRed: Float,
        val averageGreen: Float,
        val averageBlue: Float,
        val lumaLow: Float,
        val lumaHigh: Float,
        val redLow: Float,
        val redHigh: Float,
        val greenLow: Float,
        val greenHigh: Float,
        val blueLow: Float,
        val blueHigh: Float,
    ) {
        fun whiteBalanceGains(limit: Float = 0.14f): FloatArray {
            val gray = (averageRed + averageGreen + averageBlue) / 3f
            return floatArrayOf(
                (gray / averageRed.coerceAtLeast(0.03f)).coerceIn(1f - limit, 1f + limit),
                (gray / averageGreen.coerceAtLeast(0.03f)).coerceIn(1f - limit, 1f + limit),
                (gray / averageBlue.coerceAtLeast(0.03f)).coerceIn(1f - limit, 1f + limit),
            )
        }

        companion object {
            fun from(image: RasterImage): ImageStatistics {
                val lumaHistogram = IntArray(256); val redHistogram = IntArray(256)
                val greenHistogram = IntArray(256); val blueHistogram = IntArray(256)
                var sumLuma = 0.0; var sumRed = 0.0; var sumGreen = 0.0; var sumBlue = 0.0
                image.argb.forEach { color ->
                    val r = color ushr 16 and 0xFF; val g = color ushr 8 and 0xFF; val b = color and 0xFF
                    val y = (r * 0.2126f + g * 0.7152f + b * 0.0722f).roundToInt().coerceIn(0, 255)
                    lumaHistogram[y]++; redHistogram[r]++; greenHistogram[g]++; blueHistogram[b]++
                    sumLuma += y; sumRed += r; sumGreen += g; sumBlue += b
                }
                val count = image.argb.size.coerceAtLeast(1)
                fun low(histogram: IntArray) = percentile(histogram, count, 0.02f) / 255f
                fun high(histogram: IntArray) = percentile(histogram, count, 0.98f) / 255f
                return ImageStatistics(
                    (sumLuma / count / 255.0).toFloat(), (sumRed / count / 255.0).toFloat(),
                    (sumGreen / count / 255.0).toFloat(), (sumBlue / count / 255.0).toFloat(),
                    low(lumaHistogram), high(lumaHistogram), low(redHistogram), high(redHistogram),
                    low(greenHistogram), high(greenHistogram), low(blueHistogram), high(blueHistogram),
                )
            }

            private fun percentile(histogram: IntArray, count: Int, percentile: Float): Int {
                val target = ceil(count * percentile).toInt().coerceAtLeast(1)
                var cumulative = 0
                histogram.forEachIndexed { value, amount ->
                    cumulative += amount
                    if (cumulative >= target) return value
                }
                return 255
            }
        }
    }

    private fun channelDelta(
        shadows: ChannelBalance,
        midtones: ChannelBalance,
        highlights: ChannelBalance,
        shadowWeight: Float,
        midWeight: Float,
        highlightWeight: Float,
        channel: (ChannelBalance) -> Float,
    ) = (channel(shadows) * shadowWeight + channel(midtones) * midWeight + channel(highlights) * highlightWeight) * 0.18f

    private fun levels(value: Float, low: Float, high: Float): Float {
        if (high - low < 0.08f) return value
        return ((value - low) / (high - low)).coerceIn(0f, 1f)
    }

    private fun toneCurve(value: Float, amount: Float): Float {
        val x = value.coerceIn(0f, 1f)
        val centered = x - 0.5f
        return x + amount * centered * (1f - 4f * centered * centered) * 0.75f
    }

    private fun hueWeight(hue: Float, center: Float): Float {
        val distance = min(abs(hue - center), 360f - abs(hue - center))
        return (1f - distance / 45f).coerceIn(0f, 1f)
    }

    private fun rgbToHsl(red: Float, green: Float, blue: Float): FloatArray {
        val r = red.coerceIn(0f, 1f); val g = green.coerceIn(0f, 1f); val b = blue.coerceIn(0f, 1f)
        val maxValue = max(r, max(g, b)); val minValue = min(r, min(g, b)); val delta = maxValue - minValue
        val lightness = (maxValue + minValue) / 2f
        if (delta < EPSILON) return floatArrayOf(0f, 0f, lightness)
        val saturation = delta / (1f - abs(2f * lightness - 1f)).coerceAtLeast(EPSILON)
        val hue = when (maxValue) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return floatArrayOf(wrapHue(hue), saturation, lightness)
    }

    private fun hslToRgb(hue: Float, saturation: Float, lightness: Float): FloatArray {
        val h = wrapHue(hue)
        val c = (1f - abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = lightness - c / 2f
        val base = when {
            h < 60f -> floatArrayOf(c, x, 0f)
            h < 120f -> floatArrayOf(x, c, 0f)
            h < 180f -> floatArrayOf(0f, c, x)
            h < 240f -> floatArrayOf(0f, x, c)
            h < 300f -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        return floatArrayOf(base[0] + m, base[1] + m, base[2] + m)
    }

    private fun channels(color: Int) = floatArrayOf(
        (color ushr 16 and 0xFF) / 255f,
        (color ushr 8 and 0xFF) / 255f,
        (color and 0xFF) / 255f,
    )

    private fun pack(alpha: Int, red: Float, green: Float, blue: Float): Int =
        (alpha shl 24) or ((red.coerceIn(0f, 1f) * 255f).roundToInt() shl 16) or
            ((green.coerceIn(0f, 1f) * 255f).roundToInt() shl 8) or (blue.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun luma(red: Float, green: Float, blue: Float) = red * 0.2126f + green * 0.7152f + blue * 0.0722f
    private fun mix(start: Float, end: Float, amount: Float) = start + (end - start) * amount.coerceIn(0f, 1f)
    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    private fun wrapHue(value: Float) = ((value % 360f) + 360f) % 360f

    private const val EPSILON = 0.0001f
    private const val LN_2 = 0.6931472f
}
