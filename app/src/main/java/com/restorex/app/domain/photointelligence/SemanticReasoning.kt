package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object FacePersonAssociator {
    fun associate(faces: List<FaceEvidence>, mask: ConfidenceMask?): List<FacePersonAssociation> = faces.map { face ->
        if (mask == null) return@map FacePersonAssociation(face.id, null, FaceAssociationStatus.NO_PERSON_MASK, 0f)
        var sum = 0f; var count = 0
        val steps = 12
        for (y in 0 until steps) for (x in 0 until steps) {
            val nx = face.bounds.left + (x + .5f) / steps * (face.bounds.right - face.bounds.left)
            val ny = face.bounds.top + (y + .5f) / steps * (face.bounds.bottom - face.bounds.top)
            sum += mask.sample(nx, ny); count++
        }
        val coverage = sum / count
        val status = when {
            coverage >= .55f -> FaceAssociationStatus.ASSOCIATED_WITH_COMBINED_PERSON_MASK
            coverage >= .25f -> FaceAssociationStatus.AMBIGUOUS
            else -> FaceAssociationStatus.OUTSIDE_PERSON_MASK
        }
        FacePersonAssociation(face.id, mask.id, status, coverage)
    }
}

class SemanticRegionPhotometricAnalyzer {
    fun analyze(source: RasterImage, personMask: ConfidenceMask): SemanticRegionalEvidence {
        val person = accumulator(); val background = accumulator()
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val c = source.argb[y * source.width + x]; val alpha = (c ushr 24 and 0xff) / 255f
            if (alpha <= 0f) continue
            val r = (c ushr 16 and 0xff) / 255f; val g = (c ushr 8 and 0xff) / 255f; val b = (c and 0xff) / 255f
            val linear = SmartMagicColorMath.luminance(SmartMagicColorMath.srgbToLinear(r), SmartMagicColorMath.srgbToLinear(g), SmartMagicColorMath.srgbToLinear(b))
            val saturation = (max(r, max(g, b)) - min(r, min(g, b))) / max(r, max(g, b)).coerceAtLeast(.001f)
            val subjectWeight = personMask.sample((x + .5f) / source.width, (y + .5f) / source.height) * alpha
            person.add(linear, saturation, subjectWeight)
            background.add(linear, saturation, (1f - subjectWeight / alpha) * alpha)
        }
        val p = person.finish(); val b = background.finish()
        return SemanticRegionalEvidence(p, b, p.meanLinearLuminance - b.meanLinearLuminance, personMask.id)
    }

    private fun accumulator() = Accumulator()
    private class Accumulator {
        var weight = 0.0; var luma = 0.0; var saturation = 0.0; var shadows = 0.0; var highlights = 0.0; var black = 0.0; var white = 0.0
        var noise = 0.0; var previous = Float.NaN
        fun add(y: Float, s: Float, w: Float) {
            if (w <= 0f) return
            weight += w; luma += y * w; saturation += s * w
            if (y < .035f) shadows += w; if (y > .80f) highlights += w; if (y < .002f) black += w; if (y > .985f) white += w
            if (previous.isFinite() && y < .20f) noise += abs(y - previous) * w
            previous = y
        }
        fun finish(): WeightedRegionStatistics {
            val w = weight.coerceAtLeast(1e-9)
            return WeightedRegionStatistics(weight.toFloat(), (luma / w).toFloat(), (shadows / w).toFloat(),
                (highlights / w).toFloat(), (black / w).toFloat(), (white / w).toFloat(),
                (saturation / w).toFloat(), (noise / w).toFloat().coerceIn(0f, 1f))
        }
    }
}

