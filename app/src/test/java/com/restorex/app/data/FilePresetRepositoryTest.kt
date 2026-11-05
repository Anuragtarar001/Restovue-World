package com.restorex.app.data

import com.restorex.app.domain.model.Adjustments
import com.restorex.app.domain.model.SavedPreset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePresetRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun presetsRoundTripAllAdjustmentData() {
        val repository = FilePresetRepository(temporary.newFolder())
        val preset = SavedPreset(7, "Warm detail", Adjustments(exposure = .7f, temperature = .3f, sharpening = 1.2f, dehaze = .4f), "warm", .75f)
        repository.save(listOf(preset))
        assertEquals(listOf(preset), repository.load())
    }
}
