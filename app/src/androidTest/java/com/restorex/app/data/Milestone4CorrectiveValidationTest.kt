package com.restorex.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.BrightRegionAnalyzer
import com.restorex.app.domain.photointelligence.CompositionReasoningEngine
import com.restorex.app.domain.photointelligence.ConfidenceMask
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.PhotographicDiagnosisEngine
import com.restorex.app.domain.photointelligence.PhotometricAnalyzerV2
import com.restorex.app.domain.photointelligence.RegionalCorrectionPlan
import com.restorex.app.domain.photointelligence.RegionalCorrectionRenderer
import com.restorex.app.domain.photointelligence.RegionalOperation
import com.restorex.app.domain.photointelligence.RegionalOperationType
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max

/** Corrective validation: controlled corrections are deliberately separate from automatic plans. */
@RunWith(AndroidJUnit4::class)
class Milestone4CorrectiveValidationTest {
    @Test fun automaticAndControlledRegionalBuffersSurviveLosslessExport() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val test = InstrumentationRegistry.getInstrumentation().context
        val root = File(requireNotNull(app.getExternalFilesDir(null)), "milestone4-corrective-validation").apply { mkdirs() }
        LiteRtSemanticAnalyzer(app).use { semanticAnalyzer ->
            FIXTURES.forEach { filename ->
                val sourceBitmap = requireNotNull(test.assets.open("milestone4/$filename").use(BitmapFactory::decodeStream))
                val source = sourceBitmap.toRaster()
                val directory = File(root, filename.removeSuffix(".png")).apply { mkdirs() }
                writeAndReopen(directory, "original.png", source)

                val semantic = semanticAnalyzer.analyze(source) { false }
                assertNull(semantic.failureDetail, semantic.failureReason)
                val mask = requireNotNull(semantic.personMask)
                val maskQuality = requireNotNull(semantic.personMaskReference).quality
                assertTrue(maskQuality.accepted)
                val evidence = PhotometricAnalyzerV2().analyze(source)
                val diagnoses = PhotographicDiagnosisEngine().diagnose(evidence)
                val bright = BrightRegionAnalyzer().analyze(source)
                val regional = SemanticRegionPhotometricAnalyzer().analyze(source, mask)
                val graph = SemanticCompositionEngine().integrate(
                    CompositionReasoningEngine().build(evidence, bright), semantic, regional)
                val assessment = SemanticSubjectDiagnosisEngine().assess(semantic, regional)
                val supplement = SemanticCorrectionPlanner().plan(filename, evidence, diagnoses, graph, semantic, regional, assessment)
                val automatic = linkedMapOf<EnhancementLevel, Pair<RegionalCorrectionPlan, RegionalRenderResult>>()
                for (level in EnhancementLevel.entries) {
                    val plan = RegionalPlanBuilder().build(filename, 0, level, supplement, regional, mask.id)
                    val result = RegionalCorrectionRenderer().render(RegionalRenderRequest(
                        source, filename, 0, plan, mask, maskQuality, semantic.faces, regional))
                    automatic[level] = plan to result
                    val reopened = writeAndReopen(directory, "automatic_${level.name.lowercase()}_export.png", result.image)
                    assertArrayEquals("PNG export changed automatic RGB for $filename/$level", result.image.argb, reopened.argb)
                }

                val subjectPlan = controlledPlan(filename, mask.id, RegionalTarget.PERSON, .12f)
                val subjectResult = RegionalCorrectionRenderer().render(RegionalRenderRequest(
                    source, filename, 0, subjectPlan, mask, maskQuality, semantic.faces, regional))
                val subjectExport = writeAndReopen(directory, "controlled_subject_export.png", subjectResult.image)
                assertArrayEquals(subjectResult.image.argb, subjectExport.argb)
                val subjectDiff = pixelDifference(source, subjectExport, mask, RegionalTarget.PERSON)

                val backgroundPlan = controlledPlan(filename, mask.id, RegionalTarget.BACKGROUND, .08f)
                val backgroundResult = RegionalCorrectionRenderer().render(RegionalRenderRequest(
                    source, filename, 0, backgroundPlan, mask, maskQuality, semantic.faces, regional))
                val backgroundExport = writeAndReopen(directory, "controlled_background_export.png", backgroundResult.image)
                assertArrayEquals(backgroundResult.image.argb, backgroundExport.argb)
                val backgroundDiff = pixelDifference(source, backgroundExport, mask, RegionalTarget.BACKGROUND)

                if (filename == "M4_01_subject_protection.png") {
                    assertTrue("Controlled subject correction produced no changed RGB pixels", subjectDiff.changedPixels > 0)
                    assertTrue("Controlled background correction produced no changed RGB pixels", backgroundDiff.changedPixels > 0)
                    assertTrue("Subject operation did not favor subject interior", subjectDiff.targetMeanAbsDifference > subjectDiff.protectedMeanAbsDifference)
                    assertTrue("Background operation did not favor background interior", backgroundDiff.targetMeanAbsDifference > backgroundDiff.protectedMeanAbsDifference)
                    assertTrue("Subject protected interior tolerance exceeded", subjectDiff.protectedMeanAbsDifference <= PROTECTED_MEAN_TOLERANCE)
                    assertTrue("Background protected interior tolerance exceeded", backgroundDiff.protectedMeanAbsDifference <= PROTECTED_MEAN_TOLERANCE)
                }

                writeDifference(directory, "controlled_subject_difference.png", source, subjectExport)
                writeDifference(directory, "controlled_background_difference.png", source, backgroundExport)
                writeBoundary(directory, "controlled_subject_boundary.png", source, subjectExport, mask)
                writeBoundary(directory, "controlled_background_boundary.png", source, backgroundExport, mask)
                val trace = traceJson(filename, source, semantic.faces.size, mask, assessment.decision.name,
                    diagnoses.map { it.conditionId }, automatic, subjectPlan, subjectResult, subjectDiff,
                    backgroundPlan, backgroundResult, backgroundDiff)
                File(directory, "execution_trace.json").writeText(trace)
                sourceBitmap.recycle()
            }
        }
    }

    private fun controlledPlan(source: String, maskId: String, target: RegionalTarget, ev: Float) =
        RegionalCorrectionPlan(sourceIdentity = source, transformationVersion = 0, maskId = maskId,
            level = EnhancementLevel.NATURAL, operations = listOf(RegionalOperation(
                id = "CONTROLLED_${target.name}_EXPOSURE", type = RegionalOperationType.EXPOSURE,
                target = target, amount = ev, unit = "EV", confidence = 1f,
                evidenceReferences = listOf("ENGINEERING_CONTROLLED_TEST_NOT_PRODUCTION_DECISION"),
                qualityChecks = listOf("NO_NEW_CLIPPING", "PROTECTED_INTERIOR", "BOUNDARY_DISCONTINUITY"))),
            protections = emptyList(), explanation = emptyList())

    private fun pixelDifference(before: RasterImage, after: RasterImage, mask: ConfidenceMask, target: RegionalTarget): DifferenceMetrics {
        var changed = 0L; var sum = 0L; var maximum = 0
        var targetSum = 0L; var targetChannels = 0L; var protectedSum = 0L; var protectedChannels = 0L
        var boundarySum = 0L; var boundaryChannels = 0L
        for (y in 0 until before.height) for (x in 0 until before.width) {
            val i = y * before.width + x; val a = before.argb[i]; val b = after.argb[i]
            var pixelChanged = false; var pixelSum = 0
            for (shift in intArrayOf(16, 8, 0)) {
                val difference = abs((a ushr shift and 255) - (b ushr shift and 255))
                if (difference > 0) pixelChanged = true
                pixelSum += difference; maximum = max(maximum, difference)
            }
            if (pixelChanged) changed++
            sum += pixelSum
            val person = mask.sample((x + .5f) / before.width, (y + .5f) / before.height)
            val targetWeight = if (target == RegionalTarget.PERSON) person else 1f - person
            when {
                targetWeight >= .8f -> { targetSum += pixelSum; targetChannels += 3 }
                targetWeight <= .05f -> { protectedSum += pixelSum; protectedChannels += 3 }
                person in .2f.. .8f -> { boundarySum += pixelSum; boundaryChannels += 3 }
            }
        }
        val totalPixels = before.argb.size.toLong()
        return DifferenceMetrics(changed, changed * 100.0 / totalPixels, sum.toDouble() / (totalPixels * 3), maximum,
            targetSum.toDouble() / targetChannels.coerceAtLeast(1), protectedSum.toDouble() / protectedChannels.coerceAtLeast(1),
            boundarySum.toDouble() / boundaryChannels.coerceAtLeast(1))
    }

    private fun traceJson(filename: String, source: RasterImage, faceCount: Int, mask: ConfidenceMask, subjectDecision: String,
        diagnoses: List<String>, automatic: Map<EnhancementLevel, Pair<RegionalCorrectionPlan, RegionalRenderResult>>,
        subjectPlan: RegionalCorrectionPlan, subjectResult: RegionalRenderResult, subjectDiff: DifferenceMetrics,
        backgroundPlan: RegionalCorrectionPlan, backgroundResult: RegionalRenderResult, backgroundDiff: DifferenceMetrics): String {
        fun operationJson(operation: RegionalOperation) =
            "{\"id\":\"${operation.id}\",\"type\":\"${operation.type}\",\"target\":\"${operation.target}\",\"amount\":${operation.amount},\"unit\":\"${operation.unit}\"}"
        fun metricsJson(value: DifferenceMetrics) =
            "{\"changedPixels\":${value.changedPixels},\"changedPercent\":${value.changedPercent},\"meanAbsRgbDifference\":${value.meanAbsRgbDifference},\"maxChannelDifference\":${value.maxChannelDifference},\"targetInteriorMeanAbsDifference\":${value.targetMeanAbsDifference},\"protectedInteriorMeanAbsDifference\":${value.protectedMeanAbsDifference},\"boundaryBandMeanAbsDifference\":${value.boundaryMeanAbsDifference}}"
        val automaticJson = automatic.entries.joinToString(",") { (level, pair) ->
            val diff = pixelDifference(source, pair.second.image, mask, pair.first.operations.firstOrNull()?.target ?: RegionalTarget.PERSON)
            val operations = pair.first.operations.joinToString(",") { operationJson(it) }
            "\"${level.name}\":{\"plannerDisposition\":\"${if (pair.first.operations.isEmpty()) "NO_OP" else "EXECUTABLE"}\",\"operations\":[$operations],\"rendererInvoked\":true,\"renderStatus\":\"${pair.second.status}\",\"appliedStrength\":${pair.second.appliedStrength},\"correctedBufferGenerated\":${diff.changedPixels > 0},\"exportPath\":\"automatic_${level.name.lowercase()}_export.png\",\"pixelDifference\":${metricsJson(diff)},\"qualityFailures\":[${pair.second.metrics.failureReasons.joinToString { "\"$it\"" }}]}"
        }
        val q = mask.quality()
        return """{
  "schemaVersion":1,
  "fixture":"$filename",
  "fixtureRole":"ORIGINAL_TEST_INPUT_NOT_CORRECTED_OUTPUT",
  "diagnoses":[${diagnoses.joinToString { "\"$it\"" }}],
  "semanticSubjectDecision":"$subjectDecision",
  "faceCount":$faceCount,
  "mask":{"id":"${mask.id}","width":${mask.width},"height":${mask.height},"accepted":${q.accepted},"weightedAreaFraction":${q.weightedAreaFraction},"meanForegroundScore":${q.meanForegroundScore},"boundaryUncertainty":${q.boundaryUncertainty}},
  "productionIntegration":{"regionalPreviewWired":false,"regionalExportWired":false,"finding":"Production SmartMagicViewModel and SmartMagicExportManager use the existing global SmartMagicPipeline; regional outputs are currently validation-only."},
  "automatic":{$automaticJson},
  "controlledSubject":{"engineeringTestOnly":true,"operation":${operationJson(subjectPlan.operations.single())},"rendererInvoked":true,"renderStatus":"${subjectResult.status}","correctedBufferGenerated":${subjectDiff.changedPixels > 0},"exportPath":"controlled_subject_export.png","losslessExportMatchesCorrectedBuffer":true,"pixelDifference":${metricsJson(subjectDiff)},"vetoes":[${subjectResult.metrics.failureReasons.joinToString { "\"$it\"" }}]},
  "controlledBackground":{"engineeringTestOnly":true,"operation":${operationJson(backgroundPlan.operations.single())},"rendererInvoked":true,"renderStatus":"${backgroundResult.status}","correctedBufferGenerated":${backgroundDiff.changedPixels > 0},"exportPath":"controlled_background_export.png","losslessExportMatchesCorrectedBuffer":true,"pixelDifference":${metricsJson(backgroundDiff)},"vetoes":[${backgroundResult.metrics.failureReasons.joinToString { "\"$it\"" }}]}
}"""
    }

    private fun writeAndReopen(directory: File, name: String, raster: RasterImage): RasterImage {
        val bitmap = raster.toBitmap()
        FileOutputStream(File(directory, name)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val reopened = requireNotNull(BitmapFactory.decodeFile(File(directory, name).absolutePath))
        return reopened.toRaster().also { reopened.recycle() }
    }

    private fun writeDifference(directory: File, name: String, before: RasterImage, after: RasterImage) {
        val pixels = IntArray(before.argb.size)
        for (i in pixels.indices) {
            val a = before.argb[i]; val b = after.argb[i]
            val red = (abs((a ushr 16 and 255) - (b ushr 16 and 255)) * 6).coerceAtMost(255)
            val green = (abs((a ushr 8 and 255) - (b ushr 8 and 255)) * 6).coerceAtMost(255)
            val blue = (abs((a and 255) - (b and 255)) * 6).coerceAtMost(255)
            pixels[i] = -0x1000000 or (red shl 16) or (green shl 8) or blue
        }
        writeAndReopen(directory, name, RasterImage(before.width, before.height, pixels))
    }

    private fun writeBoundary(directory: File, name: String, before: RasterImage, after: RasterImage, mask: ConfidenceMask) {
        val pixels = IntArray(before.argb.size)
        for (y in 0 until before.height) for (x in 0 until before.width) {
            val i = y * before.width + x; val score = mask.sample((x + .5f) / before.width, (y + .5f) / before.height)
            pixels[i] = if (score in .2f.. .8f) after.argb[i] else before.argb[i]
        }
        writeAndReopen(directory, name, RasterImage(before.width, before.height, pixels))
    }

    private fun Bitmap.toRaster(): RasterImage {
        val pixels = IntArray(width * height); getPixels(pixels, 0, width, 0, 0, width, height)
        return RasterImage(width, height, pixels)
    }
    private fun RasterImage.toBitmap() = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)

    data class DifferenceMetrics(
        val changedPixels: Long, val changedPercent: Double, val meanAbsRgbDifference: Double,
        val maxChannelDifference: Int, val targetMeanAbsDifference: Double,
        val protectedMeanAbsDifference: Double, val boundaryMeanAbsDifference: Double,
    )

    companion object {
        const val PROTECTED_MEAN_TOLERANCE = 1.25
        val FIXTURES = listOf("M4_01_subject_protection.png", "M4_02_backlit_fine_hair.png", "M4_03_group_portrait.png")
    }
}