class SemanticCompositionEngine {
    fun integrate(base: SceneGraph, result: SemanticAnalysisResult, regional: SemanticRegionalEvidence?): SceneGraph {
        val maskRef = result.personMaskReference
        val personNode = maskRef?.takeIf { it.quality.accepted }?.let {
            val mask = requireNotNull(result.personMask)
            val bounds = maskBounds(mask)
            val faceCue = result.faces.maxOfOrNull { face -> face.bounds.area }?.let { (it * 14f).coerceIn(0f, 1f) } ?: 0f
            val scaleCue = (it.quality.weightedAreaFraction * 2.5f).coerceIn(0f, 1f)
            val contrastCue = regional?.let { r -> (abs(r.personMinusBackgroundLuminance) * 2f).coerceIn(0f, 1f) } ?: 0f
            val confidence = (it.quality.meanForegroundScore * .45f + faceCue * .30f + scaleCue * .15f + contrastCue * .10f).coerceIn(0f, 1f)
            val priority = if (confidence >= .62f && (faceCue >= .18f || scaleCue >= .35f)) CompositionPriority.P1 else CompositionPriority.UNRESOLVED
            RegionNode("semantic-person-combined", RegionKind.SEMANTIC_CONFIRMED, bounds, max(scaleCue, faceCue), confidence, priority, "PERSON")
        }
        val faceNodes = result.faces.map { face ->
            RegionNode(face.id, RegionKind.SEMANTIC_CONFIRMED, face.bounds, (face.bounds.area * 16f).coerceIn(0f, 1f),
                face.detectorScore ?: face.heuristicQuality, CompositionPriority.UNRESOLVED, "FACE")
        }
        val background = personNode?.let {
            RegionNode("semantic-background", RegionKind.SEMANTIC_CONFIRMED, NormalizedRect(0f, 0f, 1f, 1f),
                .2f, it.confidence, if (it.priority == CompositionPriority.P1) CompositionPriority.P3 else CompositionPriority.UNRESOLVED, "BACKGROUND")
        }
        val nodes = base.nodes + listOfNotNull(personNode, background) + faceNodes
        val edges = base.edges + result.associations.filter { it.status == FaceAssociationStatus.ASSOCIATED_WITH_COMBINED_PERSON_MASK }.map {
            RegionEdge(it.faceId, "semantic-person-combined", "CONTAINED_BY_COMBINED_PERSON_MASK", it.personCoverageWithinFace)
        } + if (personNode != null && background != null) listOf(RegionEdge(personNode.id, background.id, "FOREGROUND_OF", personNode.confidence)) else emptyList()
        return SceneGraph(nodes, edges, personNode?.takeIf { it.priority == CompositionPriority.P1 }?.id,
            personNode?.priority != CompositionPriority.P1)
    }

    private fun maskBounds(mask: ConfidenceMask): NormalizedRect {
        var minX = mask.width; var minY = mask.height; var maxX = -1; var maxY = -1
        for (y in 0 until mask.height) for (x in 0 until mask.width) if (mask.score(x, y) >= .5f) {
            minX = min(minX, x); minY = min(minY, y); maxX = max(maxX, x); maxY = max(maxY, y)
        }
        return if (maxX < 0) NormalizedRect(0f, 0f, 1f, 1f) else NormalizedRect(minX / mask.width.toFloat(), minY / mask.height.toFloat(), (maxX + 1) / mask.width.toFloat(), (maxY + 1) / mask.height.toFloat())
    }
}

