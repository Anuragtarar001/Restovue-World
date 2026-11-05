package com.restorex.app.data

import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.NormalizedPoint
import com.restorex.app.domain.model.NormalizedRect
import com.restorex.app.domain.model.PerspectiveQuad
import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CpuGeometryPipelineTest {
    @Test fun neutralGeometryReturnsOriginalPixels() {
        val source = image(8, 6)
        assertArrayEquals(source.argb, CpuGeometryPipeline.apply(source, EditState()).argb)
    }

    @Test fun portraitAndLandscapeCropPreserveRequestedRatio() {
        val landscape = CpuGeometryPipeline.apply(image(400, 200), EditState(cropAspect = CropAspect.THREE_FOUR))
        val portrait = CpuGeometryPipeline.apply(image(200, 400), EditState(cropAspect = CropAspect.SIXTEEN_NINE))
        assertEquals(.75f, landscape.width.toFloat() / landscape.height, .01f)
        assertEquals(16f / 9f, portrait.width.toFloat() / portrait.height, .01f)
    }

    @Test fun freeCropUsesNormalizedBounds() {
        val output = CpuGeometryPipeline.apply(image(100, 80), EditState(cropAspect = CropAspect.FREE, cropRect = NormalizedRect(.1f, .2f, .9f, .8f)))
        assertEquals(80, output.width)
        assertEquals(48, output.height)
    }

    @Test fun flipsAndFineRotationChangePixelsWithoutChangingCanvas() {
        val source = image(20, 12)
        val output = CpuGeometryPipeline.apply(source, EditState(flipHorizontal = true, fineRotationDegrees = 8f))
        assertEquals(source.width, output.width)
        assertEquals(source.height, output.height)
        assertNotEquals(source.argb.toList(), output.argb.toList())
    }

    @Test fun identityPerspectiveIsStableAndCornerMoveWarpsImage() {
        val source = image(24, 18)
        val identity = CpuGeometryPipeline.apply(source, EditState(perspective = PerspectiveQuad()))
        val warped = CpuGeometryPipeline.apply(source, EditState(perspective = PerspectiveQuad(topLeft = NormalizedPoint(.15f, .1f))))
        assertArrayEquals(source.argb, identity.argb)
        assertNotEquals(source.argb.toList(), warped.argb.toList())
    }

    private fun image(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { i ->
        (0xFF shl 24) or ((i * 17 % 256) shl 16) or ((i * 9 % 256) shl 8) or (i * 3 % 256)
    })
}
