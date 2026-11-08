package com.restorex.app.domain.photointelligence

/** EXIF transform from encoded normalized coordinates to the oriented-source canonical space. */
enum class ExifTransform {
    NORMAL, FLIP_HORIZONTAL, ROTATE_180, FLIP_VERTICAL, TRANSPOSE, ROTATE_90, TRANSVERSE, ROTATE_270;

    fun encodedToOriented(point: NormalizedPoint): NormalizedPoint = when (this) {
        NORMAL -> point
        FLIP_HORIZONTAL -> NormalizedPoint(1f - point.x, point.y)
        ROTATE_180 -> NormalizedPoint(1f - point.x, 1f - point.y)
        FLIP_VERTICAL -> NormalizedPoint(point.x, 1f - point.y)
        TRANSPOSE -> NormalizedPoint(point.y, point.x)
        ROTATE_90 -> NormalizedPoint(1f - point.y, point.x)
        TRANSVERSE -> NormalizedPoint(1f - point.y, 1f - point.x)
        ROTATE_270 -> NormalizedPoint(point.y, 1f - point.x)
    }

    fun orientedToEncoded(point: NormalizedPoint): NormalizedPoint = when (this) {
        NORMAL, FLIP_HORIZONTAL, ROTATE_180, FLIP_VERTICAL, TRANSPOSE, TRANSVERSE -> encodedToOriented(point)
        ROTATE_90 -> ROTATE_270.encodedToOriented(point)
        ROTATE_270 -> ROTATE_90.encodedToOriented(point)
    }
}

data class LetterboxTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val modelWidth: Int,
    val modelHeight: Int,
) {
    init { require(sourceWidth > 0 && sourceHeight > 0 && modelWidth > 0 && modelHeight > 0) }
    private val scale = minOf(modelWidth / sourceWidth.toFloat(), modelHeight / sourceHeight.toFloat())
    private val contentWidth = sourceWidth * scale
    private val contentHeight = sourceHeight * scale
    private val offsetX = (modelWidth - contentWidth) / 2f
    private val offsetY = (modelHeight - contentHeight) / 2f

    fun sourceToModel(point: NormalizedPoint) = NormalizedPoint(
        (offsetX + point.x * contentWidth) / modelWidth,
        (offsetY + point.y * contentHeight) / modelHeight,
    )

    fun modelToSource(point: NormalizedPoint): NormalizedPoint? {
        val x = (point.x * modelWidth - offsetX) / contentWidth
        val y = (point.y * modelHeight - offsetY) / contentHeight
        if (x !in 0f..1f || y !in 0f..1f) return null
        return NormalizedPoint(x, y)
    }
}
