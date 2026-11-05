package com.restorex.app.data

import com.restorex.app.domain.model.Adjustments
import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeLutTest {
    @Test
    fun parsesCubeAndUsesTrilinearInterpolation() {
        val lut = CubeLutParser.parse(identityCube())
        val sample = lut.sample(.25f, .5f, .75f)

        assertEquals(.25f, sample[0], .0001f)
        assertEquals(.5f, sample[1], .0001f)
        assertEquals(.75f, sample[2], .0001f)
    }

    @Test
    fun malformedCubeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CubeLutParser.parse("LUT_3D_SIZE 4\n0 0 0\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CubeLutParser.parse("TITLE \"missing size\"\n0 0 0")
        }
    }

    @Test
    fun lutIntensityInterpolatesAtZeroHalfAndFull() {
        val lut = CubeLutParser.parse(invertedCube())
        val source = RasterImage(1, 1, intArrayOf(0xFF404040.toInt()))
        val zero = CpuImagePipeline.apply(source, Adjustments(), lut, 0f)
        val half = CpuImagePipeline.apply(source, Adjustments(), lut, .5f)
        val full = CpuImagePipeline.apply(source, Adjustments(), lut, 1f)

        assertEquals(64, red(zero.argb[0]))
        assertTrue(red(half.argb[0]) in 126..129)
        assertTrue(red(full.argb[0]) in 190..192)
    }

    private fun identityCube() = """
        TITLE "Identity"
        LUT_3D_SIZE 2
        DOMAIN_MIN 0 0 0
        DOMAIN_MAX 1 1 1
        0 0 0
        1 0 0
        0 1 0
        1 1 0
        0 0 1
        1 0 1
        0 1 1
        1 1 1
    """.trimIndent()

    private fun invertedCube() = """
        LUT_3D_SIZE 2
        1 1 1
        0 1 1
        1 0 1
        0 0 1
        1 1 0
        0 1 0
        1 0 0
        0 0 0
    """.trimIndent()

    private fun red(color: Int) = color ushr 16 and 0xFF
}
