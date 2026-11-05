package com.restorex.app.domain.model

import kotlin.math.PI

data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
) {
    init { require(x in 0f..1f && y in 0f..1f && pressure in 0f..1f) }
}

data class BrushSettings(
    /** Radius relative to the shortest source-image edge. */
    val radius: Float = .04f,
    val hardness: Float = .55f,
    val opacity: Float = 1f,
    val strength: Float = .35f,
) {
    init {
        require(radius in .002f..0.5f)
        require(hardness in 0f..1f && opacity in 0f..1f && strength in -1f..1f)
    }
}

data class LocalLighting(
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
) {
    init {
        require(exposure in -2f..2f)
        require(highlights in -1f..1f && shadows in -1f..1f && temperature in -1f..1f && tint in -1f..1f)
    }
}

enum class BrushEditType {
    HEAL, CLONE, SMOOTH, DODGE, BURN, BRUSH_LIGHT, ERASER,
}

sealed interface LocalEditOperation { val id: Long }

data class BrushStroke(
    override val id: Long,
    val type: BrushEditType,
    val points: List<NormalizedPoint>,
    val settings: BrushSettings = BrushSettings(),
    val sourceAnchor: NormalizedPoint? = null,
    val alignedSampling: Boolean = true,
    val lighting: LocalLighting = LocalLighting(),
) : LocalEditOperation {
    init {
        require(points.isNotEmpty() && points.size <= MAX_POINTS_PER_STROKE)
        if (type == BrushEditType.CLONE) requireNotNull(sourceAnchor)
    }

    companion object { const val MAX_POINTS_PER_STROKE = 2048 }
}

data class RadialLight(
    override val id: Long,
    val center: NormalizedPoint = NormalizedPoint(.5f, .5f),
    val width: Float = .35f,
    val height: Float = .25f,
    val rotationDegrees: Float = 0f,
    val feather: Float = .65f,
    val inverted: Boolean = false,
    val opacity: Float = 1f,
    val exposure: Float = .5f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
) : LocalEditOperation {
    init {
        require(width in .01f..1f && height in .01f..1f)
        require(rotationDegrees in -180f..180f && feather in 0f..1f && opacity in 0f..1f)
        require(exposure in -2f..2f && highlights in -1f..1f && shadows in -1f..1f)
    }
}

data class LinearLight(
    override val id: Long,
    val center: NormalizedPoint = NormalizedPoint(.5f, .5f),
    val rotationDegrees: Float = 0f,
    val feather: Float = .25f,
    val inverted: Boolean = false,
    val opacity: Float = 1f,
    val exposure: Float = .5f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
) : LocalEditOperation {
    init {
        require(rotationDegrees in -180f..180f && feather in .01f..1f && opacity in 0f..1f)
        require(exposure in -2f..2f && temperature in -1f..1f && tint in -1f..1f)
    }
}

internal fun Float.degreesToRadians(): Float = this * PI.toFloat() / 180f
