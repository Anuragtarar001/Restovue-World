package com.smartphotomagic.engine

import kotlin.math.max
import kotlin.math.min

/**
 * A 3-D bilateral grid over (u, v, z) with u,v in [0,1] (normalised image position) and
 * z = log2 luminance. Used to compute an edge-aware "base" (illumination) layer and to store
 * smooth, edge-aware local exposure adjustments. Because slicing uses each full-resolution
 * pixel's own luminance as the third coordinate, the result is an edge-aware upsampling:
 * adjustments do not bleed across strong luminance edges, which is what prevents halos.
 * (Chen, Paris & Durand, "Real-time edge-aware image processing with the bilateral grid", 2007.)
 */
class BilateralGrid(val nx: Int, val ny: Int, val nz: Int, val zMin: Float, val zMax: Float, val channels: Int) {
    val data = FloatArray(nx * ny * nz * channels)
    private val zScale = (nz - 1) / (zMax - zMin)

    fun index(i: Int, j: Int, k: Int) = ((k * ny + j) * nx + i) * channels

    fun zOf(k: Int): Float = zMin + k / zScale

    fun copy(): BilateralGrid = BilateralGrid(nx, ny, nz, zMin, zMax, channels).also { System.arraycopy(data, 0, it.data, 0, data.size) }

    /** Trilinear slice of all channels at (u, v, z) into out[0 until channels]. */
    fun slice(u: Float, v: Float, z: Float, out: FloatArray) {
        val gx = (u * (nx - 1)).coerceIn(0f, (nx - 1).toFloat())
        val gy = (v * (ny - 1)).coerceIn(0f, (ny - 1).toFloat())
        val gz = ((z - zMin) * zScale).coerceIn(0f, (nz - 1).toFloat())
        val i0 = min(gx.toInt(), nx - 2); val j0 = min(gy.toInt(), ny - 2); val k0 = min(gz.toInt(), nz - 2)
        val fx = gx - i0; val fy = gy - j0; val fz = gz - k0
        for (c in 0 until channels) out[c] = 0f
        for (dk in 0..1) {
            val wz = if (dk == 0) 1f - fz else fz
            for (dj in 0..1) {
                val wy = if (dj == 0) 1f - fy else fy
                val wyz = wy * wz
                val base0 = index(i0, j0 + dj, k0 + dk)
                val w0 = (1f - fx) * wyz; val w1 = fx * wyz
                for (c in 0 until channels) out[c] += data[base0 + c] * w0 + data[base0 + channels + c] * w1
            }
        }
    }

    /** Separable [1 4 6 4 1]/16 blur along the given axes, [passes] times. */
    fun blur(passes: Int, spatial: Boolean = true, range: Boolean = true) {
        val tmp = FloatArray(data.size)
        repeat(passes) {
            if (spatial) { blurAxis(0, tmp); blurAxis(1, tmp) }
            if (range) blurAxis(2, tmp)
        }
    }

    private fun blurAxis(axis: Int, tmp: FloatArray) {
        val k = floatArrayOf(1f / 16, 4f / 16, 6f / 16, 4f / 16, 1f / 16)
        val n = when (axis) { 0 -> nx; 1 -> ny; else -> nz }
        for (kk in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) {
            val p = when (axis) { 0 -> i; 1 -> j; else -> kk }
            val dst = index(i, j, kk)
            for (c in 0 until channels) tmp[dst + c] = 0f
            for (t in -2..2) {
                // clamp-to-edge boundary
                val q = (p + t).coerceIn(0, n - 1)
                val src = when (axis) { 0 -> index(q, j, kk); 1 -> index(i, q, kk); else -> index(i, j, q) }
                val w = k[t + 2]
                for (c in 0 until channels) tmp[dst + c] += data[src + c] * w
            }
        }
        System.arraycopy(tmp, 0, data, 0, data.size)
    }

    companion object {
        /**
         * Build an edge-aware base layer grid (1 channel: smoothed log2 luminance) from [logLum]
         * sampled on a pw x ph proxy. Spatial cell size ~1/[cellsLong] of the long side,
         * range bin [binStops].
         */
        fun buildBase(logLum: FloatArray, pw: Int, ph: Int, cellsLong: Int = 24, binStops: Float = 0.6f,
                      zMin: Float = -14f, zMax: Float = 1.5f): BilateralGrid {
            val aspect = pw.toFloat() / ph
            val nx = if (aspect >= 1f) cellsLong else max(6, (cellsLong * aspect).toInt())
            val ny = if (aspect >= 1f) max(6, (cellsLong / aspect).toInt()) else cellsLong
            val nz = ((zMax - zMin) / binStops).toInt() + 1
            // homogeneous accumulation: channel 0 = sum(z), channel 1 = weight
            val acc = BilateralGrid(nx, ny, nz, zMin, zMax, 2)
            val zScale = (nz - 1) / (zMax - zMin)
            for (y in 0 until ph) {
                val gy = y.toFloat() / max(1, ph - 1) * (ny - 1)
                val j0 = min(gy.toInt(), ny - 2); val fy = gy - j0
                for (x in 0 until pw) {
                    val z = logLum[y * pw + x].coerceIn(zMin, zMax)
                    val gx = x.toFloat() / max(1, pw - 1) * (nx - 1)
                    val gz = (z - zMin) * zScale
                    val i0 = min(gx.toInt(), nx - 2); val k0 = min(gz.toInt(), nz - 2)
                    val fx = gx - i0; val fz = gz - k0
                    for (dk in 0..1) for (dj in 0..1) for (di in 0..1) {
                        val w = (if (di == 0) 1 - fx else fx) * (if (dj == 0) 1 - fy else fy) * (if (dk == 0) 1 - fz else fz)
                        val idx = acc.index(i0 + di, j0 + dj, k0 + dk)
                        acc.data[idx] += z * w
                        acc.data[idx + 1] += w
                    }
                }
            }
            acc.blur(2, spatial = true, range = false)
            acc.blur(1, spatial = false, range = true)
            val base = BilateralGrid(nx, ny, nz, zMin, zMax, 2) // ch0 = base, ch1 = local exposure gain (filled later)
            // Prior: an empty cell behaves like identity (base == z) so isolated tones are unaffected.
            val pixelsPerCell = pw.toFloat() * ph / (nx * ny)
            val eps = 0.002f * pixelsPerCell
            for (k in 0 until nz) {
                val zc = acc.zOf(k)
                for (j in 0 until ny) for (i in 0 until nx) {
                    val a = acc.index(i, j, k)
                    base.data[base.index(i, j, k)] = (acc.data[a] + eps * zc) / (acc.data[a + 1] + eps)
                }
            }
            return base
        }
    }
}
