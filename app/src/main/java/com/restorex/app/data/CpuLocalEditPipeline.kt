package com.restorex.app.data

import com.restorex.app.domain.model.BrushEditType
import com.restorex.app.domain.model.BrushStroke
import com.restorex.app.domain.model.LinearLight
import com.restorex.app.domain.model.LocalEditOperation
import com.restorex.app.domain.model.LocalLighting
import com.restorex.app.domain.model.RadialLight
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.model.degreesToRadians
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Conventional, deterministic local edits rendered from compact normalized vector operations. */
object CpuLocalEditPipeline {
    fun apply(
        source: RasterImage,
        operations: List<LocalEditOperation>,
        shouldCancel: () -> Boolean = { false },
        reuseSourcePixels: Boolean = false,
    ): RasterImage {
        if (operations.isEmpty()) return source
        val pixels = if (reuseSourcePixels) source.argb else source.argb.copyOf()
        val localLight = SparseLightMask(source.width, source.height)
        operations.forEachIndexed { index, operation ->
            if (shouldCancel()) throw CancellationException("Local render superseded")
            when (operation) {
                is BrushStroke -> when (operation.type) {
                    BrushEditType.HEAL -> applyHeal(pixels, source.width, source.height, operation, shouldCancel)
                    BrushEditType.CLONE -> applyClone(pixels, source.width, source.height, operation, shouldCancel)
                    BrushEditType.SMOOTH -> applySmooth(pixels, source.width, source.height, operation, shouldCancel)
                    BrushEditType.DODGE -> localLight.paint(
                        operation,
                        operation.lighting.takeUnless { it.exposure == 0f } ?: LocalLighting(exposure = abs(operation.settings.strength).coerceAtLeast(.05f)), shouldCancel,
                    )
                    BrushEditType.BURN -> localLight.paint(
                        operation,
                        operation.lighting.takeUnless { it.exposure == 0f } ?: LocalLighting(exposure = -abs(operation.settings.strength).coerceAtLeast(.05f)), shouldCancel,
                    )
                    BrushEditType.BRUSH_LIGHT -> localLight.paint(
                        operation,
                        operation.lighting.takeUnless { it == LocalLighting() } ?: LocalLighting(exposure = operation.settings.strength), shouldCancel,
                    )
                    BrushEditType.ERASER -> localLight.erase(operation, shouldCancel)
                }
                is RadialLight -> applyRadial(pixels, source.width, source.height, operation, shouldCancel)
                is LinearLight -> applyLinear(pixels, source.width, source.height, operation, shouldCancel)
            }
            if (index and 7 == 0 && shouldCancel()) throw CancellationException("Local render superseded")
        }
        localLight.applyTo(pixels, shouldCancel)
        return RasterImage(source.width, source.height, pixels, source.scaleFromOriginal)
    }

    private fun applyClone(pixels: IntArray, width: Int, height: Int, stroke: BrushStroke, shouldCancel: () -> Boolean) {
        val sourceAnchor = requireNotNull(stroke.sourceAnchor)
        val snapshot = pixels.copyOf()
        val mask = StrokeMask.create(stroke, width, height)
        val destination = stroke.points.first().toPixel(width, height)
        val anchor = sourceAnchor.toPixel(width, height)
        mask.forEach(shouldCancel) { x, y, alpha ->
            val sourceX = (anchor.first + x - destination.first).coerceIn(0, width - 1)
            val sourceY = (anchor.second + y - destination.second).coerceIn(0, height - 1)
            val sampled = snapshot[sourceY * width + sourceX]
            val index = y * width + x
            pixels[index] = blend(pixels[index], sampled, alpha * stroke.settings.opacity)
        }
    }

