package com.restorex.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchExecutionTest {
    @Test
    fun reportsEveryItemAndContinuesAfterIndividualFailure() = runBlocking {
        val attempted = mutableListOf<Int>()
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = runSequentialBatch(listOf(10, 20, 30), { done, total -> progress += done to total }) { _, item ->
            attempted += item
            if (item == 20) error("synthetic failure")
        }

        assertEquals(listOf(10, 20, 30), attempted)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(2, result.completed)
        assertEquals(1, result.failures.size)
        assertTrue(result.failures.single().contains("Image 2"))
    }
}
