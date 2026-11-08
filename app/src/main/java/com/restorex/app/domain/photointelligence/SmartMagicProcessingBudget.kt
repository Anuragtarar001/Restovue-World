package com.restorex.app.domain.photointelligence

/** Workload budget; it never changes export dimensions or bypasses quality protections. */
data class SmartMagicProcessingBudget(
    val tier: String,
    val analysisMaxDimension: Int,
    val previewMaxDimension: Int,
    val optimizationMaxDimension: Int,
    val optimizerCandidateBudget: Int,
    val regionalCandidateBudget: Int,
    val prewarmModes: Boolean,
) {
    init {
        require(analysisMaxDimension in 640..1536 && previewMaxDimension in 480..1080)
        require(optimizationMaxDimension in 192..512)
        require(optimizerCandidateBudget in 2..8 && regionalCandidateBudget in 1..4)
    }
}

object SmartMagicProcessingBudgetPolicy {
    fun forRuntime(maxHeapBytes: Long, processors: Int): SmartMagicProcessingBudget {
        require(maxHeapBytes > 0 && processors > 0)
        val heapMb = maxHeapBytes / (1024 * 1024)
        return when {
            heapMb <= 192 || processors <= 4 -> SmartMagicProcessingBudget(
                "constrained", 896, 560, 256, 3, 2, false,
            )
            heapMb <= 320 || processors <= 6 -> SmartMagicProcessingBudget(
                "mainstream", 1080, 640, 288, 4, 3, false,
            )
            else -> SmartMagicProcessingBudget(
                "high-throughput", 1280, 720, 320, 5, 4, true,
            )
        }
    }
}
