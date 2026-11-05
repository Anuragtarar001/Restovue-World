package com.restorex.app.presentation

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Bounded preview policy. Dimensions are screen pixels, never density-independent units. */
object PreviewSizing {
    const val DEFAULT_PIXEL_BUDGET = 1_500_000
    const val INTERACTIVE_MAX_DIMENSION = 720
    const val MIN_PREVIEW_DIMENSION = 640

    fun maxDimensionForViewport(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        pixelBudget: Int = DEFAULT_PIXEL_BUDGET,
    ): Int {
        require(viewportWidthPx > 0 && viewportHeightPx > 0 && pixelBudget > 0)
        val viewportLargest = max(viewportWidthPx, viewportHeightPx)
        val budgetSafeSquare = floor(sqrt(pixelBudget.toDouble())).toInt()
        return min(viewportLargest, budgetSafeSquare).coerceAtLeast(MIN_PREVIEW_DIMENSION)
    }
}
