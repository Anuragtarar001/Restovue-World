package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceMaskTest {
    @Test fun `mask composition remains soft and bounded`() {
        val a = ConfidenceMask.fromFloats("a", 2, 2, floatArrayOf(0f, .25f, .5f, 1f))
        val b = ConfidenceMask.fromFloats("b", 2, 2, floatArrayOf(1f, .5f, .25f, 0f))
        assertEquals(.5f, a.union(b).score(1, 0), .01f)
        assertEquals(.25f, a.intersect(b).score(1, 0), .01f)
        assertEquals(.125f, a.exclude(b).score(1, 0), .01f)
        assertEquals(1f, a.invert().score(0, 0), .01f)
    }

    @Test fun `invalid or empty masks are rejected`() {
        val empty = ConfidenceMask.fromFloats("empty", 8, 8, FloatArray(64))
        assertFalse(empty.quality().accepted)
        assertTrue(MaskRejectionReason.EMPTY in empty.quality().rejectionReasons)
    }

    @Test fun `bilinear sampling preserves geometry`() {
        val mask = ConfidenceMask.fromFloats("gradient", 2, 2, floatArrayOf(0f, 1f, 0f, 1f))
        assertEquals(.5f, mask.sample(.5f, .5f), .01f)
    }

    @Test fun `soft mask drives subject and background measurements`() {
        val image = RasterImage(2, 1, intArrayOf(0xff202020.toInt(), 0xfff0f0f0.toInt()))
        val mask = ConfidenceMask.fromFloats("person", 2, 1, floatArrayOf(1f, 0f))
        val result = SemanticRegionPhotometricAnalyzer().analyze(image, mask)
        assertTrue(result.person.meanLinearLuminance < result.background.meanLinearLuminance)
    }
}
