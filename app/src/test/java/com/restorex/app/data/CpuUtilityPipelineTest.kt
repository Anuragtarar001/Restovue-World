package com.restorex.app.data

import com.restorex.app.domain.model.PassportCatalog
import com.restorex.app.domain.model.PrintSheetCalculator
import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.*
import org.junit.Test

class CpuUtilityPipelineTest {
    @Test fun collageUsesRequestedDimensionsAndDistinctCells() {
        val result = CpuUtilityPipeline.collage(listOf(solid(40, 20, 0xFFFF0000.toInt()), solid(20, 40, 0xFF0000FF.toInt())), 200, 100, 4)
        assertEquals(200, result.width); assertEquals(100, result.height)
        assertTrue(result.argb.any { it == 0xFFFF0000.toInt() }); assertTrue(result.argb.any { it == 0xFF0000FF.toInt() })
    }

    @Test fun hdrFusionRecoversInformationFromDarkAndBrightInputs() {
        val dark = solid(30, 20, gray(25)); val bright = solid(30, 20, gray(235))
        val merged = CpuUtilityPipeline.hdrMerge(listOf(dark, bright), 1f)
        val value = merged.argb[0] and 255
        assertTrue(value in 80..180)
    }

    @Test fun panoramaFindsKnownHorizontalOverlap() {
        fun pattern(globalX: Int, y: Int) = 0xFF000000.toInt() or (((globalX * 37 + y * 11) and 255) shl 16) or (((globalX * 17 + y * 29) and 255) shl 8) or ((globalX * 7 + y * 43) and 255)
        val left = RasterImage(100, 50, IntArray(100 * 50) { pattern(it % 100, it / 100) })
        val right = RasterImage(100, 50, IntArray(100 * 50) { pattern(60 + it % 100, it / 100) })
        val result = CpuUtilityPipeline.panorama(listOf(left, right))
        assertTrue(result.width in 150..170); assertEquals(50, result.height)
    }

    @Test fun adaptiveDocumentThresholdSeparatesInkAndPaper() {
        val source = solid(40, 30, gray(230)).copy(argb = IntArray(40 * 30) { index -> if (index % 40 in 18..21) gray(25) else gray(230) })
        val output = CpuUtilityPipeline.documentEnhance(source, true)
        assertEquals(0, output.argb[20] and 255); assertEquals(255, output.argb[2] and 255)
    }

    @Test fun signatureCleanupCropsBoundsAndMakesPaperTransparent() {
        val pixels = IntArray(60 * 40) { gray(255) }
        for (y in 15..20) for (x in 20..40) pixels[y * 60 + x] = gray(0)
        val output = CpuUtilityPipeline.signatureCleanup(RasterImage(60, 40, pixels))
        assertTrue(output.width < 60 && output.height < 40); assertTrue(output.argb.any { it ushr 24 == 0 }); assertTrue(output.argb.any { it ushr 24 == 255 })
    }

    @Test fun passportAndSheetDimensionsRespectDpi() {
        val india = PassportCatalog.presets.first { it.id == "india" }
        assertEquals(413, india.pixelWidth(300)); assertEquals(531, india.pixelHeight(300))
        assertEquals(2480, PrintSheetCalculator.pixelsForMillimeters(210f, 300))
        val sheet = CpuUtilityPipeline.passportSheet(solid(400, 500, gray(100)), 100, 130, 400, 300, 10, 5, 4)
        assertEquals(400, sheet.width); assertEquals(300, sheet.height)
    }

    private fun solid(w: Int, h: Int, c: Int) = RasterImage(w, h, IntArray(w * h) { c })
    private fun gray(v: Int) = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
}
