package com.restorex.app.domain.model

data class RasterImage(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val scaleFromOriginal: Float = 1f,
) {
    init {
        require(width > 0 && height > 0)
        require(argb.size == width * height)
        require(scaleFromOriginal > 0f)
    }
}

@JvmInline
value class ImageSource(val value: String)

enum class ExportFormat(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
}

data class ExportOptions(
    val format: ExportFormat,
    val jpegQuality: Int = 95,
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val preserveMetadata: Boolean = true,
) {
    init {
        require(jpegQuality in 1..100)
        require(maxWidth == null || maxWidth > 0)
        require(maxHeight == null || maxHeight > 0)
    }
}
