package com.restorex.app.data

import android.graphics.Bitmap
import android.graphics.Matrix
import com.restorex.app.domain.engine.CropCalculator
import com.restorex.app.domain.engine.ImageProcessor
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.RasterImage

class AndroidImageProcessor(private val lutStore: LutStore? = null) : ImageProcessor {
    override fun render(source: RasterImage, edits: EditState): RasterImage {
        val geometryRaster = renderGeometry(source, edits)
        return CpuImagePipeline.apply(
            geometryRaster,
            edits.effectiveAdjustments,
            lutStore?.load(edits.lut),
            edits.lut?.intensity ?: 0f,
        )
    }

    override fun renderSharpenMask(source: RasterImage, edits: EditState): RasterImage =
        CpuImagePipeline.sharpeningMask(renderGeometry(source, edits), edits.effectiveAdjustments)

    private fun renderGeometry(source: RasterImage, edits: EditState): RasterImage {
        val sourceBitmap = source.toBitmap()
        val rotated = if (edits.quarterTurnsClockwise == 0) {
            sourceBitmap
        } else {
            Bitmap.createBitmap(
                sourceBitmap,
                0,
                0,
                sourceBitmap.width,
                sourceBitmap.height,
                Matrix().apply { postRotate(edits.quarterTurnsClockwise * 90f) },
                true,
            ).also { sourceBitmap.recycle() }
        }

        val bounds = CropCalculator.centered(rotated.width, rotated.height, edits.cropAspect)
        val cropped = if (bounds.width == rotated.width && bounds.height == rotated.height) {
            rotated
        } else {
            Bitmap.createBitmap(rotated, bounds.left, bounds.top, bounds.width, bounds.height)
                .also { rotated.recycle() }
        }

        return cropped.toRaster(source.scaleFromOriginal).also { cropped.recycle() }
    }
}

internal fun RasterImage.toBitmap(): Bitmap =
    Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)

internal fun Bitmap.toRaster(scaleFromOriginal: Float = 1f): RasterImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return RasterImage(width, height, pixels, scaleFromOriginal)
}
