package com.restorex.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class SmartMagicExportResult(
    val uri: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val elapsedMillis: Long,
)

/** Full-resolution, safely finalized MediaStore export for the dedicated one-tap workflow. */
class SmartMagicExportManager(
    private val resolver: ContentResolver,
    private val pipeline: SmartMagicPipeline,
) {
    suspend fun export(
        source: String,
        parameters: SmartMagicParameters,
        onStage: (String) -> Unit = {},
    ): SmartMagicExportResult = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Direct gallery saving requires Android 10 or newer. Use the editor export workflow on Android 9."
        }
        val started = System.nanoTime()
        onStage("Opening full-resolution photo")
        val bitmap = decodeFullResolution(source.toUri())
        var raster: RasterImage? = bitmap.toRaster(1f)
        bitmap.recycle()
        currentCoroutineContext().ensureActive()
        val hasTransparency = requireNotNull(raster).argb.any { it ushr 24 != 255 }
        onStage("Applying full-resolution enhancement")
        val context = currentCoroutineContext()
        val enhanced = pipeline.enhance(requireNotNull(raster), parameters, { !context.isActive }, reuseSourcePixels = true)
        raster = null
        context.ensureActive()
        val format = if (hasTransparency) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val mimeType = if (hasTransparency) "image/png" else "image/jpeg"
        val extension = if (hasTransparency) "png" else "jpg"
        val displayName = "Restovue_SmartMagic_${LocalDateTime.now().format(NAME_FORMAT)}.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Restovue")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.WIDTH, enhanced.width)
            put(MediaStore.Images.Media.HEIGHT, enhanced.height)
        }
        val destination = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Android could not create a gallery destination"
        }
        try {
            onStage("Saving to gallery")
            val output = enhanced.toBitmap()
            try {
                resolver.openOutputStream(destination, "w").use { stream ->
                    requireNotNull(stream) { "Android could not open the gallery destination" }
                    check(output.compress(format, if (hasTransparency) 100 else 95, stream)) { "Image encoding failed" }
                }
            } finally { output.recycle() }
            context.ensureActive()
            resolver.update(destination, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            SmartMagicExportResult(
                destination.toString(), displayName, enhanced.width, enhanced.height, mimeType,
                (System.nanoTime() - started) / 1_000_000L,
            )
        } catch (cancelled: CancellationException) {
            resolver.delete(destination, null, null); throw cancelled
        } catch (error: Throwable) {
            resolver.delete(destination, null, null); throw error
        }
    }

    private fun decodeFullResolution(uri: Uri): Bitmap {
        val safePixels = (Runtime.getRuntime().maxMemory() * .65 / ESTIMATED_PEAK_BYTES_PER_PIXEL).toLong()
            .coerceAtLeast(2_000_000L)
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            ExportMemoryPolicy.requireSafe(info.size.width, info.size.height, safePixels)
        }
    }

    private companion object {
        const val ESTIMATED_PEAK_BYTES_PER_PIXEL = 13.0
        val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}
