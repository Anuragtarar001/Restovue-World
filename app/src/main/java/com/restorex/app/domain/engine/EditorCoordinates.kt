package com.restorex.app.domain.engine

import kotlin.math.max
import kotlin.math.min

data class FloatPoint(val x: Float, val y: Float)
data class FloatSize(val width: Float, val height: Float)
enum class CanvasScaleMode { FIT, FILL }

/**
 * Immutable mapping between source-image pixels and the editor viewport. The same mapping is used
 * for both comparison layers, and is the foundation for future masks and brush input.
 */
data class EditorCoordinates(
    val imageSize: FloatSize,
    val viewportSize: FloatSize,
    val zoom: Float = 1f,
    val pan: FloatPoint = FloatPoint(0f, 0f),
    val scaleMode: CanvasScaleMode = CanvasScaleMode.FIT,
) {
    init {
        require(imageSize.width > 0f && imageSize.height > 0f)
        require(viewportSize.width > 0f && viewportSize.height > 0f)
        require(zoom >= 1f)
    }

    val fitScale: Float = min(viewportSize.width / imageSize.width, viewportSize.height / imageSize.height)
    val fillScale: Float = max(viewportSize.width / imageSize.width, viewportSize.height / imageSize.height)
    val baseScale: Float = if (scaleMode == CanvasScaleMode.FIT) fitScale else fillScale
    val displayScale: Float = baseScale * zoom
    val fittedSize: FloatSize = FloatSize(imageSize.width * fitScale, imageSize.height * fitScale)
    val baseSize: FloatSize = FloatSize(imageSize.width * baseScale, imageSize.height * baseScale)
    val viewportCenter: FloatPoint = FloatPoint(viewportSize.width / 2f, viewportSize.height / 2f)

    fun imageToViewport(point: FloatPoint): FloatPoint = FloatPoint(
        viewportCenter.x + (point.x - imageSize.width / 2f) * displayScale + pan.x,
        viewportCenter.y + (point.y - imageSize.height / 2f) * displayScale + pan.y,
    )

    fun viewportToImage(point: FloatPoint): FloatPoint = FloatPoint(
        imageSize.width / 2f + (point.x - viewportCenter.x - pan.x) / displayScale,
        imageSize.height / 2f + (point.y - viewportCenter.y - pan.y) / displayScale,
    )

    fun clampedPan(candidate: FloatPoint): FloatPoint {
        val maxX = max(0f, (baseSize.width * zoom - viewportSize.width) / 2f)
        val maxY = max(0f, (baseSize.height * zoom - viewportSize.height) / 2f)
        return FloatPoint(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun panForZoomAround(viewportPoint: FloatPoint, targetZoom: Float): FloatPoint {
        require(targetZoom >= 1f)
        val imagePoint = viewportToImage(viewportPoint)
        val target = copy(zoom = targetZoom, pan = FloatPoint(0f, 0f))
        val withoutPan = target.imageToViewport(imagePoint)
        return target.clampedPan(FloatPoint(viewportPoint.x - withoutPan.x, viewportPoint.y - withoutPan.y))
    }
}
