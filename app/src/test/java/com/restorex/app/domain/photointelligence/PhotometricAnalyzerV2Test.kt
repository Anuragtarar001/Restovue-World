package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import org.junit.Assert.*
import org.junit.Test

class PhotometricAnalyzerV2Test {
    private val analyzer = PhotometricAnalyzerV2(maxSamples = 4096)

    @Test fun linearLightLuminanceMatchesKnownGrey() {
        val evidence = analyzer.analyze(TestImages.solid(value = 128))
        val expected = SmartMagicColorMath.srgbToLinear(128f / 255f)
        assertEquals(expected, evidence.luminance.mean, .002f)
    }

    @Test fun histogramPercentilesAreOrderedAndAccurate() {
        val evidence = analyzer.analyze(TestImages.gradient())
        val l = evidence.luminance
        assertTrue(l.p01 <= l.p05 && l.p05 <= l.p25 && l.p25 <= l.p50)
        assertTrue(l.p50 <= l.p75 && l.p75 <= l.p95 && l.p95 <= l.p99)
        assertTrue(l.robustRange > .5f)
    }

    @Test fun transparentPixelsAreExcludedAndPartialAlphaIsWeighted() {
        val pixels = intArrayOf(0x00ffffff, 0xff000000.toInt(), 0x80ffffff.toInt(), 0xff000000.toInt())
        val evidence = analyzer.analyze(RasterImage(2, 2, pixels))
        assertEquals(2.5019f, evidence.effectiveVisibleWeight, .01f)
        assertTrue(evidence.luminance.mean in .18f..22f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fullyTransparentImageIsRejected() {
        analyzer.analyze(TestImages.solid(8, 8, 255, 0))
    }

    @Test fun boundedSamplingAndRegionsAreDeterministic() {
        val image = TestImages.gradient(2048, 1536)
        val first = analyzer.analyze(image); val second = analyzer.analyze(image)
        assertTrue(first.sampledPixels <= 5000)
        assertEquals(80, first.regions.size)
        assertEquals(first.copy(analysisDurationNanos = 0), second.copy(analysisDurationNanos = 0))
        assertTrue(first.regions.all { it.bounds.left in 0f..1f && it.bounds.right in 0f..1f })
    }

    @Test fun tinyAndUnusualAspectImagesRemainStable() {
        listOf(TestImages.solid(1, 1, 127), TestImages.gradient(3, 97), TestImages.gradient(101, 2)).forEach {
            val result = analyzer.analyze(it)
            assertTrue(result.luminance.mean.isFinite())
            assertTrue(result.regions.all { r -> r.luminance.mean.isFinite() })
        }
    }

    @Test fun colorEvidenceDistinguishesSaturatedAndGrey() {
        val saturated = analyzer.analyze(TestImages.saturated())
        val grey = analyzer.analyze(TestImages.solid(value = 128))
        assertTrue(saturated.color.meanSaturation > grey.color.meanSaturation + .8f)
        assertTrue(grey.color.whiteBalanceConfidence > saturated.color.whiteBalanceConfidence)
    }
}
