package com.restorex.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidImageProcessorTest {
    @Test
    fun rotateAndCropProducesExpectedDimensions() {
        val source = RasterImage(4, 2, IntArray(8) { 0xFF336699.toInt() })
        val result = AndroidImageProcessor().render(
            source,
            EditState(quarterTurnsClockwise = 1, cropAspect = CropAspect.SQUARE),
        )

        assertEquals(2, result.width)
        assertEquals(2, result.height)
    }
}
