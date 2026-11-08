package com.restorex.app.data

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.ConfidenceMask
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.FaceEvidence
import com.restorex.app.domain.photointelligence.FacePersonAssociator
import com.restorex.app.domain.photointelligence.NormalizedRect
import com.restorex.app.domain.photointelligence.ProductionRenderStrategy
import com.restorex.app.domain.photointelligence.SemanticAnalysisResult
import com.restorex.app.domain.photointelligence.SemanticAnalyzer
import com.restorex.app.domain.photointelligence.SemanticClass
import com.restorex.app.domain.photointelligence.SemanticMaskReference
import com.restorex.app.domain.photointelligence.SemanticModelIdentity
import com.restorex.app.domain.photointelligence.SmartMagicWorkStage
import com.restorex.app.domain.photointelligence.SmartMagicWorkState
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticId
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticStatus
import com.restorex.app.domain.photointelligence.ProductionRenderOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartMagicProcessingCoordinatorTest {
    @Test fun `progress reports only work that actually executes and is monotonic`() = runBlocking {
        val source = gradient(96, 64)
        val coordinator = SmartMagicProcessingCoordinator(SmartMagicPipeline(), emptyAnalyzer(source.width, source.height))
        val updates = mutableListOf<com.restorex.app.domain.photointelligence.SmartMagicWorkUpdate>()
        val session = coordinator.createSession("progress", source, onProgress = { updates += it })
        assertTrue(updates.any { it.stage == SmartMagicWorkStage.ANALYZING_LIGHTING && it.state == SmartMagicWorkState.COMPLETED })
        assertTrue(updates.any { it.stage == SmartMagicWorkStage.DETECTING_SUBJECTS && it.state == SmartMagicWorkState.COMPLETED })
        val candidateCounts = updates.filter { it.stage == SmartMagicWorkStage.OPTIMIZING_TONAL_BALANCE && it.state == SmartMagicWorkState.ACTIVE }
            .mapNotNull { it.completedWorkUnits }
        assertEquals(candidateCounts.sorted(), candidateCounts)
        assertTrue(candidateCounts.all { it >= 0 })
        val renderUpdates = mutableListOf<com.restorex.app.domain.photointelligence.SmartMagicWorkUpdate>()
        coordinator.render(source, session, EnhancementLevel.NATURAL, onProgress = { renderUpdates += it })
        assertEquals(SmartMagicWorkState.STARTED, renderUpdates.first().state)
        assertEquals(SmartMagicWorkState.COMPLETED, renderUpdates.last().state)
    }

    @Test fun `adjustment summary uses actual regional operation units`() = runBlocking {
        val source = backlitFixture()
        val coordinator = SmartMagicProcessingCoordinator(SmartMagicPipeline(), personAnalyzer(source.width, source.height))
        val session = coordinator.createSession("summary", source)
        val render = coordinator.render(source, session, EnhancementLevel.EXTREME)
        val summary = coordinator.describeAdjustments(session, render)
        assertTrue(summary.isNotEmpty())
        if (render.strategy == ProductionRenderStrategy.REGIONAL_ONLY || render.strategy == ProductionRenderStrategy.COMBINED) {
            assertTrue(summary.any { it.value.contains("EV") || it.value.contains("normalized") || it.value.contains("gamma") })
        }
    }

    @Test fun `complete diagnostics preserve inventory provenance and accepted regional values`() = runBlocking {
        val source = backlitFixture()
        val coordinator = SmartMagicProcessingCoordinator(SmartMagicPipeline(), personAnalyzer(source.width, source.height))
        val session = coordinator.createSession("diagnostics", source)
        val render = coordinator.render(source, session, EnhancementLevel.EXTREME)
        val report = coordinator.adjustmentDiagnosticReport(session, render, 7, 11)
        assertEquals(AdjustmentDiagnosticId.entries.toSet(), report.entries.map { it.id }.toSet())
        assertEquals(AdjustmentDiagnosticId.entries.size, report.entries.size)
        assertEquals(7, report.sourceProcessingGeneration)
        assertEquals(11, report.renderGeneration)

        val operation = session.regionalPlans.getValue(EnhancementLevel.EXTREME).operations
            .first { it.id == "subject-exposure" }
        val exposure = report.entries.first { it.id == AdjustmentDiagnosticId.SUBJECT_EXPOSURE }
        assertEquals(AdjustmentDiagnosticStatus.APPLIED, exposure.status)
        val diagnosticValue = exposure.values.single()
        assertEquals(diagnosticValue.proposedValue!! * render.regionalResult!!.appliedStrength,
            diagnosticValue.appliedValue!!, 0f)
        assertTrue(diagnosticValue.proposedValue!! <= operation.amount)
        assertTrue(report.entries.first { it.id == AdjustmentDiagnosticId.EXPOSURE }.status in setOf(
            AdjustmentDiagnosticStatus.APPLIED, AdjustmentDiagnosticStatus.NOT_REQUIRED))
        assertEquals(AdjustmentDiagnosticStatus.NOT_EVALUATED,
            report.entries.first { it.id == AdjustmentDiagnosticId.BACKGROUND_EXPOSURE }.status)
        assertTrue(report.entries.first { it.id == AdjustmentDiagnosticId.BRIGHTNESS }.status in setOf(
            AdjustmentDiagnosticStatus.APPLIED, AdjustmentDiagnosticStatus.NOT_REQUIRED))
    }

    @Test fun `diagnostics distinguish rejected proposal from evaluated neutral`() = runBlocking {
        val personSource = backlitFixture()
        val personCoordinator = SmartMagicProcessingCoordinator(SmartMagicPipeline(), personAnalyzer(personSource.width, personSource.height))
        val personSession = personCoordinator.createSession("rejected", personSource)
        val accepted = personCoordinator.render(personSource, personSession, EnhancementLevel.ENHANCED)
        val rejected = accepted.copy(strategy = ProductionRenderStrategy.REGIONAL_ONLY,
            outcome = ProductionRenderOutcome.REJECTED_FOR_QUALITY)
        val rejectedReport = personCoordinator.adjustmentDiagnosticReport(personSession, rejected, 1, 2)
        assertEquals(AdjustmentDiagnosticStatus.REJECTED,
            rejectedReport.entries.first { it.id == AdjustmentDiagnosticId.SUBJECT_EXPOSURE }.status)

        val globalSource = gradient(96, 64)
        val globalCoordinator = SmartMagicProcessingCoordinator(SmartMagicPipeline(), emptyAnalyzer(96, 64))
        val globalSession = globalCoordinator.createSession("neutral", globalSource)
        val level = EnhancementLevel.NATURAL
        val neutralParameters = globalSession.globalParameters.getValue(level).copy(exposureEv = 0f)
        val neutralSession = globalSession.copy(globalParameters = globalSession.globalParameters + (level to neutralParameters),
            optimizationResults = emptyMap())
        val globalRender = globalCoordinator.render(globalSource, globalSession, level).copy(
            strategy = ProductionRenderStrategy.GLOBAL_ONLY, outcome = ProductionRenderOutcome.APPLIED)
        val neutralReport = globalCoordinator.adjustmentDiagnosticReport(neutralSession, globalRender, 3, 4)
        assertEquals(AdjustmentDiagnosticStatus.NOT_REQUIRED,
            neutralReport.entries.first { it.id == AdjustmentDiagnosticId.EXPOSURE }.status)
    }

    @Test fun `reliable person evidence combines global and residual regional production paths`() = runBlocking {
        val source = backlitFixture()
        val coordinator = SmartMagicProcessingCoordinator(SmartMagicPipeline(), personAnalyzer(source.width, source.height))
        val session = coordinator.createSession("fixture", source)
        val result = coordinator.render(source, session, EnhancementLevel.ENHANCED)
        assertTrue(result.strategy == ProductionRenderStrategy.COMBINED ||
            result.strategy == ProductionRenderStrategy.REGIONAL_ONLY)
        assertTrue(result.regionalResult != null)
        assertFalse(result.image.argb.contentEquals(source.argb))
        assertEquals(session.sessionId, session.regionalPlans.getValue(EnhancementLevel.ENHANCED).sourceIdentity)
    }

    @Test fun `no person uses independently calculated global plans`() = runBlocking {
        val source = gradient(96, 64)
        val coordinator = SmartMagicProcessingCoordinator(SmartMagicPipeline(), emptyAnalyzer(source.width, source.height))
        val session = coordinator.createSession("landscape", source)
        assertNotEquals(session.globalParameters.getValue(EnhancementLevel.NATURAL),
            session.globalParameters.getValue(EnhancementLevel.EXTREME))
        val result = coordinator.render(source, session, EnhancementLevel.ENHANCED)
        assertTrue(result.strategy == ProductionRenderStrategy.GLOBAL_ONLY || result.strategy == ProductionRenderStrategy.NO_OP)
    }

    @Test fun `stale rotated geometry is rejected`() = runBlocking {
        val source = gradient(96, 64)
        val coordinator = SmartMagicProcessingCoordinator(SmartMagicPipeline(), emptyAnalyzer(source.width, source.height))
        val session = coordinator.createSession("source-a", source)
        val rotated = RasterImage(64, 96, source.argb.copyOf())
        val failure = runCatching { coordinator.render(rotated, session, EnhancementLevel.NATURAL) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun `session fingerprint and plans are deterministic`() = runBlocking {
        val source = backlitFixture()
        val coordinator = SmartMagicProcessingCoordinator(SmartMagicPipeline(), personAnalyzer(source.width, source.height))
        val first = coordinator.createSession("same", source)
        val second = coordinator.createSession("same", source)
        assertEquals(first.sourceFingerprint, second.sourceFingerprint)
        assertEquals(first.sessionId, second.sessionId)
        assertEquals(first.regionalPlans, second.regionalPlans)
    }

    private fun personAnalyzer(width: Int, height: Int): SemanticAnalyzer = SemanticAnalyzer { _, _ ->
        val mask = ConfidenceMask.fromFloats("person", 32, 32, FloatArray(1024) { index ->
            val x = index % 32; if (x in 4..17) 1f else 0f
        }).feather(1)
        val face = FaceEvidence("face", NormalizedRect(.18f, .25f, .42f, .62f), .9f, .9f, emptyList())
        SemanticAnalysisResult(MODEL, MODEL, width, height, listOf(face), mask,
            SemanticMaskReference(mask.id, mask.width, mask.height, quality = mask.quality()),
            FacePersonAssociator.associate(listOf(face), mask), setOf(SemanticClass.FACE, SemanticClass.PERSON, SemanticClass.BACKGROUND),
            emptySet(), 1)
    }

    private fun emptyAnalyzer(width: Int, height: Int): SemanticAnalyzer = SemanticAnalyzer { _, _ ->
        SemanticAnalysisResult(MODEL, MODEL, width, height, emptyList(), null, null, emptyList(),
            setOf(SemanticClass.FACE, SemanticClass.PERSON, SemanticClass.BACKGROUND), emptySet(), 1)
    }

    private fun backlitFixture() = RasterImage(96, 64, IntArray(96 * 64) { index ->
        val x = index % 96
        if (x < 56) 0xff24232a.toInt() else 0xffd8e4ef.toInt()
    })

    private fun gradient(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { index ->
        val x = index % width; val y = index / width
        (0xff shl 24) or ((24 + x * 150 / width) shl 16) or ((32 + y * 140 / height) shl 8) or (48 + (x + y) * 90 / (width + height))
    })

    private companion object {
        val MODEL = SemanticModelIdentity("test", "test", "1", "sha")
    }
}
