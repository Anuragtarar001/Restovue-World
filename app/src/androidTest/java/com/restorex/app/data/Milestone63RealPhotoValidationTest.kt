package com.restorex.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.restorex.app.RestoreXApplication
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticReport
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.FaceEvidence
import com.restorex.app.domain.photointelligence.SmartMagicProductionRender
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Owner photographs are injected into app-private storage and never bundled or committed. */
@RunWith(AndroidJUnit4::class)
class Milestone63RealPhotoValidationTest {
    private val app = ApplicationProvider.getApplicationContext<RestoreXApplication>()

    @Test fun unifiedPipelineProcessesAllOwnerPhotographs() = runBlocking {
        val input = File(app.filesDir, "m63-input")
        val requestedCase = InstrumentationRegistry.getArguments().getString("photoCase")
        val cases = listOf(
            Case("sunrise", "orig1.jpeg", "origfixed.jpeg"),
            Case("papa", "papa-original.jpeg", "papa-extreme.jpeg"),
            Case("wedding", "wedding.jpeg", "wedding-extreme.jpeg"),
        ).filter { requestedCase == null || it.id == requestedCase }
        assumeTrue("Unknown requested photo case", cases.isNotEmpty())
        assumeTrue("Private M6.3 photographs were not injected", cases.all {
            File(input, it.original).isFile && File(input, it.previousExtreme).isFile
        })
        val root = File(requireNotNull(app.getExternalFilesDir(null)), "milestone6-3").apply {
            deleteRecursively(); mkdirs()
        }
        val coordinator = app.container.smartMagicCoordinator
        cases.forEachIndexed { caseIndex, case ->
            val source = decode(File(input, case.original))
            val oldExtreme = decode(File(input, case.previousExtreme))
            assertEquals(source.width, oldExtreme.width); assertEquals(source.height, oldExtreme.height)
            val analysis = scale(source, coordinator.processingBudget.analysisMaxDimension)
            val previewSource = scale(source, coordinator.processingBudget.previewMaxDimension)
            val start = System.nanoTime()
            val session = coordinator.createSession("private:m63:${case.id}", analysis)
            val analysisMs = (System.nanoTime() - start) / 1_000_000.0
            val renders = linkedMapOf<EnhancementLevel, SmartMagicProductionRender>()
            val reports = linkedMapOf<EnhancementLevel, AdjustmentDiagnosticReport>()
            EnhancementLevel.entries.forEachIndexed { modeIndex, level ->
                val preview = coordinator.render(previewSource, session, level)
                renders[level] = coordinator.renderForExport(source, session, preview)
                reports[level] = coordinator.adjustmentDiagnosticReport(
                    session, renders.getValue(level), caseIndex + 1L, caseIndex * 10L + modeIndex + 1L)
            }
            val output = File(root, case.id).apply { mkdirs() }
            save(source, File(output, "original.png")); save(oldExtreme, File(output, "previous_extreme.png"))
            renders.forEach { (level, render) ->
                save(render.image, File(output, "${level.name.lowercase()}.png"))
                save(difference(source, render.image), File(output, "difference_${level.name.lowercase()}.png"))
            }
            save(contactSheet(source, oldExtreme, renders), File(output, "contact_sheet.png"))
            File(output, "validation.json").writeText(reportJson(case, source, oldExtreme, session.semanticResult.faces,
                renders, reports, analysisMs).toString(2))
            assertTrue(reports.values.all { it.entries.size == 29 })
            assertTrue(renders.values.all { it.image.width == source.width && it.image.height == source.height })
        }
    }

    private fun reportJson(
        case: Case, source: RasterImage, oldExtreme: RasterImage, faces: List<FaceEvidence>,
        renders: Map<EnhancementLevel, SmartMagicProductionRender>,
        reports: Map<EnhancementLevel, AdjustmentDiagnosticReport>, analysisMs: Double,
    ) = JSONObject().apply {
        put("case", case.id); put("source", case.original); put("width", source.width); put("height", source.height)
        put("analysisMs", analysisMs); put("faceCount", faces.size)
        put("previousExtreme", comparison(source, oldExtreme))
        put("faces", JSONArray(faces.map { face -> JSONObject().apply {
            put("id", face.id); put("bounds", JSONArray(listOf(face.bounds.left, face.bounds.top, face.bounds.right, face.bounds.bottom)))
            put("originalMeanLinearLuminance", faceMean(source, face)); put("oldExtremeMeanLinearLuminance", faceMean(oldExtreme, face))
            put("modes", JSONObject().apply { renders.forEach { (level, render) -> put(level.name, faceMean(render.image, face)) } })
        } }))
        put("modes", JSONObject().apply {
            renders.forEach { (level, render) ->
                put(level.name, JSONObject().apply {
                    put("strategy", render.strategy.name); put("outcome", render.outcome.name)
                    put("renderMs", render.elapsedNanos / 1_000_000.0)
                    put("comparison", comparison(source, render.image))
                    put("regionalStrength", render.regionalResult?.appliedStrength)
                    put("adjustments", JSONArray(reports.getValue(level).entries.map { entry -> JSONObject().apply {
                        put("id", entry.id.name); put("status", entry.status.name); put("region", entry.region)
                        put("reason", entry.decisionReason)
                        put("values", JSONArray(entry.values.map { value -> JSONObject().apply {
                            put("component", value.component); put("neutral", value.neutralValue)
                            put("proposed", value.proposedValue); put("applied", value.appliedValue); put("unit", value.unit)
                        } }))
                    } }))
                })
            }
        })
    }

