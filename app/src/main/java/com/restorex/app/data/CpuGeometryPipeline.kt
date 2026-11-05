package com.restorex.app.data

import com.restorex.app.domain.engine.CropCalculator
import com.restorex.app.domain.engine.PixelRect
import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.RasterImage
import kotlinx.coroutines.CancellationException
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object CpuGeometryPipeline {
    fun apply(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean = { false }): RasterImage {
        val hasGeometry = edits.quarterTurnsClockwise != 0 || edits.cropAspect != CropAspect.ORIGINAL ||
            !edits.cropRect.isFull || edits.flipHorizontal || edits.flipVertical ||
            edits.fineRotationDegrees != 0f || !edits.perspective.isIdentity
        if (!hasGeometry) return source

        val turns = edits.quarterTurnsClockwise
        val rotatedWidth = if (turns % 2 == 0) source.width else source.height
        val rotatedHeight = if (turns % 2 == 0) source.height else source.width
        val aspectBounds = when {
            edits.cropAspect == CropAspect.FREE || edits.cropAspect == CropAspect.ORIGINAL ->
                PixelRect(0, 0, rotatedWidth, rotatedHeight)
            edits.cropAspect == CropAspect.CUSTOM && edits.customCropRatio != null ->
                centeredRatio(rotatedWidth, rotatedHeight, edits.customCropRatio)
            else -> CropCalculator.centered(rotatedWidth, rotatedHeight, edits.cropAspect)
        }
        val cropLeft = (aspectBounds.left + edits.cropRect.left * aspectBounds.width).roundToInt().coerceIn(0, rotatedWidth - 1)
        val cropTop = (aspectBounds.top + edits.cropRect.top * aspectBounds.height).roundToInt().coerceIn(0, rotatedHeight - 1)
        val cropRight = (aspectBounds.left + edits.cropRect.right * aspectBounds.width).roundToInt().coerceIn(cropLeft + 1, rotatedWidth)
        val cropBottom = (aspectBounds.top + edits.cropRect.bottom * aspectBounds.height).roundToInt().coerceIn(cropTop + 1, rotatedHeight)
        val width = cropRight - cropLeft
        val height = cropBottom - cropTop
        val base = IntArray(width * height)
        for (y in 0 until height) {
            if (y and 31 == 0 && shouldCancel()) throw CancellationException("Geometry render superseded")
            for (x in 0 until width) {
                val displayX = if (edits.flipHorizontal) width - 1 - x else x
                val displayY = if (edits.flipVertical) height - 1 - y else y
                val rx = cropLeft + displayX
                val ry = cropTop + displayY
                val (sx, sy) = when (turns) {
                    0 -> rx to ry
                    1 -> ry to (source.height - 1 - rx)
                    2 -> (source.width - 1 - rx) to (source.height - 1 - ry)
                    else -> (source.width - 1 - ry) to rx
                }
                base[y * width + x] = source.argb[sy * source.width + sx]
            }
        }
        var current = RasterImage(width, height, base, source.scaleFromOriginal)
        if (edits.fineRotationDegrees != 0f) current = rotateFine(current, edits.fineRotationDegrees, shouldCancel)
        if (!edits.perspective.isIdentity) current = perspective(current, edits, shouldCancel)
        return current
    }

    private fun rotateFine(source: RasterImage, degrees: Float, shouldCancel: () -> Boolean): RasterImage {
        val radians = Math.toRadians((-degrees).toDouble())
        val c = cos(radians).toFloat(); val s = sin(radians).toFloat()
        val cx = (source.width - 1) / 2f; val cy = (source.height - 1) / 2f
        val output = IntArray(source.argb.size)
        for (y in 0 until source.height) {
            if (y and 31 == 0 && shouldCancel()) throw CancellationException("Geometry render superseded")
            for (x in 0 until source.width) {
                val dx = x - cx; val dy = y - cy
                output[y * source.width + x] = sampleBilinear(source, cx + c * dx - s * dy, cy + s * dx + c * dy)
            }
        }
        return source.copy(argb = output)
    }

    private fun perspective(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean): RasterImage {
        val q = edits.perspective
        val h = homography(
            doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0),
            doubleArrayOf(
                q.topLeft.x.toDouble(), q.topLeft.y.toDouble(), q.topRight.x.toDouble(), q.topRight.y.toDouble(),
                q.bottomRight.x.toDouble(), q.bottomRight.y.toDouble(), q.bottomLeft.x.toDouble(), q.bottomLeft.y.toDouble(),
            ),
        )
        val output = IntArray(source.argb.size)
        for (y in 0 until source.height) {
            if (y and 31 == 0 && shouldCancel()) throw CancellationException("Perspective render superseded")
            val v = y.toDouble() / (source.height - 1).coerceAtLeast(1)
            for (x in 0 until source.width) {
                val u = x.toDouble() / (source.width - 1).coerceAtLeast(1)
                val denominator = h[6] * u + h[7] * v + 1.0
                val sx = ((h[0] * u + h[1] * v + h[2]) / denominator * (source.width - 1)).toFloat()
                val sy = ((h[3] * u + h[4] * v + h[5]) / denominator * (source.height - 1)).toFloat()
                output[y * source.width + x] = sampleBilinear(source, sx, sy)
            }
        }
        return source.copy(argb = output)
    }

    internal fun homography(from: DoubleArray, to: DoubleArray): DoubleArray {
        require(from.size == 8 && to.size == 8)
        val matrix = Array(8) { DoubleArray(9) }
        for (i in 0..3) {
            val x = from[i * 2]; val y = from[i * 2 + 1]
            val u = to[i * 2]; val v = to[i * 2 + 1]
            matrix[i * 2] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            matrix[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        for (column in 0 until 8) {
            val pivot = (column until 8).maxBy { kotlin.math.abs(matrix[it][column]) }
            require(kotlin.math.abs(matrix[pivot][column]) > 1e-10) { "Perspective points are degenerate" }
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

    private fun sampleBilinear(source: RasterImage, x: Float, y: Float): Int {
        if (x < 0f || y < 0f || x > source.width - 1f || y > source.height - 1f) return 0
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val x1 = min(x0 + 1, source.width - 1); val y1 = min(y0 + 1, source.height - 1)
        val fx = x - x0; val fy = y - y0
        fun channel(shift: Int): Int {
            val c00 = source.argb[y0 * source.width + x0] ushr shift and 0xFF
            val c10 = source.argb[y0 * source.width + x1] ushr shift and 0xFF
            val c01 = source.argb[y1 * source.width + x0] ushr shift and 0xFF
            val c11 = source.argb[y1 * source.width + x1] ushr shift and 0xFF
            return ((c00 + (c10 - c00) * fx) * (1f - fy) + (c01 + (c11 - c01) * fx) * fy).roundToInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
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
