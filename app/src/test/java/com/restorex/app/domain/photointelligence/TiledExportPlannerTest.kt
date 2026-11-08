package com.restorex.app.domain.photointelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiledExportPlannerTest {
    @Test fun `48 megapixel geometry is fully covered with bounded overlap`() {
        val width = 8192; val height = 6144
        val tiles = TiledExportPlanner().plan(width, height, coreSize = 1024, overlap = 48)
        assertEquals(width.toLong() * height, tiles.sumOf { it.core.width.toLong() * it.core.height })
        assertTrue(tiles.all { it.decode.left <= it.core.left && it.decode.top <= it.core.top })
        assertTrue(tiles.all { it.decode.right >= it.core.right && it.decode.bottom >= it.core.bottom })
        assertTrue(tiles.maxOf { it.decode.width } <= 1120)
        assertTrue(tiles.maxOf { it.decode.height } <= 1120)
    }
}
