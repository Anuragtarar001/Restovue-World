package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PhotometricAnalyzerV2(
    private val maxSamples: Int = 262_144,
    private val gridScales: IntArray = intArrayOf(4, 8),
) {
    init { require(maxSamples >= 256); require(gridScales.isNotEmpty() && gridScales.all { it in 2..16 }) }

    fun analyze(source: RasterImage, shouldCancel: () -> Boolean = { false }): PhotometricEvidence {
        val started = System.nanoTime()
        val step = ceil(sqrt((source.argb.size / maxSamples.toDouble()).coerceAtLeast(1.0))).toInt().coerceAtLeast(1)
        val histogram = FloatArray(GLOBAL_BINS)
        val saturationHistogram = FloatArray(256)
        val regions = gridScales.associateWith { scale -> Array(scale * scale) { RegionAccumulator() } }
        var sampled = 0; var visibleWeight = 0f
        var sumY = 0.0; var sumR = 0.0; var sumG = 0.0; var sumB = 0.0
        var sumSat = 0.0; var sumChroma = 0.0; var highSat = 0f
        var neutralWeight = 0f; var neutralR = 0.0; var neutralG = 0.0; var neutralB = 0.0
        var black = 0f; var white = 0f
        var blackR = 0f; var blackG = 0f; var blackB = 0f
        var whiteR = 0f; var whiteG = 0f; var whiteB = 0f
        var edgeWeight = 0f; var edgeCount = 0f; var gradientSum = 0.0; var laplacianSum = 0.0
        var smoothWeight = 0f; var smoothLaplacian = 0.0

        for (y in 0 until source.height step step) {
            if (shouldCancel()) throw CancellationException("Photometric analysis cancelled")
            for (x in 0 until source.width step step) {
                val argb = source.argb[y * source.width + x]
                val alpha = (argb ushr 24 and 0xff) / 255f
                sampled++
                if (alpha <= 0f) continue
                val sr = (argb ushr 16 and 0xff) / 255f
                val sg = (argb ushr 8 and 0xff) / 255f
                val sb = (argb and 0xff) / 255f
                val r = SmartMagicColorMath.srgbToLinear(sr)
                val g = SmartMagicColorMath.srgbToLinear(sg)
                val b = SmartMagicColorMath.srgbToLinear(sb)
                val luma = SmartMagicColorMath.luminance(r, g, b).coerceIn(0f, 1f)
                val maximum = max(sr, max(sg, sb)); val minimum = min(sr, min(sg, sb))
                val chroma = maximum - minimum
                val saturation = if (maximum <= EPSILON) 0f else chroma / maximum
                val globalBin = (luma * (GLOBAL_BINS - 1)).roundToInt()
                histogram[globalBin] += alpha
                saturationHistogram[(saturation * 255f).roundToInt()] += alpha
                visibleWeight += alpha; sumY += luma * alpha; sumR += r * alpha; sumG += g * alpha; sumB += b * alpha
                sumSat += saturation * alpha; sumChroma += chroma * alpha
                if (saturation >= .8f) highSat += alpha
                if (luma <= BLACK_THRESHOLD) black += alpha
                if (luma >= WHITE_THRESHOLD) white += alpha
                if (sr <= 2f / 255f) blackR += alpha
                if (sg <= 2f / 255f) blackG += alpha
                if (sb <= 2f / 255f) blackB += alpha
                if (sr >= 253f / 255f) whiteR += alpha
                if (sg >= 253f / 255f) whiteG += alpha
                if (sb >= 253f / 255f) whiteB += alpha
                if (saturation <= .10f && luma in .035f..0.92f) {
                    neutralWeight += alpha; neutralR += r * alpha; neutralG += g * alpha; neutralB += b * alpha
                }

                var gradient = 0f; var laplacian = 0f
                if (x >= step && x + step < source.width && y >= step && y + step < source.height) {
                    val left = visibleLinearLuma(source.argb[y * source.width + x - step])
                    val right = visibleLinearLuma(source.argb[y * source.width + x + step])
                    val top = visibleLinearLuma(source.argb[(y - step) * source.width + x])
                    val bottom = visibleLinearLuma(source.argb[(y + step) * source.width + x])
                    if (left != null && right != null && top != null && bottom != null) {
                        gradient = sqrt((right - left) * (right - left) + (bottom - top) * (bottom - top))
                        laplacian = abs(4f * luma - left - right - top - bottom)
                        edgeWeight += alpha; gradientSum += gradient * alpha; laplacianSum += laplacian * alpha
                        if (gradient >= .055f) edgeCount += alpha
                        if (gradient < .025f) { smoothWeight += alpha; smoothLaplacian += laplacian * alpha }
                    }
                }
                gridScales.forEach { scale ->
                    val column = (x.toLong() * scale / source.width).toInt().coerceIn(0, scale - 1)
                    val row = (y.toLong() * scale / source.height).toInt().coerceIn(0, scale - 1)
                    regions.getValue(scale)[row * scale + column].add(luma, saturation, gradient, laplacian, alpha)
                }
            }
        }
        require(visibleWeight > EPSILON) { "The image contains no visible pixels" }
        val p01 = percentile(histogram, visibleWeight, .01f); val p05 = percentile(histogram, visibleWeight, .05f)
        val p25 = percentile(histogram, visibleWeight, .25f); val p50 = percentile(histogram, visibleWeight, .5f)
        val p75 = percentile(histogram, visibleWeight, .75f); val p95 = percentile(histogram, visibleWeight, .95f)
        val p99 = percentile(histogram, visibleWeight, .99f)
        val globalLuma = LuminanceDistribution(
            mean = (sumY / visibleWeight).toFloat(), median = p50, p01 = p01, p05 = p05, p25 = p25,
            p50 = p50, p75 = p75, p95 = p95, p99 = p99,
            shadowOccupancy = fractionBelow(histogram, visibleWeight, SHADOW_THRESHOLD),
            highlightOccupancy = fractionAbove(histogram, visibleWeight, HIGHLIGHT_THRESHOLD),
            blackClipping = black / visibleWeight, whiteClipping = white / visibleWeight,
            robustRange = p95 - p05, globalContrast = p75 - p25,
        )
        val nr = if (neutralWeight > EPSILON) (neutralR / neutralWeight).toFloat() else null
        val ng = if (neutralWeight > EPSILON) (neutralG / neutralWeight).toFloat() else null
        val nb = if (neutralWeight > EPSILON) (neutralB / neutralWeight).toFloat() else null
        val spread = if (nr == null || ng == null || nb == null) 1f else
            (max(nr, max(ng, nb)) - min(nr, min(ng, nb))) / max(nr, max(ng, nb)).coerceAtLeast(.01f)
        val wbConfidence = ((neutralWeight / visibleWeight) * 5f).coerceIn(0f, 1f) * (1f - smooth(.20f, .62f, spread))
        val neutralMean = if (nr == null || ng == null || nb == null) 1f else (nr + ng + nb) / 3f
        fun gain(channel: Float?): Float = if (channel == null || wbConfidence < .15f) 1f else
            (1f + ((neutralMean / channel.coerceAtLeast(.01f)) - 1f) * wbConfidence).coerceIn(.85f, 1.15f)
        val color = ColorEvidence(
            meanLinearRed = (sumR / visibleWeight).toFloat(), meanLinearGreen = (sumG / visibleWeight).toFloat(),
            meanLinearBlue = (sumB / visibleWeight).toFloat(), meanSaturation = (sumSat / visibleWeight).toFloat(),
            medianSaturation = percentile(saturationHistogram, visibleWeight, .5f), meanChroma = (sumChroma / visibleWeight).toFloat(),
            highSaturationFraction = highSat / visibleWeight, neutralFraction = neutralWeight / visibleWeight,
            neutralRed = nr, neutralGreen = ng, neutralBlue = nb, whiteBalanceConfidence = wbConfidence,
            proposedRedGain = gain(nr), proposedGreenGain = gain(ng), proposedBlueGain = gain(nb),
            channelBlackClipping = ChannelFractions(blackR / visibleWeight, blackG / visibleWeight, blackB / visibleWeight),
            channelWhiteClipping = ChannelFractions(whiteR / visibleWeight, whiteG / visibleWeight, whiteB / visibleWeight),
        )
        val detail = DetailEvidence(
            edgeDensity = edgeCount / edgeWeight.coerceAtLeast(EPSILON),
            meanGradient = (gradientSum / edgeWeight.coerceAtLeast(EPSILON)).toFloat().coerceIn(0f, 1f),
            localContrast = (laplacianSum / edgeWeight.coerceAtLeast(EPSILON)).toFloat().coerceIn(0f, 1f),
            noiseRisk = (((smoothLaplacian / smoothWeight.coerceAtLeast(EPSILON)).toFloat() - .003f) / .05f).coerceIn(0f, 1f),
            fineDetailEnergy = ((laplacianSum / edgeWeight.coerceAtLeast(EPSILON)).toFloat() * 4f).coerceIn(0f, 1f),
        )
        val spatial = regions.flatMap { (scale, accumulators) ->
            accumulators.mapIndexed { index, accumulator ->
                val row = index / scale; val column = index % scale
                accumulator.toEvidence(scale, row, column, globalLuma.mean)
            }
        }
        return PhotometricEvidence(
            analysisVersion = VERSION, sourceWidth = source.width, sourceHeight = source.height,
            sampledPixels = sampled, effectiveVisibleWeight = visibleWeight, histogramBins = GLOBAL_BINS,
            luminance = globalLuma, color = color, detail = detail, regions = spatial,
            analysisDurationNanos = System.nanoTime() - started,
        )
    }

    private class RegionAccumulator {
        private val histogram = FloatArray(REGION_BINS)
        private var weight = 0f; private var lumaSum = 0.0; private var satSum = 0.0
        private var gradientSum = 0.0; private var lapSum = 0.0; private var edges = 0f
        fun add(luma: Float, saturation: Float, gradient: Float, laplacian: Float, alpha: Float) {
            histogram[(luma * (REGION_BINS - 1)).roundToInt()] += alpha
            weight += alpha; lumaSum += luma * alpha; satSum += saturation * alpha
            gradientSum += gradient * alpha; lapSum += laplacian * alpha
            if (gradient >= .055f) edges += alpha
        }
        fun toEvidence(scale: Int, row: Int, column: Int, globalMean: Float): SpatialRegionEvidence {
            val safe = weight.coerceAtLeast(EPSILON)
            val p01 = percentile(histogram, safe, .01f); val p05 = percentile(histogram, safe, .05f)
            val p25 = percentile(histogram, safe, .25f); val p50 = percentile(histogram, safe, .5f)
            val p75 = percentile(histogram, safe, .75f); val p95 = percentile(histogram, safe, .95f)
            val p99 = percentile(histogram, safe, .99f); val mean = (lumaSum / safe).toFloat()
            val luma = LuminanceDistribution(mean, p50, p01, p05, p25, p50, p75, p95, p99,
                fractionBelow(histogram, safe, SHADOW_THRESHOLD), fractionAbove(histogram, safe, HIGHLIGHT_THRESHOLD),
                fractionBelow(histogram, safe, BLACK_THRESHOLD), fractionAbove(histogram, safe, WHITE_THRESHOLD),
                p95 - p05, p75 - p25)
            return SpatialRegionEvidence(
                id = "grid-${scale}-${row}-${column}", scale = scale, row = row, column = column,
                bounds = NormalizedRect(column / scale.toFloat(), row / scale.toFloat(), (column + 1) / scale.toFloat(), (row + 1) / scale.toFloat()),
                visibleWeight = weight, luminance = luma, meanSaturation = (satSum / safe).toFloat(),
                localContrast = (lapSum / safe).toFloat().coerceIn(0f, 1f), edgeDensity = edges / safe,
                noiseRisk = (((lapSum / safe).toFloat() - .003f) / .05f).coerceIn(0f, 1f),
                relativeBrightness = mean - globalMean,
            )
        }
    }

    private fun visibleLinearLuma(argb: Int): Float? {
        if ((argb ushr 24 and 0xff) == 0) return null
        return SmartMagicColorMath.luminance(
            SmartMagicColorMath.srgbToLinear((argb ushr 16 and 0xff) / 255f),
            SmartMagicColorMath.srgbToLinear((argb ushr 8 and 0xff) / 255f),
            SmartMagicColorMath.srgbToLinear((argb and 0xff) / 255f),
        )
    }

    companion object {
        const val VERSION = "photometric-v2.0.0"
        private const val GLOBAL_BINS = 1024; private const val REGION_BINS = 256; private const val EPSILON = 1e-6f
        private const val BLACK_THRESHOLD = .00061f // linear sRGB value of 2/255
        private const val WHITE_THRESHOLD = .98225f // linear sRGB value of 253/255
        private const val SHADOW_THRESHOLD = .035f; private const val HIGHLIGHT_THRESHOLD = .80f

        private fun percentile(histogram: FloatArray, total: Float, fraction: Float): Float {
            if (total <= EPSILON) return 0f
            val target = total * fraction.coerceIn(0f, 1f); var cumulative = 0f
            histogram.forEachIndexed { index, amount ->
                cumulative += amount
                if (cumulative >= target) return index / (histogram.size - 1f)
            }
            return 1f
        }
        private fun fractionBelow(histogram: FloatArray, total: Float, threshold: Float): Float {
            if (total <= EPSILON) return 0f
            val last = ceil(threshold * (histogram.size - 1)).toInt().coerceIn(0, histogram.lastIndex)
            var sum = 0f; for (i in 0..last) sum += histogram[i]
            return (sum / total).coerceIn(0f, 1f)
        }
        private fun fractionAbove(histogram: FloatArray, total: Float, threshold: Float): Float {
            if (total <= EPSILON) return 0f
            val first = (threshold * (histogram.size - 1)).roundToInt().coerceIn(0, histogram.lastIndex)
            var sum = 0f; for (i in first..histogram.lastIndex) sum += histogram[i]
            return (sum / total).coerceIn(0f, 1f)
        }
        private fun smooth(edge0: Float, edge1: Float, value: Float): Float {
            val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
