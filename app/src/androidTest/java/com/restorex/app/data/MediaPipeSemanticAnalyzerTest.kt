package com.restorex.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.CompositionReasoningEngine
import com.restorex.app.domain.photointelligence.PhotometricAnalyzerV2
import com.restorex.app.domain.photointelligence.SemanticClass
import com.restorex.app.domain.photointelligence.SemanticCompositionEngine
import com.restorex.app.domain.photointelligence.SemanticRegionPhotometricAnalyzer
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiteRtSemanticAnalyzerTest {
    @Test fun bundledModelsRunOnPublicDomainPortraitAndProduceAlignedSoftMask() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val bitmap = requireNotNull(testContext.assets.open("public_domain_portrait.jpg").use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 4 })
        })
        val raster = bitmap.toRaster()
        val managedBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        LiteRtSemanticAnalyzer(context).use { analyzer ->
            val cold = analyzer.analyze(raster) { false }
            assertNull(cold.failureReason)
            assertTrue(cold.faces.isNotEmpty())
            assertTrue(cold.personMask != null)
            assertTrue(requireNotNull(cold.personMaskReference).quality.weightedAreaFraction in .02f.. .92f)
            assertTrue(SemanticClass.PERSON in cold.supportedClasses)
            val warm = analyzer.analyze(raster) { false }
            assertNull(warm.failureReason)
            val regional = SemanticRegionPhotometricAnalyzer().analyze(raster, requireNotNull(cold.personMask))
            assertTrue(regional.person.effectiveWeight > 0f && regional.background.effectiveWeight > 0f)
            val baseGraph = CompositionReasoningEngine().build(PhotometricAnalyzerV2().analyze(raster), emptyList())
            val graph = SemanticCompositionEngine().integrate(baseGraph, cold, regional)
            val overlay = SemanticDiagnosticRenderer.render(bitmap, cold, graph)
            val file = File(requireNotNull(context.getExternalFilesDir(null)), "milestone3-semantic-overlay.png")
            FileOutputStream(file).use { overlay.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.i("RestovueM3", "coldMs=${cold.inferenceDurationNanos / 1_000_000} warmMs=${warm.inferenceDurationNanos / 1_000_000} " +
                "faces=${cold.faces.size} mask=${cold.personMask?.width}x${cold.personMask?.height} quality=${cold.personMaskReference?.quality} " +
                "managedDelta=${Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - managedBefore} " +
                "nativeDelta=${Debug.getNativeHeapAllocatedSize() - nativeBefore} overlay=${file.absolutePath}")
            overlay.recycle()
        }
        bitmap.recycle()
    }

    @Test fun noPersonInputDoesNotFabricateFace() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val raster = RasterImage(256, 256, IntArray(256 * 256) { 0xff2a4050.toInt() })
        LiteRtSemanticAnalyzer(context).use { analyzer ->
            val result = analyzer.analyze(raster) { false }
            assertNull(result.failureReason)
            assertTrue(result.faces.isEmpty())
            assertTrue(result.personMaskReference != null)
            assertTrue(result.personMaskReference?.quality?.accepted == false)
        }
    }

    @Test fun missingModelIsReportedWithoutGuessedMask() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val raster = RasterImage(8, 8, IntArray(64) { 0xff808080.toInt() })
        LiteRtSemanticAnalyzer(context, faceAssetPath = "models/missing.tflite").use { analyzer ->
            val result = analyzer.analyze(raster) { false }
            assertEquals(com.restorex.app.domain.photointelligence.SemanticFailureReason.INFERENCE_FAILED, result.failureReason)
            assertTrue(result.personMask == null && result.faces.isEmpty())
        }
    }

    @Test fun cancellationStopsBeforeInference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val raster = RasterImage(8, 8, IntArray(64))
        try {
            LiteRtSemanticAnalyzer(context).use { it.analyze(raster) { true } }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected: cancellation is propagated and cannot become a stale semantic result.
        }
    }

    private fun Bitmap.toRaster(): RasterImage {
        val pixels = IntArray(width * height); getPixels(pixels, 0, width, 0, 0, width, height)
        return RasterImage(width, height, pixels)
    }
}
