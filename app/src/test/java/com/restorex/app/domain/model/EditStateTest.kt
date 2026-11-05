package com.restorex.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditStateTest {
    @Test
    fun filterIntensityBlendsWithoutReplacingManualAdjustments() {
        val state = EditState(
            adjustments = Adjustments(brightness = 0.1f, saturation = 0.8f),
            filter = FilterSelection(
                presetId = "vivid",
                intensity = 0.5f,
                adjustments = Adjustments(contrast = 1.2f, saturation = 1.4f),
            ),
        )

        assertEquals(0.1f, state.effectiveAdjustments.brightness, 0.0001f)
        assertEquals(1.1f, state.effectiveAdjustments.contrast, 0.0001f)
        assertEquals(0.96f, state.effectiveAdjustments.saturation, 0.0001f)
    }

    @Test
    fun geometryOnlyPreservesCropAndRotation() {
        val state = EditState(
            adjustments = Adjustments(exposure = 1f),
            quarterTurnsClockwise = 3,
            cropAspect = CropAspect.FOUR_THREE,
            filter = FilterSelection("mono", 1f, Adjustments(saturation = 0f)),
        ).geometryOnly()

        assertTrue(state.adjustments.isNeutral)
        assertTrue(state.filter.isOriginal)
        assertEquals(3, state.quarterTurnsClockwise)
        assertEquals(CropAspect.FOUR_THREE, state.cropAspect)
    }

    @Test
    fun filterParameterInterpolationSupportsZeroHalfAndFullIntensity() {
        val base = Adjustments(temperature = .1f)
        val filter = Adjustments(contrast = 1.4f, saturation = .5f, temperature = .4f, film = FilmEffects(grain = .6f))
        val zero = base.blendedWith(filter, 0f)
        val half = base.blendedWith(filter, .5f)
        val full = base.blendedWith(filter, 1f)

        assertEquals(1f, zero.contrast, .0001f)
        assertEquals(1.2f, half.contrast, .0001f)
        assertEquals(1.4f, full.contrast, .0001f)
        assertEquals(.75f, half.saturation, .0001f)
        assertEquals(.3f, half.film.grain, .0001f)
        assertEquals(.1f, zero.temperature, .0001f)
    }
}
