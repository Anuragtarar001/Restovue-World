package com.restorex.app.data

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.math.abs

class SmartMagicPipelineTest {
    private val pipeline = SmartMagicPipeline()

    @Test fun toneCurveIsMonotonicAcrossAllowedParameters() {
        val parameterSets = listOf(
            SmartMagicParameters(),
            SmartMagicParameters(contrast = .22f, highlights = -.6f, shadows = .4f, whites = .15f, blacks = -.15f),
            SmartMagicParameters(contrast = -.1f, highlights = -.35f, shadows = .2f, whites = -.1f, blacks = .1f),
        )
        parameterSets.forEach { parameters ->
            var previous = -1f
            for (index in 0..4096) {
                val value = pipeline.toneMapLuminance(index / 4096f, parameters)
                assertTrue("tone curve inverted at $index", value + 1e-5f >= previous)
                previous = value
            }
        }
    }

    @Test fun shadowsAndHighlightsAffectDifferentTonalRegions() {
        val shadows = SmartMagicParameters(shadows = .35f)
        val highlights = SmartMagicParameters(highlights = -.45f)
        val shadowDeltaLow = pipeline.toneMapLuminance(.15f, shadows) - .15f
        val shadowDeltaHigh = pipeline.toneMapLuminance(.85f, shadows) - .85f
        val highlightDeltaLow = abs(pipeline.toneMapLuminance(.15f, highlights) - .15f)
        val highlightDeltaHigh = abs(pipeline.toneMapLuminance(.85f, highlights) - .85f)
        assertTrue(shadowDeltaLow > shadowDeltaHigh)
        assertTrue(highlightDeltaHigh > highlightDeltaLow)
    }

    @Test fun processingIsDeterministicAndDoesNotMutateSource() {
        val source = gradient(96, 64)
        val original = source.argb.copyOf()
        val p = SmartMagicParameters(exposureEv = .2f, contrast = .08f, vibrance = .12f, clarity = .06f, texture = .05f, sharpening = .24f)
        val first = pipeline.enhance(source, p)
        val second = pipeline.enhance(source, p)
        assertArrayEquals(first.argb, second.argb)
        assertArrayEquals(original, source.argb)
        assertFalse(first.argb.contentEquals(original))
    }

    @Test fun alphaAndDimensionsArePreservedWithoutFringingTransparentPixels() {
        val source = RasterImage(3, 1, intArrayOf(0x00FF0000, 0x8000FF00.toInt(), 0xFF336699.toInt()))
        val result = pipeline.enhance(source, SmartMagicParameters(exposureEv = .25f, vibrance = .2f))
        assertEquals(3, result.width); assertEquals(1, result.height)
        assertEquals(0, result.argb[0] ushr 24)
        assertEquals(128, result.argb[1] ushr 24)
        assertEquals(255, result.argb[2] ushr 24)
        assertEquals(source.argb[0], result.argb[0])
    }

    @Test fun zeroGlobalParametersLeaveOpaqueNeutralPixelsNearlyUnchanged() {
        val source = gradient(64, 32)
        val result = pipeline.enhance(source, SmartMagicParameters(sharpening = 0f))
        val maximumError = source.argb.indices.maxOf { index ->
            val a = source.argb[index]; val b = result.argb[index]
            maxOf(abs((a ushr 16 and 255) - (b ushr 16 and 255)), abs((a ushr 8 and 255) - (b ushr 8 and 255)), abs((a and 255) - (b and 255)))
        }
        assertTrue(maximumError <= 1)
    }

    @Test fun processingHonorsCancellation() {
        var checks = 0
        try {
            pipeline.enhance(gradient(512, 512), SmartMagicParameters(clarity = .08f), shouldCancel = { ++checks > 1 })
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) { assertTrue(checks > 1) }
    }

    @Test fun repeatedPreviewAndExportRenderingMatchAtSameResolution() {
        val source = gradient(128, 96)
        val p = SmartMagicParameters(exposureEv = .16f, highlights = -.22f, shadows = .18f, vibrance = .1f, sharpening = .22f)
        val preview = pipeline.enhance(source, p)
        val exportOwned = RasterImage(source.width, source.height, source.argb.copyOf())
        val exported = pipeline.enhance(exportOwned, p, reuseSourcePixels = true)
        assertArrayEquals(preview.argb, exported.argb)
    }

    private fun gradient(width: Int, height: Int): RasterImage = RasterImage(width, height, IntArray(width * height) { index ->
        val x = index % width; val y = index / width
        val r = 25 + x * 210 / (width - 1).coerceAtLeast(1)
        val g = 20 + y * 200 / (height - 1).coerceAtLeast(1)
        val b = 35 + (x + y) * 170 / (width + height - 2).coerceAtLeast(1)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    })
}
