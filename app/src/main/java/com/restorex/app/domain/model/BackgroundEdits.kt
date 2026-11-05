package com.restorex.app.domain.model

enum class BackgroundBrushMode { REMOVE, RESTORE }
enum class BackgroundFillMode { TRANSPARENT, SOLID, GRADIENT, IMAGE }

data class BackgroundMaskStroke(
    override val id: Long,
    val mode: BackgroundBrushMode,
    val points: List<NormalizedPoint>,
    val radius: Float = .06f,
    val hardness: Float = .55f,
    val opacity: Float = 1f,
) : LocalEditOperation {
    init {
        require(points.isNotEmpty() && points.size <= BrushStroke.MAX_POINTS_PER_STROKE)
        require(radius in .002f..0.5f && hardness in 0f..1f && opacity in 0f..1f)
    }
}

/** The most recent fill operation is composited after all mask strokes. */
data class BackgroundFill(
    override val id: Long,
    val mode: BackgroundFillMode = BackgroundFillMode.TRANSPARENT,
    val startColor: Int = 0xFFFFFFFF.toInt(),
    val endColor: Int = 0xFFB0B0B0.toInt(),
    val opacity: Float = 1f,
    val imageUri: String? = null,
    val imageScale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val blur: Float = 0f,
) : LocalEditOperation {
    init {
        require(opacity in 0f..1f && imageScale in .25f..4f && offsetX in -1f..1f && offsetY in -1f..1f && blur in 0f..1f)
        require(mode != BackgroundFillMode.IMAGE || !imageUri.isNullOrBlank())
    }
}
