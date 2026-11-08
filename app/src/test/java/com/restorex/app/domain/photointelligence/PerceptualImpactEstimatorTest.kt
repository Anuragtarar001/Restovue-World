package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualImpactEstimatorTest {
    private val estimator = PerceptualImpactEstimator()

    @Test fun `identical image is perceptually negligible`() {
        val source = RasterImage(16, 16, IntArray(256) { 0xff606060.toInt() })
        val impact = estimator.estimate(source, source)
        assertTrue(impact.perceptuallyNegligible)
        assertTrue(impact.changedPixels == 0)
    }

    @Test fun `bounded visible exposure is not negligible`() {
        val source = RasterImage(16, 16, IntArray(256) { 0xff303030.toInt() })
        val output = RasterImage(16, 16, IntArray(256) { 0xff383838.toInt() })
        val impact = estimator.estimate(source, output)
        assertFalse(impact.perceptuallyNegligible)
        assertTrue(impact.p95OklabDelta > .01f)
        assertTrue(impact.meanLinearLuminanceDelta > 0f)
    }
}
