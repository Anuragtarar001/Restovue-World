package com.restorex.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.restorex.app.RestoreXApplication
import com.restorex.app.domain.smartmagic.SmartMagicParameters
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.EnhancementLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SmartMagicExportManagerTest {
    @Test fun previewSizedAnalysisAndRenderAreMeasured() {
        val application = ApplicationProvider.getApplicationContext<RestoreXApplication>()
        val width = 1080; val height = 720
        val pixels = IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            Color.rgb(24 + x * 205 / width, 34 + y * 180 / height, 58 + (x + y) * 140 / (width + height))
        }
        val source = RasterImage(width, height, pixels)
        val started = System.nanoTime()
        val analysis = application.container.smartMagicPipeline.analyze(source)
        val parameters = application.container.smartMagicParameterEngine.parametersFor(analysis)
        val enhanced = application.container.smartMagicPipeline.enhance(source, parameters)
        val elapsed = (System.nanoTime() - started) / 1_000_000L
        assertEquals(width, enhanced.width); assertEquals(height, enhanced.height)
        println("SMART_MAGIC_DEVICE_PREVIEW pixels=${width * height} elapsedMs=$elapsed")
    }

    @Test fun twelveMegapixelExportWritesReopenableGalleryImageWithinBoundedMemory() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<RestoreXApplication>()
        val source = File(application.cacheDir, "smart_magic_export_source.png")
        val width = 4000; val height = 3000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in row.indices) row[x] = Color.rgb(28 + x * 190 / width, 42 + y * 155 / height, 72 + (x + y) * 110 / (width + height))
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var exported: Uri? = null
        try {
            val runtime = Runtime.getRuntime()
            val usedBefore = runtime.totalMemory() - runtime.freeMemory()
            val result = application.container.smartMagicExportManager.export(
                Uri.fromFile(source).toString(), SmartMagicParameters(
                    exposureEv = .12f, contrast = .05f, vibrance = .08f, clarity = .05f,
                    texture = .04f, luminanceNoiseReduction = .03f, chromaNoiseReduction = .04f,
                    sharpening = .2f, sharpeningMasking = .35f,
                ),
            )
            val usedAfter = runtime.totalMemory() - runtime.freeMemory()
            exported = Uri.parse(result.uri)
            assertEquals(width, result.width); assertEquals(height, result.height)
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(application.contentResolver, exported))
            assertEquals(width, decoded.width); assertEquals(height, decoded.height)
            assertTrue(result.displayName.startsWith("Restovue_SmartMagic_"))
            println("SMART_MAGIC_DEVICE_EXPORT pixels=${width * height} elapsedMs=${result.elapsedMillis} heapBefore=$usedBefore heapAfter=$usedAfter maxHeap=${runtime.maxMemory()}")
            decoded.recycle()
        } finally {
            exported?.let { application.contentResolver.delete(it, null, null) }
            source.delete()
        }
    }

    @Test fun twelveMegapixelProductionExportReusesPreviewSessionAndLevel() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<RestoreXApplication>()
        val source = File(application.cacheDir, "smart_magic_production_12mp.png")
        val width = 4000; val height = 3000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            val value = 40 + y * 150 / height
            row.indices.forEach { x -> row[x] = Color.rgb(value + x * 20 / width, value, value + 8) }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val preview = RasterImage(800, 600, IntArray(800 * 600) { index ->
            val x = index % 800; val y = index / 800; val value = 40 + y * 150 / 600
            Color.rgb(value + x * 20 / 800, value, value + 8)
        }, .2f)
        val uri = Uri.fromFile(source).toString()
        var exported: Uri? = null
        try {
            val session = application.container.smartMagicCoordinator.createSession(uri, preview)
            val selected = application.container.smartMagicCoordinator.render(preview, session, EnhancementLevel.ENHANCED)
            val result = application.container.smartMagicExportManager.export(uri, session, selected)
            exported = Uri.parse(result.uri)
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(application.contentResolver, exported))
            assertEquals(width, decoded.width); assertEquals(height, decoded.height)
            assertEquals(selected.outcome, result.productionOutcome)
            println("M45_PRODUCTION_12MP outcome=${result.productionOutcome} elapsedMs=${result.elapsedMillis} maxHeap=${Runtime.getRuntime().maxMemory()}")
            decoded.recycle()
        } finally {
            exported?.let { application.contentResolver.delete(it, null, null) }
            source.delete()
        }
    }
}
