package com.restorex.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.restorex.app.domain.model.BackgroundFill
import com.restorex.app.domain.model.BackgroundFillMode
import com.restorex.app.domain.model.RasterImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Composites an imported background under transparent foreground pixels with bounded decoding. */
class AndroidBackgroundPipeline(private val resolver: ContentResolver) {
    fun apply(source: RasterImage, fill: BackgroundFill?): RasterImage {
        if (fill?.mode != BackgroundFillMode.IMAGE || fill.imageUri == null) return source
        val background = decodeBounded(fill.imageUri, max(source.width, source.height)) ?: return source
        val softened = if (fill.blur > .01f) approximateBlur(background, fill.blur) else background
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cover = max(source.width.toFloat() / softened.width, source.height.toFloat() / softened.height) * fill.imageScale
        val drawWidth = softened.width * cover; val drawHeight = softened.height * cover
        val centerX = source.width * (.5f + fill.offsetX * .5f); val centerY = source.height * (.5f + fill.offsetY * .5f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (fill.opacity * 255).roundToInt() }
        canvas.drawBitmap(softened, null, RectF(centerX - drawWidth / 2f, centerY - drawHeight / 2f, centerX + drawWidth / 2f, centerY + drawHeight / 2f), paint)
        val foreground = source.toBitmap(); canvas.drawBitmap(foreground, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        foreground.recycle(); if (softened !== background) softened.recycle(); background.recycle()
        return output.toRaster(source.scaleFromOriginal).also { output.recycle() }
    }

    private fun decodeBounded(uri: String, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDimension * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return resolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun approximateBlur(source: Bitmap, amount: Float): Bitmap {
        val factor = (1f - amount * .9f).coerceIn(.08f, .95f)
        val small = source.scale((source.width * factor).roundToInt().coerceAtLeast(1), (source.height * factor).roundToInt().coerceAtLeast(1))
        return small.scale(source.width, source.height).also { small.recycle() }
    }
}
