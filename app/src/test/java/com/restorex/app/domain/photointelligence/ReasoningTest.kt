package com.restorex.app.domain.photointelligence

import org.junit.Assert.*
import org.junit.Test

class ReasoningTest {
    private val analyzer = PhotometricAnalyzerV2(maxSamples = 12_000)
    private val diagnosis = PhotographicDiagnosisEngine()

    private fun ids(image: com.restorex.app.domain.model.RasterImage) = diagnosis.diagnose(analyzer.analyze(image)).map { it.conditionId }.toSet()

    @Test fun underexposedAndOverexposedAreMultiLabelDiagnoses() {
        val dark = ids(TestImages.solid(value = 22)); val bright = ids(TestImages.solid(value = 245))
        assertTrue("PHT_GLOBAL_UNDEREXPOSED" in dark)
        assertTrue("PHT_EXCESSIVE_SHADOW_OCCUPANCY" in dark)
        assertTrue("PHT_GLOBAL_OVEREXPOSED" in bright)
        assertTrue("PHT_EXCESSIVE_HIGHLIGHT_OCCUPANCY" in bright)
    }

    @Test fun backlightPatternDoesNotClaimASubject() {
        val e = analyzer.analyze(TestImages.centerVsBorder(center = 30, border = 230))
        assertTrue(diagnosis.diagnose(e).any { it.conditionId == "PHT_STRONG_BACKLIGHT_PATTERN" })
        val graph = CompositionReasoningEngine().build(e, emptyList())
        assertNull(graph.semanticPrimarySubjectId)
        assertTrue(graph.unresolvedPrimarySubject)
        assertTrue(graph.nodes.all { it.priority == CompositionPriority.UNRESOLVED && it.semanticClass == null })
    }

    @Test fun pointAndBroadBrightnessAreDistinguished() {
        val point = BrightRegionAnalyzer().analyze(TestImages.pointSource())
        val broad = BrightRegionAnalyzer().analyze(TestImages.pointSource(broad = true))
        assertTrue(point.any { it.kind == BrightRegionKind.POINT_SOURCE || it.kind == BrightRegionKind.SPECULAR_CANDIDATE })
        assertTrue(broad.any { it.kind == BrightRegionKind.BROAD_BRIGHT_SURFACE })
        assertFalse(broad.any { it.kind == BrightRegionKind.POINT_SOURCE && it.areaFraction > .2f })
    }

    @Test fun noiseRiskAndSaturationAreDiagnosed() {
        assertTrue(ids(TestImages.noisy()).contains("PHT_HIGH_NOISE_RISK"))
        assertTrue(ids(TestImages.saturated()).contains("PHT_EXCESSIVE_CHROMA"))
    }

    @Test fun twentySyntheticScenarioMatrixProducesFiniteEvidence() {
        val scenarios = listOf(
            TestImages.gradient(), TestImages.solid(value = 20), TestImages.solid(value = 245),
            TestImages.centerVsBorder(25, 230), TestImages.centerVsBorder(35, 220), TestImages.centerVsBorder(220, 25),
            TestImages.gradient(low = 90, high = 145), TestImages.solid(value = 225), TestImages.solid(value = 24),
            TestImages.centerVsBorder(8, 80), TestImages.gradient(low = 25, high = 160), TestImages.solid(value = 235),
            TestImages.pointSource(), TestImages.centerVsBorder(30, 245), TestImages.gradient(low = 105, high = 155),
            TestImages.noisy(), TestImages.saturated(), TestImages.solid(value = 128), TestImages.solid(value = 0),
            TestImages.gradient(3, 97),
        )
        assertEquals(20, scenarios.size)
        scenarios.forEach { image ->
            val e = analyzer.analyze(image); val d = diagnosis.diagnose(e)
            assertTrue(e.luminance.mean.isFinite())
            assertTrue(d.all { it.severity.isFinite() && it.confidence.isFinite() })
        }
    }
}
