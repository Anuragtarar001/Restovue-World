package com.restorex.app.domain.engine

import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.NormalizedPoint
import com.restorex.app.domain.model.NormalizedRect
import com.restorex.app.domain.model.PerspectiveQuad
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceCoordinateMapperTest {
    @Test
    fun visibleSourceRoundTripSurvivesEveryRotation() {
        for (turns in 0..3) {
            val mapper = SourceCoordinateMapper(400, 300, turns, CropAspect.ORIGINAL)
            val visible = NormalizedPoint(.27f, .68f)
            val roundTrip = mapper.sourceToVisible(mapper.visibleToSource(visible))

            assertEquals(visible.x, roundTrip.x, .005f)
            assertEquals(visible.y, roundTrip.y, .005f)
        }
    }

    @Test
    fun cropCoordinatesMapIntoTheCenteredSourceRegion() {
        val mapper = SourceCoordinateMapper(400, 200, 0, CropAspect.SQUARE)

        val left = mapper.visibleToSource(NormalizedPoint(0f, .5f))
        val right = mapper.visibleToSource(NormalizedPoint(1f, .5f))

        assertEquals(.25f, left.x, .01f)
        assertEquals(.75f, right.x, .01f)
    }

    @Test
    fun freeCropAndFlipsRemainRoundTripStable() {
        val mapper = SourceCoordinateMapper(400, 300, 1, CropAspect.FREE, NormalizedRect(.1f, .2f, .85f, .9f), true, true)
        val visible = NormalizedPoint(.2f, .7f)
        val roundTrip = mapper.sourceToVisible(mapper.visibleToSource(visible))

        assertEquals(visible.x, roundTrip.x, .01f)
        assertEquals(visible.y, roundTrip.y, .01f)
    }

    @Test
    fun fineRotationAndPerspectiveRemainRoundTripStable() {
        val quad = PerspectiveQuad(
            topLeft = NormalizedPoint(.08f, .06f), topRight = NormalizedPoint(.95f, .02f),
            bottomRight = NormalizedPoint(.9f, .96f), bottomLeft = NormalizedPoint(.04f, .9f),
        )
        val mapper = SourceCoordinateMapper(800, 600, 0, CropAspect.CUSTOM, customCropRatio = 4f / 3f, fineRotationDegrees = 11f, perspective = quad)
        val visible = NormalizedPoint(.35f, .62f)
        val roundTrip = mapper.sourceToVisible(mapper.visibleToSource(visible))

        assertEquals(visible.x, roundTrip.x, .015f)
        assertEquals(visible.y, roundTrip.y, .015f)
    }
}
