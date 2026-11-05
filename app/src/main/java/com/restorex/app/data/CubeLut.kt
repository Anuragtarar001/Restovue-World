package com.restorex.app.data

import kotlin.math.floor

data class CubeLut(
    val size: Int,
    val values: FloatArray,
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
) {
    init {
        require(size in 2..65) { "LUT_3D_SIZE must be between 2 and 65" }
        require(values.size == size * size * size * 3) { "LUT data count does not match cube size" }
        require(domainMin.size == 3 && domainMax.size == 3)
        require((0..2).all { domainMax[it] > domainMin[it] })
        require(values.all { it.isFinite() })
    }

    fun sample(red: Float, green: Float, blue: Float): FloatArray {
        fun coordinate(value: Float, channel: Int): Float =
            ((value - domainMin[channel]) / (domainMax[channel] - domainMin[channel]))
                .coerceIn(0f, 1f) * (size - 1)
        val rx = coordinate(red, 0); val gy = coordinate(green, 1); val bz = coordinate(blue, 2)
        val r0 = floor(rx).toInt(); val g0 = floor(gy).toInt(); val b0 = floor(bz).toInt()
        val r1 = (r0 + 1).coerceAtMost(size - 1); val g1 = (g0 + 1).coerceAtMost(size - 1); val b1 = (b0 + 1).coerceAtMost(size - 1)
        val fr = rx - r0; val fg = gy - g0; val fb = bz - b0
        fun value(r: Int, g: Int, b: Int, channel: Int): Float = values[((b * size * size + g * size + r) * 3) + channel]
        return FloatArray(3) { channel ->
            val c000 = value(r0, g0, b0, channel); val c100 = value(r1, g0, b0, channel)
            val c010 = value(r0, g1, b0, channel); val c110 = value(r1, g1, b0, channel)
            val c001 = value(r0, g0, b1, channel); val c101 = value(r1, g0, b1, channel)
            val c011 = value(r0, g1, b1, channel); val c111 = value(r1, g1, b1, channel)
            val c00 = lerp(c000, c100, fr); val c10 = lerp(c010, c110, fr)
            val c01 = lerp(c001, c101, fr); val c11 = lerp(c011, c111, fr)
            lerp(lerp(c00, c10, fg), lerp(c01, c11, fg), fb).coerceIn(0f, 1f)
        }
    }

    private fun lerp(start: Float, end: Float, amount: Float) = start + (end - start) * amount
}

object CubeLutParser {
    fun parse(text: String): CubeLut {
        var size: Int? = null
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val parts = line.split(Regex("\\s+"))
            when (parts[0].uppercase()) {
                "TITLE" -> Unit
                "LUT_3D_SIZE" -> {
                    require(parts.size == 2) { "Invalid LUT_3D_SIZE at line ${index + 1}" }
                    size = parts[1].toIntOrNull() ?: error("Invalid LUT size at line ${index + 1}")
                }
                "DOMAIN_MIN" -> domainMin = parseTriplet(parts, index)
                "DOMAIN_MAX" -> domainMax = parseTriplet(parts, index)
                "LUT_1D_SIZE" -> error("1D LUT files are not supported")
                else -> {
                    require(parts.size == 3) { "Invalid LUT row at line ${index + 1}" }
                    parts.forEach { token -> values += token.toFloatOrNull() ?: error("Invalid number at line ${index + 1}") }
                }
            }
        }
        val cubeSize = requireNotNull(size) { "Missing LUT_3D_SIZE" }
        return CubeLut(cubeSize, values.toFloatArray(), domainMin, domainMax)
    }

    private fun parseTriplet(parts: List<String>, line: Int): FloatArray {
        require(parts.size == 4) { "Invalid domain at line ${line + 1}" }
        return FloatArray(3) { channel -> parts[channel + 1].toFloatOrNull() ?: error("Invalid domain at line ${line + 1}") }
    }
}
