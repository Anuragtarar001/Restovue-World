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
import com.restorex.app.domain.photointelligence.ProductionRenderOutcome
import com.restorex.app.domain.photointelligence.SmartMagicProductionSession
import com.restorex.app.domain.photointelligence.SmartMagicProductionRender
import com.restorex.app.domain.photointelligence.SmartMagicWorkStage
import com.restorex.app.domain.photointelligence.SmartMagicWorkState
import com.restorex.app.domain.photointelligence.SmartMagicWorkUpdate
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
    val productionOutcome: ProductionRenderOutcome? = null,
    val decodeMillis: Long = 0,
    val renderMillis: Long = 0,
    val encodeAndSaveMillis: Long = 0,
)

/** Full-resolution, safely finalized MediaStore export for the dedicated one-tap workflow. */
class SmartMagicExportManager(
    private val resolver: ContentResolver,
    private val pipeline: SmartMagicPipeline,
    private val coordinator: SmartMagicProcessingCoordinator,
) {
    suspend fun export(
        source: String,
        session: SmartMagicProductionSession,
        preview: SmartMagicProductionRender,
        onProgress: (SmartMagicWorkUpdate) -> Unit = {},
    ): SmartMagicExportResult = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Direct gallery saving requires Android 10 or newer. Use the editor export workflow on Android 9."
        }
        require(source == session.sourceIdentity) { "Smart Magic session belongs to a different photograph" }
        val started = System.nanoTime()
        var stageStarted = System.nanoTime()
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.PREPARING_EXPORT, SmartMagicWorkState.STARTED,
            detail = "Opening full-resolution photograph"))
        val raster = decodeRaster(source.toUri())
        val decodeMillis = (System.nanoTime() - stageStarted) / 1_000_000L
        val hasTransparency = raster.argb.any { it ushr 24 != 255 }
        currentCoroutineContext().ensureActive()
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.PREPARING_EXPORT, SmartMagicWorkState.COMPLETED,
            detail = "Full-resolution photograph prepared"))
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.RENDERING_EXPORT, SmartMagicWorkState.STARTED,
            detail = "Applying ${preview.level.name.lowercase()} correction"))
        val context = currentCoroutineContext()
        stageStarted = System.nanoTime()
        val rendered = coordinator.renderForExport(raster, session, preview) { !context.isActive }
        val renderMillis = (System.nanoTime() - stageStarted) / 1_000_000L
        context.ensureActive()
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.RENDERING_EXPORT, SmartMagicWorkState.COMPLETED,
            detail = "Full-resolution correction rendered"))
        stageStarted = System.nanoTime()
        saveRendered(rendered.image, hasTransparency, started, rendered.outcome) { detail ->
            onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.ENCODING_AND_SAVING,
                SmartMagicWorkState.ACTIVE, detail = detail))
        }.copy(
            decodeMillis = decodeMillis,
            renderMillis = renderMillis,
            encodeAndSaveMillis = (System.nanoTime() - stageStarted) / 1_000_000L,
        ).also {
            onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.FINALIZING, SmartMagicWorkState.COMPLETED,
                detail = "Saved to gallery"))
        }
    }

    /** Legacy global-only entry retained for regression tests and callers outside production Smart Magic. */
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
        var raster: RasterImage? = decodeRaster(source.toUri())
        currentCoroutineContext().ensureActive()
        val hasTransparency = requireNotNull(raster).argb.any { it ushr 24 != 255 }
        onStage("Applying full-resolution enhancement")
        val context = currentCoroutineContext()
        val enhanced = pipeline.enhance(requireNotNull(raster), parameters, { !context.isActive }, reuseSourcePixels = true)
        raster = null
        context.ensureActive()
        saveRendered(enhanced, hasTransparency, started, null, onStage)
    }

    private suspend fun saveRendered(
        enhanced: RasterImage,
        hasTransparency: Boolean,
        started: Long,
        outcome: ProductionRenderOutcome?,
        onStage: (String) -> Unit,
    ): SmartMagicExportResult {
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
        return try {
            onStage("Saving to gallery")
            val output = enhanced.toBitmap()
            try {
                resolver.openOutputStream(destination, "w").use { stream ->
                    requireNotNull(stream) { "Android could not open the gallery destination" }
                    check(output.compress(format, if (hasTransparency) 100 else 95, stream)) { "Image encoding failed" }
                }
            } finally { output.recycle() }
            currentCoroutineContext().ensureActive()
            resolver.update(destination, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            SmartMagicExportResult(
                destination.toString(), displayName, enhanced.width, enhanced.height, mimeType,
                (System.nanoTime() - started) / 1_000_000L, outcome,
            )
        } catch (cancelled: CancellationException) {
            resolver.delete(destination, null, null); throw cancelled
        } catch (error: Throwable) {
            resolver.delete(destination, null, null); throw error
        }
    }

    private fun decodeRaster(uri: Uri): RasterImage {
        val bitmap = decodeFullResolution(uri)
        return try { bitmap.toRaster(1f) } finally { bitmap.recycle() }
    }

    private fun decodeFullResolution(uri: Uri): Bitmap {
        // The decoded Bitmap is recycled immediately after the owned RasterImage is created. The
        // production coordinator then keeps at most source + one full-size output for regional
        // rendering; spatial preview-only stages are not retained during export.
        val safePixels = ExportMemoryPolicy.safePixelCount(Runtime.getRuntime().maxMemory(), spatiallyExpensive = false)
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            ExportMemoryPolicy.requireSafe(info.size.width, info.size.height, safePixels)
        }
    }

    private companion object {
        val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}
