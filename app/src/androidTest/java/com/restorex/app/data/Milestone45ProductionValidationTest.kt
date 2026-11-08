package com.restorex.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.restorex.app.RestoreXApplication
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.ConfidenceMask
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.SmartMagicProductionRender
import java.io.File
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Milestone45ProductionValidationTest {
    private val app = ApplicationProvider.getApplicationContext<RestoreXApplication>()
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test fun establishedFixturesRunThroughProductionCoordinator() = runBlocking {
        val root = File(requireNotNull(app.getExternalFilesDir(null)), "milestone6-validation").apply { mkdirs() }
        val rows = mutableListOf<ContactRow>()
        FIXTURES.forEach { fixture ->
            val directory = File(root, fixture.substringBeforeLast('.')).apply { mkdirs() }
            val original = loadAsset(fixture)
            val analysis = scale(original, app.container.smartMagicCoordinator.processingBudget.analysisMaxDimension)
            val sessionStarted = System.nanoTime()
            val session = app.container.smartMagicCoordinator.createSession("asset:$fixture", analysis)
            val sessionMs = (System.nanoTime() - sessionStarted) / 1_000_000L
            val renders = linkedMapOf<EnhancementLevel, SmartMagicProductionRender>()
            EnhancementLevel.entries.forEach { level ->
                renders[level] = app.container.smartMagicCoordinator.render(original, session, level)
            }
            save(original, File(directory, "original.png"))
            renders.forEach { (level, render) ->
                val name = level.name.lowercase()
                save(render.image, File(directory, "$name.png"))
                save(difference(original, render.image), File(directory, "difference_$name.png"))
            }
            session.semanticResult.personMask?.let { mask ->
                save(maskOverlay(original, mask), File(directory, "mask_overlay.png"))
            }
            val trace = traceJson(fixture, sessionMs, session, renders)
            File(directory, "execution_trace.json").writeText(trace.toString(2))
            File(directory, "quality_report.json").writeText(qualityJson(renders).toString(2))
            if (fixture.contains("fine_hair")) save(centerCrop(original, renders.getValue(EnhancementLevel.NATURAL).image), File(directory, "fine_hair_boundary_closeup.png"))
            if (fixture.contains("group")) save(centerCrop(original, renders.getValue(EnhancementLevel.NATURAL).image), File(directory, "group_boundary_closeup.png"))
            rows += ContactRow(original, renders)
            assertEquals(original.width, renders.getValue(EnhancementLevel.ENHANCED).image.width)
            assertEquals(original.height, renders.getValue(EnhancementLevel.ENHANCED).image.height)
        }
        createContactSheet(rows, File(root, "M6_VISUAL_CONTACT_SHEET.png"))
        publishArchive(root)
    }

    @Test fun selectedProductionLevelIsUsedByMediaStoreExport() = runBlocking {
        val fixture = FIXTURES[1]
        val sourceFile = File(app.cacheDir, "m45_export_source.png")
        testAssets.open("milestone4/$fixture").use { input -> sourceFile.outputStream().use { input.copyTo(it) } }
        val full = loadAsset(fixture)
        val session = app.container.smartMagicCoordinator.createSession(Uri.fromFile(sourceFile).toString(), scale(full, 1280))
        val direct = app.container.smartMagicCoordinator.render(full, session, EnhancementLevel.NATURAL)
        var destination: Uri? = null
        try {
            val result = app.container.smartMagicExportManager.export(Uri.fromFile(sourceFile).toString(), session, direct)
            destination = Uri.parse(result.uri)
            val decoded = app.contentResolver.openInputStream(destination).use { BitmapFactory.decodeStream(it) }
            assertEquals(full.width, decoded.width); assertEquals(full.height, decoded.height)
            val exported = decoded.toRaster(1f); decoded.recycle()
            val meanDifference = meanAbsoluteRgb(direct.image, exported)
            // JPEG quality 95 permits small encoding differences but not substitution of the source.
            assertTrue("export differs unexpectedly from production render: $meanDifference", meanDifference < 4.0)
            if (!direct.image.argb.contentEquals(full.argb)) assertTrue(meanAbsoluteRgb(full, exported) > .05)
            println("M45_MEDIASTORE_EXPORT level=NATURAL outcome=${result.productionOutcome} elapsedMs=${result.elapsedMillis} meanVsRender=$meanDifference")
        } finally {
            destination?.let { app.contentResolver.delete(it, null, null) }
            sourceFile.delete()
        }
    }

    @Test fun boundedProductionPreviewStageTiming() = runBlocking {
        val coordinator = app.container.smartMagicCoordinator
        val source = loadAsset(FIXTURES[1])
        val preview = scale(source, coordinator.processingBudget.previewMaxDimension)
        val analysis = scale(source, coordinator.processingBudget.analysisMaxDimension)
        val sessionStart = System.nanoTime()
        val session = coordinator.createSession("asset:${FIXTURES[1]}:bounded-preview", analysis)
        val sessionMs = (System.nanoTime() - sessionStart) / 1_000_000.0
        val values = linkedMapOf<String, Double>()
        EnhancementLevel.entries.forEach { level ->
            val start = System.nanoTime()
            coordinator.render(preview, session, level)
            values[level.name] = (System.nanoTime() - start) / 1_000_000.0
        }
        println("M6_BOUNDED_PREVIEW dimensions=${preview.width}x${preview.height} sessionMs=$sessionMs " +
            values.entries.joinToString(" ") { "${it.key}Ms=${it.value}" })
        assertTrue(preview.width <= coordinator.processingBudget.previewMaxDimension ||
            preview.height <= coordinator.processingBudget.previewMaxDimension)
    }

    private fun traceJson(fixture: String, sessionMs: Long,
        session: com.restorex.app.domain.photointelligence.SmartMagicProductionSession,
        renders: Map<EnhancementLevel, SmartMagicProductionRender>): JSONObject = JSONObject().apply {
        put("schemaVersion", 1); put("fixture", fixture); put("pipeline", "SmartMagicProcessingCoordinator")
        put("sessionId", session.sessionId); put("sourceFingerprint", session.sourceFingerprint)
        put("analysisDimensions", "${session.sourceWidth}x${session.sourceHeight}")
        put("sessionCreationMs", sessionMs); put("semanticInferenceMs", session.semanticResult.inferenceDurationNanos / 1_000_000.0)
        put("stageTimingsMs", JSONObject().apply {
            put("globalAnalysis", session.stageTimings.globalAnalysisNanos / 1_000_000.0)
            put("photometricAnalysis", session.stageTimings.photometricAnalysisNanos / 1_000_000.0)
            put("semanticInference", session.stageTimings.semanticInferenceNanos / 1_000_000.0)
            put("reasoningAndPlanning", session.stageTimings.reasoningAndPlanningNanos / 1_000_000.0)
        })
        put("processingBudget", JSONObject().apply {
            val budget = app.container.smartMagicCoordinator.processingBudget
            put("tier", budget.tier); put("analysisMaxDimension", budget.analysisMaxDimension)
            put("previewMaxDimension", budget.previewMaxDimension); put("optimizationMaxDimension", budget.optimizationMaxDimension)
            put("optimizerCandidateBudget", budget.optimizerCandidateBudget); put("regionalCandidateBudget", budget.regionalCandidateBudget)
        })
        put("faceCount", session.semanticResult.faces.size); put("maskAccepted", session.semanticResult.personMaskReference?.quality?.accepted)
        put("subjectDecision", session.subjectAssessment?.decision?.name ?: "NONE")
        put("diagnoses", JSONArray(session.diagnoses.map { it.conditionId }))
        put("advancedEvidence", JSONObject().apply {
            put("analysisVersion", session.advancedEvidence.analysisVersion)
            put("hazeConfidence", session.advancedEvidence.hazeConfidence)
            put("atmosphericIntentRisk", session.advancedEvidence.atmosphericIntentRisk)
            put("veilingGlareConfidence", session.advancedEvidence.veilingGlareConfidence)
            put("recoverableHighlightConfidence", session.advancedEvidence.recoverableHighlightConfidence)
            put("shadowRecoveryConfidence", session.advancedEvidence.shadowRecoveryConfidence)
            put("localContrastDeficit", session.advancedEvidence.localContrastDeficit)
            put("detailConfidence", session.advancedEvidence.detailConfidence)
            put("noiseConstraint", session.advancedEvidence.noiseConstraint)
            put("analysisMs", session.advancedEvidence.analysisDurationNanos / 1_000_000.0)
        })
        put("plans", JSONObject().apply { session.regionalPlans.forEach { (level, plan) ->
            put(level.name, JSONArray(plan.operations.map { operation -> JSONObject().apply {
                put("id", operation.id); put("type", operation.type.name); put("target", operation.target.name)
                put("amount", operation.amount); put("confidence", operation.confidence)
            } }))
        } })
        put("adaptiveOptimization", JSONObject().apply { session.optimizationResults.forEach { (level, result) ->
            put(level.name, JSONObject().apply {
                put("selectedCandidate", result.selectedCandidateId); put("candidateBudget", result.candidateBudget)
                put("stopReason", result.stopReason); put("evaluations", JSONArray(result.evaluations.map { evaluation ->
                    JSONObject().apply {
                        put("id", evaluation.id); put("score", evaluation.score); put("valid", evaluation.valid)
                        put("rejections", JSONArray(evaluation.rejectionReasons)); put("runtimeMs", evaluation.renderNanos / 1_000_000.0)
                        put("exposureEv", evaluation.parameters.exposureEv); put("midtoneGamma", evaluation.parameters.midtoneGamma)
                        put("contrast", evaluation.parameters.contrast); put("shadows", evaluation.parameters.shadows)
                        put("highlights", evaluation.parameters.highlights); put("vibrance", evaluation.parameters.vibrance)
                    }
                }))
            })
        } })
        put("levels", JSONObject().apply { renders.forEach { (level, render) -> put(level.name, renderJson(render)) } })
    }

    private fun renderJson(render: SmartMagicProductionRender) = JSONObject().apply {
        put("strategy", render.strategy.name); put("outcome", render.outcome.name)
        put("elapsedMs", render.elapsedNanos / 1_000_000.0)
        put("changedFraction", render.impact.changedFraction); put("meanOklabDelta", render.impact.meanOklabDelta)
        put("p95OklabDelta", render.impact.p95OklabDelta); put("perceptuallyNegligible", render.impact.perceptuallyNegligible)
        put("localContrastDelta", render.impact.localContrastDelta)
        put("saturationAnomalyFraction", render.impact.saturationAnomalyFraction)
        put("haloRisk", render.impact.haloRisk)
        put("regionalStatus", render.regionalResult?.status?.name)
        put("appliedStrength", render.regionalResult?.appliedStrength)
        put("candidates", JSONArray(render.regionalResult?.candidateEvaluations?.map { candidate -> JSONObject().apply {
            put("strength", candidate.strength); put("accepted", candidate.accepted)
            put("failures", JSONArray(candidate.failureReasons)); put("meanOklabDelta", candidate.impact.meanOklabDelta)
            put("runtimeMs", candidate.runtimeNanos / 1_000_000.0)
        } } ?: emptyList<JSONObject>()))
    }

    private fun qualityJson(renders: Map<EnhancementLevel, SmartMagicProductionRender>) = JSONObject().apply {
        renders.forEach { (level, render) -> put(level.name, JSONObject().apply {
            val q = render.regionalResult?.metrics
            put("outcome", render.outcome.name); put("newHighlightClipping", q?.newHighlightClipping)
            put("newShadowClipping", q?.newShadowClipping); put("protectedInteriorDifference", q?.protectedInteriorMeanDifference)
            put("boundaryDiscontinuity", q?.boundaryDiscontinuity); put("shadowChromaAmplification", q?.shadowChromaAmplification)
            put("failureReasons", JSONArray(q?.failureReasons ?: emptyList<String>()))
        }) }
    }

    private fun loadAsset(name: String): RasterImage = testAssets.open(
        if (name == "public_domain_portrait.jpg") name else "milestone4/$name",
    ).use { input ->
        BitmapFactory.decodeStream(input).let { bitmap -> try { bitmap.toRaster(1f) } finally { bitmap.recycle() } }
    }

    private fun scale(source: RasterImage, maxDimension: Int): RasterImage {
        val largest = max(source.width, source.height); if (largest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / largest; val width = (source.width * ratio).roundToInt(); val height = (source.height * ratio).roundToInt()
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) pixels[y * width + x] = source.argb[
            (y.toLong() * source.height / height).toInt() * source.width + (x.toLong() * source.width / width).toInt()]
        return RasterImage(width, height, pixels, ratio)
    }

    private fun save(raster: RasterImage, file: File) {
        val bitmap = raster.toBitmap(); try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
    }

    private fun difference(a: RasterImage, b: RasterImage): RasterImage = RasterImage(a.width, a.height, IntArray(a.argb.size) { i ->
        val ca = a.argb[i]; val cb = b.argb[i]
        val r = (abs((ca ushr 16 and 255) - (cb ushr 16 and 255)) * 8).coerceAtMost(255)
        val g = (abs((ca ushr 8 and 255) - (cb ushr 8 and 255)) * 8).coerceAtMost(255)
        val blue = (abs((ca and 255) - (cb and 255)) * 8).coerceAtMost(255)
        Color.rgb(r, g, blue)
    })

    private fun maskOverlay(source: RasterImage, mask: ConfidenceMask): RasterImage = RasterImage(source.width, source.height, IntArray(source.argb.size) { i ->
        val x = i % source.width; val y = i / source.width; val color = source.argb[i]
        val amount = mask.sample((x + .5f) / source.width, (y + .5f) / source.height) * .42f
        Color.argb(color ushr 24, ((color ushr 16 and 255) * (1f - amount) + 255 * amount).roundToInt(),
            ((color ushr 8 and 255) * (1f - amount)).roundToInt(), ((color and 255) * (1f - amount) + 180 * amount).roundToInt())
    })

    private fun centerCrop(original: RasterImage, corrected: RasterImage): RasterImage {
        val x0 = original.width / 4; val y0 = original.height / 8; val width = original.width / 2; val height = original.height * 3 / 4
        val out = IntArray(width * 2 * height)
        for (y in 0 until height) for (x in 0 until width) {
            out[y * width * 2 + x] = original.argb[(y + y0) * original.width + x + x0]
            out[y * width * 2 + width + x] = corrected.argb[(y + y0) * corrected.width + x + x0]
        }
        return RasterImage(width * 2, height, out)
    }

    private fun createContactSheet(rows: List<ContactRow>, destination: File) {
        val cellW = 384; val cellH = 280; val labelH = 38
        val bitmap = Bitmap.createBitmap(cellW * 4, (cellH + labelH) * rows.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(16, 17, 22))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
        rows.forEachIndexed { rowIndex, row ->
            val images = listOf(row.original) + EnhancementLevel.entries.map { row.renders.getValue(it).image }
            val labels = listOf("Original") + EnhancementLevel.entries.map { level ->
                "$level: ${row.renders.getValue(level).outcome}"
            }
            images.forEachIndexed { column, raster ->
                val source = raster.toBitmap(); val target = android.graphics.Rect(column * cellW, rowIndex * (cellH + labelH), (column + 1) * cellW, rowIndex * (cellH + labelH) + cellH)
                canvas.drawBitmap(source, null, target, paint); source.recycle()
                canvas.drawText(labels[column].take(30), column * cellW + 8f, rowIndex * (cellH + labelH) + cellH + 27f, paint)
            }
        }
        destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    private fun meanAbsoluteRgb(a: RasterImage, b: RasterImage): Double {
        require(a.width == b.width && a.height == b.height)
        var sum = 0L
        a.argb.indices.forEach { i -> val x = a.argb[i]; val y = b.argb[i]
            sum += abs((x ushr 16 and 255) - (y ushr 16 and 255)) + abs((x ushr 8 and 255) - (y ushr 8 and 255)) + abs((x and 255) - (y and 255)) }
        return sum.toDouble() / (a.argb.size * 3.0)
    }

    private fun publishArchive(root: File) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "restovue-milestone6-validation.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/RestovueValidation")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            app.contentResolver.openOutputStream(uri, "w").use { raw ->
                ZipOutputStream(BufferedOutputStream(requireNotNull(raw))).use { zip ->
                    root.walkTopDown().filter { it.isFile }.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            app.contentResolver.update(uri, values, null, null)
            println("M6_VALIDATION_ARCHIVE uri=$uri")
        } catch (failure: Throwable) {
            app.contentResolver.delete(uri, null, null)
            throw failure
        }
    }

    private data class ContactRow(val original: RasterImage, val renders: Map<EnhancementLevel, SmartMagicProductionRender>)
    private companion object { val FIXTURES = listOf(
        "M4_01_subject_protection.png", "M4_02_backlit_fine_hair.png", "M4_03_group_portrait.png",
        "public_domain_portrait.jpg",
    ) }
}
