package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedPhotometricAnalyzerTest {
    @Test fun `advanced evidence is deterministic and bounded`() {
        val source = hazyGradient()
        val photometric = PhotometricAnalyzerV2().analyze(source)
        val diagnoses = PhotographicDiagnosisEngine().diagnose(photometric)
        val first = AdvancedPhotometricAnalyzer().analyze(source, photometric, diagnoses)
        val second = AdvancedPhotometricAnalyzer().analyze(source, photometric, diagnoses)
        assertEquals(first.copy(analysisDurationNanos = 0), second.copy(analysisDurationNanos = 0))
        assertTrue(first.localContrastDeficit in 0f..1f)
        assertTrue(first.hazeConfidence in 0f..1f)
        assertTrue(first.analysisDurationNanos > 0)
    }

    @Test fun `low key evidence suppresses shadow recovery confidence`() {
        val source = RasterImage(96, 64, IntArray(96 * 64) { index ->
            val value = 5 + (index % 96) * 18 / 96
            (0xff shl 24) or (value shl 16) or (value shl 8) or value
        })
        val p = PhotometricAnalyzerV2().analyze(source)
        val evidence = AdvancedPhotometricAnalyzer().analyze(source, p, PhotographicDiagnosisEngine().diagnose(p))
        assertTrue(evidence.lowKeyIntent > .5f)
        assertTrue(evidence.shadowRecoveryConfidence < .25f)
    }

    @Test fun `warm ambiguous scene records intent instead of forced neutralization`() {
        val source = RasterImage(96, 64, IntArray(96 * 64) { index ->
            val x = index % 96
            (0xff shl 24) or ((150 + x / 4) shl 16) or ((78 + x / 8) shl 8) or (32 + x / 10)
        })
        val p = PhotometricAnalyzerV2().analyze(source)
        val evidence = AdvancedPhotometricAnalyzer().analyze(source, p, PhotographicDiagnosisEngine().diagnose(p))
        assertTrue(evidence.warmLightIntent > 0f)
        assertTrue(evidence.atmosphericIntentRisk >= evidence.warmLightIntent)
    }

    private fun hazyGradient() = RasterImage(128, 72, IntArray(128 * 72) { index ->
        val x = index % 128; val y = index / 128
        val base = 142 + x * 22 / 128 + y * 8 / 72
        (0xff shl 24) or ((base + 3).coerceAtMost(255) shl 16) or (base shl 8) or (base - 3)
    })
}
