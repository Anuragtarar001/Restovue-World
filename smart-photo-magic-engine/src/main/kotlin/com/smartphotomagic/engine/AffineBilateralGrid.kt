package com.smartphotomagic.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Low-resolution bilateral grid of 3x4 affine colour transforms.
 *
 * The fitter follows the sufficient-statistics formulation from Bilateral Guided Upsampling:
 * ten values for the symmetric 4x4 Gram matrix and twelve values for RGB * [r g b 1].
 * Statistics are blurred before each cell is solved, and a coarse whole-image affine fit is used
 * as the ridge prior. Empty and weakly populated cells therefore approach a useful global
 * correction rather than black.
 */
class AffineBilateralGrid private constructor(private val coefficients: BilateralGrid) {
    fun apply(u: Float, v: Float, r: Float, g: Float, b: Float, scratch: FloatArray) {
        require(scratch.size >= COEFFICIENTS)
        val guide = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
        coefficients.slice(u, v, guide, scratch)
        val ro = scratch[0] * r + scratch[1] * g + scratch[2] * b + scratch[3]
        val go = scratch[4] * r + scratch[5] * g + scratch[6] * b + scratch[7]
        val bo = scratch[8] * r + scratch[9] * g + scratch[10] * b + scratch[11]
        scratch[0] = ro.coerceIn(0f, 1f)
        scratch[1] = go.coerceIn(0f, 1f)
        scratch[2] = bo.coerceIn(0f, 1f)
    }

    companion object {
        private const val STATS = 22
        const val COEFFICIENTS = 12
        private val IDENTITY = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
        )

        fun fit(
            source: PixelSource,
            target: PixelSource,
            cellsLong: Int = 16,
            rangeBins: Int = 8,
            cancel: CancelToken = CancelToken(),
        ): AffineBilateralGrid {
            require(source.width == target.width && source.height == target.height)
            require(source.width > 1 && source.height > 1 && cellsLong >= 4 && rangeBins >= 4)
            val w = source.width
            val h = source.height
            val aspect = w.toFloat() / h
            val nx = if (aspect >= 1f) cellsLong else max(4, (cellsLong * aspect).toInt())
            val ny = if (aspect >= 1f) max(4, (cellsLong / aspect).toInt()) else cellsLong
            val acc = BilateralGrid(nx, ny, rangeBins, 0f, 1f, STATS)
            val global = FloatArray(STATS)
            val srcRow = IntArray(w)
            val dstRow = IntArray(w)
            val x = FloatArray(4)
            val yv = FloatArray(3)
            val zScale = rangeBins - 1f

            for (py in 0 until h) {
                if ((py and 31) == 0) cancel.check()
                source.read(0, py, w, 1, srcRow)
                target.read(0, py, w, 1, dstRow)
                val gy = py.toFloat() / (h - 1) * (ny - 1)
                val j0 = min(gy.toInt(), ny - 2)
                val fy = gy - j0
                for (px in 0 until w) {
                    unpack(srcRow[px], x, yv, source = true)
                    unpack(dstRow[px], x, yv, source = false)
                    accumulate(global, 0, x, yv, 1f)
                    val guide = (0.2126f * x[0] + 0.7152f * x[1] + 0.0722f * x[2]).coerceIn(0f, 1f)
                    val gx = px.toFloat() / (w - 1) * (nx - 1)
                    val gz = guide * zScale
                    val i0 = min(gx.toInt(), nx - 2)
                    val k0 = min(gz.toInt(), rangeBins - 2)
                    val fx = gx - i0
                    val fz = gz - k0
                    for (dk in 0..1) for (dj in 0..1) for (di in 0..1) {
                        val weight = (if (di == 0) 1f - fx else fx) *
                            (if (dj == 0) 1f - fy else fy) *
                            (if (dk == 0) 1f - fz else fz)
                        accumulate(acc.data, acc.index(i0 + di, j0 + dj, k0 + dk), x, yv, weight)
                    }
                }
            }

            // Smooth the normal-equation statistics, not the solved coefficients. This avoids
            // seams while preserving the edge-aware range dimension.
            acc.blur(1, spatial = true, range = true)
            val globalPrior = solve(global, 0, IDENTITY, max(1f, w.toFloat() * h * 0.00002f))
            val result = BilateralGrid(nx, ny, rangeBins, 0f, 1f, COEFFICIENTS)
            val pixelsPerCell = w.toFloat() * h / (nx * ny * rangeBins)
            val localPriorWeight = max(0.08f, pixelsPerCell * 0.015f)
            for (k in 0 until rangeBins) for (j in 0 until ny) for (i in 0 until nx) {
                val solved = solve(acc.data, acc.index(i, j, k), globalPrior, localPriorWeight)
                System.arraycopy(solved, 0, result.data, result.index(i, j, k), COEFFICIENTS)
            }
            return AffineBilateralGrid(result)
        }

        private fun unpack(color: Int, x: FloatArray, y: FloatArray, source: Boolean) {
            val r = ((color ushr 16) and 255) / 255f
            val g = ((color ushr 8) and 255) / 255f
            val b = (color and 255) / 255f
            if (source) {
                x[0] = r; x[1] = g; x[2] = b; x[3] = 1f
            } else {
                y[0] = r; y[1] = g; y[2] = b
            }
        }

        private fun accumulate(out: FloatArray, offset: Int, x: FloatArray, y: FloatArray, weight: Float) {
            var p = offset
            for (row in 0..3) for (col in row..3) out[p++] += weight * x[row] * x[col]
            for (row in 0..2) for (col in 0..3) out[p++] += weight * y[row] * x[col]
        }

        private fun solve(stats: FloatArray, offset: Int, prior: FloatArray, lambda: Float): FloatArray {
            val a = Array(4) { FloatArray(4) }
            var p = offset
            for (row in 0..3) for (col in row..3) {
                val value = stats[p++]
                a[row][col] = value
                a[col][row] = value
            }
            for (d in 0..3) a[d][d] += lambda
            val result = FloatArray(COEFFICIENTS)
            for (channel in 0..2) {
                val rhs = FloatArray(4) { col -> stats[offset + 10 + channel * 4 + col] + lambda * prior[channel * 4 + col] }
                val rowResult = solve4(a, rhs) ?: FloatArray(4) { prior[channel * 4 + it] }
                System.arraycopy(rowResult, 0, result, channel * 4, 4)
            }
            return result
        }

        private fun solve4(matrix: Array<FloatArray>, vector: FloatArray): FloatArray? {
            val m = Array(4) { row -> FloatArray(5) { col -> if (col < 4) matrix[row][col] else vector[row] } }
            for (pivot in 0..3) {
                var best = pivot
                for (row in pivot + 1..3) if (kotlin.math.abs(m[row][pivot]) > kotlin.math.abs(m[best][pivot])) best = row
                if (kotlin.math.abs(m[best][pivot]) < 1e-8f) return null
                if (best != pivot) { val tmp = m[pivot]; m[pivot] = m[best]; m[best] = tmp }
                val divisor = m[pivot][pivot]
                for (col in pivot..4) m[pivot][col] /= divisor
                for (row in 0..3) if (row != pivot) {
                    val factor = m[row][pivot]
                    for (col in pivot..4) m[row][col] -= factor * m[pivot][col]
                }
            }
            return FloatArray(4) { m[it][4] }
        }
    }
}
