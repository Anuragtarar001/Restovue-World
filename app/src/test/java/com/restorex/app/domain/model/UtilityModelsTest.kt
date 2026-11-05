package com.restorex.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UtilityModelsTest {
    @Test
    fun batchTargetSizeIsStoredForLossyFormats() {
        val options = BatchOptions(
            format = BatchFormat.JPEG,
            quality = 92,
            maxWidth = 2048,
            targetKilobytes = 450,
        )

        assertEquals(450, options.targetKilobytes)
        assertEquals(2048, options.maxWidth)
    }

    @Test
    fun pngRejectsLossyTargetSize() {
        assertThrows(IllegalArgumentException::class.java) {
            BatchOptions(format = BatchFormat.PNG, targetKilobytes = 200)
        }
    }

    @Test
    fun targetSizeOutsideSupportedBoundsIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BatchOptions(format = BatchFormat.WEBP, targetKilobytes = 9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BatchOptions(format = BatchFormat.JPEG, targetKilobytes = 50_001)
        }
    }
}
