package com.restorex.app.domain.engine

import com.restorex.app.domain.model.CropAspect
import org.junit.Assert.assertEquals
import org.junit.Test

class CropCalculatorTest {
    @Test
    fun squareCropCentersOnLandscapeImage() {
        assertEquals(PixelRect(500, 0, 1000, 1000), CropCalculator.centered(2000, 1000, CropAspect.SQUARE))
    }

    @Test
    fun sixteenNineCropCentersOnPortraitImage() {
        assertEquals(PixelRect(0, 718, 1080, 608), CropCalculator.centered(1080, 2044, CropAspect.SIXTEEN_NINE))
    }

    @Test
    fun originalKeepsAllPixels() {
        assertEquals(PixelRect(0, 0, 4032, 3024), CropCalculator.centered(4032, 3024, CropAspect.ORIGINAL))
    }

    @Test
    fun thumbnailCropPreservesPortraitAndLandscapeAspect() {
        val portrait = CropCalculator.centeredToAspect(1200, 2000, 5, 6)
        val landscape = CropCalculator.centeredToAspect(2400, 1200, 5, 6)

        assertEquals(PixelRect(0, 280, 1200, 1440), portrait)
        assertEquals(PixelRect(700, 0, 1000, 1200), landscape)
    }
}
