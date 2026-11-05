package com.restorex.app.data

/** Conservative preflight that turns an allocation crash into an actionable export error. */
object ExportMemoryPolicy {
    fun safePixelCount(maxHeapBytes: Long, spatiallyExpensive: Boolean): Long {
        require(maxHeapBytes > 0)
        val estimatedPeakBytesPerPixel = if (spatiallyExpensive) 20L else 12L
        return (maxHeapBytes * SAFE_HEAP_FRACTION / estimatedPeakBytesPerPixel).toLong().coerceAtLeast(MIN_PIXELS)
    }

    fun requireSafe(width: Int, height: Int, safePixels: Long) {
        require(width > 0 && height > 0 && safePixels > 0)
        require(width.toLong() * height <= safePixels) {
            "This image exceeds the safe full-resolution memory budget. Choose smaller export dimensions."
        }
    }

    private const val SAFE_HEAP_FRACTION = .65
    private const val MIN_PIXELS = 2_000_000L
}