    private fun applyHeal(pixels: IntArray, width: Int, height: Int, stroke: BrushStroke, shouldCancel: () -> Boolean) {
        val snapshot = pixels.copyOf()
        val mask = StrokeMask.create(stroke, width, height)
        val destination = stroke.points.first().toPixel(width, height)
        val radius = (stroke.settings.radius * min(width, height)).coerceAtLeast(1f)
        val anchor = stroke.sourceAnchor?.toPixel(width, height) ?: run {
            val offset = (radius * 2.4f).roundToInt()
            val x = if (destination.first + offset + radius < width) destination.first + offset else destination.first - offset
            x.coerceIn(0, width - 1) to destination.second
        }
        val destinationMean = meanColor(snapshot, width, height, destination.first, destination.second, radius.roundToInt())
        val sourceMean = meanColor(snapshot, width, height, anchor.first, anchor.second, radius.roundToInt())
        val deltaR = (destinationMean[0] - sourceMean[0]) * .72f
        val deltaG = (destinationMean[1] - sourceMean[1]) * .72f
        val deltaB = (destinationMean[2] - sourceMean[2]) * .72f
        mask.forEach(shouldCancel) { x, y, alpha ->
            val sourceX = (anchor.first + x - destination.first).coerceIn(0, width - 1)
            val sourceY = (anchor.second + y - destination.second).coerceIn(0, height - 1)
            val sampled = snapshot[sourceY * width + sourceX]
            val healed = pack(
                sampled ushr 24 and 0xFF,
                (sampled ushr 16 and 0xFF) + deltaR,
                (sampled ushr 8 and 0xFF) + deltaG,
                (sampled and 0xFF) + deltaB,
            )
            val index = y * width + x
            pixels[index] = blend(pixels[index], healed, alpha * stroke.settings.opacity)
        }
    }

    private fun applySmooth(pixels: IntArray, width: Int, height: Int, stroke: BrushStroke, shouldCancel: () -> Boolean) {
        val snapshot = pixels.copyOf()
        val mask = StrokeMask.create(stroke, width, height)
        val filterRadius = (min(width, height) * .0015f).roundToInt().coerceIn(1, 4)
        mask.forEach(shouldCancel) { x, y, alpha ->
            val center = snapshot[y * width + x]
            val centerLuma = luma(center)
            var sumR = 0f; var sumG = 0f; var sumB = 0f; var weightSum = 0f
            for (dy in -filterRadius..filterRadius) for (dx in -filterRadius..filterRadius) {
                val sample = snapshot[(y + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)]
                val rangeDelta = luma(sample) - centerLuma
                val spatial = exp(-(dx * dx + dy * dy) / (filterRadius * filterRadius * 1.75f).coerceAtLeast(1f))
                val edge = exp(-(rangeDelta * rangeDelta) / 900f)
                val weight = spatial * edge
                sumR += (sample ushr 16 and 0xFF) * weight
                sumG += (sample ushr 8 and 0xFF) * weight
                sumB += (sample and 0xFF) * weight
                weightSum += weight
            }
            val smooth = pack(center ushr 24 and 0xFF, sumR / weightSum, sumG / weightSum, sumB / weightSum)
            val amount = alpha * stroke.settings.opacity * abs(stroke.settings.strength)
            pixels[y * width + x] = blend(center, smooth, amount)
        }
    }

    private fun applyRadial(pixels: IntArray, width: Int, height: Int, light: RadialLight, shouldCancel: () -> Boolean) {
        val centerX = light.center.x * (width - 1)
        val centerY = light.center.y * (height - 1)
        val radiusX = (light.width * width).coerceAtLeast(1f)
        val radiusY = (light.height * height).coerceAtLeast(1f)
        val angle = light.rotationDegrees.degreesToRadians()
        val cosine = cos(angle); val sine = sin(angle)
        val inner = (1f - light.feather).coerceIn(0f, .99f)
        for (y in 0 until height) for (x in 0 until width) {
            if (x == 0 && y and 15 == 0 && shouldCancel()) throw CancellationException("Local render superseded")
            val dx = x - centerX; val dy = y - centerY
            val rotatedX = cosine * dx + sine * dy
            val rotatedY = -sine * dx + cosine * dy
            val distance = sqrt((rotatedX / radiusX).pow(2) + (rotatedY / radiusY).pow(2))
            var mask = 1f - smoothStep(inner, 1f, distance)
            if (light.inverted) mask = 1f - mask
            val alpha = mask * light.opacity
            if (alpha > .001f) {
                val index = y * width + x
                pixels[index] = lightPixel(pixels[index], light.exposure, light.highlights, light.shadows, 0f, 0f, alpha)
            }
        }
    }

