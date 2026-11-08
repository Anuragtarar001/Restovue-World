package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalCorrectionRendererTest {
    @Test fun `subject exposure changes subject interior and protects distant background`() {
        val fixture = fixture()
        val result = RegionalCorrectionRenderer().render(fixture.request(operation(RegionalTarget.PERSON, .35f)))
        assertTrue(result.status == RegionalRenderStatus.APPLIED || result.status == RegionalRenderStatus.REDUCED)
        assertTrue(luma(result.image.argb[pixel(8, 8)]) > luma(fixture.image.argb[pixel(8, 8)]))
        assertEquals(fixture.image.argb[pixel(27, 8)], result.image.argb[pixel(27, 8)])
        assertTrue(result.metrics.protectedInteriorMeanDifference < .0045f)
    }

    @Test fun `background exposure protects subject interior`() {
        val fixture = fixture()
        val result = RegionalCorrectionRenderer().render(fixture.request(operation(RegionalTarget.BACKGROUND, .25f)))
        assertEquals(fixture.image.argb[pixel(6, 8)], result.image.argb[pixel(6, 8)])
        assertTrue(luma(result.image.argb[pixel(27, 8)]) > luma(fixture.image.argb[pixel(27, 8)]))
    }

    @Test fun `soft mask produces a partial linear light blend`() {
        val image = RasterImage(32, 16, IntArray(512) { 0xff404040.toInt() })
        val values = FloatArray(128) { index -> when (index % 16) { in 0..5 -> 1f; in 6..9 -> .5f; else -> 0f } }
        val mask = ConfidenceMask.fromFloats("soft", 16, 8, values)
        val fixture = fixture(image, mask)
        val result = RegionalCorrectionRenderer().render(fixture.request(operation(RegionalTarget.PERSON, .5f))).image
        val full = luma(result.argb[pixel(4, 8)]); val partial = luma(result.argb[pixel(15, 8)]); val none = luma(result.argb[pixel(27, 8)])
        assertTrue(full > partial && partial > none)
        // A 0.5 mask is not equivalent to averaging gamma-encoded bytes.
        assertNotEquals((channel(result.argb[pixel(4, 8)]) + 64) / 2, channel(result.argb[pixel(15, 8)]))
    }

    @Test fun `fully transparent pixels retain hidden color and alpha`() {
        val fixture = fixture()
        fixture.image.argb[0] = 0x00123456
        val result = RegionalCorrectionRenderer().render(fixture.request(operation(RegionalTarget.PERSON, .3f)))
        assertEquals(0x00123456, result.image.argb[0])
    }

    @Test fun `invalid masks and stale transforms preserve source`() {
        val fixture = fixture()
        val invalid = fixture.request(operation(RegionalTarget.PERSON, .3f)).copy(
            maskQuality = fixture.mask.quality().copy(accepted = false, rejectionReasons = listOf(MaskRejectionReason.LOW_CONFIDENCE)))
        assertEquals(RegionalRenderStatus.SKIPPED_INVALID_MASK, RegionalCorrectionRenderer().render(invalid).status)
        val stale = fixture.request(operation(RegionalTarget.PERSON, .3f)).copy(transformationVersion = 2)
        val staleResult = RegionalCorrectionRenderer().render(stale)
        assertEquals(RegionalRenderStatus.SKIPPED_STALE_SESSION, staleResult.status)
        assertArrayEquals(fixture.image.argb, staleResult.image.argb)
    }

    @Test fun `protection policy vetoes its affected operation`() {
        val fixture = fixture()
        val request = fixture.request(operation(RegionalTarget.PERSON, .3f)).let { base ->
            base.copy(plan = base.plan.copy(protections = listOf(
                ProtectionDecision("PRT_FACE", DecisionDisposition.PROTECT, "FACE_ALREADY_EXPOSED", 1f, listOf("test")))))
        }
        val result = RegionalCorrectionRenderer().render(request)
        assertEquals(RegionalRenderStatus.SKIPPED_PROTECTION, result.status)
        assertArrayEquals(fixture.image.argb, result.image.argb)
    }

    @Test fun `face and group guards reduce positive subject exposure`() {
        val fixture = fixture()
        val operation = operation(RegionalTarget.PERSON, .55f)
        val noFace = RegionalCorrectionRenderer().render(fixture.request(operation)).image
        val face = FaceEvidence("face", NormalizedRect(.1f, .2f, .4f, .8f), .9f, .9f, emptyList())
        val group = RegionalCorrectionRenderer().render(fixture.request(operation).copy(faces = listOf(face, face.copy(id = "face2")))).image
        assertTrue(luma(group.argb[pixel(8, 8)]) < luma(noFace.argb[pixel(8, 8)]))
    }

    @Test fun `empty plan is an explicit no op`() {
        val fixture = fixture()
        val result = RegionalCorrectionRenderer().render(fixture.request())
        assertEquals(RegionalRenderStatus.NO_OP, result.status)
        assertArrayEquals(fixture.image.argb, result.image.argb)
    }

    @Test fun `correction lost to output quantization is reported as no op`() {
        val fixture = fixture()
        val result = RegionalCorrectionRenderer().render(
            fixture.request(operation(RegionalTarget.PERSON, .000001f)),
        )
        assertEquals(RegionalRenderStatus.NO_OP, result.status)
        assertEquals(0f, result.appliedStrength)
        assertTrue(result.decisions.any { it.code == "REGIONAL_QUANTIZED_NO_OP" })
        assertArrayEquals(fixture.image.argb, result.image.argb)
    }

    @Test(expected = CancellationException::class)
    fun `rendering observes cancellation`() {
        val fixture = fixture()
        RegionalCorrectionRenderer().render(fixture.request(operation(RegionalTarget.PERSON, .3f))) { true }
    }

    @Test fun `mask refinement stays bounded and does not erase confident interiors`() {
        val fixture = fixture()
        val refined = EdgeAwareMaskRefiner().refine(fixture.image, fixture.mask)
        assertTrue(refined.score(2, 4) > .9f)
        assertTrue(refined.score(13, 4) < .1f)
        assertTrue(refined.copyBytes().all { (it.toInt() and 255) in 0..255 })
    }

    @Test fun `preview and export calls are deterministic and pixel equivalent`() {
        val fixture = fixture()
        val request = fixture.request(operation(RegionalTarget.PERSON, .25f))
        val preview = RegionalCorrectionRenderer().render(request)
        val export = RegionalCorrectionRenderer().render(request)
        assertEquals(preview.status, export.status)
        assertArrayEquals(preview.image.argb, export.image.argb)
    }

    @Test fun `extreme remains bounded and quality checked`() {
        val fixture = fixture()
        val result = RegionalCorrectionRenderer().render(fixture.request(operation(RegionalTarget.PERSON, .72f), level = EnhancementLevel.EXTREME))
        assertTrue(result.metrics.maximumLinearChannel <= 1.0001f)
        assertTrue(result.metrics.newHighlightClipping <= .012f)
        assertTrue(result.appliedStrength in 0f..1f)
    }

    @Test fun `saturated shadow exposure preserves hue without neighbor color contamination`() {
        val fixture = fixture()
        val location = pixel(8, 8)
        fixture.image.argb[location] = 0xff000020.toInt()
        val result = RegionalCorrectionRenderer().render(fixture.request(operation(RegionalTarget.PERSON, .5f)))
        val color = result.image.argb[location]
        assertEquals(0, color ushr 16 and 255)
        assertEquals(0, color ushr 8 and 255)
        assertTrue((color and 255) in 32..50)
    }

    private data class Fixture(val image: RasterImage, val mask: ConfidenceMask, val regional: SemanticRegionalEvidence) {
        fun request(vararg operations: RegionalOperation, level: EnhancementLevel = EnhancementLevel.ENHANCED): RegionalRenderRequest {
            val plan = RegionalCorrectionPlan(sourceIdentity = "source", transformationVersion = 1, maskId = mask.id,
                level = level, operations = operations.toList(), protections = emptyList(), explanation = emptyList())
            return RegionalRenderRequest(image, "source", 1, plan, mask, mask.quality(), emptyList(), regional)
        }
    }

    private fun fixture(
        image: RasterImage = RasterImage(32, 16, IntArray(512) { index -> if (index % 32 < 16) 0xff383838.toInt() else 0xff707070.toInt() }),
        mask: ConfidenceMask = ConfidenceMask.fromFloats("person", 16, 8, FloatArray(128) { if (it % 16 < 8) 1f else 0f }),
    ): Fixture = Fixture(image, mask, SemanticRegionalEvidence(
        WeightedRegionStatistics(1f, .04f, .65f, .01f, .01f, 0f, .15f, .2f),
        WeightedRegionStatistics(1f, .18f, .1f, .02f, 0f, 0f, .2f, .05f), -.14f, mask.id))

    private fun operation(target: RegionalTarget, amount: Float) = RegionalOperation(
        "test", RegionalOperationType.EXPOSURE, target, amount, "EV", 1f, listOf("test"), listOf("quality"))

    private fun pixel(x: Int, y: Int) = y * 32 + x
    private fun channel(color: Int) = color ushr 16 and 255
    private fun luma(color: Int): Float {
        val value = SmartMagicColorMath.srgbToLinear(channel(color) / 255f)
        return value
    }
}
