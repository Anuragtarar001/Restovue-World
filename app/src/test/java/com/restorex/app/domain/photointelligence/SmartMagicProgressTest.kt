package com.restorex.app.domain.photointelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartMagicProgressTest {
    @Test fun `determinate fraction is based on actual work units`() {
        val update = SmartMagicWorkUpdate(
            SmartMagicWorkStage.OPTIMIZING_TONAL_BALANCE,
            SmartMagicWorkState.ACTIVE,
            completedWorkUnits = 3,
            totalWorkUnits = 8,
        )
        assertEquals(.375f, update.fraction!!, 0f)
    }

    @Test fun `unmeasurable work remains indeterminate`() {
        val update = SmartMagicWorkUpdate(
            SmartMagicWorkStage.DETECTING_SUBJECTS,
            SmartMagicWorkState.STARTED,
        )
        assertNull(update.fraction)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `progress cannot exceed its real work total`() {
        SmartMagicWorkUpdate(
            SmartMagicWorkStage.OPTIMIZING_TONAL_BALANCE,
            SmartMagicWorkState.ACTIVE,
            completedWorkUnits = 9,
            totalWorkUnits = 8,
        )
    }
}