    private fun applyLinear(pixels: IntArray, width: Int, height: Int, light: LinearLight, shouldCancel: () -> Boolean) {
        val centerX = light.center.x * (width - 1)
        val centerY = light.center.y * (height - 1)
        val angle = light.rotationDegrees.degreesToRadians()
        val normalX = cos(angle); val normalY = sin(angle)
        val diagonal = hypot(width.toFloat(), height.toFloat()).coerceAtLeast(1f)
        for (y in 0 until height) for (x in 0 until width) {
            if (x == 0 && y and 15 == 0 && shouldCancel()) throw CancellationException("Local render superseded")
            val projection = ((x - centerX) * normalX + (y - centerY) * normalY) / diagonal
            var mask = smoothStep(-light.feather / 2f, light.feather / 2f, projection)
            if (light.inverted) mask = 1f - mask
            val alpha = mask * light.opacity
            if (alpha > .001f) {
                val index = y * width + x
                pixels[index] = lightPixel(pixels[index], light.exposure, 0f, 0f, light.temperature, light.tint, alpha)
            }
        }
    }

    private class SparseLightMask(private val width: Int, private val height: Int) {
        private val columns = (width + TILE_SIZE - 1) / TILE_SIZE
        private val tiles = HashMap<Int, LightTile>()

        fun paint(stroke: BrushStroke, lighting: LocalLighting, shouldCancel: () -> Boolean) {
            val mask = StrokeMask.create(stroke, width, height)
            mask.forEach(shouldCancel) { x, y, alpha ->
                val (tile, offset) = tileAt(x, y)
                val amount = alpha * stroke.settings.opacity
                tile.exposure[offset] = add(tile.exposure[offset], lighting.exposure / 2f, amount)
                tile.highlights[offset] = add(tile.highlights[offset], lighting.highlights, amount)
                tile.shadows[offset] = add(tile.shadows[offset], lighting.shadows, amount)
                tile.temperature[offset] = add(tile.temperature[offset], lighting.temperature, amount)
                tile.tint[offset] = add(tile.tint[offset], lighting.tint, amount)
            }
        }

        fun erase(stroke: BrushStroke, shouldCancel: () -> Boolean) {
            val mask = StrokeMask.create(stroke, width, height)
            mask.forEach(shouldCancel) { x, y, alpha ->
                val (tile, offset) = tileAt(x, y)
                val remaining = 1f - alpha * stroke.settings.opacity
                tile.exposure[offset] = (tile.exposure[offset] * remaining).roundToInt().toShort()
                tile.highlights[offset] = (tile.highlights[offset] * remaining).roundToInt().toShort()
                tile.shadows[offset] = (tile.shadows[offset] * remaining).roundToInt().toShort()
                tile.temperature[offset] = (tile.temperature[offset] * remaining).roundToInt().toShort()
                tile.tint[offset] = (tile.tint[offset] * remaining).roundToInt().toShort()
            }
        }

