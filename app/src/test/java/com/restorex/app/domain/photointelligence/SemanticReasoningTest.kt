package com.restorex.app.domain.photointelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticReasoningTest {
    @Test fun `all EXIF transforms round trip`() {
        val point = NormalizedPoint(.23f, .71f)
        ExifTransform.entries.forEach { transform ->
            val roundTrip = transform.orientedToEncoded(transform.encodedToOriented(point))
            assertEquals(point.x, roundTrip.x, .0001f)
            assertEquals(point.y, roundTrip.y, .0001f)
        }
    }

    @Test fun `letterbox rejects padding and round trips content`() {
        val transform = LetterboxTransform(1600, 900, 256, 256)
        val point = NormalizedPoint(.3f, .7f)
        val restored = transform.modelToSource(transform.sourceToModel(point))!!
        assertEquals(point.x, restored.x, .0001f)
        assertEquals(point.y, restored.y, .0001f)
        assertNull(transform.modelToSource(NormalizedPoint(.5f, .05f)))
    }

    @Test fun `face association uses confidence mask not box identity assumptions`() {
        val values = FloatArray(100)
        for (y in 2..7) for (x in 2..7) values[y * 10 + x] = .9f
        val mask = ConfidenceMask.fromFloats("person", 10, 10, values)
        val face = FaceEvidence("f", NormalizedRect(.3f, .3f, .6f, .6f), .8f, .8f, emptyList())
        val result = FacePersonAssociator.associate(listOf(face), mask).single()
        assertEquals(FaceAssociationStatus.ASSOCIATED_WITH_COMBINED_PERSON_MASK, result.status)
    }

    @Test fun `a person does not automatically become primary subject`() {
        val mask = ConfidenceMask.fromFloats("tiny", 10, 10, FloatArray(100) { if (it == 55) 1f else 0f })
        val quality = mask.quality()
        val semantic = SemanticAnalysisResult(null, null, 100, 100, emptyList(), mask,
            SemanticMaskReference(mask.id, mask.width, mask.height, quality = quality), emptyList(),
            setOf(SemanticClass.PERSON), emptySet(), 1)
        val graph = SemanticCompositionEngine().integrate(SceneGraph(emptyList(), emptyList(), null, true), semantic, null)
        assertNull(graph.semanticPrimarySubjectId)
        assertTrue(graph.unresolvedPrimarySubject)
    }

    @Test fun `reliable person mask permits conservative recovery when a small face is not localized`() {
        val mask = ConfidenceMask.fromFloats("environmental-person", 20, 10, FloatArray(200) { index ->
            val x = index % 20; val y = index / 20
            if (x in 9..12 && y in 1..8) .94f else 0f
        }).feather(1)
        val semantic = SemanticAnalysisResult(null, null, 1600, 740, emptyList(), mask,
            SemanticMaskReference(mask.id, mask.width, mask.height, quality = mask.quality()), emptyList(),
            setOf(SemanticClass.PERSON), emptySet(), 1)
        val regional = SemanticRegionalEvidence(
            WeightedRegionStatistics(1f, .084f, .49f, 0f, 0f, 0f, .02f, .1f),
            WeightedRegionStatistics(1f, .185f, .31f, .01f, 0f, 0f, .05f, .1f),
            -.101f, mask.id,
        )
        val assessment = SemanticSubjectDiagnosisEngine().assess(semantic, regional)
        assertEquals(SemanticSubjectDecision.REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE, assessment.decision)
        assertTrue(assessment.confidence <= .58f)
    }

    @Test fun `deep silhouette remains protected without face evidence`() {
        val mask = ConfidenceMask.fromFloats("silhouette", 20, 10, FloatArray(200) { index -> if (index % 20 in 8..12) .95f else 0f }).feather(1)
        val semantic = SemanticAnalysisResult(null, null, 1000, 700, emptyList(), mask,
            SemanticMaskReference(mask.id, mask.width, mask.height, quality = mask.quality()), emptyList(),
            setOf(SemanticClass.PERSON), emptySet(), 1)
        val regional = SemanticRegionalEvidence(
            WeightedRegionStatistics(1f, .025f, .82f, 0f, 0f, 0f, .08f, .2f),
            WeightedRegionStatistics(1f, .34f, .08f, .02f, 0f, 0f, .04f, .1f), -.315f, mask.id)
        assertEquals(SemanticSubjectDecision.PROTECT_INTENTIONAL_SILHOUETTE,
            SemanticSubjectDiagnosisEngine().assess(semantic, regional).decision)
    }

    @Test fun `well exposed person remains protected`() {
        val mask = ConfidenceMask.fromFloats("balanced", 20, 10, FloatArray(200) { index -> if (index % 20 in 8..12) .95f else 0f }).feather(1)
        val semantic = SemanticAnalysisResult(null, null, 1000, 700, emptyList(), mask,
            SemanticMaskReference(mask.id, mask.width, mask.height, quality = mask.quality()), emptyList(),
            setOf(SemanticClass.PERSON), emptySet(), 1)
        val regional = SemanticRegionalEvidence(
            WeightedRegionStatistics(1f, .21f, .12f, .01f, 0f, 0f, .03f, .1f),
            WeightedRegionStatistics(1f, .24f, .10f, .01f, 0f, 0f, .03f, .1f), -.03f, mask.id)
        assertEquals(SemanticSubjectDecision.PROTECT_CORRECTLY_EXPOSED_SUBJECT,
            SemanticSubjectDiagnosisEngine().assess(semantic, regional).decision)
    }
}
