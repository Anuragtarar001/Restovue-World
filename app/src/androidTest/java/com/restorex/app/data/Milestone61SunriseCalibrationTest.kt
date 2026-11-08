package com.restorex.app.data

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.restorex.app.RestoreXApplication
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.ConfidenceMask
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.FaceEvidence
import com.restorex.app.domain.photointelligence.SmartMagicProductionRender
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

/** Private calibration inputs are injected with run-as and are never added to test assets or Git. */
@RunWith(AndroidJUnit4::class)
class Milestone61SunriseCalibrationTest {
    private val app = ApplicationProvider.getApplicationContext<RestoreXApplication>()

    @Test fun analyzeAndRenderExactSunrisePair() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val variant = args.getString("variant") ?: "baseline"
        val input = File(app.filesDir, "m61-input")
        assumeTrue("Private M6.1 calibration inputs were not injected", File(input, "orig1.jpeg").isFile && File(input, "origfixed.jpeg").isFile)
        val original = decode(File(input, "orig1.jpeg"))
        val oldExtreme = decode(File(input, "origfixed.jpeg"))
        assertEquals(original.width, oldExtreme.width)
        assertEquals(original.height, oldExtreme.height)

        val root = File(requireNotNull(app.getExternalFilesDir(null)), "milestone6-1-$variant").apply {
            deleteRecursively(); mkdirs()
        }
        val coordinator = app.container.smartMagicCoordinator
        val analysis = scale(original, coordinator.processingBudget.analysisMaxDimension)
        val sessionStart = System.nanoTime()
        val session = coordinator.createSession("private:m61-sunrise", analysis)
        val sessionMs = (System.nanoTime() - sessionStart) / 1_000_000.0
        val renders = linkedMapOf<EnhancementLevel, SmartMagicProductionRender>()
        EnhancementLevel.entries.forEach { level -> renders[level] = coordinator.render(original, session, level) }

