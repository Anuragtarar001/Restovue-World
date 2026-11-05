package com.restorex.app.data

import android.graphics.Bitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Bounded target-size encoder shared by single-photo and batch export. */
internal object AndroidTargetSizeEncoder {
    data class Result(val bytes: ByteArray, val width: Int, val height: Int)

    fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, preferredQuality: Int, targetKb: Int): Result {
        require(preferredQuality in 1..100 && targetKb > 0)
        val targetBytes = targetKb * 1024L
        var working = bitmap
        var ownsWorking = false
        try {
            repeat(12) {
                var low = 1
                var high = preferredQuality
                var best: ByteArray? = null
                repeat(7) {
                    if (low <= high) {
                        val quality = (low + high) / 2
                        val bytes = ByteArrayOutputStream((targetBytes.coerceAtMost(4L * 1024 * 1024)).toInt()).use { output ->
                            check(working.compress(format, quality, output)) { "Image encoding failed" }
                            output.toByteArray()
                        }
                        if (bytes.size <= targetBytes) {
                            best = bytes
                            low = quality + 1
                        } else high = quality - 1
                    }
                }
                if (best != null) return Result(requireNotNull(best), working.width, working.height)
                if (minOf(working.width, working.height) <= 320) return@repeat
                val next = working.scale(
                    (working.width * .85f).roundToInt().coerceAtLeast(1),
                    (working.height * .85f).roundToInt().coerceAtLeast(1),
                )
                if (ownsWorking) working.recycle()
                working = next
                ownsWorking = true
            }
            throw IllegalArgumentException("Target size cannot be reached within the minimum output dimensions")
        } finally {
            if (ownsWorking) working.recycle()
        }
    }
}
