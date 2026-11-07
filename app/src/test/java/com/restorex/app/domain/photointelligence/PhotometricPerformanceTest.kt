package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotometricPerformanceTest {
    @Test fun boundedAnalysisOnSmall12And48MegapixelSources() {
        val analyzer = PhotometricAnalyzerV2(maxSamples = 262_144)
        val results = listOf(640 to 480, 4000 to 3000, 8000 to 6000).map { (width, height) ->
            val source = RasterImage(width, height, IntArray(width * height) { index ->
                val x = index % width; val value = 24 + (x * 208 / width)
                -0x1000000 or (value shl 16) or (value shl 8) or value
            })
            val start = System.nanoTime(); val evidence = analyzer.analyze(source); val elapsed = System.nanoTime() - start
            Triple(width.toLong() * height, evidence.sampledPixels, elapsed)
        }
        results.forEach { (sourcePixels, analyzedPixels, nanos) ->
            println("M2_BENCH sourcePixels=$sourcePixels analyzedPixels=$analyzedPixels analysisMs=${nanos / 1_000_000.0}")
            assertTrue(analyzedPixels <= 262_144)
            assertTrue(nanos > 0)
        }
        assertEquals(80, analyzer.analyze(TestImages.gradient()).regions.size)
        val knowledgeStarted = System.nanoTime()
        val knowledge = Milestone2Knowledge.create()
        val validation = SmartValueKnowledgeValidator().validate(knowledge)
        val knowledgeNanos = System.nanoTime() - knowledgeStarted
        val evidence = analyzer.analyze(TestImages.centerVsBorder(25, 225))
        val diagnoses = PhotographicDiagnosisEngine().diagnose(evidence)
        val graph = CompositionReasoningEngine().build(evidence, emptyList())
        val planningStarted = System.nanoTime()
        val plans = CorrectionDecisionEngine(knowledge).plan("benchmark", evidence, diagnoses, graph)
        val planningNanos = System.nanoTime() - planningStarted
        println("M2_BENCH knowledgeCreateValidateMs=${knowledgeNanos / 1_000_000.0} planningMs=${planningNanos / 1_000_000.0}")
        assertTrue(validation.isValid)
        assertTrue(plans.enhanced.analysisVersion.isNotBlank())
    }
}
