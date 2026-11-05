package com.restorex.app.domain.engine

import com.restorex.app.domain.model.BrushSettings
import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.NormalizedPoint
import kotlin.math.min

/** Maps the visible, post-crop/rotation canvas back to immutable source-image coordinates. */
class SourceCoordinateMapper(
    private val sourceWidth: Int,
    private val sourceHeight: Int,
    private val quarterTurnsClockwise: Int,
    cropAspect: CropAspect,
) {
    private val rotatedWidth = if (quarterTurnsClockwise % 2 == 0) sourceWidth else sourceHeight
    private val rotatedHeight = if (quarterTurnsClockwise % 2 == 0) sourceHeight else sourceWidth
    val visibleBounds = CropCalculator.centered(rotatedWidth, rotatedHeight, cropAspect)

    init {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(quarterTurnsClockwise in 0..3)
    }

    fun visibleToSource(point: NormalizedPoint): NormalizedPoint {
        val rotatedX = visibleBounds.left + point.x * (visibleBounds.width - 1).coerceAtLeast(1)
        val rotatedY = visibleBounds.top + point.y * (visibleBounds.height - 1).coerceAtLeast(1)
        val source = when (quarterTurnsClockwise) {
            0 -> FloatPoint(rotatedX, rotatedY)
            1 -> FloatPoint(rotatedY, sourceHeight - 1f - rotatedX)
            2 -> FloatPoint(sourceWidth - 1f - rotatedX, sourceHeight - 1f - rotatedY)
            else -> FloatPoint(sourceWidth - 1f - rotatedY, rotatedX)
        }
        return NormalizedPoint(
            (source.x / (sourceWidth - 1).coerceAtLeast(1)).coerceIn(0f, 1f),
            (source.y / (sourceHeight - 1).coerceAtLeast(1)).coerceIn(0f, 1f),
            point.pressure,
        )
    }

    fun sourceToVisible(point: NormalizedPoint): NormalizedPoint {
        val sourceX = point.x * (sourceWidth - 1).coerceAtLeast(1)
        val sourceY = point.y * (sourceHeight - 1).coerceAtLeast(1)
        val rotated = when (quarterTurnsClockwise) {
            0 -> FloatPoint(sourceX, sourceY)
            1 -> FloatPoint(sourceHeight - 1f - sourceY, sourceX)
            2 -> FloatPoint(sourceWidth - 1f - sourceX, sourceHeight - 1f - sourceY)
            else -> FloatPoint(sourceY, sourceWidth - 1f - sourceX)
        }
        return NormalizedPoint(
            ((rotated.x - visibleBounds.left) / (visibleBounds.width - 1).coerceAtLeast(1)).coerceIn(0f, 1f),
            ((rotated.y - visibleBounds.top) / (visibleBounds.height - 1).coerceAtLeast(1)).coerceIn(0f, 1f),
            point.pressure,
        )
    }

    fun visibleBrushToSource(settings: BrushSettings): BrushSettings {
        val visibleRadiusPixels = settings.radius * min(visibleBounds.width, visibleBounds.height)
        return settings.copy(radius = (visibleRadiusPixels / min(sourceWidth, sourceHeight)).coerceIn(.002f, .5f))
    }

    fun sourceBrushToVisible(settings: BrushSettings): BrushSettings {
        val sourceRadiusPixels = settings.radius * min(sourceWidth, sourceHeight)
        return settings.copy(radius = (sourceRadiusPixels / min(visibleBounds.width, visibleBounds.height)).coerceIn(.002f, .5f))
    }

    fun visibleWidthToSource(value: Float): Float =
        (value * visibleBounds.width / sourceWidth).coerceIn(.01f, 1f)

    fun visibleHeightToSource(value: Float): Float =
        (value * visibleBounds.height / sourceHeight).coerceIn(.01f, 1f)

    fun sourceWidthToVisible(value: Float): Float =
        (value * sourceWidth / visibleBounds.width).coerceIn(.01f, 1f)

    fun sourceHeightToVisible(value: Float): Float =
        (value * sourceHeight / visibleBounds.height).coerceIn(.01f, 1f)

    fun visibleRotationToSource(degrees: Float): Float {
        var result = degrees - quarterTurnsClockwise * 90f
        while (result > 180f) result -= 360f
        while (result < -180f) result += 360f
        return result
    }

    fun sourceRotationToVisible(degrees: Float): Float {
        var result = degrees + quarterTurnsClockwise * 90f
        while (result > 180f) result -= 360f
        while (result < -180f) result += 360f
        return result
    }
}
