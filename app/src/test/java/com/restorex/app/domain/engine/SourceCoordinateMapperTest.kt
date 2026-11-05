package com.restorex.app.domain.engine

import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.NormalizedPoint
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
}
