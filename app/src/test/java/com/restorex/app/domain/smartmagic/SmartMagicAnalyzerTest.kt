package com.restorex.app.domain.smartmagic

import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.random.Random

class SmartMagicAnalyzerTest {
    private val analyzer = SmartMagicAnalyzer()
    private val parameters = SmartMagicParameterEngine()

    @Test fun srgbLinearRoundTripIsStable() {
        for (index in 0..255) {
            val original = index / 255f
            val roundTrip = SmartMagicColorMath.linearToSrgb(SmartMagicColorMath.srgbToLinear(original))
            assertTrue(abs(original - roundTrip) < 0.00002f)
        }
        assertTrue(abs(SmartMagicColorMath.srgbToLinear(.5f) - .21404f) < .0001f)
    }

    @Test fun luminanceUsesLinearSrgbCoefficients() {
        assertEquals(.2126f, SmartMagicColorMath.luminance(1f, 0f, 0f), 1e-6f)
        assertEquals(.7152f, SmartMagicColorMath.luminance(0f, 1f, 0f), 1e-6f)
        assertEquals(.0722f, SmartMagicColorMath.luminance(0f, 0f, 1f), 1e-6f)
    }

    @Test fun uniformGrayProducesCorrectPercentilesAndLowDetail() {
        val result = analyzer.analyze(solid(96, 64, argb(255, 128, 128, 128)))
        val expected = SmartMagicColorMath.srgbToLinear(128 / 255f)
        assertEquals(expected, result.luminance.median, .002f)
        assertTrue(result.luminance.p05 <= result.luminance.p95)
        assertTrue(result.detail.edgeDensity < .01f)
        assertTrue(result.color.neutralFraction > .99f)
    }

    @Test fun percentilesRemainOrderedForGradient() {
        val image = raster(256, 32) { x, _ -> argb(255, x, x, x) }
        val l = analyzer.analyze(image).luminance
        assertTrue(listOf(l.p01, l.p05, l.p25, l.p50, l.p75, l.p95, l.p99).zipWithNext().all { it.first <= it.second })
        assertTrue(l.effectiveRange > .7f)
    }

    @Test fun darkAndBrightImagesReceiveDifferentExposureDecisions() {
        val dark = parameters.parametersFor(analyzer.analyze(solid(128, 96, argb(255, 45, 42, 40))))
        val bright = parameters.parametersFor(analyzer.analyze(solid(128, 96, argb(255, 235, 235, 235))))
        assertTrue(dark.exposureEv > bright.exposureEv)
        assertTrue(dark.exposureEv in -.6f..0.8f)
        assertTrue(bright.exposureEv in -.6f..0.8f)
    }

    @Test fun alreadyBalancedImageIsNotForcedToLargeExposure() {
        val balanced = raster(160, 120) { x, _ ->
            val value = 65 + x * 145 / 159
            argb(255, value, value, value)
        }
        val p = parameters.parametersFor(analyzer.analyze(balanced))
        assertTrue(abs(p.exposureEv) < .28f)
    }

    @Test fun vividImageReceivesLessColorBoostThanDullImage() {
        val vivid = raster(128, 96) { x, y -> when ((x / 16 + y / 16) % 3) {
            0 -> argb(255, 250, 20, 25); 1 -> argb(255, 20, 245, 35); else -> argb(255, 20, 40, 250)
        } }
        val dull = raster(128, 96) { x, _ -> val v = 100 + x / 3; argb(255, v + 8, v, v - 5) }
        val vividP = parameters.parametersFor(analyzer.analyze(vivid))
        val dullP = parameters.parametersFor(analyzer.analyze(dull))
        assertTrue(vividP.vibrance < dullP.vibrance)
    }

    @Test fun neutralEvidenceProducesConservativeBoundedWhiteBalance() {
        val cast = raster(128, 96) { x, _ ->
            val v = 70 + x / 2
            argb(255, (v * 1.13f).toInt().coerceAtMost(255), v, (v * .9f).toInt())
        }
        val p = parameters.parametersFor(analyzer.analyze(cast))
        assertTrue(p.redGain in .85f..1.15f && p.greenGain in .85f..1.15f && p.blueGain in .85f..1.15f)
        assertTrue(p.whiteBalanceConfidence in 0f..1f)
    }

    @Test fun noiseEstimatorSeparatesNoiseFromCleanField() {
        val clean = solid(160, 120, argb(255, 90, 90, 90))
        val random = Random(42)
        val noisy = raster(160, 120) { _, _ ->
            val n = random.nextInt(-24, 25); val r = (90 + n).coerceIn(0, 255)
            argb(255, r, (90 - n / 2).coerceIn(0, 255), (90 + n / 3).coerceIn(0, 255))
        }
        assertTrue(analyzer.analyze(noisy).detail.estimatedNoise > analyzer.analyze(clean).detail.estimatedNoise)
    }

    @Test fun parameterBoundsHoldForEdgeCases() {
        val cases = listOf(
            solid(32, 32, argb(255, 0, 0, 0)), solid(32, 32, argb(255, 255, 255, 255)),
            solid(32, 32, argb(255, 127, 127, 127)), solid(32, 32, argb(255, 255, 0, 255)),
            raster(2, 97) { _, y -> argb(255, y * 255 / 96, 20, 255 - y * 255 / 96) },
        )
        cases.forEach { parameters.parametersFor(analyzer.analyze(it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun fullyTransparentImageIsRejectedClearly() {
        analyzer.analyze(solid(16, 16, argb(0, 255, 0, 0)))
    }

    @Test(expected = CancellationException::class)
    fun analysisHonorsCancellation() {
        analyzer.analyze(solid(512, 512, argb(255, 80, 90, 100))) { true }
    }

    private fun solid(width: Int, height: Int, color: Int) = RasterImage(width, height, IntArray(width * height) { color })
    private fun raster(width: Int, height: Int, pixel: (Int, Int) -> Int) =
        RasterImage(width, height, IntArray(width * height) { pixel(it % width, it / width) })
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
}
