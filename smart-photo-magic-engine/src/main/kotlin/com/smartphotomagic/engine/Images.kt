package com.smartphotomagic.engine

import java.util.concurrent.CancellationException

/** Read access to an upright, sRGB-encoded 8-bit image (ARGB_8888 ints, row-major). */
interface PixelSource {
    val width: Int
    val height: Int
    /** Fill [out] (stride = w) with the w*h region starting at (x, y). Must be thread-safe. */
    fun read(x: Int, y: Int, w: Int, h: Int, out: IntArray)
}

/** Write access for rendered output. Regions written concurrently never overlap. */
interface PixelSink {
    fun write(x: Int, y: Int, w: Int, h: Int, data: IntArray)
}

class IntArrayImage(override val width: Int, override val height: Int, val pixels: IntArray = IntArray(width * height)) :
    PixelSource, PixelSink {
    override fun read(x: Int, y: Int, w: Int, h: Int, out: IntArray) {
        for (j in 0 until h) System.arraycopy(pixels, (y + j) * width + x, out, j * w, w)
    }
    override fun write(x: Int, y: Int, w: Int, h: Int, data: IntArray) {
        for (j in 0 until h) System.arraycopy(data, j * w, pixels, (y + j) * width + x, w)
    }
}

/** Planar linear-light float RGB image. */
class LinearImage(val width: Int, val height: Int) {
    val r = FloatArray(width * height)
    val g = FloatArray(width * height)
    val b = FloatArray(width * height)
    val size get() = width * height
}

/** Face rectangle in normalised [0,1] image coordinates (upright image). */
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float = 1f) {
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    val w get() = right - left
    val h get() = bottom - top
}

enum class Mode { NATURAL, ENHANCED, EXTREME }

class CancelToken {
    @Volatile var cancelled = false
    fun cancel() { cancelled = true }
    fun check() { if (cancelled) throw CancellationException("cancelled") }
}

fun interface ProgressListener { fun onProgress(fraction: Float) }