class SemanticSubjectDiagnosisEngine {
    fun assess(result: SemanticAnalysisResult, regional: SemanticRegionalEvidence?): SemanticSubjectAssessment {
        if (result.personMask == null || result.personMaskReference == null) return assessment(SemanticSubjectDecision.NO_PERSON, 1f, "No person mask was produced")
        val quality = result.personMaskReference.quality
        if (!quality.accepted) return assessment(SemanticSubjectDecision.MASK_UNRELIABLE, 1f - quality.meanForegroundScore,
            "Person mask rejected: ${quality.rejectionReasons.joinToString()}")
        if (regional == null) return assessment(SemanticSubjectDecision.INSUFFICIENT_EVIDENCE, .2f, "Regional photometry is unavailable")
        val deficit = regional.background.meanLinearLuminance - regional.person.meanLinearLuminance
        val faceSupport = result.associations.any { it.status == FaceAssociationStatus.ASSOCIATED_WITH_COMBINED_PERSON_MASK }
        return when {
            deficit > .22f && regional.person.meanLinearLuminance < .10f && !faceSupport -> assessment(
                SemanticSubjectDecision.PROTECT_INTENTIONAL_SILHOUETTE, (deficit * 2f).coerceIn(0f, 1f), "Dark human region against bright background; no reliable face evidence", regional)
            deficit > .10f && faceSupport && regional.person.shadowOccupancy > .22f -> assessment(
                SemanticSubjectDecision.REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE, (.45f + deficit).coerceIn(0f, 1f), "Reliable human mask and face evidence show a recoverable subject deficit", regional)
            deficit > .075f && !faceSupport &&
                quality.meanForegroundScore >= .72f && quality.weightedAreaFraction in .015f.. .45f &&
                regional.person.meanLinearLuminance in .045f.. .14f && regional.person.shadowOccupancy > .35f -> assessment(
                SemanticSubjectDecision.REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE,
                (.34f + deficit + quality.meanForegroundScore * .12f).coerceAtMost(.58f),
                "Reliable person segmentation shows a moderate recoverable subject deficit; no face was localized, so only conservative whole-subject recovery is eligible",
                regional)
            regional.person.meanLinearLuminance in .12f.. .62f && regional.person.whiteClipping < .03f -> assessment(
                SemanticSubjectDecision.PROTECT_CORRECTLY_EXPOSED_SUBJECT, quality.meanForegroundScore, "Person exposure is inside conservative bounds; global exposure must not disturb it", regional)
            else -> assessment(SemanticSubjectDecision.INSUFFICIENT_EVIDENCE, .35f, "Human region exists but exposure intent is ambiguous", regional)
        }
    }
    private fun assessment(decision: SemanticSubjectDecision, confidence: Float, explanation: String, r: SemanticRegionalEvidence? = null) = SemanticSubjectAssessment(
        decision, confidence.coerceIn(0f, 1f), r?.let { listOf(MetricEvidence("person_minus_background_luminance", it.personMinusBackgroundLuminance, "linear", explanation)) } ?: emptyList(),
        emptyList(), explanation)
}

class SemanticCorrectionPlanner(private val base: CorrectionDecisionEngine = CorrectionDecisionEngine()) {
    fun plan(sourceIdentity: String, evidence: PhotometricEvidence, diagnoses: List<ConditionDiagnosis>, graph: SceneGraph,
             semantic: SemanticAnalysisResult, regional: SemanticRegionalEvidence, assessment: SemanticSubjectAssessment): SemanticPlanningSupplement {
        val plans = base.plan(sourceIdentity, evidence, diagnoses, graph)
        val maskId = semantic.personMaskReference?.id ?: "none"
        val decisions = when (assessment.decision) {
            SemanticSubjectDecision.REGIONAL_SUBJECT_EXPOSURE_ELIGIBLE -> listOf(RegionalPlanDecision(maskId, CorrectionOperation.SHADOW_RECOVERY,
                "ELIGIBLE_MILESTONE_4_RENDERER", "REQUIRES_VALIDATED_PERSON_MASK", listOf("BACKGROUND")))
            SemanticSubjectDecision.PROTECT_CORRECTLY_EXPOSED_SUBJECT -> listOf(RegionalPlanDecision(maskId, CorrectionOperation.TONE_CONTRAST,
                "ELIGIBLE_MILESTONE_4_RENDERER", "REQUIRES_BACKGROUND_COMPLEMENT_AND_PERSON_PROTECTION", listOf("PERSON")))
            else -> emptyList()
        }
        val protections = if (assessment.decision == SemanticSubjectDecision.PROTECT_CORRECTLY_EXPOSED_SUBJECT || assessment.decision == SemanticSubjectDecision.PROTECT_INTENTIONAL_SILHOUETTE)
            listOf(ProtectionDecision("PRT_SEMANTIC_PERSON", DecisionDisposition.PROTECT, assessment.decision.name, assessment.confidence,
                listOf(CorrectionOperation.GLOBAL_EXPOSURE.name))) else emptyList()
        return SemanticPlanningSupplement(plans, assessment, decisions, protections)
    }
}
