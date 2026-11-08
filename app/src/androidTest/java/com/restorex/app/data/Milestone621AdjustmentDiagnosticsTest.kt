package com.restorex.app.data

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.restorex.app.RestoreXApplication
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticId
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.model.RasterImage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Private evidence generator. The source photograph is injected with run-as and never bundled. */
@RunWith(AndroidJUnit4::class)
class Milestone621AdjustmentDiagnosticsTest {
    private val app = ApplicationProvider.getApplicationContext<RestoreXApplication>()

    @Test fun exactSunriseProducesCompleteModeReports() = runBlocking {
        val sourceFile = File(app.filesDir, "m61-input/orig1.jpeg")
        assumeTrue("Private sunrise input was not injected", sourceFile.isFile)
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
        val source = try { bitmap.toRaster(1f) } finally { bitmap.recycle() }
        val coordinator = app.container.smartMagicCoordinator
        val analysis = scale(source, coordinator.processingBudget.analysisMaxDimension)
        val session = coordinator.createSession("private:m621-diagnostics", analysis)
        val reports = EnhancementLevel.entries.mapIndexed { index, level ->
            val render = coordinator.render(source, session, level)
            coordinator.adjustmentDiagnosticReport(session, render, 42, 100L + index)
        }
        reports.forEach { report ->
            assertEquals(AdjustmentDiagnosticId.entries.toSet(), report.entries.map { it.id }.toSet())
            assertEquals(AdjustmentDiagnosticId.entries.size, report.entries.size)
        }
        val output = File(requireNotNull(app.getExternalFilesDir(null)), "milestone6-2-1").apply { mkdirs() }
        File(output, "complete_adjustment_reports.json").writeText(JSONObject().apply {
            put("source", "orig1.jpeg")
            put("sessionIdentity", session.sessionId)
            put("reports", JSONArray(reports.map { report -> JSONObject().apply {
                put("sourceProcessingGeneration", report.sourceProcessingGeneration)
                put("renderGeneration", report.renderGeneration)
                put("mode", report.mode.name)
                put("entries", JSONArray(report.entries.map { entry -> JSONObject().apply {
                    put("id", entry.id.name); put("group", entry.id.group.name); put("name", entry.id.label)
                    put("status", entry.status.name); put("region", entry.region)
                    put("decisionReason", entry.decisionReason)
                    put("values", JSONArray(entry.values.map { value -> JSONObject().apply {
                        put("component", value.component); put("neutralValue", value.neutralValue)
                        put("proposedValue", value.proposedValue); put("appliedValue", value.appliedValue)
                        put("unit", value.unit)
                    } }))
                } }))
            } }))
        }.toString(2))
    }

    private fun scale(source: RasterImage, maxDimension: Int): RasterImage {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val sx = (x.toLong() * source.width / width).toInt().coerceAtMost(source.width - 1)
            val sy = (y.toLong() * source.height / height).toInt().coerceAtMost(source.height - 1)
            pixels[y * width + x] = source.argb[sy * source.width + sx]
        }
        return RasterImage(width, height, pixels, source.scaleFromOriginal * scale)
    }
}
