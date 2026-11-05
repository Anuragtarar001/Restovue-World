package com.restorex.app.data

import android.graphics.Bitmap
import com.restorex.app.domain.engine.CropCalculator
import com.restorex.app.domain.engine.ImageProcessor
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.RasterImage

class AndroidImageProcessor(private val lutStore: LutStore? = null) : ImageProcessor {
    override fun render(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean): RasterImage {
        val localRaster = CpuLocalEditPipeline.apply(source, edits.localEdits, shouldCancel)
        val geometryRaster = renderGeometry(localRaster, edits, shouldCancel)
        return CpuImagePipeline.apply(
            geometryRaster,
            edits.effectiveAdjustments,
            lutStore?.load(edits.lut),
            edits.lut?.intensity ?: 0f,
            shouldCancel,
        )
    }

    override fun renderSharpenMask(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean): RasterImage =
        CpuImagePipeline.sharpeningMask(
            renderGeometry(CpuLocalEditPipeline.apply(source, edits.localEdits, shouldCancel), edits, shouldCancel),
            edits.effectiveAdjustments,
        )

    /** Export owns [source], so its packed pixel buffer may be reused to lower peak heap usage. */
    fun renderForExport(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean): RasterImage {
        val localRaster = CpuLocalEditPipeline.apply(source, edits.localEdits, shouldCancel, reuseSourcePixels = true)
        val geometryRaster = renderGeometry(localRaster, edits, shouldCancel)
        return CpuImagePipeline.apply(
            geometryRaster,
            edits.effectiveAdjustments,
            lutStore?.load(edits.lut),
            edits.lut?.intensity ?: 0f,
            shouldCancel,
            reuseSourcePixels = true,
        )
    }

    private fun renderGeometry(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean): RasterImage {
        if (edits.quarterTurnsClockwise == 0 && edits.cropAspect == com.restorex.app.domain.model.CropAspect.ORIGINAL) return source
        val turns = edits.quarterTurnsClockwise
        val rotatedWidth = if (turns % 2 == 0) source.width else source.height
        val rotatedHeight = if (turns % 2 == 0) source.height else source.width
        val bounds = CropCalculator.centered(rotatedWidth, rotatedHeight, edits.cropAspect)
        val pixels = IntArray(bounds.width * bounds.height)
        for (outputY in 0 until bounds.height) {
            if (outputY and 31 == 0 && shouldCancel()) throw kotlinx.coroutines.CancellationException("Geometry render superseded")
            val rotatedY = bounds.top + outputY
            for (outputX in 0 until bounds.width) {
                val rotatedX = bounds.left + outputX
                val (sourceX, sourceY) = when (turns) {
                    0 -> rotatedX to rotatedY
                    1 -> rotatedY to (source.height - 1 - rotatedX)
                    2 -> (source.width - 1 - rotatedX) to (source.height - 1 - rotatedY)
                    else -> (source.width - 1 - rotatedY) to rotatedX
                }
                pixels[outputY * bounds.width + outputX] = source.argb[sourceY * source.width + sourceX]
            }
        }
        return RasterImage(bounds.width, bounds.height, pixels, source.scaleFromOriginal)
    }
}

internal fun RasterImage.toBitmap(): Bitmap =
    Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)

internal fun Bitmap.toRaster(scaleFromOriginal: Float = 1f): RasterImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return RasterImage(width, height, pixels, scaleFromOriginal)
}