        fun applyTo(pixels: IntArray, shouldCancel: () -> Boolean) {
            tiles.forEach { (key, tile) ->
                if (shouldCancel()) throw CancellationException("Local render superseded")
                val tileX = key % columns; val tileY = key / columns
                for (localY in 0 until TILE_SIZE) for (localX in 0 until TILE_SIZE) {
                    val x = tileX * TILE_SIZE + localX; val y = tileY * TILE_SIZE + localY
                    if (x >= width || y >= height) continue
                    val offset = localY * TILE_SIZE + localX
                    val exposure = tile.exposure[offset] / Short.MAX_VALUE.toFloat() * 2f
                    val highlights = tile.highlights[offset] / Short.MAX_VALUE.toFloat()
                    val shadows = tile.shadows[offset] / Short.MAX_VALUE.toFloat()
                    val temperature = tile.temperature[offset] / Short.MAX_VALUE.toFloat()
                    val tint = tile.tint[offset] / Short.MAX_VALUE.toFloat()
                    if (abs(exposure) + abs(highlights) + abs(shadows) + abs(temperature) + abs(tint) > .0001f) {
                        val index = y * width + x
                        pixels[index] = lightPixel(pixels[index], exposure, highlights, shadows, temperature, tint, 1f)
                    }
                }
            }
        }

        private fun tileAt(x: Int, y: Int): Pair<LightTile, Int> {
            val tileX = x / TILE_SIZE; val tileY = y / TILE_SIZE
            val tile = tiles.getOrPut(tileY * columns + tileX) { LightTile() }
            return tile to ((y % TILE_SIZE) * TILE_SIZE + x % TILE_SIZE)
        }

        private fun add(current: Short, value: Float, amount: Float): Short =
            (current + value * amount * Short.MAX_VALUE).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

        private class LightTile {
            val exposure = ShortArray(TILE_SIZE * TILE_SIZE)
            val highlights = ShortArray(TILE_SIZE * TILE_SIZE)
            val shadows = ShortArray(TILE_SIZE * TILE_SIZE)
            val temperature = ShortArray(TILE_SIZE * TILE_SIZE)
            val tint = ShortArray(TILE_SIZE * TILE_SIZE)
        }
    }

