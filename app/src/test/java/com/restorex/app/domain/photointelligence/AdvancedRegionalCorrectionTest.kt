package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedRegionalCorrectionTest {
    @Test fun `selective dehaze changes background and protects person interior`() {
        val fixture = fixture()
        val result = RegionalCorrectionRenderer().render(fixture.request(RegionalOperationType.SELECTIVE_DEHAZE, .06f))
        assertTrue(result.status == RegionalRenderStatus.APPLIED || result.status == RegionalRenderStatus.REDUCED)
        assertEquals(fixture.image.argb[8 * 48 + 5], result.image.argb[8 * 48 + 5])
        assertTrue(fixture.image.argb.indices.any { i -> i % 48 > 28 && fixture.image.argb[i] != result.image.argb[i] })
        assertTrue(result.metrics.boundaryDiscontinuity < .12f)
    }

    @Test fun `local contrast remains bounded without new clipping`() {
        val fixture = fixture()
        val result = RegionalCorrectionRenderer().render(fixture.request(RegionalOperationType.LOCAL_CONTRAST, .07f))
        assertTrue(result.metrics.newHighlightClipping <= .012f)
        assertTrue(result.metrics.maximumLinearChannel <= 1.0001f)
        assertTrue(result.candidateEvaluations.size <= 4)
    }

    @Test fun `face midtone gamma lifts darker face more than well exposed face`() {
        val width = 64; val height = 32
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            when (x) {
                in 6..25 -> 0xff303030.toInt()
                in 38..57 -> 0xffa8a8a8.toInt()
                else -> 0xff707070.toInt()
            }
        }
        val image = RasterImage(width, height, pixels)
        val mask = ConfidenceMask.fromFloats("group", 32, 16, FloatArray(512) { index ->
            val x = index % 32; if (x in 2..29) 1f else 0f
        }).feather(1)
        val faces = listOf(
            FaceEvidence("dark", NormalizedRect(.09f, .18f, .40f, .82f), .9f, .9f, emptyList()),
            FaceEvidence("bright", NormalizedRect(.59f, .18f, .90f, .82f), .9f, .9f, emptyList()),
        )
        val regional = SemanticRegionalEvidence(
            WeightedRegionStatistics(1f, .2f, .4f, .05f, 0f, 0f, .1f, .1f),
            WeightedRegionStatistics(1f, .16f, .2f, .02f, 0f, 0f, .1f, .1f), .04f, mask.id)
        val operation = RegionalOperation("face-gamma", RegionalOperationType.MIDTONE_GAMMA,
            RegionalTarget.PERSON, .38f, "gamma_delta", 1f, listOf("FACE_EXPOSURE"), listOf("FACE_PROTECTION"))
        val plan = RegionalCorrectionPlan(sourceIdentity = "faces", transformationVersion = 0,
            maskId = mask.id, level = EnhancementLevel.EXTREME, operations = listOf(operation),
            protections = emptyList(), explanation = emptyList())
        val result = RegionalCorrectionRenderer().render(RegionalRenderRequest(
            image, "faces", 0, plan, mask, mask.quality(), faces, regional,
        ))
        val darkIndex = 16 * width + 16; val brightIndex = 16 * width + 48
        val darkChange = channel(result.image.argb[darkIndex]) - channel(image.argb[darkIndex])
        val brightChange = channel(result.image.argb[brightIndex]) - channel(image.argb[brightIndex])
        assertTrue("dark=$darkChange bright=$brightChange", darkChange > brightChange + 4)
        assertTrue(result.metrics.newHighlightClipping <= .012f)
    }

    @Test fun `virtual fill includes feathered neck inside person mask without background spill`() {
        val width = 80; val height = 80
        val image = RasterImage(width, height, IntArray(width * height) { 0xff383838.toInt() })
        val mask = ConfidenceMask.fromFloats("portrait", 40, 40, FloatArray(1600) { index ->
            val x = index % 40; val y = index / 40
            if (x in 12..27 && y in 8..34) 1f else 0f
        }).feather(1)
        val face = FaceEvidence("face", NormalizedRect(.32f, .16f, .68f, .48f), .9f, .9f, emptyList())
        val regional = SemanticRegionalEvidence(
            WeightedRegionStatistics(1f, .06f, .3f, 0f, 0f, 0f, .04f, .1f),
            WeightedRegionStatistics(1f, .14f, .1f, 0f, 0f, 0f, .03f, .1f), -.08f, mask.id)
        val operation = RegionalOperation("fill", RegionalOperationType.MIDTONE_GAMMA,
            RegionalTarget.PERSON, .30f, "gamma_delta", .9f, listOf("UNDERLIT_FACE"), listOf("NO_HALOS"))
        val plan = RegionalCorrectionPlan(sourceIdentity = "fill", transformationVersion = 0,
            maskId = mask.id, level = EnhancementLevel.ENHANCED, operations = listOf(operation),
            protections = emptyList(), explanation = emptyList())
        val result = RegionalCorrectionRenderer().render(RegionalRenderRequest(
            image, "fill", 0, plan, mask, mask.quality(), listOf(face), regional))

        assertTrue(channel(result.image.argb[50 * width + 40]) > channel(image.argb[50 * width + 40]))
        assertEquals(image.argb[50 * width + 72], result.image.argb[50 * width + 72])
    }

    @Test fun `whole subject midtone recovery works without inventing a face mask`() {
        val fixture = fixture()
        val operation = RegionalOperation("subject-gamma", RegionalOperationType.MIDTONE_GAMMA,
            RegionalTarget.PERSON, .20f, "gamma_delta", .55f, listOf("RELIABLE_PERSON_MASK"), listOf("NO_NEW_CLIPPING"))
        val plan = RegionalCorrectionPlan(sourceIdentity = "advanced", transformationVersion = 0,
            maskId = fixture.mask.id, level = EnhancementLevel.EXTREME, operations = listOf(operation),
            protections = emptyList(), explanation = emptyList())
        val result = RegionalCorrectionRenderer().render(RegionalRenderRequest(
            fixture.image, "advanced", 0, plan, fixture.mask, fixture.mask.quality(), emptyList(), fixture.regional,
        ))
        assertTrue(result.status == RegionalRenderStatus.APPLIED || result.status == RegionalRenderStatus.REDUCED)
        assertTrue(result.image.argb[12 * fixture.image.width + 8] != fixture.image.argb[12 * fixture.image.width + 8])
        assertEquals(fixture.image.argb[12 * fixture.image.width + 40], result.image.argb[12 * fixture.image.width + 40])
    }

    @Test fun `small but important subject correction is not discarded by global perceptual average`() {
        val width = 100; val height = 100
        val image = RasterImage(width, height, IntArray(width * height) { 0xff707070.toInt() })
        val mask = ConfidenceMask.fromFloats("small-subject", 100, 100, FloatArray(10_000) { index ->
            val x = index % 100; val y = index / 100; if (x in 45..54 && y in 35..54) 1f else 0f
        }).feather(1)
        val regional = SemanticRegionalEvidence(
            WeightedRegionStatistics(1f, .08f, .55f, 0f, 0f, 0f, .02f, .1f),
            WeightedRegionStatistics(1f, .16f, .10f, 0f, 0f, 0f, .02f, .1f), -.08f, mask.id)
        val operation = RegionalOperation("small-subject-gamma", RegionalOperationType.MIDTONE_GAMMA,
            RegionalTarget.PERSON, .22f, "gamma_delta", .8f, listOf("P1_SUBJECT"), listOf("NO_NEW_CLIPPING"))
        val plan = RegionalCorrectionPlan(sourceIdentity = "small", transformationVersion = 0, maskId = mask.id,
            level = EnhancementLevel.EXTREME, operations = listOf(operation), protections = emptyList(), explanation = emptyList())
        val result = RegionalCorrectionRenderer().render(RegionalRenderRequest(
            image, "small", 0, plan, mask, mask.quality(), emptyList(), regional,
        ))
        assertTrue(result.status == RegionalRenderStatus.APPLIED || result.status == RegionalRenderStatus.REDUCED)
        assertTrue(result.metrics.targetMeanLuminanceChange > .005f)
    }

    private data class Fixture(val image: RasterImage, val mask: ConfidenceMask, val regional: SemanticRegionalEvidence) {
        fun request(type: RegionalOperationType, amount: Float): RegionalRenderRequest {
            val operation = RegionalOperation("advanced", type, RegionalTarget.BACKGROUND, amount,
                "normalized", 1f, listOf("ADVANCED_EVIDENCE"), listOf("NO_HALOS"))
            val plan = RegionalCorrectionPlan(sourceIdentity = "advanced", transformationVersion = 0,
                maskId = mask.id, level = EnhancementLevel.EXTREME, operations = listOf(operation),
                protections = emptyList(), explanation = emptyList())
            return RegionalRenderRequest(image, "advanced", 0, plan, mask, mask.quality(), emptyList(), regional)
        }
    }

    private fun fixture(): Fixture {
        val width = 48; val height = 24
        val image = RasterImage(width, height, IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            if (x < 20) 0xff75685f.toInt() else {
                val v = 125 + ((x + y) % 7) * 3
                (0xff shl 24) or ((v + 5) shl 16) or ((v + 8) shl 8) or (v + 10)
            }
        })
        val mask = ConfidenceMask.fromFloats("person", 24, 12, FloatArray(288) { if (it % 24 < 10) 1f else 0f }).feather(1)
        val regional = SemanticRegionalEvidence(
            WeightedRegionStatistics(1f, .18f, .1f, .01f, 0f, 0f, .1f, .1f),
            WeightedRegionStatistics(1f, .28f, .05f, .01f, 0f, 0f, .08f, .05f), -.1f, mask.id)
        return Fixture(image, mask, regional)
    }

    private fun channel(color: Int) = color ushr 16 and 255
}
