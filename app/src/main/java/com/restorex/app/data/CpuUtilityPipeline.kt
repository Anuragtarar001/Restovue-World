package com.restorex.app.data

import com.restorex.app.domain.model.RasterImage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object CpuUtilityPipeline {
    fun passportSheet(source: RasterImage, photoWidthPx: Int, photoHeightPx: Int, sheetWidthPx: Int, sheetHeightPx: Int, marginPx: Int, spacingPx: Int, copies: Int): RasterImage {
        require(photoWidthPx > 0 && photoHeightPx > 0 && sheetWidthPx > 0 && sheetHeightPx > 0 && copies > 0)
        val output = IntArray(sheetWidthPx * sheetHeightPx) { 0xFFFFFFFF.toInt() }
        val across = ((sheetWidthPx - marginPx * 2 + spacingPx) / (photoWidthPx + spacingPx)).coerceAtLeast(1)
        val down = ((sheetHeightPx - marginPx * 2 + spacingPx) / (photoHeightPx + spacingPx)).coerceAtLeast(1)
        repeat(min(copies, across * down)) { index ->
            val x = marginPx + index % across * (photoWidthPx + spacingPx); val y = marginPx + index / across * (photoHeightPx + spacingPx)
            drawCenterCrop(source, output, sheetWidthPx, sheetHeightPx, x, y, photoWidthPx, photoHeightPx)
        }
        return RasterImage(sheetWidthPx, sheetHeightPx, output)
    }

    fun collage(images: List<RasterImage>, width: Int, height: Int, spacing: Int = 0, background: Int = 0xFFFFFFFF.toInt()): RasterImage {
        require(images.size in 2..9 && width > 0 && height > 0 && spacing >= 0)
        val columns = ceil(sqrt(images.size.toDouble())).toInt(); val rows = ceil(images.size.toDouble() / columns).toInt()
        val cellWidth = ((width - spacing * (columns + 1)) / columns).coerceAtLeast(1)
        val cellHeight = ((height - spacing * (rows + 1)) / rows).coerceAtLeast(1)
        val output = IntArray(width * height) { background }
        images.forEachIndexed { index, image ->
            val column = index % columns; val row = index / columns
            val left = spacing + column * (cellWidth + spacing); val top = spacing + row * (cellHeight + spacing)
            drawCenterCrop(image, output, width, height, left, top, cellWidth, cellHeight)
        }
        return RasterImage(width, height, output)
    }

    fun hdrMerge(images: List<RasterImage>, strength: Float = .65f): RasterImage {
        require(images.size >= 2 && strength in 0f..1f)
        val width = images.minOf { it.width }; val height = images.minOf { it.height }
        val reference = images[images.size / 2]
        val shifts = images.map { estimateTranslation(reference, it) }
        val output = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            var sumR = 0f; var sumG = 0f; var sumB = 0f; var sumW = 0f
            images.forEachIndexed { imageIndex, image ->
                val shift = shifts[imageIndex]
                val color = sampleNearest(image, (x + shift.first).toFloat() / width, (y + shift.second).toFloat() / height)
                val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
                val luma = (r * .2126f + g * .7152f + b * .0722f) / 255f
                val weight = (.05f + 1f - abs(luma - .5f) * 1.8f).coerceAtLeast(.05f)
                sumR += r * weight; sumG += g * weight; sumB += b * weight; sumW += weight
            }
            val mergedR = sumR / sumW; val mergedG = sumG / sumW; val mergedB = sumB / sumW
            val base = sampleNearest(images[images.size / 2], x.toFloat() / width, y.toFloat() / height)
            output[y * width + x] = blend(base, rgb(mergedR, mergedG, mergedB), strength)
        }
        return RasterImage(width, height, output)
    }

    private fun estimateTranslation(reference: RasterImage, candidate: RasterImage): Pair<Int, Int> {
        if (reference === candidate) return 0 to 0
        val width = min(reference.width, candidate.width); val height = min(reference.height, candidate.height)
        val maxShift = min(24, min(width, height) / 25).coerceAtLeast(1); var bestX = 0; var bestY = 0; var best = Float.MAX_VALUE
        val sampleX = max(1, width / 32); val sampleY = max(1, height / 32)
        for (dy in -maxShift..maxShift) for (dx in -maxShift..maxShift) {
            var error = 0f; var count = 0
            for (y in maxShift until height - maxShift step sampleY) for (x in maxShift until width - maxShift step sampleX) {
                error += abs(luma(reference.argb[y * reference.width + x]) - luma(candidate.argb[(y + dy) * candidate.width + x + dx])); count++
            }
            if (count > 0 && error / count < best) { best = error / count; bestX = dx; bestY = dy }
        }
        return bestX to bestY
    }

    /** Translation/overlap panorama suitable for ordered, horizontally overlapping captures. */
    fun panorama(images: List<RasterImage>): RasterImage {
        require(images.size >= 2)
        return images.drop(1).fold(images.first()) { left, right -> stitchPair(left, right) }
    }

    fun documentEnhance(source: RasterImage, threshold: Boolean = false): RasterImage {
        val gray = IntArray(source.argb.size) { index ->
            val c = source.argb[index]; val y = ((c ushr 16 and 255) * .2126f + (c ushr 8 and 255) * .7152f + (c and 255) * .0722f).roundToInt()
            rgb(y.toFloat(), y.toFloat(), y.toFloat())
        }
        if (!threshold) return RasterImage(source.width, source.height, gray, source.scaleFromOriginal)
        val integral = LongArray((source.width + 1) * (source.height + 1))
        for (y in 1..source.height) { var row = 0L; for (x in 1..source.width) { row += gray[(y - 1) * source.width + x - 1] and 255; integral[y * (source.width + 1) + x] = integral[(y - 1) * (source.width + 1) + x] + row } }
        val radius = max(4, min(source.width, source.height) / 40)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val x0 = max(0, x - radius); val x1 = min(source.width - 1, x + radius); val y0 = max(0, y - radius); val y1 = min(source.height - 1, y + radius)
            val stride = source.width + 1; val sum = integral[(y1 + 1) * stride + x1 + 1] - integral[y0 * stride + x1 + 1] - integral[(y1 + 1) * stride + x0] + integral[y0 * stride + x0]
            val mean = sum.toFloat() / ((x1 - x0 + 1) * (y1 - y0 + 1)); val value = gray[y * source.width + x] and 255
            gray[y * source.width + x] = if (value < mean * .9f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return RasterImage(source.width, source.height, gray, source.scaleFromOriginal)
    }

    fun signatureCleanup(source: RasterImage): RasterImage {
        val enhanced = documentEnhance(source, threshold = true)
        val cleanedPixels = enhanced.argb.copyOf()
        for (y in 1 until enhanced.height - 1) {
            var dark = 0; for (x in 0 until enhanced.width) if ((cleanedPixels[y * enhanced.width + x] and 255) < 128) dark++
            if (dark > enhanced.width * .75f) for (x in 0 until enhanced.width) {
                val index = y * enhanced.width + x
                val verticalStroke = (cleanedPixels[index - enhanced.width] and 255) < 128 && (cleanedPixels[index + enhanced.width] and 255) < 128
                if (!verticalStroke) cleanedPixels[index] = 0xFFFFFFFF.toInt()
            }
        }
        val cleaned = enhanced.copy(argb = cleanedPixels)
        var left = enhanced.width; var top = enhanced.height; var right = -1; var bottom = -1
        for (y in 0 until cleaned.height) for (x in 0 until cleaned.width) if ((cleaned.argb[y * cleaned.width + x] and 255) < 128) {
            left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y)
        }
        if (right < left) return enhanced
        val pad = max(2, min(enhanced.width, enhanced.height) / 100); left = max(0, left - pad); top = max(0, top - pad); right = min(enhanced.width - 1, right + pad); bottom = min(enhanced.height - 1, bottom + pad)
        val width = right - left + 1; val height = bottom - top + 1; val output = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val value = cleaned.argb[(top + y) * cleaned.width + left + x] and 255
            output[y * width + x] = if (value < 128) 0xFF000000.toInt() else 0x00FFFFFF
        }
        return RasterImage(width, height, output, source.scaleFromOriginal)
    }

    private fun stitchPair(left: RasterImage, right: RasterImage): RasterImage {
        val commonHeight = min(left.height, right.height); var bestOverlap = 0; var bestShift = 0; var bestError = Float.MAX_VALUE
        val minOverlap = min(left.width, right.width) / 5; val maxOverlap = min(left.width, right.width) * 3 / 4
        val step = max(1, commonHeight / 80)
        for (overlap in minOverlap..maxOverlap step max(1, (maxOverlap - minOverlap) / 40)) for (shift in -commonHeight / 12..commonHeight / 12 step max(1, commonHeight / 60)) {
            var error = 0f; var count = 0
            for (y in 0 until commonHeight step step) { val ry = y - shift; if (ry !in 0 until right.height) continue
                for (dx in 0 until overlap step max(1, overlap / 30)) {
                    error += abs(luma(left.argb[y * left.width + left.width - overlap + dx]) - luma(right.argb[ry * right.width + dx])); count++
                }
            }
            if (count > 0 && error / count < bestError) { bestError = error / count; bestOverlap = overlap; bestShift = shift }
        }
        require(bestOverlap > 0 && bestError < 70f) { "Insufficient panorama overlap" }
        val top = min(0, bestShift); val bottom = max(left.height, bestShift + right.height); val width = left.width + right.width - bestOverlap; val height = bottom - top
        val output = IntArray(width * height)
        for (y in 0 until left.height) for (x in 0 until left.width) output[(y - top) * width + x] = left.argb[y * left.width + x]
        val rightStart = left.width - bestOverlap
        for (y in 0 until right.height) for (x in 0 until right.width) { val oy = y + bestShift - top; val ox = rightStart + x; if (oy !in 0 until height) continue
            val index = oy * width + ox; output[index] = if (x < bestOverlap && output[index] != 0) blend(output[index], right.argb[y * right.width + x], x.toFloat() / bestOverlap) else right.argb[y * right.width + x]
        }
        return RasterImage(width, height, output)
    }

    private fun drawCenterCrop(source: RasterImage, output: IntArray, outWidth: Int, outHeight: Int, left: Int, top: Int, width: Int, height: Int) {
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height); val shownW = width / scale; val shownH = height / scale
        val sx0 = (source.width - shownW) / 2f; val sy0 = (source.height - shownH) / 2f
        for (y in 0 until height) for (x in 0 until width) if (left + x in 0 until outWidth && top + y in 0 until outHeight) output[(top + y) * outWidth + left + x] = sampleNearest(source, (sx0 + x / scale) / source.width, (sy0 + y / scale) / source.height)
    }
    private fun sampleNearest(image: RasterImage, nx: Float, ny: Float) = image.argb[(ny * image.height).toInt().coerceIn(0, image.height - 1) * image.width + (nx * image.width).toInt().coerceIn(0, image.width - 1)]
    private fun luma(c: Int) = (c ushr 16 and 255) * .2126f + (c ushr 8 and 255) * .7152f + (c and 255) * .0722f
    private fun rgb(r: Float, g: Float, b: Float) = 0xFF000000.toInt() or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
    private fun blend(a: Int, b: Int, t: Float) = rgb((a ushr 16 and 255) + ((b ushr 16 and 255) - (a ushr 16 and 255)) * t, (a ushr 8 and 255) + ((b ushr 8 and 255) - (a ushr 8 and 255)) * t, (a and 255) + ((b and 255) - (a and 255)) * t)
}
