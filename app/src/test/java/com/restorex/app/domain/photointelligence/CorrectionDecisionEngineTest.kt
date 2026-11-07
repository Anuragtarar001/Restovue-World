package com.restorex.app.domain.photointelligence

import org.junit.Assert.*
import org.junit.Test

class CorrectionDecisionEngineTest {
    private val analyzer = PhotometricAnalyzerV2(maxSamples = 12_000)
    private val diagnose = PhotographicDiagnosisEngine()
    private val planner = CorrectionDecisionEngine()

    private fun plans(image: com.restorex.app.domain.model.RasterImage, id: String = "fixture"): CorrectionPlanSet {
        val e = analyzer.analyze(image); val d = diagnose.diagnose(e)
        val bright = BrightRegionAnalyzer().analyze(image); val graph = CompositionReasoningEngine().build(e, bright)
        return planner.plan(id, e, d, graph)
    }

    @Test fun plansAreDeterministicAndIndependentlyCalculated() {
        val first = plans(TestImages.solid(value = 24)); val second = plans(TestImages.solid(value = 24))
        assertEquals(first, second)
        val values = listOf(first.natural, first.enhanced, first.extreme).map { p ->
            p.operations.firstOrNull { it.operation == CorrectionOperation.GLOBAL_EXPOSURE }?.parameters?.first()?.value
        }
        assertTrue(values.all { it != null })
        assertTrue(values.distinct().size >= 2)
    }

    @Test fun exposureCorrectionFallsAsInputBrightnessIncreases() {
        fun exposure(value: Int): Float = plans(TestImages.solid(value = value)).enhanced.operations
            .firstOrNull { it.operation == CorrectionOperation.GLOBAL_EXPOSURE }?.parameters?.first()?.value ?: 0f
        assertTrue(exposure(25) >= exposure(40))
        assertTrue(exposure(40) >= exposure(60))
    }

    @Test fun highlightClippingNeverIncreasesPermittedPositiveExposure() {
        val base = TestImages.centerVsBorder(center = 25, border = 180)
        val clipped = TestImages.centerVsBorder(center = 25, border = 255)
        fun exposure(image: com.restorex.app.domain.model.RasterImage): Float = plans(image).enhanced.operations
            .firstOrNull { it.operation == CorrectionOperation.GLOBAL_EXPOSURE }?.parameters?.first()?.value ?: 0f
        assertTrue(exposure(clipped) <= exposure(base) + 1e-5f)
    }

    @Test fun noiseConstrainsShadowRecovery() {
        fun shadow(image: com.restorex.app.domain.model.RasterImage): Float = plans(image).extreme.operations
            .firstOrNull { it.operation == CorrectionOperation.SHADOW_RECOVERY }?.parameters?.first()?.value ?: 0f
        assertTrue(shadow(TestImages.noisy()) <= shadow(TestImages.solid(value = 35)) + .01f)
    }

    @Test fun protectionCanVetoAnOtherwiseEligibleCorrection() {
        val plan = plans(TestImages.noisy(base = 22, amplitude = 22)).natural
        val highNoise = plan.protections.any { it.policyId == "PRT_HIGH_NOISE" && it.confidence >= .70f }
        if (highNoise) {
            assertFalse(plan.operations.any { it.operation == CorrectionOperation.SHADOW_RECOVERY || it.operation == CorrectionOperation.CLARITY })
            assertTrue(plan.rejectedOperations.any { it.reasonCode.startsWith("VETOED_") })
        }
    }

    @Test fun missingSemanticMaskDefersRegionalCorrection() {
        val plan = plans(TestImages.centerVsBorder(center = 25, border = 235)).enhanced
        assertTrue(plan.deferredOperations.any { it.requiredCapability == "SEMANTIC_PRIMARY_SUBJECT_MASK" })
        assertFalse(plan.operations.any { it.targetScope != "GLOBAL" })
    }

    @Test fun balancedImageProducesExplicitNoOp() {
        val width = 96; val height = 64
        val image = com.restorex.app.domain.model.RasterImage(width, height, IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            val value = if (((x / 8) + (y / 8)) % 3 == 0) 100 else 170
            -0x1000000 or (value shl 16) or (value shl 8) or value
        })
        val plan = plans(image).natural
        assertTrue(plan.operations.toString(), plan.operations.isEmpty())
        assertTrue(plan.explanation.any { it.code == "NO_OP" })
        assertTrue(plan.protections.any { it.policyId == "PRT_POTENTIAL_MONOCHROME" })
    }

    @Test fun allParametersStayFiniteAndBoundedAcrossPerturbations() {
        for (value in 0..255 step 3) {
            val set = plans(TestImages.solid(8, 8, value))
            listOf(set.natural, set.enhanced, set.extreme).flatMap { it.operations }.flatMap { it.parameters }.forEach {
                assertTrue(it.value.isFinite()); assertTrue(it.value in it.minimum..it.maximum)
            }
        }
    }

    @Test fun smallInputPerturbationsDoNotCauseLargeExposureJumps() {
        var previous: Float? = null
        for (value in 20..80) {
            val current = plans(TestImages.solid(8, 8, value)).enhanced.operations
                .firstOrNull { it.operation == CorrectionOperation.GLOBAL_EXPOSURE }?.parameters?.first()?.value ?: 0f
            previous?.let { assertTrue("jump at $value: $it -> $current", kotlin.math.abs(current - it) < .16f) }
            previous = current
        }
    }

    @Test fun correctionPlanSerializationRoundTrips() {
        val original = plans(TestImages.noisy()).enhanced
        assertEquals(original, CorrectionPlanCodec.decode(CorrectionPlanCodec.encode(original)))
    }

    @Test fun diagnosticsAreReproducibleAndDoNotExposeIdentity() {
        val image = TestImages.pointSource(); val e = analyzer.analyze(image); val d = diagnose.diagnose(e)
        val bright = BrightRegionAnalyzer().analyze(image); val graph = CompositionReasoningEngine().build(e, bright)
        val p = planner.plan("private/path/photo.jpg", e, d, graph)
        val json = DiagnosticJson.report("private/path/photo.jpg", e.copy(analysisDurationNanos = 0), d, graph, bright, p)
        assertFalse(json.contains("private/path")); assertTrue(json.contains("\"schemaVersion\":2")); assertTrue(json.contains("sourceHash"))
    }
}
