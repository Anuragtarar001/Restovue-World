package com.restorex.app.domain.engine

import com.restorex.app.domain.model.BrushSettings
import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.NormalizedPoint
import com.restorex.app.domain.model.NormalizedRect
import com.restorex.app.domain.model.PerspectiveQuad
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.roundToInt

/** Maps the visible, post-crop/rotation canvas back to immutable source-image coordinates. */
class SourceCoordinateMapper(
    private val sourceWidth: Int,
    private val sourceHeight: Int,
    private val quarterTurnsClockwise: Int,
    cropAspect: CropAspect,
    cropRect: NormalizedRect = NormalizedRect(),
    private val flipHorizontal: Boolean = false,
    private val flipVertical: Boolean = false,
    customCropRatio: Float? = null,
    private val fineRotationDegrees: Float = 0f,
    private val perspective: PerspectiveQuad = PerspectiveQuad(),
) {
    private val rotatedWidth = if (quarterTurnsClockwise % 2 == 0) sourceWidth else sourceHeight
    private val rotatedHeight = if (quarterTurnsClockwise % 2 == 0) sourceHeight else sourceWidth
    private val aspectBounds = when {
        cropAspect == CropAspect.ORIGINAL || cropAspect == CropAspect.FREE -> PixelRect(0, 0, rotatedWidth, rotatedHeight)
        cropAspect == CropAspect.CUSTOM && customCropRatio != null -> centeredRatio(rotatedWidth, rotatedHeight, customCropRatio)
        cropAspect == CropAspect.CUSTOM -> PixelRect(0, 0, rotatedWidth, rotatedHeight)
        else -> CropCalculator.centered(rotatedWidth, rotatedHeight, cropAspect)
    }
    private val cropLeft = aspectBounds.left + (cropRect.left * aspectBounds.width).roundToInt()
    private val cropTop = aspectBounds.top + (cropRect.top * aspectBounds.height).roundToInt()
    val visibleBounds = PixelRect(
        cropLeft,
        cropTop,
        ((cropRect.right - cropRect.left) * aspectBounds.width).roundToInt().coerceAtLeast(1),
        ((cropRect.bottom - cropRect.top) * aspectBounds.height).roundToInt().coerceAtLeast(1),
    )

    init {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(quarterTurnsClockwise in 0..3)
    }

    fun visibleToSource(point: NormalizedPoint): NormalizedPoint {
        val projected = applyHomography(point, perspectiveToRect = false)
        val unrotated = rotateNormalized(projected, -fineRotationDegrees)
        val visibleX = if (flipHorizontal) 1f - unrotated.x else unrotated.x
        val visibleY = if (flipVertical) 1f - unrotated.y else unrotated.y
        val rotatedX = visibleBounds.left + visibleX * (visibleBounds.width - 1).coerceAtLeast(1)
        val rotatedY = visibleBounds.top + visibleY * (visibleBounds.height - 1).coerceAtLeast(1)
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
        var visibleX = ((rotated.x - visibleBounds.left) / (visibleBounds.width - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        var visibleY = ((rotated.y - visibleBounds.top) / (visibleBounds.height - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        if (flipHorizontal) visibleX = 1f - visibleX
        if (flipVertical) visibleY = 1f - visibleY
        val rotatedVisible = rotateNormalized(NormalizedPoint(visibleX, visibleY, point.pressure), fineRotationDegrees)
        return applyHomography(rotatedVisible, perspectiveToRect = true)
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

    private fun rotateNormalized(point: NormalizedPoint, degrees: Float): NormalizedPoint {
        if (degrees == 0f) return point
        val radians = Math.toRadians(degrees.toDouble())
        val c = cos(radians).toFloat(); val s = sin(radians).toFloat()
        val x = point.x * (visibleBounds.width - 1).coerceAtLeast(1)
        val y = point.y * (visibleBounds.height - 1).coerceAtLeast(1)
        val cx = (visibleBounds.width - 1) / 2f; val cy = (visibleBounds.height - 1) / 2f
        val dx = x - cx; val dy = y - cy
        return NormalizedPoint(
            ((cx + c * dx - s * dy) / (visibleBounds.width - 1).coerceAtLeast(1)).coerceIn(0f, 1f),
            ((cy + s * dx + c * dy) / (visibleBounds.height - 1).coerceAtLeast(1)).coerceIn(0f, 1f),
            point.pressure,
        )
    }

    private fun applyHomography(point: NormalizedPoint, perspectiveToRect: Boolean): NormalizedPoint {
        if (perspective.isIdentity) return point
        val rect = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val quad = doubleArrayOf(
            perspective.topLeft.x.toDouble(), perspective.topLeft.y.toDouble(),
            perspective.topRight.x.toDouble(), perspective.topRight.y.toDouble(),
            perspective.bottomRight.x.toDouble(), perspective.bottomRight.y.toDouble(),
            perspective.bottomLeft.x.toDouble(), perspective.bottomLeft.y.toDouble(),
        )
        return runCatching {
            val h = homography(if (perspectiveToRect) quad else rect, if (perspectiveToRect) rect else quad)
            val denominator = h[6] * point.x + h[7] * point.y + 1.0
            NormalizedPoint(
                ((h[0] * point.x + h[1] * point.y + h[2]) / denominator).toFloat().coerceIn(0f, 1f),
                ((h[3] * point.x + h[4] * point.y + h[5]) / denominator).toFloat().coerceIn(0f, 1f),
                point.pressure,
            )
        }.getOrDefault(point)
    }

    private fun homography(from: DoubleArray, to: DoubleArray): DoubleArray {
        val matrix = Array(8) { DoubleArray(9) }
        for (i in 0..3) {
            val x = from[i * 2]; val y = from[i * 2 + 1]; val u = to[i * 2]; val v = to[i * 2 + 1]
            matrix[i * 2] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            matrix[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        for (column in 0 until 8) {
            val pivot = (column until 8).maxBy { kotlin.math.abs(matrix[it][column]) }
            require(kotlin.math.abs(matrix[pivot][column]) > 1e-10)
            val swap = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = swap
            val divisor = matrix[column][column]
            for (j in column..8) matrix[column][j] /= divisor
            for (row in 0 until 8) if (row != column) {
                val factor = matrix[row][column]
                for (j in column..8) matrix[row][j] -= factor * matrix[column][j]
            }
        }
        return DoubleArray(8) { matrix[it][8] }
    }

    private fun centeredRatio(width: Int, height: Int, ratio: Float): PixelRect {
        val current = width.toFloat() / height
        return if (current > ratio) {
            val cropWidth = (height * ratio).roundToInt().coerceAtLeast(1)
            PixelRect((width - cropWidth) / 2, 0, cropWidth, height)
        } else {
            val cropHeight = (width / ratio).roundToInt().coerceAtLeast(1)
            PixelRect(0, (height - cropHeight) / 2, width, cropHeight)
        }
    }
}