    private fun comparison(a: RasterImage, b: RasterImage) = JSONObject().apply {
        val impact = com.restorex.app.domain.photointelligence.PerceptualImpactEstimator().estimate(a, b)
        put("changedFraction", impact.changedFraction); put("meanOklabDelta", impact.meanOklabDelta)
        put("p95OklabDelta", impact.p95OklabDelta); put("meanLinearLuminanceDelta", impact.meanLinearLuminanceDelta)
        put("haloRisk", impact.haloRisk); put("saturationAnomalyFraction", impact.saturationAnomalyFraction)
    }

    private fun faceMean(image: RasterImage, face: FaceEvidence): Double {
        val left = (face.bounds.left * image.width).toInt().coerceIn(0, image.width - 1)
        val top = (face.bounds.top * image.height).toInt().coerceIn(0, image.height - 1)
        val right = (face.bounds.right * image.width).toInt().coerceIn(left + 1, image.width)
        val bottom = (face.bounds.bottom * image.height).toInt().coerceIn(top + 1, image.height)
        var sum = 0.0; var count = 0
        for (y in top until bottom) for (x in left until right) { sum += linearLuminance(image.argb[y * image.width + x]); count++ }
        return sum / count.coerceAtLeast(1)
    }

    private fun linearLuminance(color: Int): Double {
        fun linear(v: Int): Double { val s = v / 255.0; return if (s <= .04045) s / 12.92 else ((s + .055) / 1.055).pow(2.4) }
        return .2126 * linear(color ushr 16 and 255) + .7152 * linear(color ushr 8 and 255) + .0722 * linear(color and 255)
    }

    private fun decode(file: File): RasterImage = requireNotNull(BitmapFactory.decodeFile(file.absolutePath)).let { bitmap ->
        try { bitmap.toRaster(1f) } finally { bitmap.recycle() }
    }

    private fun scale(source: RasterImage, maxDimension: Int): RasterImage {
        val largest = max(source.width, source.height); if (largest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / largest; val width = (source.width * ratio).roundToInt(); val height = (source.height * ratio).roundToInt()
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) pixels[y * width + x] =
            source.argb[(y.toLong() * source.height / height).toInt() * source.width + (x.toLong() * source.width / width).toInt()]
        return RasterImage(width, height, pixels, ratio)
    }

    private fun save(raster: RasterImage, file: File) { val bitmap = raster.toBitmap(); try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() } }
    private fun difference(a: RasterImage, b: RasterImage) = RasterImage(a.width, a.height, IntArray(a.argb.size) { i ->
        val x = a.argb[i]; val y = b.argb[i]; Color.rgb(
            (abs((x ushr 16 and 255) - (y ushr 16 and 255)) * 8).coerceAtMost(255),
            (abs((x ushr 8 and 255) - (y ushr 8 and 255)) * 8).coerceAtMost(255),
            (abs((x and 255) - (y and 255)) * 8).coerceAtMost(255))
    })

    private fun contactSheet(original: RasterImage, old: RasterImage, renders: Map<EnhancementLevel, SmartMagicProductionRender>): RasterImage {
        val images = listOf(original, old) + EnhancementLevel.entries.map { renders.getValue(it).image }
        val labels = listOf("Original", "Previous Extreme", "Natural", "Enhanced", "Extreme")
        val cellW = 420; val cellH = 300; val labelH = 40
        val bitmap = Bitmap.createBitmap(cellW * images.size, cellH + labelH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(16, 17, 22)); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 24f }
        images.forEachIndexed { index, raster ->
            val source = raster.toBitmap(); canvas.drawBitmap(source, null, android.graphics.Rect(index * cellW, 0, (index + 1) * cellW, cellH), paint)
            source.recycle(); canvas.drawText(labels[index], index * cellW + 8f, cellH + 29f, paint)
        }
        return try { bitmap.toRaster(1f) } finally { bitmap.recycle() }
    }

    private data class Case(val id: String, val original: String, val previousExtreme: String)
}
