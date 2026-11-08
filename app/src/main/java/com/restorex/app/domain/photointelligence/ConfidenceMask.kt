package com.restorex.app.domain.photointelligence

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Compact immutable soft mask. One unsigned byte per model output pixel. */
class ConfidenceMask private constructor(
    val id: String,
    val width: Int,
    val height: Int,
    private val values: ByteArray,
) {
    init {
        require(id.isNotBlank() && width > 0 && height > 0)
        require(width.toLong() * height <= MAX_PIXELS && values.size == width * height)
    }

    fun score(x: Int, y: Int): Float = (values[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)].toInt() and 0xff) / 255f

    fun sample(normalizedX: Float, normalizedY: Float): Float {
        val px = normalizedX.coerceIn(0f, 1f) * (width - 1)
        val py = normalizedY.coerceIn(0f, 1f) * (height - 1)
        val x0 = px.toInt(); val y0 = py.toInt(); val x1 = min(width - 1, x0 + 1); val y1 = min(height - 1, y0 + 1)
        val fx = px - x0; val fy = py - y0
        val top = score(x0, y0) * (1f - fx) + score(x1, y0) * fx
        val bottom = score(x0, y1) * (1f - fx) + score(x1, y1) * fx
        return top * (1f - fy) + bottom * fy
    }

    fun copyBytes(): ByteArray = values.copyOf()
    fun invert(newId: String = "$id-inverted") = fromBytes(newId, width, height, ByteArray(values.size) { (255 - (values[it].toInt() and 0xff)).toByte() })
    fun union(other: ConfidenceMask, newId: String = "$id-union-${other.id}") = combine(other, newId) { a, b -> max(a, b) }
    fun intersect(other: ConfidenceMask, newId: String = "$id-intersect-${other.id}") = combine(other, newId) { a, b -> min(a, b) }
    fun exclude(other: ConfidenceMask, newId: String = "$id-exclude-${other.id}") = combine(other, newId) { a, b -> a * (1f - b) }

    fun feather(radius: Int, newId: String = "$id-feathered"): ConfidenceMask {
        if (radius <= 0) return this
        val r = radius.coerceAtMost(16); val horizontal = FloatArray(values.size); val out = ByteArray(values.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f; var count = 0
            for (dx in -r..r) { sum += score((x + dx).coerceIn(0, width - 1), y); count++ }
            horizontal[y * width + x] = sum / count
        }
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f; var count = 0
            for (dy in -r..r) { sum += horizontal[(y + dy).coerceIn(0, height - 1) * width + x]; count++ }
            out[y * width + x] = (sum / count * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
        return fromBytes(newId, width, height, out)
    }

    fun quality(threshold: Float = .5f): MaskQuality {
        require(threshold in 0f..1f)
        var weighted = 0f; var foreground = 0; var foregroundSum = 0f; var uncertain = 0
        val visited = BooleanArray(values.size); val queue = IntArray(values.size); val componentSizes = mutableListOf<Int>()
        for (i in values.indices) {
            val s = (values[i].toInt() and 0xff) / 255f
            weighted += s
            if (s >= threshold) { foreground++; foregroundSum += s }
            if (s in .2f..0.8f) uncertain++
        }
        for (start in values.indices) {
            if (visited[start] || (values[start].toInt() and 0xff) / 255f < threshold) continue
            var head = 0; var tail = 0; var size = 0; queue[tail++] = start; visited[start] = true
            while (head < tail) {
                val p = queue[head++]; size++; val x = p % width; val y = p / width
                val neighbors = intArrayOf(x - 1, y, x + 1, y, x, y - 1, x, y + 1)
                var n = 0
                while (n < neighbors.size) {
                    val nx = neighbors[n++]; val ny = neighbors[n++]
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val ni = ny * width + nx
                    if (!visited[ni] && (values[ni].toInt() and 0xff) / 255f >= threshold) { visited[ni] = true; queue[tail++] = ni }
                }
            }
            componentSizes += size
        }
        val total = values.size.toFloat(); val area = foreground / total; val weightedArea = weighted / total
        val largest = componentSizes.maxOrNull() ?: 0
        val isolated = if (foreground == 0) 0f else (foreground - largest).coerceAtLeast(0) / foreground.toFloat()
        val boundaryUncertainty = uncertain / total
        val reasons = buildList {
            if (weightedArea < .004f) add(MaskRejectionReason.EMPTY)
            if (weightedArea > .96f) add(MaskRejectionReason.NEARLY_FULL_FRAME)
            if (foreground > 0 && foregroundSum / foreground < .58f) add(MaskRejectionReason.LOW_CONFIDENCE)
            if (componentSizes.size > 12 && isolated > .25f) add(MaskRejectionReason.EXCESSIVE_FRAGMENTATION)
            if (boundaryUncertainty > .58f) add(MaskRejectionReason.UNCERTAIN_BOUNDARY)
        }
        return MaskQuality(weightedArea, area, if (foreground == 0) 0f else foregroundSum / foreground,
            boundaryUncertainty, componentSizes.size, isolated, reasons.isEmpty(), reasons)
    }

    private fun combine(other: ConfidenceMask, newId: String, op: (Float, Float) -> Float): ConfidenceMask {
        require(width == other.width && height == other.height) { "Mask dimensions must match" }
        return fromFloats(newId, width, height, FloatArray(values.size) { op((values[it].toInt() and 0xff) / 255f, (other.values[it].toInt() and 0xff) / 255f) })
    }

    companion object {
        const val MAX_PIXELS = 1_048_576L
        fun fromFloats(id: String, width: Int, height: Int, values: FloatArray): ConfidenceMask {
            require(values.all { it.isFinite() && it in 0f..1f })
            return fromBytes(id, width, height, ByteArray(values.size) { (values[it] * 255f).roundToInt().coerceIn(0, 255).toByte() })
        }
        fun fromBytes(id: String, width: Int, height: Int, values: ByteArray) = ConfidenceMask(id, width, height, values.copyOf())
    }
}
