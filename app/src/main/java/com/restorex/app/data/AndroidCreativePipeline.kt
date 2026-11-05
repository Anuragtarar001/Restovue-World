package com.restorex.app.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.core.net.toUri
import android.util.LruCache
import com.restorex.app.domain.model.CreativeLayer
import com.restorex.app.domain.model.FrameSettings
import com.restorex.app.domain.model.FrameStyle
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.model.ShapeLayer
import com.restorex.app.domain.model.ShapeType
import com.restorex.app.domain.model.TextAlignment
import com.restorex.app.domain.model.TextFont
import com.restorex.app.domain.model.TextLayer
import com.restorex.app.domain.model.StickerLayer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Android Canvas-backed deterministic overlay renderer shared by preview and export. */
class AndroidCreativePipeline(private val resolver: ContentResolver) {
    private val stickerCache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) { if (oldValue !== newValue && !oldValue.isRecycled) oldValue.recycle() }
    }
    fun apply(source: RasterImage, layers: List<CreativeLayer>, frame: FrameSettings): RasterImage {
        if (layers.isEmpty() && frame.isNeutral) return source
        val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(source.argb, 0, source.width, 0, 0, source.width, source.height)
        }
        val canvas = Canvas(bitmap)
        layers.sortedWith(compareBy<CreativeLayer> { it.zIndex }.thenBy { it.id }).forEach { layer ->
            when (layer) {
                is TextLayer -> drawText(canvas, source.width, source.height, layer)
                is ShapeLayer -> drawShape(canvas, source.width, source.height, layer)
                is StickerLayer -> drawSticker(canvas, source.width, source.height, layer)
            }
        }
        if (!frame.isNeutral) drawFrame(canvas, source.width, source.height, frame)
        return bitmap.toRaster(source.scaleFromOriginal).also { bitmap.recycle() }
    }

    private fun drawSticker(canvas: Canvas, width: Int, height: Int, layer: StickerLayer) {
        val cx = layer.center.x * width; val cy = layer.center.y * height; val drawWidth = layer.width * width; val drawHeight = layer.height * height
        val target = max(drawWidth, drawHeight).roundToInt().coerceIn(128, 2048); val key = "${layer.uri}|$target"
        var cached = true
        val bitmap = stickerCache[key] ?: run {
            cached = false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(layer.uri.toUri())?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return
            var sample = 1; while (max(bounds.outWidth, bounds.outHeight) / sample > target * 2) sample *= 2
            resolver.openInputStream(layer.uri.toUri())?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return
        }
        if (!cached && bitmap.allocationByteCount <= 8 * 1024 * 1024) { stickerCache.put(key, bitmap); cached = true }
        canvas.save(); canvas.rotate(layer.rotationDegrees, cx, cy)
        canvas.drawBitmap(bitmap, null, RectF(cx - drawWidth / 2f, cy - drawHeight / 2f, cx + drawWidth / 2f, cy + drawHeight / 2f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (layer.opacity * 255).roundToInt() })
        canvas.restore(); if (!cached) bitmap.recycle()
    }

    private fun drawText(canvas: Canvas, width: Int, height: Int, layer: TextLayer) {
        val unit = min(width, height).toFloat()
        val x = layer.center.x * width; val y = layer.center.y * height
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withOpacity(layer.color, layer.opacity)
            textSize = layer.size * unit
            typeface = when (layer.font) {
                TextFont.SANS -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                TextFont.SERIF -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                TextFont.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            textAlign = when (layer.alignment) {
                TextAlignment.LEFT -> Paint.Align.LEFT
                TextAlignment.CENTER -> Paint.Align.CENTER
                TextAlignment.RIGHT -> Paint.Align.RIGHT
            }
            if (layer.shadow > 0f) setShadowLayer(layer.shadow * unit * .04f, unit * .008f, unit * .008f, Color.BLACK)
        }
        val lines = layer.text.lines()
        val lineHeight = paint.fontSpacing
        val top = y - (lines.size - 1) * lineHeight / 2f
        canvas.save(); canvas.rotate(layer.rotationDegrees, x, y)
        layer.backgroundColor?.let { background ->
            val widest = lines.maxOf { paint.measureText(it) }
            canvas.drawRoundRect(RectF(x - widest / 2f - unit * .02f, top - lineHeight, x + widest / 2f + unit * .02f, top + lines.size * lineHeight), unit * .015f, unit * .015f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withOpacity(background, layer.opacity) })
        }
        if (layer.outline > 0f) {
            val fill = paint.color
            paint.style = Paint.Style.STROKE; paint.strokeWidth = max(1f, layer.outline * unit * .02f); paint.color = Color.BLACK
            lines.forEachIndexed { index, line -> canvas.drawText(line, x, top + index * lineHeight, paint) }
            paint.style = Paint.Style.FILL; paint.color = fill
        }
        lines.forEachIndexed { index, line -> canvas.drawText(line, x, top + index * lineHeight, paint) }
        canvas.restore()
    }

    private fun drawShape(canvas: Canvas, width: Int, height: Int, layer: ShapeLayer) {
        val unit = min(width, height).toFloat(); val cx = layer.center.x * width; val cy = layer.center.y * height
        val halfW = layer.width * width / 2f; val halfH = layer.height * height / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withOpacity(layer.color, layer.opacity)
            style = if (layer.filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = max(1f, layer.strokeWidth * unit); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        canvas.save(); canvas.rotate(layer.rotationDegrees, cx, cy)
        when (layer.type) {
            ShapeType.RECTANGLE -> canvas.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, paint)
            ShapeType.CIRCLE -> canvas.drawOval(RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH), paint)
            ShapeType.LINE -> canvas.drawLine(cx - halfW, cy, cx + halfW, cy, paint)
            ShapeType.ARROW -> {
                canvas.drawLine(cx - halfW, cy, cx + halfW, cy, paint)
                val head = min(halfW, halfH.coerceAtLeast(unit * .04f))
                canvas.drawLine(cx + halfW, cy, cx + halfW - head, cy - head * .65f, paint)
                canvas.drawLine(cx + halfW, cy, cx + halfW - head, cy + head * .65f, paint)
            }
            ShapeType.SPEECH_BUBBLE -> {
                canvas.drawRoundRect(RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH * .65f), unit * .03f, unit * .03f, paint)
                val tail = Path().apply { moveTo(cx - halfW * .35f, cy + halfH * .65f); lineTo(cx - halfW * .1f, cy + halfH); lineTo(cx + halfW * .05f, cy + halfH * .65f) }
                canvas.drawPath(tail, paint)
            }
        }
        canvas.restore()
    }

    private fun drawFrame(canvas: Canvas, width: Int, height: Int, frame: FrameSettings) {
        val unit = min(width, height).toFloat(); val thickness = max(1f, frame.thickness * unit)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = frame.color; style = Paint.Style.STROKE; strokeWidth = thickness }
        if (frame.shadow > 0f) paint.setShadowLayer(frame.shadow * unit * .03f, 0f, frame.shadow * unit * .01f, Color.BLACK)
        when (frame.style) {
            FrameStyle.NONE -> Unit
            FrameStyle.SIMPLE -> canvas.drawRect(thickness / 2f, thickness / 2f, width - thickness / 2f, height - thickness / 2f, paint)
            FrameStyle.ROUNDED -> canvas.drawRoundRect(RectF(thickness / 2f, thickness / 2f, width - thickness / 2f, height - thickness / 2f), frame.cornerRadius * unit, frame.cornerRadius * unit, paint)
            FrameStyle.POLAROID -> {
                val fill = Paint().apply { color = frame.color; style = Paint.Style.FILL }
                canvas.drawRect(0f, 0f, width.toFloat(), thickness, fill); canvas.drawRect(0f, 0f, thickness, height.toFloat(), fill)
                canvas.drawRect(width - thickness, 0f, width.toFloat(), height.toFloat(), fill); canvas.drawRect(0f, height - thickness * 2.4f, width.toFloat(), height.toFloat(), fill)
            }
            FrameStyle.FILM -> {
                canvas.drawRect(thickness / 2f, thickness / 2f, width - thickness / 2f, height - thickness / 2f, paint)
                val hole = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
                val step = max(thickness * 1.7f, 8f); var x = thickness
                while (x < width - thickness) { canvas.drawRect(x, thickness * .2f, x + step * .5f, thickness * .7f, hole); canvas.drawRect(x, height - thickness * .7f, x + step * .5f, height - thickness * .2f, hole); x += step }
            }
        }
    }

    private fun withOpacity(color: Int, opacity: Float): Int = (color and 0x00FFFFFF) or (((color ushr 24 and 0xFF) * opacity).toInt().coerceIn(0, 255) shl 24)
}
