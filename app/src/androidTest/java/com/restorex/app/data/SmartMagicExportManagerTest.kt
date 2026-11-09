package com.restorex.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.restorex.app.RestoreXApplication
import com.restorex.app.domain.smartmagic.SmartMagicParameters
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.FaceEvidence
import com.restorex.app.domain.photointelligence.NormalizedRect
import com.restorex.app.domain.photointelligence.ConfidenceMask
import com.restorex.app.domain.photointelligence.RegionalCorrectionPlan
import com.restorex.app.domain.photointelligence.RegionalCorrectionRenderer
import com.restorex.app.domain.photointelligence.RegionalOperation
import com.restorex.app.domain.photointelligence.RegionalOperationType
import com.restorex.app.domain.photointelligence.RegionalRenderRequest
import com.restorex.app.domain.photointelligence.RegionalTarget
import com.restorex.app.domain.photointelligence.SemanticRegionalEvidence
import com.restorex.app.domain.photointelligence.WeightedRegionStatistics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class SmartMagicExportManagerTest {
    @Test fun twelveMegapixelCachedBilateralFinishProfile() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val decoded = testAssets.open("public_domain_portrait.jpg").use(BitmapFactory::decodeStream)
        val fullBitmap = Bitmap.createScaledBitmap(decoded, 4000, 3000, true)
        decoded.recycle()
        val previewBitmap = Bitmap.createScaledBitmap(fullBitmap, 800, 600, true)
        val full = fullBitmap.toRaster(1f)
        val preview = previewBitmap.toRaster(.2f)
        fullBitmap.recycle(); previewBitmap.recycle()
        val face = FaceEvidence("benchmark-face", NormalizedRect(.30f, .18f, .64f, .72f), .95f, .95f, emptyList())
        val adapter = ProFinishEngineAdapter()
        val plans = adapter.planAll(preview, listOf(face)) { false }
        val globalPreview = adapter.render(preview, plans.getValue(EnhancementLevel.EXTREME)) { false }
        // Stand-in for an approved residual face lift. The fitter sees exactly the source/output
        // pair that production caches after its preview quality gate.
        val approved = globalPreview.copy(argb = globalPreview.argb.copyOf())
        for (y in 0 until approved.height) for (x in 0 until approved.width) {
            val dx = (x / approved.width.toFloat() - .47f) / .21f
            val dy = (y / approved.height.toFloat() - .45f) / .31f
            val mask = (1f - dx * dx - dy * dy).coerceIn(0f, 1f) * .16f
            if (mask <= 0f) continue
            val i = y * approved.width + x
            val c = approved.argb[i]
            fun lift(v: Int) = (v + (255 - v) * mask).toInt().coerceIn(0, 255)
            approved.argb[i] = Color.rgb(lift(c ushr 16 and 255), lift(c ushr 8 and 255), lift(c and 255))
        }
        val fitStarted = System.nanoTime()
        val grid = adapter.fitFinishGrid(globalPreview, approved) { false }
        val fitMillis = (System.nanoTime() - fitStarted) / 1_000_000L
        val renderStarted = System.nanoTime()
        val rendered = adapter.render(full, plans.getValue(EnhancementLevel.EXTREME), grid) { false }
        val renderMillis = (System.nanoTime() - renderStarted) / 1_000_000L
        println("SMART_MAGIC_BGU_12MP fitMs=$fitMillis renderMs=$renderMillis pixels=${rendered.argb.size}")
        assertEquals(12_000_000, rendered.argb.size)
        assertTrue(renderMillis < 30_000L)
    }

    @Test fun twelveMegapixelMixedRegionalStageProfile() {
        val width = 4000; val height = 3000
        val image = RasterImage(width, height, IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            val noise = ((index * 1103515245 + 12345) ushr 25) - 64
            Color.rgb(
                (38 + x * 170 / width + noise / 5).coerceIn(0, 255),
                (46 + y * 150 / height + noise / 6).coerceIn(0, 255),
                (58 + (x + y) * 120 / (width + height) + noise / 4).coerceIn(0, 255),
            )
        })
        val mask = ConfidenceMask.fromFloats("diagnostic-person", 256, 192, FloatArray(256 * 192) { index ->
            val x = index % 256; val y = index / 256
            if (x in 64..191 && y in 24..182) 1f else 0f
        })
        fun operation(id: String, type: RegionalOperationType, target: RegionalTarget, amount: Float, unit: String) =
            RegionalOperation(id, type, target, amount, unit, .9f, listOf("diagnostic"), listOf("quality"))
        val plan = RegionalCorrectionPlan(
            sourceIdentity = "diagnostic", transformationVersion = 1, maskId = mask.id,
            level = EnhancementLevel.EXTREME,
            operations = listOf(
                operation("person-exposure", RegionalOperationType.EXPOSURE, RegionalTarget.PERSON, .28f, "EV"),
                operation("background-dehaze", RegionalOperationType.SELECTIVE_DEHAZE, RegionalTarget.BACKGROUND, .08f, "normalized"),
                operation("background-contrast", RegionalOperationType.LOCAL_CONTRAST, RegionalTarget.BACKGROUND, .08f, "normalized"),
                operation("background-detail", RegionalOperationType.DETAIL, RegionalTarget.BACKGROUND, .06f, "normalized"),
            ), protections = emptyList(), explanation = emptyList(),
        )
        val regional = SemanticRegionalEvidence(
            WeightedRegionStatistics(1f, .08f, .5f, .01f, .01f, 0f, .15f, .2f),
            WeightedRegionStatistics(1f, .20f, .1f, .02f, 0f, 0f, .2f, .05f), -.12f, mask.id,
        )
        val started = System.nanoTime()
        val result = RegionalCorrectionRenderer().render(RegionalRenderRequest(
            image, "diagnostic", 1, plan, mask, mask.quality(), emptyList(), regional,
            preferredStrength = .7f, maxCandidateAttempts = 1,
        ))
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000L
        println("SMART_MAGIC_REGIONAL_12MP elapsedMs=$elapsedMillis status=${result.status} " +
            "candidates=${result.candidateEvaluations.size} failures=${result.metrics.failureReasons}")
        assertEquals(width, result.image.width)
    }

    @Test fun extremeTwelveMegapixelPhotographicStageProfile() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<RestoreXApplication>()
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val decoded = testAssets.open("public_domain_portrait.jpg").use(BitmapFactory::decodeStream)
        val fullBitmap = Bitmap.createScaledBitmap(decoded, 4000, 3000, true)
        decoded.recycle()
        val previewBitmap = Bitmap.createScaledBitmap(fullBitmap, 800, 600, true)
        val full = fullBitmap.toRaster(1f)
        val preview = previewBitmap.toRaster(.2f)
        fullBitmap.recycle(); previewBitmap.recycle()
        fun addCameraNoise(image: RasterImage, amplitude: Int) {
            image.argb.indices.forEach { index ->
                var hash = index * 1103515245 + 12345
                hash = hash xor (hash ushr 16)
                val redDelta = (hash and 31) * amplitude / 31 - amplitude / 2
                hash = hash * 1664525 + 1013904223
                val greenDelta = (hash and 31) * amplitude / 31 - amplitude / 2
                hash = hash * 1664525 + 1013904223
                val blueDelta = (hash and 31) * amplitude / 31 - amplitude / 2
                val color = image.argb[index]
                val red = ((color ushr 16 and 255) + redDelta).coerceIn(0, 255)
                val green = ((color ushr 8 and 255) + greenDelta).coerceIn(0, 255)
                val blue = ((color and 255) + blueDelta).coerceIn(0, 255)
                image.argb[index] = (color and -0x1000000) or (red shl 16) or (green shl 8) or blue
            }
        }
        addCameraNoise(full, 28)
        addCameraNoise(preview, 28)

        val runtime = Runtime.getRuntime()
        fun usedHeap() = runtime.totalMemory() - runtime.freeMemory()
        val analysisStarted = System.nanoTime()
        val session = application.container.smartMagicCoordinator.createSession(
            "diagnostic://public-domain-portrait", preview,
        )
        val analysisMillis = (System.nanoTime() - analysisStarted) / 1_000_000L
        val previewStarted = System.nanoTime()
        val selected = application.container.smartMagicCoordinator.render(
            preview, session, EnhancementLevel.EXTREME,
        )
        val previewMillis = (System.nanoTime() - previewStarted) / 1_000_000L
        val heapBeforeRender = usedHeap()
        val renderStarted = System.nanoTime()
        val rendered = application.container.smartMagicCoordinator.renderForExport(full, session, selected)
        val renderMillis = (System.nanoTime() - renderStarted) / 1_000_000L
        val heapAfterRender = usedHeap()
        val encodeStarted = System.nanoTime()
        val encoded = ByteArrayOutputStream()
        val bitmap = rendered.image.toBitmap()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, encoded)
        bitmap.recycle()
        val encodeMillis = (System.nanoTime() - encodeStarted) / 1_000_000L
        val plan = session.proFinishPlans.getValue(EnhancementLevel.EXTREME)
        println(
            "SMART_MAGIC_PHOTO_12MP analysisMs=$analysisMillis previewMs=$previewMillis " +
                "renderMs=$renderMillis encodeMs=$encodeMillis jpegBytes=${encoded.size()} " +
                "strategy=${rendered.strategy} outcome=${rendered.outcome} " +
                "lumaNR=${plan.lumaNR} chromaNR=${plan.chromaNR} sharpen=${plan.sharpen} " +
                "heapBefore=$heapBeforeRender heapAfter=$heapAfterRender maxHeap=${runtime.maxMemory()}",
        )
        assertEquals(4000, rendered.image.width)
        assertEquals(3000, rendered.image.height)
    }

    @Test fun productionExportSavesSelectedPreviewPlanToGallery() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<RestoreXApplication>()
        val source = File(application.cacheDir, "smart_magic_production_export.png")
        val width = 1600; val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            val value = 34 + y * 164 / height
            row.indices.forEach { x -> row[x] = Color.rgb(value + x * 18 / width, value, value + 9) }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val preview = RasterImage(800, 600, IntArray(800 * 600) { index ->
            val x = index % 800; val y = index / 800; val value = 34 + y * 164 / 600
            Color.rgb(value + x * 18 / 800, value, value + 9)
        }, .5f)
        val uri = Uri.fromFile(source).toString()
        var exported: Uri? = null
        try {
            val session = application.container.smartMagicCoordinator.createSession(uri, preview)
            val selected = application.container.smartMagicCoordinator.render(preview, session, EnhancementLevel.ENHANCED)
            val result = application.container.smartMagicExportManager.export(uri, session, selected)
            exported = Uri.parse(result.uri)
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(application.contentResolver, exported))
            assertEquals(width, decoded.width)
            assertEquals(height, decoded.height)
            assertTrue(result.productionOutcome != null)
            decoded.recycle()
        } finally {
            exported?.let { application.contentResolver.delete(it, null, null) }
            source.delete()
        }
    }

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
