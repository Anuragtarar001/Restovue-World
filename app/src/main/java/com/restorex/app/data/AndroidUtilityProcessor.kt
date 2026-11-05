package com.restorex.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import com.restorex.app.domain.model.PrintSheetCalculator
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.model.UtilityMode
import com.restorex.app.domain.model.UtilityOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AndroidUtilityProcessor(private val resolver: ContentResolver) {
    suspend fun process(mode: UtilityMode, sources: List<String>, destination: String, options: UtilityOptions = UtilityOptions()) = withContext(Dispatchers.IO) {
        require(sources.size in mode.minimumImages..mode.maximumImages)
        val perImagePixels = when (mode) {
            UtilityMode.COLLAGE -> (options.outputWidth.toLong() * options.outputHeight / sources.size).coerceAtMost(2_000_000)
            UtilityMode.HDR_NATURAL, UtilityMode.HDR_DRAMATIC, UtilityMode.PANORAMA -> 2_500_000
            else -> 6_000_000
        }
        val images = ArrayList<RasterImage>(sources.size)
        sources.forEach { uri -> coroutineContext.ensureActive(); images += decode(uri, perImagePixels) }
        val result = when (mode) {
            UtilityMode.COLLAGE -> CpuUtilityPipeline.collage(images, options.outputWidth, options.outputHeight, options.spacing)
            UtilityMode.HDR_NATURAL -> CpuUtilityPipeline.hdrMerge(images, .65f)
            UtilityMode.HDR_DRAMATIC -> CpuUtilityPipeline.hdrMerge(images, 1f)
            UtilityMode.PANORAMA -> CpuUtilityPipeline.panorama(images)
            UtilityMode.DOCUMENT -> CpuUtilityPipeline.documentEnhance(images.single(), threshold = true)
            UtilityMode.SIGNATURE -> CpuUtilityPipeline.signatureCleanup(images.single())
            UtilityMode.PASSPORT_INDIA, UtilityMode.PASSPORT_JAPAN -> {
                val photoW = PrintSheetCalculator.pixelsForMillimeters(options.photoWidthMm, options.dpi)
                val photoH = PrintSheetCalculator.pixelsForMillimeters(options.photoHeightMm, options.dpi)
                val sheetW = options.outputWidth; val sheetH = options.outputHeight
                CpuUtilityPipeline.passportSheet(images.single(), photoW, photoH, sheetW, sheetH, options.spacing, options.spacing, options.copies)
            }
            UtilityMode.BATCH -> error("Batch mode requires a destination directory")
        }
        coroutineContext.ensureActive()
        val bitmap = result.toBitmap()
        try {
            resolver.openOutputStream(destination.toUri(), "w").use { stream ->
                requireNotNull(stream); check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally { bitmap.recycle() }
    }

    private fun decode(uri: String, maxPixels: Long): RasterImage {
        val source = ImageDecoder.createSource(resolver, uri.toUri())
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val pixels = info.size.width.toLong() * info.size.height
            if (pixels > maxPixels) {
                val scale = sqrt(maxPixels.toDouble() / pixels).toFloat()
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE; decoder.isMutableRequired = false
        }
        return bitmap.toRaster().also { bitmap.recycle() }
    }
}
