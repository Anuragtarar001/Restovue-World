package com.restorex.app.data

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportMemoryPolicyTest {
    @Test
    fun spatialPipelineGetsAConservativeBudgetOn256MiBHeap() {
        val simple = ExportMemoryPolicy.safePixelCount(256L * 1024 * 1024, spatiallyExpensive = false)
        val spatial = ExportMemoryPolicy.safePixelCount(256L * 1024 * 1024, spatiallyExpensive = true)

        assertTrue(spatial < simple)
        assertTrue(spatial >= 8_000_000)
    }

    @Test
    fun oversized50MegapixelExportIsRejectedBeforeAllocation() {
        val safe = ExportMemoryPolicy.safePixelCount(256L * 1024 * 1024, spatiallyExpensive = false)

        assertThrows(IllegalArgumentException::class.java) {
            ExportMemoryPolicy.requireSafe(8192, 6144, safe)
        }
    }

    @Test
    fun custom12MegapixelExportFitsA512MiBHeapBudget() {
        val safe = ExportMemoryPolicy.safePixelCount(512L * 1024 * 1024, spatiallyExpensive = true)

        ExportMemoryPolicy.requireSafe(4000, 3000, safe)
    }
}
