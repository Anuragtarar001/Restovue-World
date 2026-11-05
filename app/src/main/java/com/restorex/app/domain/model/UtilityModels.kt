package com.restorex.app.domain.model

enum class UtilityMode(val label: String, val minimumImages: Int, val maximumImages: Int) {
    COLLAGE("Collage", 2, 9), HDR_NATURAL("HDR Natural", 2, 5), HDR_DRAMATIC("HDR Dramatic", 2, 5),
    PANORAMA("Panorama", 2, 8), PASSPORT_INDIA("India passport sheet", 1, 1), PASSPORT_JAPAN("Japan passport sheet", 1, 1),
    DOCUMENT("Document cleanup", 1, 1), SIGNATURE("Signature cleanup", 1, 1),
    BATCH("Batch editor", 2, 50),
}

enum class BatchFormat(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
}
data class BatchOptions(
    val format: BatchFormat = BatchFormat.JPEG,
    val quality: Int = 90,
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val quarterTurns: Int = 0,
    val removeMetadata: Boolean = false,
    val prefix: String = "Restovue_",
    val targetKilobytes: Int? = null,
) {
    init {
        require(quality in 1..100 && quarterTurns in 0..3 && prefix.length <= 40)
        require(maxWidth == null || maxWidth > 0)
        require(maxHeight == null || maxHeight > 0)
        require(targetKilobytes == null || targetKilobytes in 10..50_000)
        require(targetKilobytes == null || format != BatchFormat.PNG) { "PNG cannot target a lossy file size" }
    }
}

data class UtilityOptions(
    val outputWidth: Int = 2400,
    val outputHeight: Int = 1600,
    val spacing: Int = 20,
    val dpi: Int = 300,
    val copies: Int = 8,
    val photoWidthMm: Float = 35f,
    val photoHeightMm: Float = 45f,
) {
    init { require(outputWidth in 64..12000 && outputHeight in 64..12000 && spacing in 0..1000 && dpi in 72..1200 && copies in 1..100); require(photoWidthMm in 10f..100f && photoHeightMm in 10f..100f) }
}
