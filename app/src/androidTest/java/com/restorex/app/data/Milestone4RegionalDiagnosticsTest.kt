package com.restorex.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.BrightRegionAnalyzer
import com.restorex.app.domain.photointelligence.CompositionReasoningEngine
import com.restorex.app.domain.photointelligence.ConfidenceMask
import com.restorex.app.domain.photointelligence.EdgeAwareMaskRefiner
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.PhotographicDiagnosisEngine
import com.restorex.app.domain.photointelligence.PhotometricAnalyzerV2
import com.restorex.app.domain.photointelligence.RegionalCorrectionPlan
import com.restorex.app.domain.photointelligence.RegionalCorrectionRenderer
import com.restorex.app.domain.photointelligence.RegionalPlanBuilder
import com.restorex.app.domain.photointelligence.RegionalRenderRequest
import com.restorex.app.domain.photointelligence.RegionalRenderResult
import com.restorex.app.domain.photointelligence.RegionalTarget
import com.restorex.app.domain.photointelligence.SemanticCompositionEngine
import com.restorex.app.domain.photointelligence.SemanticCorrectionPlanner
import com.restorex.app.domain.photointelligence.SemanticRegionPhotometricAnalyzer
import com.restorex.app.domain.photointelligence.SemanticSubjectDiagnosisEngine
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class Milestone4RegionalDiagnosticsTest {
    @Test fun realModelsRenderSuppliedMilestone4Fixtures() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val test = InstrumentationRegistry.getInstrumentation().context
        val root = File(requireNotNull(app.getExternalFilesDir(null)), "milestone4-diagnostics").apply { mkdirs() }
        val requestedFixture = InstrumentationRegistry.getArguments().getString("fixture")
        LiteRtSemanticAnalyzer(app).use { semanticAnalyzer ->
            FIXTURES.filter { requestedFixture == null || it == requestedFixture }.forEach { filename ->
                val bitmap = requireNotNull(test.assets.open("milestone4/$filename").use(BitmapFactory::decodeStream))
                val raster = bitmap.toRaster()
                val directory = File(root, filename.removeSuffix(".png")).apply { mkdirs() }
                writeBitmap(directory, "original.png", bitmap)
                val managedBefore = usedManaged(); val nativeBefore = Debug.getNativeHeapAllocatedSize(); val started = System.nanoTime()
                val semantic = semanticAnalyzer.analyze(raster) { false }
                assertNull("Semantic failure for $filename: ${semantic.failureDetail}", semantic.failureReason)
                val mask = requireNotNull(semantic.personMask)
                val quality = requireNotNull(semantic.personMaskReference).quality
                assertTrue("Rejected person mask for $filename: ${quality.rejectionReasons}", quality.accepted)
                val refined = EdgeAwareMaskRefiner().refine(raster, mask)
                writeBitmap(directory, "person_mask_soft.png", maskBitmap(mask, raster.width, raster.height))
                writeBitmap(directory, "person_mask_refined.png", maskBitmap(refined, raster.width, raster.height))
                writeBitmap(directory, "mask_overlay.png", overlay(bitmap, refined))

                val evidence = PhotometricAnalyzerV2().analyze(raster)
                val diagnoses = PhotographicDiagnosisEngine().diagnose(evidence)
                val bright = BrightRegionAnalyzer().analyze(raster)
                val baseGraph = CompositionReasoningEngine().build(evidence, bright)
                val regional = SemanticRegionPhotometricAnalyzer().analyze(raster, mask)
                val graph = SemanticCompositionEngine().integrate(baseGraph, semantic, regional)
                val assessment = SemanticSubjectDiagnosisEngine().assess(semantic, regional)
                val supplement = SemanticCorrectionPlanner().plan(filename, evidence, diagnoses, graph, semantic, regional, assessment)
                val rendered = linkedMapOf<EnhancementLevel, Pair<RegionalCorrectionPlan, RegionalRenderResult>>()
                EnhancementLevel.entries.forEach { level ->
                    val plan = RegionalPlanBuilder().build(filename, 0, level, supplement, regional, mask.id)
                    val result = RegionalCorrectionRenderer().render(RegionalRenderRequest(
                        raster, filename, 0, plan, mask, quality, semantic.faces, regional))
                    rendered[level] = plan to result
                    writeBitmap(directory, "${level.name.lowercase()}.png", result.image.toBitmap())
                }
                val enhanced = rendered.getValue(EnhancementLevel.ENHANCED)
                val targets = enhanced.first.operations.map { it.target }.toSet()
                if (RegionalTarget.PERSON in targets) writeBitmap(directory, "subject_only.png", enhanced.second.image.toBitmap())
                if (RegionalTarget.BACKGROUND in targets) writeBitmap(directory, "background_only.png", enhanced.second.image.toBitmap())
                writeBitmap(directory, "difference_map.png", difference(raster, enhanced.second.image))
                writeBitmap(directory, "boundary_closeup.png", boundaryCloseup(bitmap, refined, enhanced.second.image.toBitmap()))

                val elapsed = System.nanoTime() - started
                File(directory, "analysis.json").writeText(analysisJson(filename, raster, semantic.faces.size, mask, quality.accepted,
                    quality.boundaryUncertainty, regional.person.meanLinearLuminance, regional.background.meanLinearLuminance,
                    assessment.decision.name, semantic.inferenceDurationNanos, elapsed,
                    usedManaged() - managedBefore, Debug.getNativeHeapAllocatedSize() - nativeBefore))
                File(directory, "correction_plan.json").writeText(plansJson(rendered))
                File(directory, "quality_report.json").writeText(qualityJson(rendered))
                Log.i("RestovueM4", "$filename faces=${semantic.faces.size} assessment=${assessment.decision} " +
                    "semanticMs=${semantic.inferenceDurationNanos / 1_000_000} totalMs=${elapsed / 1_000_000} " +
                    "managedDelta=${usedManaged() - managedBefore} nativeDelta=${Debug.getNativeHeapAllocatedSize() - nativeBefore}")
                bitmap.recycle()
            }
        }
        val expected = requestedFixture?.removeSuffix(".png") ?: "M4_01_subject_protection"
        assertTrue(File(root, "$expected/quality_report.json").isFile)
    }

    private fun analysisJson(name: String, raster: RasterImage, faces: Int, mask: ConfidenceMask, accepted: Boolean,
        boundary: Float, personLuma: Float, backgroundLuma: Float, decision: String, inference: Long, total: Long,
        managedDelta: Long, nativeDelta: Long) = """{
  "fixture":"$name","sourceDimensions":[${raster.width},${raster.height}],"sourceType":"generated_visual_fixture_not_ground_truth",
  "semanticModelOutput":"real_bundled_tflite","faceCount":$faces,"maskDimensions":[${mask.width},${mask.height}],
  "maskAccepted":$accepted,"boundaryUncertainty":$boundary,"personMeanLinearLuminance":$personLuma,
  "backgroundMeanLinearLuminance":$backgroundLuma,"subjectDecision":"$decision",
  "semanticInferenceNanos":$inference,"totalDiagnosticNanos":$total,"managedHeapDeltaBytes":$managedDelta,"nativeHeapDeltaBytes":$nativeDelta
}"""

    private fun plansJson(results: Map<EnhancementLevel, Pair<RegionalCorrectionPlan, RegionalRenderResult>>) =
        results.entries.joinToString(prefix = "{\n", postfix = "\n}", separator = ",\n") { (level, pair) ->
            val operations = pair.first.operations.joinToString(prefix = "[", postfix = "]") {
                "{\"id\":\"${it.id}\",\"type\":\"${it.type}\",\"target\":\"${it.target}\",\"amount\":${it.amount},\"unit\":\"${it.unit}\"}"
            }
            "  \"${level.name}\":{\"operations\":$operations,\"status\":\"${pair.second.status}\",\"appliedStrength\":${pair.second.appliedStrength}}"
        }

    private fun qualityJson(results: Map<EnhancementLevel, Pair<RegionalCorrectionPlan, RegionalRenderResult>>) =
        results.entries.joinToString(prefix = "{\n", postfix = "\n}", separator = ",\n") { (level, pair) ->
            val m = pair.second.metrics
            "  \"${level.name}\":{\"passed\":${m.passed},\"newHighlightClipping\":${m.newHighlightClipping}," +
                "\"newShadowClipping\":${m.newShadowClipping},\"targetMeanLuminanceChange\":${m.targetMeanLuminanceChange}," +
                "\"protectedInteriorMeanDifference\":${m.protectedInteriorMeanDifference},\"boundaryDiscontinuity\":${m.boundaryDiscontinuity}," +
                "\"shadowChromaAmplification\":${m.shadowChromaAmplification},\"shadowNoiseAmplification\":${m.shadowNoiseAmplification}," +
                "\"maskLeakage\":${m.maskLeakage}," +
                "\"failures\":[${m.failureReasons.joinToString { "\"$it\"" }}]}"
        }

    private fun maskBitmap(mask: ConfidenceMask, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val v = (mask.sample((x + .5f) / width, (y + .5f) / height) * 255).toInt().coerceIn(0, 255)
            pixels[y * width + x] = Color.argb(255, v, v, v)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun overlay(original: Bitmap, mask: ConfidenceMask): Bitmap {
        val pixels = IntArray(original.width * original.height); original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
        for (y in 0 until original.height) for (x in 0 until original.width) {
            val i = y * original.width + x; val m = mask.sample((x + .5f) / original.width, (y + .5f) / original.height) * .42f
            val c = pixels[i]
            pixels[i] = Color.argb(Color.alpha(c), (Color.red(c) * (1 - m) + 255 * m).toInt(),
                (Color.green(c) * (1 - m) + 170 * m).toInt(), (Color.blue(c) * (1 - m)).toInt())
        }
        return Bitmap.createBitmap(pixels, original.width, original.height, Bitmap.Config.ARGB_8888)
    }

    private fun difference(before: RasterImage, after: RasterImage): Bitmap {
        val pixels = IntArray(before.argb.size)
        for (i in pixels.indices) {
            val a = before.argb[i]; val b = after.argb[i]
            pixels[i] = Color.rgb(min(255, abs(Color.red(a) - Color.red(b)) * 5),
                min(255, abs(Color.green(a) - Color.green(b)) * 5), min(255, abs(Color.blue(a) - Color.blue(b)) * 5))
        }
        return Bitmap.createBitmap(pixels, before.width, before.height, Bitmap.Config.ARGB_8888)
    }

    private fun boundaryCloseup(original: Bitmap, mask: ConfidenceMask, result: Bitmap): Bitmap {
        var minX = original.width; var minY = original.height; var maxX = 0; var maxY = 0
        for (y in 0 until mask.height) for (x in 0 until mask.width) if (mask.score(x, y) in .2f.. .8f) {
            minX = min(minX, x); minY = min(minY, y); maxX = max(maxX, x); maxY = max(maxY, y)
        }
        val sx = (minX.coerceAtMost(maxX) * original.width / mask.width).coerceIn(0, original.width - 1)
        val sy = (minY.coerceAtMost(maxY) * original.height / mask.height).coerceIn(0, original.height - 1)
        val ex = (((maxX + 1) * original.width / mask.width).coerceAtLeast(sx + 1)).coerceAtMost(original.width)
        val ey = (((maxY + 1) * original.height / mask.height).coerceAtLeast(sy + 1)).coerceAtMost(original.height)
        val cropWidth = min(ex - sx, max(1, original.width / 2)); val cropHeight = min(ey - sy, max(1, original.height / 2))
        val left = sx.coerceAtMost(original.width - cropWidth); val top = sy.coerceAtMost(original.height - cropHeight)
        val originalCrop = Bitmap.createBitmap(original, left, top, cropWidth, cropHeight)
        val resultCrop = Bitmap.createBitmap(result, left, top, cropWidth, cropHeight)
        val combined = Bitmap.createBitmap(cropWidth * 2, cropHeight, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(combined).apply { drawBitmap(originalCrop, 0f, 0f, null); drawBitmap(resultCrop, cropWidth.toFloat(), 0f, null) }
        originalCrop.recycle(); resultCrop.recycle(); result.recycle()
        return combined
    }

    private fun Bitmap.toRaster(): RasterImage {
        val pixels = IntArray(width * height); getPixels(pixels, 0, width, 0, 0, width, height)
        return RasterImage(width, height, pixels)
    }
    private fun RasterImage.toBitmap() = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    private fun writeBitmap(dir: File, name: String, bitmap: Bitmap) {
        FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun usedManaged() = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    companion object {
        val FIXTURES = listOf("M4_01_subject_protection.png", "M4_02_backlit_fine_hair.png", "M4_03_group_portrait.png")
    }
}
