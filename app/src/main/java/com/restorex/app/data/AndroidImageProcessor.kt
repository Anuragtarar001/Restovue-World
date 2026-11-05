package com.restorex.app.data

import android.graphics.Bitmap
import com.restorex.app.domain.engine.CropCalculator
import com.restorex.app.domain.engine.ImageProcessor
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.RasterImage

class AndroidImageProcessor(
    private val lutStore: LutStore? = null,
    private val backgroundPipeline: AndroidBackgroundPipeline? = null,
    private val creativePipeline: AndroidCreativePipeline? = null,
) : ImageProcessor {
    override fun render(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean): RasterImage {
        val localRaster = CpuLocalEditPipeline.apply(source, edits.localEdits, shouldCancel)
        val geometryRaster = renderGeometry(localRaster, edits, shouldCancel)
        val adjusted = CpuImagePipeline.apply(
            geometryRaster,
            edits.effectiveAdjustments,
            lutStore?.load(edits.lut),
            edits.lut?.intensity ?: 0f,
            shouldCancel,
        )
        val background = edits.localEdits.filterIsInstance<com.restorex.app.domain.model.BackgroundFill>().lastOrNull()
        return creativePipeline?.apply(backgroundPipeline?.apply(adjusted, background) ?: adjusted, edits.creativeLayers, edits.frame)
            ?: (backgroundPipeline?.apply(adjusted, background) ?: adjusted)
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
        val adjusted = CpuImagePipeline.apply(
            geometryRaster,
            edits.effectiveAdjustments,
            lutStore?.load(edits.lut),
            edits.lut?.intensity ?: 0f,
            shouldCancel,
            reuseSourcePixels = true,
        )
        val background = edits.localEdits.filterIsInstance<com.restorex.app.domain.model.BackgroundFill>().lastOrNull()
        return creativePipeline?.apply(backgroundPipeline?.apply(adjusted, background) ?: adjusted, edits.creativeLayers, edits.frame)
            ?: (backgroundPipeline?.apply(adjusted, background) ?: adjusted)
    }

    private fun renderGeometry(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean): RasterImage {
        return CpuGeometryPipeline.apply(source, edits, shouldCancel)
    }
}

internal fun RasterImage.toBitmap(): Bitmap =
    Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)

internal fun Bitmap.toRaster(scaleFromOriginal: Float = 1f): RasterImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return RasterImage(width, height, pixels, scaleFromOriginal)
}
