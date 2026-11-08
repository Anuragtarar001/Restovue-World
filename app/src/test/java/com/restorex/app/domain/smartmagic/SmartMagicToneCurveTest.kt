package com.restorex.app.domain.smartmagic

import com.restorex.app.data.CpuImagePipeline
import com.restorex.app.data.SmartMagicPipeline
import com.restorex.app.domain.model.Adjustments
import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartMagicToneCurveTest {
    @Test fun `midtone gamma curve is monotonic and preserves endpoints`() {
        val pipeline = SmartMagicPipeline()
        val parameters = SmartMagicParameters(midtoneGamma = 1.38f)
        var previous = -1f
        for (index in 0..1024) {
            val input = index / 1024f
            val output = pipeline.toneMapLuminance(input, parameters)
            assertTrue("curve inverted at $index", output + 1e-6f >= previous)
            previous = output
        }
        assertEquals(0f, pipeline.toneMapLuminance(0f, parameters), 1e-6f)
        assertEquals(1f, pipeline.toneMapLuminance(1f, parameters), 1e-6f)
    }

    @Test fun `manual gamma plus 38 means gamma 1 point 38 and lifts midtones`() {
        val source = RasterImage(1, 1, intArrayOf(0xff606060.toInt()))
        val output = CpuImagePipeline.apply(source, Adjustments(gamma = 1.38f))
        val before = source.argb[0] ushr 16 and 255
        val after = output.argb[0] ushr 16 and 255
        assertTrue(after > before)
        val expected = ((before / 255f).powForTest(1f / 1.38f) * 255f + .5f).toInt()
        assertEquals(expected, after)
    }

    private fun Float.powForTest(power: Float): Float =
        Math.pow(toDouble(), power.toDouble()).toFloat()
}
