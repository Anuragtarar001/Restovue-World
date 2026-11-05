package com.restorex.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.restorex.app.domain.model.BatchFormat
import com.restorex.app.domain.model.BatchOptions
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.RasterImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

class AndroidBatchProcessor(private val resolver: ContentResolver, private val processor: AndroidImageProcessor) {
    data class Result(val completed: Int, val failures: List<String>)

    suspend fun process(sources: List<String>, destinationTree: String, options: BatchOptions, edits: EditState, onProgress: (Int, Int) -> Unit): Result = withContext(Dispatchers.IO) {
        require(sources.size in 2..50)
        runSequentialBatch(sources, onProgress) { index, source ->
                coroutineContext.ensureActive()
                val decoded = decode(source, options)
                val rotated = rotate(decoded, options.quarterTurns)
                val batchEdits = EditState(adjustments = edits.adjustments, filter = edits.filter, lut = edits.lut)
                val rendered = processor.renderForExport(rotated, batchEdits) { !coroutineContext.isActive }
                val bitmap = rendered.toBitmap()
                try {
                    val name = "${options.prefix}${(index + 1).toString().padStart(3, '0')}.${options.format.extension}"
                    val treeUri = destinationTree.toUri()
                    val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                    val uri = DocumentsContract.createDocument(resolver, parent, options.format.mimeType, name)
                        ?: error("Cannot create $name")
                    resolver.openOutputStream(uri, "w").use { stream ->
                        requireNotNull(stream)
                        val format = compressFormat(options.format)
                        val encoded = options.targetKilobytes?.let { AndroidTargetSizeEncoder.encode(bitmap, format, options.quality, it) }
                        if (encoded != null) stream.write(encoded.bytes)
                        else check(bitmap.compress(format, if (options.format == BatchFormat.PNG) 100 else options.quality, stream))
                    }
                    if (!options.removeMetadata) copyMetadata(source.toUri(), uri)
                } finally { bitmap.recycle() }
        }
    }

    private fun compressFormat(format: BatchFormat): Bitmap.CompressFormat = when (format) {
        BatchFormat.JPEG -> Bitmap.CompressFormat.JPEG
        BatchFormat.PNG -> Bitmap.CompressFormat.PNG
        BatchFormat.WEBP -> Bitmap.CompressFormat.WEBP
    }

    private fun copyMetadata(source: android.net.Uri, destination: android.net.Uri) {
        val input = resolver.openInputStream(source)?.use(::ExifInterface) ?: return
        resolver.openFileDescriptor(destination, "rw")?.use { descriptor ->
            val output = ExifInterface(descriptor.fileDescriptor)
            listOf(ExifInterface.TAG_DATETIME, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE_REF
            ).forEach { tag -> input.getAttribute(tag)?.let { output.setAttribute(tag, it) } }
            output.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString()); output.saveAttributes()
        }
    }

    private fun decode(uri: String, options: BatchOptions): RasterImage {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri.toUri())) { decoder, info, _ ->
            val sx = options.maxWidth?.let { it.toFloat() / info.size.width } ?: 1f; val sy = options.maxHeight?.let { it.toFloat() / info.size.height } ?: 1f
            val scale = min(1f, min(sx, sy)); if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return bitmap.toRaster().also { bitmap.recycle() }
    }

    private fun rotate(source: RasterImage, turns: Int): RasterImage {
        if (turns == 0) return source
        val input = source.toBitmap(); val matrix = Matrix().apply { postRotate(turns * 90f) }
        val output = Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true); input.recycle()
        return output.toRaster(source.scaleFromOriginal).also { output.recycle() }
    }
}

/** Sequential execution keeps peak image memory bounded and continues after per-item failures. */
internal suspend fun <T> runSequentialBatch(
    items: List<T>,
    onProgress: (completedItems: Int, totalItems: Int) -> Unit,
    action: suspend (index: Int, item: T) -> Unit,
): AndroidBatchProcessor.Result {
    val failures = mutableListOf<String>()
    var completed = 0
    items.forEachIndexed { index, item ->
        currentCoroutineContext().ensureActive()
        try {
            action(index, item)
            completed++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            failures += "Image ${index + 1}: ${error.message ?: "failed"}"
        }
        onProgress(index + 1, items.size)
    }
    return AndroidBatchProcessor.Result(completed, failures)
}
