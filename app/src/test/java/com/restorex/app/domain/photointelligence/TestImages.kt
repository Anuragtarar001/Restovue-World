package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import kotlin.math.roundToInt
import kotlin.random.Random

internal object TestImages {
    fun solid(width: Int = 64, height: Int = 64, value: Int, alpha: Int = 255): RasterImage =
        RasterImage(width, height, IntArray(width * height) { (alpha shl 24) or (value shl 16) or (value shl 8) or value })

    fun gradient(width: Int = 128, height: Int = 96, low: Int = 16, high: Int = 240): RasterImage =
        RasterImage(width, height, IntArray(width * height) { index ->
            val x = index % width; val v = (low + (high - low) * x / (width - 1f)).roundToInt().coerceIn(0, 255)
            -0x1000000 or (v shl 16) or (v shl 8) or v
        })

    fun centerVsBorder(center: Int, border: Int, width: Int = 96, height: Int = 96): RasterImage =
        RasterImage(width, height, IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            val v = if (x in width / 4 until width * 3 / 4 && y in height / 4 until height * 3 / 4) center else border
            -0x1000000 or (v shl 16) or (v shl 8) or v
        })

    fun noisy(base: Int = 35, amplitude: Int = 28, seed: Int = 7): RasterImage {
        val random = Random(seed); val width = 128; val height = 96
        return RasterImage(width, height, IntArray(width * height) {
            val v = (base + random.nextInt(-amplitude, amplitude + 1)).coerceIn(0, 255)
            -0x1000000 or (v shl 16) or (v shl 8) or v
        })
    }

    fun pointSource(broad: Boolean = false): RasterImage {
        val width = 128; val height = 96
        return RasterImage(width, height, IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            val bright = if (broad) x < width * 3 / 4 else (x - 64) * (x - 64) + (y - 48) * (y - 48) < 16
            val v = if (bright) 255 else 14
            -0x1000000 or (v shl 16) or (v shl 8) or v
        })
    }

    fun saturated(): RasterImage = RasterImage(64, 64, IntArray(64 * 64) { i ->
        when (i % 3) { 0 -> 0xffff0000.toInt(); 1 -> 0xff00ff00.toInt(); else -> 0xff0000ff.toInt() }
    })
}
