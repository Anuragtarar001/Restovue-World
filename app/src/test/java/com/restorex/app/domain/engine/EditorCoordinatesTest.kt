package com.restorex.app.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import com.restorex.app.domain.engine.CanvasScaleMode

class EditorCoordinatesTest {
    @Test
    fun landscapeImageFitsWithoutCroppingAndRoundTripsCoordinates() {
        val coordinates = EditorCoordinates(
            imageSize = FloatSize(4000f, 2000f),
            viewportSize = FloatSize(1000f, 800f),
        )

        assertEquals(1000f, coordinates.fittedSize.width, 0.001f)
        assertEquals(500f, coordinates.fittedSize.height, 0.001f)
        val source = FloatPoint(650f, 1200f)
        val roundTrip = coordinates.viewportToImage(coordinates.imageToViewport(source))
        assertEquals(source.x, roundTrip.x, 0.001f)
        assertEquals(source.y, roundTrip.y, 0.001f)
    }

    @Test
    fun portraitImageFitsAndPanIsClampedAtZoom() {
        val coordinates = EditorCoordinates(
            imageSize = FloatSize(1000f, 2000f),
            viewportSize = FloatSize(900f, 600f),
            zoom = 3f,
        )

        assertEquals(300f, coordinates.fittedSize.width, 0.001f)
        assertEquals(600f, coordinates.fittedSize.height, 0.001f)
        assertEquals(FloatPoint(0f, 600f), coordinates.clampedPan(FloatPoint(1000f, 1000f)))
    }

    @Test
    fun zoomAroundPointKeepsThatImagePointStationary() {
        val before = EditorCoordinates(
            imageSize = FloatSize(1600f, 900f),
            viewportSize = FloatSize(1000f, 700f),
        )
        val gesturePoint = FloatPoint(300f, 250f)
        val imagePoint = before.viewportToImage(gesturePoint)
        val pan = before.panForZoomAround(gesturePoint, 2f)
        val after = before.copy(zoom = 2f, pan = pan)

        val mapped = after.imageToViewport(imagePoint)
        assertEquals(gesturePoint.x, mapped.x, 0.001f)
        assertEquals(gesturePoint.y, mapped.y, 0.001f)
    }

    @Test
    fun fillUsesUniformMaximumScaleAndCropsOnlyTheViewport() {
        val coordinates = EditorCoordinates(
            imageSize = FloatSize(4000f, 2000f),
            viewportSize = FloatSize(900f, 900f),
            scaleMode = CanvasScaleMode.FILL,
        )

        assertEquals(0.45f, coordinates.fillScale, 0.0001f)
        assertEquals(1800f, coordinates.baseSize.width, 0.001f)
        assertEquals(900f, coordinates.baseSize.height, 0.001f)
        assertEquals(coordinates.displayScale, coordinates.displayScale, 0f)
        assertEquals(FloatPoint(450f, 0f), coordinates.clampedPan(FloatPoint(999f, 999f)))
    }

    @Test
    fun fitAndFillAlwaysUseOneScaleForBothAxes() {
        CanvasScaleMode.entries.forEach { mode ->
            val coordinates = EditorCoordinates(FloatSize(1200f, 3000f), FloatSize(1080f, 700f), scaleMode = mode)
            val widthScale = coordinates.baseSize.width / coordinates.imageSize.width
            val heightScale = coordinates.baseSize.height / coordinates.imageSize.height
            assertEquals(widthScale, heightScale, 0.0001f)
        }
    }
}
