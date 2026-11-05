package com.restorex.app.data

import com.restorex.app.domain.model.FilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.restorex.app.domain.model.FilterCategory

class DefaultFilterCatalogTest {
    @Test
    fun catalogHasStableUniqueIdsAndCapacityForExpansion() {
        val presets = DefaultFilterCatalog().presets
        assertEquals(presets.size, presets.map { it.id }.distinct().size)
        assertTrue(presets.size <= DefaultFilterCatalog.MAX_PRESETS)
        assertEquals(FilterPreset.ORIGINAL_ID, presets.first().id)
        assertEquals(DefaultFilterCatalog.MAX_PRESETS, presets.size)
        assertEquals(FilterCategory.entries.toSet(), presets.map { it.category }.toSet())
    }
}
