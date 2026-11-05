package com.restorex.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.ExportFormat
import com.restorex.app.domain.model.ExportOptions
import com.restorex.app.domain.model.ImageSource
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt
import kotlin.math.min

class AndroidImageRepository(
    private val resolver: ContentResolver,
    private val processor: AndroidImageProcessor,
) : ImageRepository {
    override suspend fun load(source: ImageSource, maxDimension: Int): RasterImage = withContext(Dispatchers.IO) {
        require(maxDimension > 0)
        decode(source.value.toUri(), maxDimension = maxDimension).let { decoded ->
            decoded.bitmap.toRaster(decoded.scaleFromOriginal).also { decoded.bitmap.recycle() }
        }
    }

    override suspend fun export(
        source: ImageSource,
        edits: EditState,
        destination: String,
        options: ExportOptions,
    ) = withContext(Dispatchers.IO) {
        val sourceUri = source.value.toUri()
        val destinationUri = destination.toUri()
        val spatiallyExpensive = edits.effectiveAdjustments.run {
            structure != 0f || clarity != 0f || texture != 0f || sharpening != 0f ||
                luminanceNoiseReduction != 0f || colorNoiseReduction != 0f || dehaze != 0f
        }
        val safePixels = ExportMemoryPolicy.safePixelCount(Runtime.getRuntime().maxMemory(), spatiallyExpensive)
        var fullSource: RasterImage? = decode(
            sourceUri,
            maxWidth = options.maxWidth,
            maxHeight = options.maxHeight,
            maxPixels = safePixels,
        ).let { decoded ->
            decoded.bitmap.toRaster(decoded.scaleFromOriginal).also { decoded.bitmap.recycle() }
        }
        val renderContext = currentCoroutineContext()
        val rendered = processor.renderForExport(requireNotNull(fullSource), edits) { !renderContext.isActive }
        fullSource = null
        renderContext.ensureActive()
        val renderedBitmap = rendered.toBitmap()
        val effectiveOptions = options.copy(maxWidth = options.maxWidth ?: edits.cropOutputWidth, maxHeight = options.maxHeight ?: edits.cropOutputHeight)
        val outputBitmap = resizeForExport(renderedBitmap, effectiveOptions)
        try {
            val compressFormat = when (options.format) {
                ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP
            }
            val encoded = if (options.targetKilobytes != null && options.format != ExportFormat.PNG) {
                AndroidTargetSizeEncoder.encode(outputBitmap, compressFormat, options.jpegQuality, options.targetKilobytes)
            } else null
            resolver.openOutputStream(destinationUri, "w").use { stream ->
                requireNotNull(stream) { "Unable to open export destination" }
                if (encoded != null) stream.write(encoded.bytes)
                else check(outputBitmap.compress(compressFormat, if (options.format == ExportFormat.PNG) 100 else options.jpegQuality, stream)) { "Image encoding failed" }
            }
            if (options.preserveMetadata) {
                copyMetadata(sourceUri, destinationUri, encoded?.width ?: outputBitmap.width, encoded?.height ?: outputBitmap.height)
            }
        } catch (cancelled: CancellationException) {
            runCatching { resolver.delete(destinationUri, null, null) }
            throw cancelled
        } finally {
            outputBitmap.recycle()
            if (outputBitmap !== renderedBitmap) renderedBitmap.recycle()
        }
    }

    private fun resizeForExport(bitmap: Bitmap, options: ExportOptions): Bitmap {
        val widthScale = options.maxWidth?.let { it.toFloat() / bitmap.width } ?: 1f
        val heightScale = options.maxHeight?.let { it.toFloat() / bitmap.height } ?: 1f
        val scale = min(1f, min(widthScale, heightScale))
        if (scale >= 0.9999f) return bitmap
        return bitmap.scale(
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun copyMetadata(source: Uri, destination: Uri, width: Int, height: Int) {
        val sourceExif = resolver.openInputStream(source)?.use(::ExifInterface) ?: return
        resolver.openFileDescriptor(destination, "rw")?.use { descriptor ->
            val outputExif = ExifInterface(descriptor.fileDescriptor)
            RECOGNIZED_EXIF_TAGS.forEach { tag ->
                sourceExif.getAttribute(tag)?.let { value ->
                    runCatching { outputExif.setAttribute(tag, value) }
                }
            }
            outputExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            outputExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, width.toString())
            outputExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, height.toString())
            outputExif.saveAttributes()
        }
    }

    private fun decode(
        uri: Uri,
        maxDimension: Int = Int.MAX_VALUE,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        maxPixels: Long = Long.MAX_VALUE,
    ): DecodedBitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        var originalLargest = 1
        var originalWidth = 1
        var originalHeight = 1
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            originalWidth = width
            originalHeight = height
            val largest = maxOf(width, height)
            originalLargest = largest
            val dimensionScale = if (largest > maxDimension) maxDimension.toFloat() / largest else 1f
            val widthScale = maxWidth?.let { it.toFloat() / width } ?: 1f
            val heightScale = maxHeight?.let { it.toFloat() / height } ?: 1f
            val scale = minOf(1f, dimensionScale, widthScale, heightScale)
            val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
            ExportMemoryPolicy.requireSafe(targetWidth, targetHeight, maxPixels)
            if (scale < .9999f) {
                decoder.setTargetSize(
                    targetWidth,
                    targetHeight,
                )
            }
        }
        PerformanceDiagnostics.logDecode(bitmap, originalWidth, originalHeight)
        return DecodedBitmap(bitmap, maxOf(bitmap.width, bitmap.height).toFloat() / originalLargest)
    }

    private data class DecodedBitmap(val bitmap: Bitmap, val scaleFromOriginal: Float)

    private companion object {
        val RECOGNIZED_EXIF_TAGS: List<String> by lazy {
            ExifInterface::class.java.fields.mapNotNull { field ->
                if (field.name.startsWith("TAG_") && field.type == String::class.java) {
                    field.get(null) as? String
                } else null
            }.distinct()
        }
    }
}
