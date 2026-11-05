package com.restorex.app.domain.model

enum class LiquifyType { PUSH, PINCH, EXPAND, SHRINK, TWIRL_CLOCKWISE, TWIRL_COUNTERCLOCKWISE, RESTORE }

data class LiquifyStroke(
    override val id: Long,
    val type: LiquifyType,
    val points: List<NormalizedPoint>,
    val radius: Float = .08f,
    val strength: Float = .35f,
    val feather: Float = .65f,
) : LocalEditOperation {
    init {
        require(points.isNotEmpty() && points.size <= BrushStroke.MAX_POINTS_PER_STROKE)
        require(radius in .005f..0.5f && strength in 0f..1f && feather in 0f..1f)
    }
}