        save(original, File(root, "original.png")); save(oldExtreme, File(root, "old_extreme.png"))
        save(difference(original, oldExtreme), File(root, "difference_old_extreme.png"))
        renders.forEach { (level, render) ->
            val name = level.name.lowercase(); save(render.image, File(root, "$name.png"))
            save(difference(original, render.image), File(root, "difference_$name.png"))
        }
        session.semanticResult.personMask?.let { save(maskOverlay(original, it), File(root, "person_mask_overlay.png")) }
        save(contactSheet(original, oldExtreme, renders), File(root, "M6_1_${variant}_CONTACT_SHEET.png"))
        session.semanticResult.personMask?.let { mask ->
            save(subjectContactSheet(original, oldExtreme, renders, mask), File(root, "M6_1_${variant}_SUBJECT_CLOSEUP.png"))
        }
        val trace = JSONObject().apply {
            put("variant", variant); put("dimensions", "${original.width}x${original.height}")
            put("originalMetadata", metadata(File(input, "orig1.jpeg")))
            put("oldExtremeMetadata", metadata(File(input, "origfixed.jpeg")))
            put("originalGlobalStatistics", imageStatsJson(original))
            put("oldExtremeGlobalStatistics", imageStatsJson(oldExtreme))
            put("sessionMs", sessionMs); put("sessionId", session.sessionId)
            put("stageTimingsMs", JSONObject().apply {
                put("global", session.stageTimings.globalAnalysisNanos / 1_000_000.0)
                put("photometric", session.stageTimings.photometricAnalysisNanos / 1_000_000.0)
                put("semantic", session.stageTimings.semanticInferenceNanos / 1_000_000.0)
                put("reasoning", session.stageTimings.reasoningAndPlanningNanos / 1_000_000.0)
            })
            put("faceCount", session.semanticResult.faces.size)
            put("faces", JSONArray(session.semanticResult.faces.map { faceJson(it, analysis) }))
            put("maskQuality", session.semanticResult.personMaskReference?.quality?.let { quality -> JSONObject().apply {
                put("accepted", quality.accepted); put("weightedAreaFraction", quality.weightedAreaFraction)
                put("foregroundAreaFraction", quality.foregroundAreaFraction)
                put("meanForegroundScore", quality.meanForegroundScore); put("boundaryUncertainty", quality.boundaryUncertainty)
                put("componentCount", quality.componentCount); put("isolatedAreaFraction", quality.isolatedAreaFraction)
                put("rejectionReasons", JSONArray(quality.rejectionReasons.map { it.name }))
            } })
            put("regionalEvidence", session.regionalEvidence?.let { evidence -> JSONObject().apply {
                put("personMeanLinear", evidence.person.meanLinearLuminance)
                put("backgroundMeanLinear", evidence.background.meanLinearLuminance)
                put("personShadowOccupancy", evidence.person.shadowOccupancy)
                put("backgroundShadowOccupancy", evidence.background.shadowOccupancy)
                put("personHighlightOccupancy", evidence.person.highlightOccupancy)
                put("backgroundHighlightOccupancy", evidence.background.highlightOccupancy)
                put("personNoiseRisk", evidence.person.noiseRisk); put("personMinusBackground", evidence.personMinusBackgroundLuminance)
            } })
            put("subjectAssessment", JSONObject().apply {
                put("decision", session.subjectAssessment?.decision?.name ?: "NONE")
                put("confidence", session.subjectAssessment?.confidence)
                put("evidence", JSONArray(session.subjectAssessment?.evidence?.map { it.toString() } ?: emptyList<String>()))
                put("counterEvidence", JSONArray(session.subjectAssessment?.counterEvidence?.map { it.toString() } ?: emptyList<String>()))
                put("explanation", session.subjectAssessment?.explanation)
            })
            put("plans", JSONObject().apply { session.regionalPlans.forEach { (level, plan) ->
                put(level.name, JSONArray(plan.operations.map { operation -> JSONObject().apply {
                    put("id", operation.id); put("type", operation.type.name); put("target", operation.target.name)
                    put("amount", operation.amount); put("unit", operation.unit); put("confidence", operation.confidence)
                } }))
            } })
            put("oldExtremeMetrics", comparisonJson(original, oldExtreme))
            session.semanticResult.personMask?.let { mask ->
                put("oldExtremeRegionalMetrics", regionalComparisonJson(original, oldExtreme, mask))
            }
            put("renders", JSONObject().apply { renders.forEach { (level, render) -> put(level.name, renderJson(original, render)) } })
            session.semanticResult.personMask?.let { mask -> put("renderRegionalMetrics", JSONObject().apply {
                renders.forEach { (level, render) -> put(level.name, regionalComparisonJson(original, render.image, mask)) }
            }) }
            if (session.semanticResult.faces.isNotEmpty()) put("faceRegionalMetrics", JSONObject().apply {
                session.semanticResult.faces.forEach { face -> put(face.id, JSONObject().apply {
                    put("oldExtreme", rectComparisonJson(original, oldExtreme, face))
                    renders.forEach { (level, render) -> put(level.name, rectComparisonJson(original, render.image, face)) }
                }) }
            })
        }
        File(root, "execution_trace.json").writeText(trace.toString(2))
        publishArchive(root, "restovue-m61-$variant.zip")
        assertTrue(session.semanticResult.personMaskReference?.quality?.accepted == true)
        assertTrue(session.subjectAssessment?.decision?.name == "REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE")
        assertTrue("The mandatory sunrise face must be model-confirmed in the subject head region",
            session.semanticResult.faces.any { face ->
                val cx = (face.bounds.left + face.bounds.right) * .5f
                val cy = (face.bounds.top + face.bounds.bottom) * .5f
                cx in .55f.. .64f && cy in .15f.. .30f && (face.detectorScore ?: 0f) >= .70f
            })
    }

    @Test fun boundedPreviewAndExportTimingExactSunrise() = runBlocking {
        val sourceFile = File(app.filesDir, "m61-input/orig1.jpeg")
        assumeTrue("Private M6.1 calibration input was not injected", sourceFile.isFile)
        val coordinator = app.container.smartMagicCoordinator; val source = decode(sourceFile)
        val analysis = scale(source, coordinator.processingBudget.analysisMaxDimension)
        val preview = scale(source, coordinator.processingBudget.previewMaxDimension)
        val sessionStart = System.nanoTime(); val session = coordinator.createSession(Uri.fromFile(sourceFile).toString(), analysis)
        val sessionMs = (System.nanoTime() - sessionStart) / 1_000_000.0
        val modeTimes = linkedMapOf<EnhancementLevel, Double>(); val renders = linkedMapOf<EnhancementLevel, SmartMagicProductionRender>()
        EnhancementLevel.entries.forEach { level -> val started = System.nanoTime(); renders[level] = coordinator.render(preview, session, level); modeTimes[level] = (System.nanoTime() - started) / 1_000_000.0 }
        var destination: Uri? = null
        try {
            val exportStarted = System.nanoTime()
            val export = app.container.smartMagicExportManager.export(Uri.fromFile(sourceFile).toString(), session, renders.getValue(EnhancementLevel.EXTREME))
            val exportMs = (System.nanoTime() - exportStarted) / 1_000_000.0; destination = Uri.parse(export.uri)
            println("M61_PERFORMANCE preview=${preview.width}x${preview.height} sessionMs=$sessionMs " +
                modeTimes.entries.joinToString(" ") { "${it.key}Ms=${it.value}" } + " exportMs=$exportMs exportManagerMs=${export.elapsedMillis}")
            assertEquals(source.width, export.width); assertEquals(source.height, export.height)
        } finally { destination?.let { app.contentResolver.delete(it, null, null) } }
    }

    private fun faceJson(face: FaceEvidence, source: RasterImage) = JSONObject().apply {
        put("id", face.id); put("left", face.bounds.left); put("top", face.bounds.top)
        put("right", face.bounds.right); put("bottom", face.bounds.bottom); put("area", face.bounds.area)
        put("detectorScore", face.detectorScore); put("heuristicQuality", face.heuristicQuality)
        put("boundingBoxMeanLinear", meanLinear(source, face))
    }

    private fun meanLinear(source: RasterImage, face: FaceEvidence): Double {
        val x0 = (face.bounds.left * source.width).toInt().coerceIn(0, source.width - 1)
        val y0 = (face.bounds.top * source.height).toInt().coerceIn(0, source.height - 1)
        val x1 = (face.bounds.right * source.width).toInt().coerceIn(x0 + 1, source.width)
        val y1 = (face.bounds.bottom * source.height).toInt().coerceIn(y0 + 1, source.height)
        var sum = 0.0; var count = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val c = source.argb[y * source.width + x]
            fun linear(v: Int): Double { val s = v / 255.0; return if (s <= .04045) s / 12.92 else ((s + .055) / 1.055).pow(2.4) }
            sum += .2126 * linear(c ushr 16 and 255) + .7152 * linear(c ushr 8 and 255) + .0722 * linear(c and 255); count++
        }
        return sum / count.coerceAtLeast(1)
    }

    private fun renderJson(original: RasterImage, render: SmartMagicProductionRender) = JSONObject().apply {
        put("strategy", render.strategy.name); put("outcome", render.outcome.name); put("elapsedMs", render.elapsedNanos / 1_000_000.0)
        put("comparison", comparisonJson(original, render.image)); put("appliedStrength", render.regionalResult?.appliedStrength)
        put("regionalStatus", render.regionalResult?.status?.name)
        put("qualityFailures", JSONArray(render.regionalResult?.metrics?.failureReasons ?: emptyList<String>()))
        put("candidates", JSONArray(render.regionalResult?.candidateEvaluations?.map { candidate -> JSONObject().apply {
            put("strength", candidate.strength); put("accepted", candidate.accepted)
            put("failures", JSONArray(candidate.failureReasons)); put("meanOklabDelta", candidate.impact.meanOklabDelta)
            put("changedFraction", candidate.impact.changedFraction); put("runtimeMs", candidate.runtimeNanos / 1_000_000.0)
        } } ?: emptyList<JSONObject>()))
    }

    private fun comparisonJson(a: RasterImage, b: RasterImage) = JSONObject().apply {
        val impact = com.restorex.app.domain.photointelligence.PerceptualImpactEstimator().estimate(a, b)
        put("changedFraction", impact.changedFraction); put("meanOklabDelta", impact.meanOklabDelta)
        put("p95OklabDelta", impact.p95OklabDelta); put("meanLinearLuminanceDelta", impact.meanLinearLuminanceDelta)
        put("p95AbsoluteLuminanceDelta", impact.p95AbsoluteLuminanceDelta); put("localContrastDelta", impact.localContrastDelta)
        put("saturationAnomalyFraction", impact.saturationAnomalyFraction); put("haloRisk", impact.haloRisk)
    }

    private fun regionalComparisonJson(a: RasterImage, b: RasterImage, mask: ConfidenceMask): JSONObject {
        var personDelta = 0.0; var personWeight = 0.0; var backgroundDelta = 0.0; var backgroundWeight = 0.0
        var personLumaA = 0.0; var personLumaB = 0.0; var backgroundLumaA = 0.0; var backgroundLumaB = 0.0
        for (i in a.argb.indices) {
            val x = i % a.width; val y = i / a.width; val m = mask.sample((x + .5f) / a.width, (y + .5f) / a.height).toDouble()
            val la = linearLuminance(a.argb[i]); val lb = linearLuminance(b.argb[i]); val delta = abs(lb - la)
            personDelta += delta * m; personLumaA += la * m; personLumaB += lb * m; personWeight += m
            val bg = 1.0 - m; backgroundDelta += delta * bg; backgroundLumaA += la * bg; backgroundLumaB += lb * bg; backgroundWeight += bg
        }
        return JSONObject().apply {
            put("personMeanAbsoluteLuminanceDelta", personDelta / personWeight.coerceAtLeast(1.0))
            put("backgroundMeanAbsoluteLuminanceDelta", backgroundDelta / backgroundWeight.coerceAtLeast(1.0))
            put("personMeanBefore", personLumaA / personWeight.coerceAtLeast(1.0)); put("personMeanAfter", personLumaB / personWeight.coerceAtLeast(1.0))
            put("backgroundMeanBefore", backgroundLumaA / backgroundWeight.coerceAtLeast(1.0)); put("backgroundMeanAfter", backgroundLumaB / backgroundWeight.coerceAtLeast(1.0))
        }
    }

    private fun rectComparisonJson(a: RasterImage, b: RasterImage, face: FaceEvidence): JSONObject {
        val left = (face.bounds.left * a.width).toInt().coerceIn(0, a.width - 1)
        val top = (face.bounds.top * a.height).toInt().coerceIn(0, a.height - 1)
        val right = (face.bounds.right * a.width).toInt().coerceIn(left + 1, a.width)
        val bottom = (face.bounds.bottom * a.height).toInt().coerceIn(top + 1, a.height)
        var before = 0.0; var after = 0.0; var absolute = 0.0; var count = 0
        for (y in top until bottom) for (x in left until right) {
            val index = y * a.width + x; val la = linearLuminance(a.argb[index]); val lb = linearLuminance(b.argb[index])
            before += la; after += lb; absolute += abs(lb - la); count++
        }
        return JSONObject().apply {
            put("meanBefore", before / count.coerceAtLeast(1)); put("meanAfter", after / count.coerceAtLeast(1))
            put("meanAbsoluteLuminanceDelta", absolute / count.coerceAtLeast(1)); put("pixels", count)
        }
    }

    private fun imageStatsJson(source: RasterImage): JSONObject {
        val luminance = FloatArray(source.argb.size); val red = IntArray(256); val green = IntArray(256); val blue = IntArray(256)
        var shadows = 0; var highlights = 0; var blackClip = 0; var whiteClip = 0; var localContrast = 0.0; var localPairs = 0
        source.argb.forEachIndexed { index, color ->
            val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
            red[r]++; green[g]++; blue[b]++; val l = linearLuminance(color).toFloat(); luminance[index] = l
            if (l < .02f) shadows++; if (l > .90f) highlights++
            if (r <= 1 && g <= 1 && b <= 1) blackClip++; if (r >= 254 && g >= 254 && b >= 254) whiteClip++
            val x = index % source.width; val y = index / source.width
            if (x > 0) { localContrast += abs(l - luminance[index - 1]); localPairs++ }
            if (y > 0) { localContrast += abs(l - luminance[index - source.width]); localPairs++ }
        }
        luminance.sort(); fun percentile(p: Float) = luminance[((luminance.size - 1) * p).roundToInt()]
        return JSONObject().apply {
            put("linearLuminancePercentiles", JSONObject().apply { listOf(.01f, .05f, .25f, .5f, .75f, .95f, .99f).forEach { p -> put("p${(p * 100).roundToInt()}", percentile(p)) } })
            put("shadowOccupancy", shadows.toDouble() / source.argb.size); put("highlightOccupancy", highlights.toDouble() / source.argb.size)
            put("blackClipping", blackClip.toDouble() / source.argb.size); put("whiteClipping", whiteClip.toDouble() / source.argb.size)
            put("meanAdjacentLinearContrast", localContrast / localPairs.coerceAtLeast(1))
            put("redHistogram", JSONArray(red.toList())); put("greenHistogram", JSONArray(green.toList())); put("blueHistogram", JSONArray(blue.toList()))
        }
    }

    private fun metadata(file: File): JSONObject {
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        return try { JSONObject().apply {
            put("filename", file.name); put("bytes", file.length()); put("width", bitmap.width); put("height", bitmap.height)
            put("config", bitmap.config?.name); put("colorSpace", bitmap.colorSpace?.name); put("hasAlpha", bitmap.hasAlpha())
        } } finally { bitmap.recycle() }
    }

    private fun linearLuminance(color: Int): Double {
        fun linear(v: Int): Double { val s = v / 255.0; return if (s <= .04045) s / 12.92 else ((s + .055) / 1.055).pow(2.4) }
        return .2126 * linear(color ushr 16 and 255) + .7152 * linear(color ushr 8 and 255) + .0722 * linear(color and 255)
    }

    private fun decode(file: File): RasterImage = BitmapFactory.decodeFile(file.absolutePath).let { bitmap ->
        requireNotNull(bitmap) { "Missing private M6.1 input ${file.name}" }
        try { bitmap.toRaster(1f) } finally { bitmap.recycle() }
    }

    private fun scale(source: RasterImage, maxDimension: Int): RasterImage {
        val largest = max(source.width, source.height); if (largest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / largest; val width = (source.width * ratio).roundToInt(); val height = (source.height * ratio).roundToInt()
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) pixels[y * width + x] = source.argb[(y.toLong() * source.height / height).toInt() * source.width + (x.toLong() * source.width / width).toInt()]
        return RasterImage(width, height, pixels, ratio)
    }

    private fun save(raster: RasterImage, file: File) { val bitmap = raster.toBitmap(); try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() } }
    private fun difference(a: RasterImage, b: RasterImage) = RasterImage(a.width, a.height, IntArray(a.argb.size) { i ->
        val x = a.argb[i]; val y = b.argb[i]; Color.rgb(
            (abs((x ushr 16 and 255) - (y ushr 16 and 255)) * 8).coerceAtMost(255),
            (abs((x ushr 8 and 255) - (y ushr 8 and 255)) * 8).coerceAtMost(255),
            (abs((x and 255) - (y and 255)) * 8).coerceAtMost(255))
    })
    private fun maskOverlay(source: RasterImage, mask: ConfidenceMask) = RasterImage(source.width, source.height, IntArray(source.argb.size) { i ->
        val c = source.argb[i]; val m = mask.sample(((i % source.width) + .5f) / source.width, ((i / source.width) + .5f) / source.height) * .45f
        Color.rgb(((c ushr 16 and 255) * (1 - m) + 255 * m).roundToInt(), ((c ushr 8 and 255) * (1 - m)).roundToInt(), ((c and 255) * (1 - m) + 160 * m).roundToInt())
    })

    private fun contactSheet(original: RasterImage, old: RasterImage, renders: Map<EnhancementLevel, SmartMagicProductionRender>): RasterImage {
        val images = listOf(original, old) + EnhancementLevel.entries.map { renders.getValue(it).image }
        val labels = listOf("Original", "Old Extreme", "Natural", "Enhanced", "Extreme")
        val cellW = 400; val cellH = 185; val labelH = 36
        val bitmap = Bitmap.createBitmap(cellW * images.size, cellH + labelH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(16, 17, 22)); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 23f }
        images.forEachIndexed { index, raster -> val source = raster.toBitmap(); canvas.drawBitmap(source, null,
            android.graphics.Rect(index * cellW, 0, (index + 1) * cellW, cellH), paint); source.recycle(); canvas.drawText(labels[index], index * cellW + 8f, cellH + 27f, paint) }
        return try { bitmap.toRaster(1f) } finally { bitmap.recycle() }
    }

    private fun subjectContactSheet(original: RasterImage, old: RasterImage, renders: Map<EnhancementLevel, SmartMagicProductionRender>, mask: ConfidenceMask): RasterImage {
        var minX = mask.width; var minY = mask.height; var maxX = -1; var maxY = -1
        for (y in 0 until mask.height) for (x in 0 until mask.width) if (mask.score(x, y) >= .35f) { minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y) }
        val left = ((minX.toFloat() / mask.width - .05f) * original.width).toInt().coerceAtLeast(0)
        val top = ((minY.toFloat() / mask.height - .06f) * original.height).toInt().coerceAtLeast(0)
        val right = (((maxX + 1f) / mask.width + .05f) * original.width).toInt().coerceAtMost(original.width)
        val bottom = (((maxY + 1f) / mask.height + .03f) * original.height).toInt().coerceAtMost(original.height)
        val images = listOf(original, old) + EnhancementLevel.entries.map { renders.getValue(it).image }
        val labels = listOf("Original", "Old Extreme", "Natural", "Enhanced", "Extreme")
        val cropW = right - left; val cropH = bottom - top; val cellH = 620; val cellW = (cellH * cropW.toFloat() / cropH).roundToInt(); val labelH = 38
        val bitmap = Bitmap.createBitmap(cellW * images.size, cellH + labelH, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(16, 17, 22)); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 25f }
        images.forEachIndexed { index, raster -> val source = raster.toBitmap(); canvas.drawBitmap(source,
            android.graphics.Rect(left, top, right, bottom), android.graphics.Rect(index * cellW, 0, (index + 1) * cellW, cellH), paint); source.recycle(); canvas.drawText(labels[index], index * cellW + 7f, cellH + 28f, paint) }
        return try { bitmap.toRaster(1f) } finally { bitmap.recycle() }
    }

    private fun publishArchive(root: File, name: String) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/zip"); put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/RestovueValidation"); put(MediaStore.MediaColumns.IS_PENDING, 1) }
        val uri = requireNotNull(app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            app.contentResolver.openOutputStream(uri, "w").use { raw -> ZipOutputStream(BufferedOutputStream(requireNotNull(raw))).use { zip ->
                root.walkTopDown().filter { it.isFile }.forEach { file -> zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
            } }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); app.contentResolver.update(uri, values, null, null)
        } catch (failure: Throwable) { app.contentResolver.delete(uri, null, null); throw failure }
    }
}
