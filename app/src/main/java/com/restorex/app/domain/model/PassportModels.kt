package com.restorex.app.domain.model

import kotlin.math.roundToInt

data class PassportPreset(val id: String, val label: String, val widthMm: Float, val heightMm: Float, val recommendedDpi: Int) {
    fun pixelWidth(dpi: Int = recommendedDpi) = (widthMm / 25.4f * dpi).toInt().coerceAtLeast(1)
    fun pixelHeight(dpi: Int = recommendedDpi) = (heightMm / 25.4f * dpi).toInt().coerceAtLeast(1)
}

object PassportCatalog {
    val presets = listOf(
        PassportPreset("india", "India (editable)", 35f, 45f, 300),
        PassportPreset("japan", "Japan (editable)", 35f, 45f, 300),
    )
}

object PrintSheetCalculator {
    fun pixelsForMillimeters(mm: Float, dpi: Int): Int { require(mm > 0 && dpi in 72..1200); return (mm / 25.4f * dpi).roundToInt() }
    fun copiesAcross(sheetWidthPx: Int, photoWidthPx: Int, marginPx: Int, spacingPx: Int) = ((sheetWidthPx - marginPx * 2 + spacingPx) / (photoWidthPx + spacingPx)).coerceAtLeast(0)
    fun copiesDown(sheetHeightPx: Int, photoHeightPx: Int, marginPx: Int, spacingPx: Int) = ((sheetHeightPx - marginPx * 2 + spacingPx) / (photoHeightPx + spacingPx)).coerceAtLeast(0)
}
