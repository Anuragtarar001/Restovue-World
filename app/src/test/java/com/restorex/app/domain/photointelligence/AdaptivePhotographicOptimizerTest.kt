package com.restorex.app.domain.photointelligence

import com.restorex.app.data.SmartMagicPipeline
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicParameterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePhotographicOptimizerTest {
    @Test fun `optimizer is deterministic and obeys candidate budget`() {
        val source = gradient(144, 96)
        val evidence = PhotometricAnalyzerV2().analyze(source)
        val diagnoses = PhotographicDiagnosisEngine().diagnose(evidence)
        val advanced = AdvancedPhotometricAnalyzer().analyze(source, evidence, diagnoses)
        val smartPipeline = SmartMagicPipeline()
        val base = SmartMagicParameterEngine(.7f).parametersFor(smartPipeline.analyze(source))
        fun optimize() = AdaptivePhotographicOptimizer().optimize(
            source, evidence, advanced, EnhancementLevel.ENHANCED, base, 4,
        ) { parameters, cancel -> smartPipeline.enhance(source, parameters, cancel) }
        val first = optimize(); val second = optimize()
        assertEquals(first.selectedCandidateId, second.selectedCandidateId)
        assertEquals(first.selectedParameters, second.selectedParameters)
        assertTrue(first.evaluations.size <= 4)
        assertTrue(first.evaluations.all { it.score.isFinite() })
    }

    @Test fun `processing budget adapts workload without changing export dimensions`() {
        val constrained = SmartMagicProcessingBudgetPolicy.forRuntime(160L * 1024 * 1024, 4)
        val mainstream = SmartMagicProcessingBudgetPolicy.forRuntime(256L * 1024 * 1024, 8)
        val fast = SmartMagicProcessingBudgetPolicy.forRuntime(512L * 1024 * 1024, 8)
        assertTrue(constrained.previewMaxDimension < mainstream.previewMaxDimension)
        assertTrue(mainstream.previewMaxDimension < fast.previewMaxDimension)
        assertTrue(constrained.optimizerCandidateBudget < fast.optimizerCandidateBudget)
    }

    private fun gradient(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { index ->
        val x = index % width; val y = index / width
        val v = 18 + x * 150 / width + y * 28 / height
        (0xff shl 24) or ((v + 9).coerceAtMost(255) shl 16) or ((v + 4).coerceAtMost(255) shl 8) or v.coerceAtMost(255)
    })
}
