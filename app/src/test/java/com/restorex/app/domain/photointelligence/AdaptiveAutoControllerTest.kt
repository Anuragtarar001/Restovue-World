package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveAutoControllerTest {
    @Test fun `plan is deterministic bounded and coordinates levels with contrast`() {
        val source = flatGradient(180, 120, 38, 105)
        val photometric = PhotometricAnalyzerV2().analyze(source)
        val advanced = AdvancedPhotometricAnalyzer().analyze(
            source, photometric, PhotographicDiagnosisEngine().diagnose(photometric))
        val analysis = SmartMagicAnalyzer().analyze(source)

        val first = AdaptiveAutoController().plan(analysis, advanced)
        val second = AdaptiveAutoController().plan(analysis, advanced)

        assertEquals(first, second)
        assertTrue(first.autoLevelsStrength in 0f..1f)
        assertTrue(first.autoContrastStrength in 0f..1f)
        assertTrue(first.autoEnhanceStrength in 0f..1f)
        assertTrue(first.baseline.redGain in .955f..1.045f)
        assertTrue(first.explanation.any { it.code == "AUTO_OPERATIONS_COORDINATED" })
    }

    @Test fun `modes share technical baseline and vary bounded finishing`() {
        val source = flatGradient(160, 100, 26, 122)
        val photometric = PhotometricAnalyzerV2().analyze(source)
        val advanced = AdvancedPhotometricAnalyzer().analyze(
            source, photometric, PhotographicDiagnosisEngine().diagnose(photometric))
        val controller = AdaptiveAutoController()
        val plan = controller.plan(SmartMagicAnalyzer().analyze(source), advanced)
        val natural = controller.parametersForMode(plan, EnhancementLevel.NATURAL)
        val extreme = controller.parametersForMode(plan, EnhancementLevel.EXTREME)

        assertEquals(natural.exposureEv, extreme.exposureEv, 0f)
        assertEquals(natural.brightness, extreme.brightness, 0f)
        assertEquals(natural.midtoneGamma, extreme.midtoneGamma, 0f)
        assertEquals(natural.redGain, extreme.redGain, 0f)
        assertTrue(extreme.vibrance + 1e-6f >= natural.vibrance)
        assertTrue(extreme.overallStrength > natural.overallStrength)
    }

    private fun flatGradient(width: Int, height: Int, low: Int, high: Int) = RasterImage(
        width, height, IntArray(width * height) { index ->
            val x = index % width
            val v = low + (high - low) * x / (width - 1).coerceAtLeast(1)
            (0xff shl 24) or ((v + 4).coerceAtMost(255) shl 16) or
                ((v + 2).coerceAtMost(255) shl 8) or v
        })
}
