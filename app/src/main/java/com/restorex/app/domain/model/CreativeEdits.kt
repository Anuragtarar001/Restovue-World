package com.restorex.app.domain.model

enum class TextFont { SANS, SERIF, MONOSPACE }
enum class TextAlignment { LEFT, CENTER, RIGHT }
enum class ShapeType { RECTANGLE, CIRCLE, LINE, ARROW, SPEECH_BUBBLE }
enum class FrameStyle { NONE, SIMPLE, ROUNDED, FILM, POLAROID }

sealed interface CreativeLayer {
    val id: Long
    val center: NormalizedPoint
    val rotationDegrees: Float
    val opacity: Float
    val zIndex: Int
}

data class TextLayer(
    override val id: Long,
    val text: String,
    override val center: NormalizedPoint = NormalizedPoint(.5f, .5f),
    val size: Float = .08f,
    val color: Int = 0xFFFFFFFF.toInt(),
    override val opacity: Float = 1f,
    override val rotationDegrees: Float = 0f,
    val font: TextFont = TextFont.SANS,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val shadow: Float = 0f,
    val outline: Float = 0f,
    val backgroundColor: Int? = null,
    override val zIndex: Int = 0,
) : CreativeLayer {
    init {
        require(text.isNotBlank() && text.length <= 500)
        require(size in .01f..1f && opacity in 0f..1f && rotationDegrees in -180f..180f)
        require(shadow in 0f..1f && outline in 0f..1f)
    }
}

data class ShapeLayer(
    override val id: Long,
    val type: ShapeType,
    override val center: NormalizedPoint = NormalizedPoint(.5f, .5f),
    val width: Float = .3f,
    val height: Float = .2f,
    val color: Int = 0xFFFFB323.toInt(),
    val filled: Boolean = false,
    val strokeWidth: Float = .015f,
    override val opacity: Float = 1f,
    override val rotationDegrees: Float = 0f,
    override val zIndex: Int = 0,
) : CreativeLayer {
    init {
        require(width in .01f..2f && height in .01f..2f && strokeWidth in .001f..0.2f)
        require(opacity in 0f..1f && rotationDegrees in -180f..180f)
    }
}

data class StickerLayer(
    override val id: Long,
    val uri: String,
    override val center: NormalizedPoint = NormalizedPoint(.5f, .5f),
    val width: Float = .3f,
    val height: Float = .3f,
    override val opacity: Float = 1f,
    override val rotationDegrees: Float = 0f,
    override val zIndex: Int = 0,
) : CreativeLayer {
    init { require(uri.isNotBlank() && width in .01f..2f && height in .01f..2f && opacity in 0f..1f && rotationDegrees in -180f..180f) }
}

data class FrameSettings(
    val style: FrameStyle = FrameStyle.NONE,
    val thickness: Float = .03f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val cornerRadius: Float = .04f,
    val shadow: Float = 0f,
) {
    init { require(thickness in 0f..0.3f && cornerRadius in 0f..0.5f && shadow in 0f..1f) }
    val isNeutral get() = style == FrameStyle.NONE || thickness == 0f
}
