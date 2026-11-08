package com.restorex.app.domain.photointelligence

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init { require(left >= 0 && top >= 0 && right > left && bottom > top) }
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class ExportTile(val core: PixelRect, val decode: PixelRect)

/**
 * Pure geometry for a future bounded decoder/encoder. This does not claim 48 MP export support;
 * Android's current ImageDecoder/Bitmap encoder path still requires a full-frame allocation.
 */
class TiledExportPlanner {
    fun plan(width: Int, height: Int, coreSize: Int = 1024, overlap: Int = 32): List<ExportTile> {
        require(width > 0 && height > 0 && coreSize in 128..4096 && overlap in 0 until coreSize / 2)
        val result = mutableListOf<ExportTile>()
        var top = 0
        while (top < height) {
            val bottom = minOf(height, top + coreSize)
            var left = 0
            while (left < width) {
                val right = minOf(width, left + coreSize)
                val core = PixelRect(left, top, right, bottom)
                val decode = PixelRect(maxOf(0, left - overlap), maxOf(0, top - overlap),
                    minOf(width, right + overlap), minOf(height, bottom + overlap))
                result += ExportTile(core, decode)
                left = right
            }
            top = bottom
        }
        return result
    }
}
