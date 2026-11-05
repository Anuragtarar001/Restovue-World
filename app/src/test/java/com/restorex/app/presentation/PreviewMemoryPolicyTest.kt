package com.restorex.app.presentation

import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewMemoryPolicyTest {
    @Test
    fun previewSizingUsesViewportButNeverExceedsPixelBudgetSquare() {
        val dimension = PreviewSizing.maxDimensionForViewport(1440, 3120, 1_500_000)

        assertTrue(dimension <= 1224)
        assertTrue(dimension >= PreviewSizing.MIN_PREVIEW_DIMENSION)
    }

    @Test
    fun rasterCacheEvictsByBytesInsteadOfOnlyEntryCount() {
        val cache = RasterMemoryCache<String>(maxBytes = 800, maxEntries = 10)
        cache.put("one", raster(10, 10))
        cache.put("two", raster(10, 10))
        cache.put("three", raster(10, 10))

        assertEquals(2, cache.count())
        assertTrue(cache.sizeBytes <= 800)
        assertEquals(null, cache["one"])
    }

    @Test
    fun repeatedPreviewInsertionsRemainBounded() {
        val cache = RasterMemoryCache<Int>(maxBytes = 14L * 1024 * 1024, maxEntries = 3)
        repeat(100) { key -> cache.put(key, raster(512, 512)) }

        assertTrue(cache.count() <= 3)
        assertTrue(cache.sizeBytes <= 14L * 1024 * 1024)
    }

    private fun raster(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height))
}