    private class StrokeMask(
        private val left: Int,
        private val top: Int,
        private val width: Int,
        private val height: Int,
        private val values: ByteArray,
    ) {
        inline fun forEach(shouldCancel: () -> Boolean = { false }, block: (x: Int, y: Int, alpha: Float) -> Unit) {
            values.indices.forEach { index ->
                if (index and 0xFFF == 0 && shouldCancel()) throw CancellationException("Local render superseded")
                val value = values[index].toInt() and 0xFF
                if (value != 0) block(left + index % width, top + index / width, value / 255f)
            }
        }

        companion object {
            fun create(stroke: BrushStroke, imageWidth: Int, imageHeight: Int): StrokeMask {
                val radius = (stroke.settings.radius * min(imageWidth, imageHeight)).coerceAtLeast(1f)
                val pixelPoints = stroke.points.map { point ->
                    Triple(point.x * (imageWidth - 1), point.y * (imageHeight - 1), point.pressure)
                }
                val left = floor(pixelPoints.minOf { it.first } - radius).toInt().coerceAtLeast(0)
                val top = floor(pixelPoints.minOf { it.second } - radius).toInt().coerceAtLeast(0)
                val right = ceil(pixelPoints.maxOf { it.first } + radius).toInt().coerceAtMost(imageWidth - 1)
                val bottom = ceil(pixelPoints.maxOf { it.second } + radius).toInt().coerceAtMost(imageHeight - 1)
                val maskWidth = right - left + 1; val maskHeight = bottom - top + 1
                val values = ByteArray(maskWidth * maskHeight)
                fun dab(cx: Float, cy: Float, pressure: Float) {
                    val inner = radius * stroke.settings.hardness
                    val minX = floor(cx - radius).toInt().coerceAtLeast(left)
                    val maxX = ceil(cx + radius).toInt().coerceAtMost(right)
                    val minY = floor(cy - radius).toInt().coerceAtLeast(top)
                    val maxY = ceil(cy + radius).toInt().coerceAtMost(bottom)
                    for (y in minY..maxY) for (x in minX..maxX) {
                        val distance = hypot(x - cx, y - cy)
                        if (distance > radius) continue
                        val feather = if (distance <= inner || radius - inner < .001f) 1f
                        else 1f - smoothStep(0f, 1f, (distance - inner) / (radius - inner))
                        val value = (feather * pressure * 255f).roundToInt().coerceIn(0, 255)
                        val index = (y - top) * maskWidth + x - left
                        if (value > (values[index].toInt() and 0xFF)) values[index] = value.toByte()
                    }
                }
                if (pixelPoints.size == 1) {
                    val point = pixelPoints.first(); dab(point.first, point.second, point.third)
                } else {
                    pixelPoints.zipWithNext().forEach { (start, end) ->
                        val distance = hypot(end.first - start.first, end.second - start.second)
                        val steps = ceil(distance / (radius * .35f).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
                        for (step in 0..steps) {
                            val amount = step.toFloat() / steps
                            dab(
                                start.first + (end.first - start.first) * amount,
                                start.second + (end.second - start.second) * amount,
                                start.third + (end.third - start.third) * amount,
                            )
                        }
                    }
                }
                return StrokeMask(left, top, maskWidth, maskHeight, values)
            }
        }
    }

    private fun com.restorex.app.domain.model.NormalizedPoint.toPixel(width: Int, height: Int) =
        (x * (width - 1)).roundToInt() to (y * (height - 1)).roundToInt()

    private fun meanColor(pixels: IntArray, width: Int, height: Int, centerX: Int, centerY: Int, radius: Int): FloatArray {
        var r = 0L; var g = 0L; var b = 0L; var count = 0
        val step = max(1, radius / 8)
        for (y in (centerY - radius).coerceAtLeast(0)..(centerY + radius).coerceAtMost(height - 1) step step) {
            for (x in (centerX - radius).coerceAtLeast(0)..(centerX + radius).coerceAtMost(width - 1) step step) {
                val color = pixels[y * width + x]
                r += color ushr 16 and 0xFF; g += color ushr 8 and 0xFF; b += color and 0xFF; count++
            }
        }
        return floatArrayOf(r.toFloat() / count, g.toFloat() / count, b.toFloat() / count)
    }

    private fun lightPixel(color: Int, exposure: Float, highlights: Float, shadows: Float, temperature: Float, tint: Float, alpha: Float): Int {
        var r = (color ushr 16 and 0xFF) / 255f
        var g = (color ushr 8 and 0xFF) / 255f
        var b = (color and 0xFF) / 255f
        val y = r * .2126f + g * .7152f + b * .0722f
        val gain = 2f.pow(exposure * alpha)
        val tonal = highlights * y * y * .28f * alpha + shadows * (1f - y).pow(2) * .32f * alpha
        r = r * gain + tonal; g = g * gain + tonal; b = b * gain + tonal
        r *= 1f + temperature * .15f * alpha + tint * .04f * alpha
        g *= 1f - tint * .08f * alpha
        b *= 1f - temperature * .15f * alpha + tint * .04f * alpha
        return pack(color ushr 24 and 0xFF, r * 255f, g * 255f, b * 255f)
    }

    private fun luma(color: Int) = (color ushr 16 and 0xFF) * .2126f + (color ushr 8 and 0xFF) * .7152f + (color and 0xFF) * .0722f

    private fun blend(base: Int, overlay: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        return pack(
            base ushr 24 and 0xFF,
            (base ushr 16 and 0xFF) + ((overlay ushr 16 and 0xFF) - (base ushr 16 and 0xFF)) * a,
            (base ushr 8 and 0xFF) + ((overlay ushr 8 and 0xFF) - (base ushr 8 and 0xFF)) * a,
            (base and 0xFF) + ((overlay and 0xFF) - (base and 0xFF)) * a,
        )
    }

    private fun pack(alpha: Int, red: Float, green: Float, blue: Float) =
        (alpha shl 24) or (red.roundToInt().coerceIn(0, 255) shl 16) or
            (green.roundToInt().coerceIn(0, 255) shl 8) or blue.roundToInt().coerceIn(0, 255)

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private const val TILE_SIZE = 128
}
