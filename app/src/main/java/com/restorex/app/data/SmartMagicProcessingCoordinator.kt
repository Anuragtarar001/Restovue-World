package com.restorex.app.data

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.BrightRegionAnalyzer
import com.restorex.app.domain.photointelligence.AdvancedPhotometricAnalyzer
import com.restorex.app.domain.photointelligence.AdaptivePhotographicOptimizer
import com.restorex.app.domain.photointelligence.AdaptiveAutoController
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticBuilder
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticReport
import com.restorex.app.domain.photointelligence.CompositionReasoningEngine
import com.restorex.app.domain.photointelligence.DecisionReason
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.FaceLightAdjustment
import com.restorex.app.domain.photointelligence.FaceLightRenderer
import com.restorex.app.domain.photointelligence.FaceEvidence
import com.restorex.app.domain.photointelligence.PerceptualImpactEstimator
import com.restorex.app.domain.photointelligence.PhotographicDiagnosisEngine
import com.restorex.app.domain.photointelligence.PhotometricAnalyzerV2
import com.restorex.app.domain.photointelligence.ProductionRenderOutcome
import com.restorex.app.domain.photointelligence.ProductionRenderStrategy
import com.restorex.app.domain.photointelligence.RegionalCorrectionRenderer
import com.restorex.app.domain.photointelligence.RegionalPlanBuilder
import com.restorex.app.domain.photointelligence.RegionalRenderRequest
import com.restorex.app.domain.photointelligence.RegionalRenderStatus
import com.restorex.app.domain.photointelligence.SemanticAnalyzer
import com.restorex.app.domain.photointelligence.SemanticCompositionEngine
import com.restorex.app.domain.photointelligence.SemanticCorrectionPlanner
import com.restorex.app.domain.photointelligence.SemanticRegionPhotometricAnalyzer
import com.restorex.app.domain.photointelligence.SemanticSubjectDiagnosisEngine
import com.restorex.app.domain.photointelligence.SemanticSubjectDecision
import com.restorex.app.domain.photointelligence.SmartMagicProductionRender
import com.restorex.app.domain.photointelligence.SmartMagicProductionSession
import com.restorex.app.domain.photointelligence.SmartMagicAdjustment
import com.restorex.app.domain.photointelligence.SmartMagicWorkStage
import com.restorex.app.domain.photointelligence.SmartMagicWorkState
import com.restorex.app.domain.photointelligence.SmartMagicWorkUpdate
import com.restorex.app.domain.photointelligence.SmartMagicStageTimings
import com.restorex.app.domain.photointelligence.SmartMagicProcessingBudget
import com.restorex.app.domain.photointelligence.SmartMagicProcessingBudgetPolicy
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import com.smartphotomagic.engine.AffineBilateralGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/** The sole production coordinator for Smart Magic analysis, preview and export decisions. */
class SmartMagicProcessingCoordinator(
    private val globalPipeline: SmartMagicPipeline,
    private val semanticAnalyzer: SemanticAnalyzer,
    private val photometricAnalyzer: PhotometricAnalyzerV2 = PhotometricAnalyzerV2(),
    private val diagnosisEngine: PhotographicDiagnosisEngine = PhotographicDiagnosisEngine(),
    private val advancedAnalyzer: AdvancedPhotometricAnalyzer = AdvancedPhotometricAnalyzer(),
    private val adaptiveOptimizer: AdaptivePhotographicOptimizer = AdaptivePhotographicOptimizer(),
    private val adaptiveAutoController: AdaptiveAutoController = AdaptiveAutoController(),
    private val brightRegionAnalyzer: BrightRegionAnalyzer = BrightRegionAnalyzer(),
    private val compositionEngine: CompositionReasoningEngine = CompositionReasoningEngine(),
    private val semanticCompositionEngine: SemanticCompositionEngine = SemanticCompositionEngine(),
    private val regionalPhotometricAnalyzer: SemanticRegionPhotometricAnalyzer = SemanticRegionPhotometricAnalyzer(),
    private val subjectDiagnosisEngine: SemanticSubjectDiagnosisEngine = SemanticSubjectDiagnosisEngine(),
    private val semanticPlanner: SemanticCorrectionPlanner = SemanticCorrectionPlanner(),
    private val regionalPlanBuilder: RegionalPlanBuilder = RegionalPlanBuilder(),
    private val regionalRenderer: RegionalCorrectionRenderer = RegionalCorrectionRenderer(),
    private val impactEstimator: PerceptualImpactEstimator = PerceptualImpactEstimator(),
    private val diagnosticBuilder: AdjustmentDiagnosticBuilder = AdjustmentDiagnosticBuilder(),
    private val proFinishEngine: ProFinishEngineAdapter = ProFinishEngineAdapter(),
    private val faceLightRenderer: FaceLightRenderer = FaceLightRenderer(),
    val processingBudget: SmartMagicProcessingBudget = SmartMagicProcessingBudgetPolicy.forRuntime(
        Runtime.getRuntime().maxMemory(), Runtime.getRuntime().availableProcessors(),
    ),
) {
    private val finishGridCache = ConcurrentHashMap<EnhancementLevel, AffineBilateralGrid>()
    private val faceLightCache = ConcurrentHashMap<EnhancementLevel, List<FaceLightAdjustment>>()
    @Volatile private var finishGridSessionId: String? = null

    suspend fun createSession(
        sourceIdentity: String,
        analysisSource: RasterImage,
        geometryVersion: Long = 0,
        shouldCancel: () -> Boolean = { false },
        onProgress: (SmartMagicWorkUpdate) -> Unit = {},
    ): SmartMagicProductionSession = withContext(Dispatchers.Default) {
        require(sourceIdentity.isNotBlank() && geometryVersion >= 0)
        val started = System.nanoTime()
        checkCancelled(shouldCancel)
        val fingerprint = fingerprint(analysisSource)
        val sessionId = "sm45-$fingerprint-g$geometryVersion"
        synchronized(finishGridCache) {
            if (finishGridSessionId != sessionId) finishGridCache.clear()
            faceLightCache.clear()
            finishGridSessionId = sessionId
        }
        var mark = System.nanoTime()
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.ANALYZING_LIGHTING, SmartMagicWorkState.STARTED,
            detail = "Measuring exposure, color and detail"))
        val smartAnalysis = globalPipeline.analyze(analysisSource, shouldCancel)
        val globalAnalysisNanos = System.nanoTime() - mark
        checkCancelled(shouldCancel)
        mark = System.nanoTime()
        val evidence = photometricAnalyzer.analyze(analysisSource, shouldCancel)
        val diagnoses = diagnosisEngine.diagnose(evidence)
        val advanced = advancedAnalyzer.analyze(analysisSource, evidence, diagnoses, shouldCancel)
        val photometricAnalysisNanos = System.nanoTime() - mark
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.ANALYZING_LIGHTING, SmartMagicWorkState.COMPLETED,
            detail = "Lighting measurements complete"))
        mark = System.nanoTime()
        val baseGraph = compositionEngine.build(evidence, brightRegionAnalyzer.analyze(analysisSource, shouldCancel))
        checkCancelled(shouldCancel)
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.DETECTING_SUBJECTS, SmartMagicWorkState.STARTED,
            detail = "Locating faces and people on device"))
        val semantic = semanticAnalyzer.analyze(analysisSource, shouldCancel)
        val proFinishPlans = proFinishEngine.planAll(analysisSource, semantic.faces, shouldCancel)
        val semanticInferenceNanos = System.nanoTime() - mark
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.DETECTING_SUBJECTS, SmartMagicWorkState.COMPLETED,
            detail = "Detected ${semantic.faces.size} face${if (semantic.faces.size == 1) "" else "s"}"))
        mark = System.nanoTime()
        checkCancelled(shouldCancel)
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.EVALUATING_TONE, SmartMagicWorkState.STARTED,
            detail = "Evaluating subject and background tone"))
        val mask = semantic.personMask
        val maskQuality = semantic.personMaskReference?.quality
        val regional = if (mask != null && maskQuality?.accepted == true) regionalPhotometricAnalyzer.analyze(analysisSource, mask) else null
        val graph = semanticCompositionEngine.integrate(baseGraph, semantic, regional)
        val assessment = subjectDiagnosisEngine.assess(semantic, regional)
        val regionalPlans = if (regional != null && mask != null) {
            val supplement = semanticPlanner.plan(sessionId, evidence, diagnoses, graph, semantic, regional, assessment)
            EnhancementLevel.entries.associateWith { level ->
                regionalPlanBuilder.build(sessionId, geometryVersion, level, supplement, regional, mask.id, advanced,
                    semantic.faces, faceIlluminationDeficit(analysisSource, semantic.faces))
            }
        } else emptyMap()
        val adaptiveAutoPlan = adaptiveAutoController.plan(smartAnalysis, advanced)
        val baseGlobalParameters = EnhancementLevel.entries.associateWith { level ->
            calibrateAdvanced(adaptiveAutoController.parametersForMode(adaptiveAutoPlan, level), level, advanced)
        }
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.EVALUATING_TONE, SmartMagicWorkState.COMPLETED,
            detail = "Correction plans prepared"))
        // Global and regional decisions share the same bounded preview evidence. A reliable person
        // mask no longer disables global optimization; the selected global result becomes the
        // base on which the residual regional defect is corrected.
        val optimizationResults = run {
            val totalCandidates = processingBudget.optimizerCandidateBudget * EnhancementLevel.entries.size
            var completedCandidates = 0
            onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.OPTIMIZING_TONAL_BALANCE,
                SmartMagicWorkState.STARTED, 0, totalCandidates,
                "Evaluating exposure, gamma, shadows and contrast"))
            val optimizationSource = analysisSource.scaledToMaxDimension(processingBudget.optimizationMaxDimension)
            EnhancementLevel.entries.associateWith { level ->
                adaptiveOptimizer.optimize(
                    source = optimizationSource,
                    sourceEvidence = evidence,
                    advanced = advanced,
                    level = level,
                    base = baseGlobalParameters.getValue(level),
                    candidateBudget = processingBudget.optimizerCandidateBudget,
                    shouldCancel = shouldCancel,
                    onCandidateEvaluated = { _, _ ->
                        completedCandidates++
                        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.OPTIMIZING_TONAL_BALANCE,
                            SmartMagicWorkState.ACTIVE, completedCandidates.coerceAtMost(totalCandidates), totalCandidates,
                            "Evaluating ${level.name.lowercase()} tonal candidates"))
                    },
                ) { parameters, cancelled ->
                    globalPipeline.enhance(optimizationSource, parameters, cancelled)
                }
            }
        }
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.OPTIMIZING_TONAL_BALANCE,
            SmartMagicWorkState.COMPLETED, processingBudget.optimizerCandidateBudget * EnhancementLevel.entries.size,
            processingBudget.optimizerCandidateBudget * EnhancementLevel.entries.size,
            "Tonal optimization complete"))
        val globalParameters = EnhancementLevel.entries.associateWith { level ->
            protectSemanticSubject(
                optimizationResults[level]?.selectedParameters ?: baseGlobalParameters.getValue(level),
                assessment.decision, level)
        }
        SmartMagicProductionSession(
            sessionId = sessionId,
            sourceIdentity = sourceIdentity,
            sourceFingerprint = fingerprint,
            sourceWidth = analysisSource.width,
            sourceHeight = analysisSource.height,
            geometryVersion = geometryVersion,
            smartAnalysis = smartAnalysis,
            photometricEvidence = evidence,
            diagnoses = diagnoses,
            advancedEvidence = advanced,
            adaptiveAutoPlan = adaptiveAutoPlan,
            semanticResult = semantic,
            sceneGraph = graph,
            regionalEvidence = regional,
            subjectAssessment = assessment,
            globalParameters = globalParameters,
            regionalPlans = regionalPlans,
            optimizationResults = optimizationResults,
            proFinishPlans = proFinishPlans,
            stageTimings = SmartMagicStageTimings(globalAnalysisNanos, photometricAnalysisNanos,
                semanticInferenceNanos, System.nanoTime() - mark),
            createdElapsedNanos = System.nanoTime() - started,
        )
    }

    fun render(
        source: RasterImage,
        session: SmartMagicProductionSession,
        level: EnhancementLevel,
        shouldCancel: () -> Boolean = { false },
        lockedRegionalStrength: Float? = null,
        onProgress: (SmartMagicWorkUpdate) -> Unit = {},
    ): SmartMagicProductionRender {
        val started = System.nanoTime()
        requireCompatibleGeometry(source, session)
        checkCancelled(shouldCancel)
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.RENDERING_PREVIEW, SmartMagicWorkState.STARTED,
            detail = "Applying ${level.name.lowercase()} correction"))
        val semantic = session.semanticResult
        val mask = semantic.personMask
        val maskReference = semantic.personMaskReference
        val regional = session.regionalEvidence
        val plan = session.regionalPlans[level]
        val globalParameters = session.globalParameters.getValue(level)
        val globalCandidate = proFinishEngine.render(source, session.proFinishPlans.getValue(level), shouldCancel)
        val globalImpact = impactEstimator.estimate(source, globalCandidate)
        val globalAccepted = !globalCandidate.argb.contentEquals(source.argb) && !globalImpact.perceptuallyNegligible
        val globalBase = if (globalAccepted) globalCandidate else source
        if (mask != null && maskReference?.quality?.accepted == true && regional != null && plan != null) {
            val residualPlan = residualRegionalPlan(plan, globalParameters,
                session.subjectAssessment?.decision == SemanticSubjectDecision.PROTECT_CORRECTLY_EXPOSED_SUBJECT)
            val result = regionalRenderer.render(RegionalRenderRequest(globalBase, session.sessionId, session.geometryVersion,
                residualPlan, mask, maskReference.quality, semantic.faces, regional, lockedRegionalStrength,
                processingBudget.regionalCandidateBudget), shouldCancel)
            val regionalAccepted = result.status == RegionalRenderStatus.APPLIED || result.status == RegionalRenderStatus.REDUCED
            val finalImage = if (regionalAccepted) result.image else globalBase
            val impact = impactEstimator.estimate(source, finalImage)
            val regionalOutcome = when (result.status) {
                RegionalRenderStatus.APPLIED -> ProductionRenderOutcome.APPLIED
                RegionalRenderStatus.REDUCED -> ProductionRenderOutcome.REDUCED_FOR_QUALITY
                RegionalRenderStatus.NO_OP -> if (impact.changedPixels == 0) ProductionRenderOutcome.QUANTIZED_NO_OP else ProductionRenderOutcome.NO_CORRECTION_NEEDED
                RegionalRenderStatus.PERCEPTUALLY_NEGLIGIBLE -> ProductionRenderOutcome.PERCEPTUALLY_NEGLIGIBLE
                RegionalRenderStatus.SKIPPED_UNSUPPORTED -> ProductionRenderOutcome.UNSUPPORTED
                else -> ProductionRenderOutcome.REJECTED_FOR_QUALITY
            }
            val strategy = when {
                regionalAccepted && globalAccepted -> ProductionRenderStrategy.COMBINED
                regionalAccepted -> ProductionRenderStrategy.REGIONAL_ONLY
                globalAccepted -> ProductionRenderStrategy.GLOBAL_ONLY
                else -> ProductionRenderStrategy.NO_OP
            }
            val outcome = if (!regionalAccepted && globalAccepted) ProductionRenderOutcome.APPLIED else regionalOutcome
            val explanation = buildList {
                if (globalAccepted) add(DecisionReason("GLOBAL_BASE_APPLIED",
                    "Validated global correction rendered before residual regional correction"))
                addAll(result.decisions)
                if (!regionalAccepted && globalAccepted) add(DecisionReason("REGIONAL_REJECTED_GLOBAL_PRESERVED",
                    "Regional proposal was rejected; the independently validated global result was retained"))
            }
            val rendered = SmartMagicProductionRender(finalImage, level, strategy,
                outcome, impact, result, explanation, System.nanoTime() - started)
            if (regionalAccepted) {
                cacheFinishGrid(session, level, globalBase, finalImage, shouldCancel)
            } else {
                removeFinishGrid(session, level)
            }
            onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.RENDERING_PREVIEW, SmartMagicWorkState.COMPLETED,
                detail = when (strategy) {
                    ProductionRenderStrategy.COMBINED -> "Global and regional preview rendered"
                    ProductionRenderStrategy.REGIONAL_ONLY -> "Regional preview rendered"
                    ProductionRenderStrategy.GLOBAL_ONLY -> "Global preview rendered; regional proposal was not accepted"
                    ProductionRenderStrategy.NO_OP -> "Original preserved"
                }))
            return rendered
        }

        // Missing regional evidence does not suppress an independently optimized, quality-checked
        // global plan. It only prevents claims of subject-specific correction.
        val hasHumanEvidence = semantic.faces.isNotEmpty() || semantic.personMaskReference?.quality?.accepted == true
        if (hasHumanEvidence) {
            removeFinishGrid(session, level)
            val finalImage = if (globalAccepted) globalCandidate else source
            val rendered = SmartMagicProductionRender(finalImage, level,
                if (globalAccepted) ProductionRenderStrategy.GLOBAL_ONLY else ProductionRenderStrategy.NO_OP,
                if (globalAccepted) ProductionRenderOutcome.APPLIED else ProductionRenderOutcome.REJECTED_FOR_QUALITY,
                if (globalAccepted) globalImpact else impactEstimator.estimate(source, source), null,
                listOf(DecisionReason("SEMANTIC_REGIONAL_UNAVAILABLE",
                    if (globalAccepted) "Regional subject correction was unavailable; validated global correction was retained"
                    else "Human evidence exists but neither a reliable regional nor useful global correction was available")),
                System.nanoTime() - started)
            onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.RENDERING_PREVIEW, SmartMagicWorkState.COMPLETED,
                detail = if (globalAccepted) "Global preview rendered" else "Original preserved because correction was unsafe"))
            return rendered
        }

        val noChange = globalCandidate.argb.contentEquals(source.argb)
        val negligible = globalImpact.perceptuallyNegligible
        val finalImage = if (globalAccepted) globalCandidate else source
        val outcome = when {
            noChange -> ProductionRenderOutcome.QUANTIZED_NO_OP
            negligible -> ProductionRenderOutcome.PERCEPTUALLY_NEGLIGIBLE
            else -> ProductionRenderOutcome.APPLIED
        }
        val rendered = SmartMagicProductionRender(finalImage, level,
            if (finalImage === source) ProductionRenderStrategy.NO_OP else ProductionRenderStrategy.GLOBAL_ONLY,
            outcome, globalImpact, null, listOf(DecisionReason("GLOBAL_PLAN", "Validated global plan executed independently")),
            System.nanoTime() - started)
        removeFinishGrid(session, level)
        onProgress(SmartMagicWorkUpdate(SmartMagicWorkStage.RENDERING_PREVIEW, SmartMagicWorkState.COMPLETED,
            detail = if (finalImage === source) "Original preserved" else "Global preview rendered"))
        return rendered
    }

    fun describeAdjustments(
        session: SmartMagicProductionSession,
        render: SmartMagicProductionRender,
    ): List<SmartMagicAdjustment> {
        if (render.strategy == ProductionRenderStrategy.NO_OP) return listOf(
            SmartMagicAdjustment("no-op", "Correction", "Original preserved: ${render.outcome.name.lowercase().replace('_', ' ')}"),
        )
        val regionalAdjustments = if (render.strategy == ProductionRenderStrategy.REGIONAL_ONLY ||
            render.strategy == ProductionRenderStrategy.COMBINED) {
            val strength = render.regionalResult?.appliedStrength ?: 1f
            val personOnlyFallback = render.regionalResult?.decisions?.any {
                it.code == "REGIONAL_PERSON_FALLBACK_APPLIED"
            } == true
            val faceOnlyFallback = render.regionalResult?.decisions?.any {
                it.code == "REGIONAL_FACE_FALLBACK_APPLIED"
            } == true
            residualRegionalPlan(session.regionalPlans.getValue(render.level),
                session.globalParameters.getValue(render.level),
                session.subjectAssessment?.decision == SemanticSubjectDecision.PROTECT_CORRECTLY_EXPOSED_SUBJECT).operations
                .filter { !personOnlyFallback || it.target == com.restorex.app.domain.photointelligence.RegionalTarget.PERSON }
                .filter { !faceOnlyFallback || it.type == com.restorex.app.domain.photointelligence.RegionalOperationType.MIDTONE_GAMMA }
                .map { operation ->
                val target = operation.target.name.lowercase().replaceFirstChar { it.uppercase() }
                val value = operation.amount * strength
                val formatted = when (operation.unit) {
                    "EV" -> "%+.2f EV".format(value)
                    "gamma_delta" -> "%+.3f gamma delta".format(value)
                    "gain_delta" -> "%+.3f gain".format(value)
                    else -> "%+.3f normalized".format(value)
                }
                SmartMagicAdjustment(operation.id, "$target ${operation.type.name.lowercase().replace('_', ' ')}", formatted)
            }
        } else emptyList()
        val p = session.globalParameters.getValue(render.level)
        val globalAdjustments = buildList {
            fun addIf(id: String, label: String, value: Float, neutral: Float, suffix: String) {
                if (kotlin.math.abs(value - neutral) > .0005f) add(SmartMagicAdjustment(id, label, "%+.3f %s".format(value - neutral, suffix)))
            }
            addIf("exposure", "Exposure", p.exposureEv, 0f, "EV")
            addIf("brightness", "Brightness", p.brightness, 0f, "normalized luminance")
            addIf("midtone", "Midtone curve", p.midtoneGamma, 1f, "gamma delta")
            addIf("shadows", "Shadows", p.shadows, 0f, "normalized")
            addIf("highlights", "Highlights", p.highlights, 0f, "normalized")
            addIf("contrast", "Contrast", p.contrast, 0f, "normalized")
            addIf("vibrance", "Vibrance", p.vibrance, 0f, "normalized")
            addIf("luma-noise", "Luminance noise reduction", p.luminanceNoiseReduction, 0f, "normalized")
            addIf("chroma-noise", "Color noise reduction", p.chromaNoiseReduction, 0f, "normalized")
        }
        val faceLightAdjustments = render.explanation.filter { it.code == "FACE_LIGHT_APPLIED" }.map {
            SmartMagicAdjustment("face-light", "Face Light", it.explanation)
        }
        return (globalAdjustments + regionalAdjustments + faceLightAdjustments).ifEmpty {
            listOf(SmartMagicAdjustment("no-op", "Correction", "No measurable adjustment selected"))
        }
    }

    /**
     * Applies an approved per-face light to the preview and refits the same cached affine grid used
     * by fast export. Full-resolution save therefore remains one parallel photographic pass.
     */
    fun applyFaceLight(
        previewSource: RasterImage,
        session: SmartMagicProductionSession,
        baseRender: SmartMagicProductionRender,
        adjustment: FaceLightAdjustment,
        shouldCancel: () -> Boolean = { false },
    ): SmartMagicProductionRender = applyFaceLights(
        previewSource, session, baseRender, listOf(adjustment), shouldCancel,
    )

    fun applyFaceLights(
        previewSource: RasterImage,
        session: SmartMagicProductionSession,
        baseRender: SmartMagicProductionRender,
        adjustments: List<FaceLightAdjustment>,
        shouldCancel: () -> Boolean = { false },
    ): SmartMagicProductionRender {
        val started = System.nanoTime()
        requireCompatibleGeometry(previewSource, session)
        require(adjustments.isNotEmpty()) { "At least one face light is required" }
        require(adjustments.map { it.faceId }.distinct().size == adjustments.size) { "Only one light may target each face" }
        checkCancelled(shouldCancel)
        val litPreview = faceLightRenderer.renderAll(baseRender.image, session.semanticResult.faces, adjustments)
        checkCancelled(shouldCancel)
        cacheFaceLights(session, baseRender.level, adjustments)
        val impact = impactEstimator.estimate(previewSource, litPreview)
        return baseRender.copy(
            image = litPreview,
            strategy = if (baseRender.strategy == ProductionRenderStrategy.NO_OP) ProductionRenderStrategy.REGIONAL_ONLY else baseRender.strategy,
            outcome = ProductionRenderOutcome.APPLIED,
            impact = impact,
            explanation = baseRender.explanation.filterNot { it.code == "FACE_LIGHT_APPLIED" } + DecisionReason(
                "FACE_LIGHT_APPLIED",
                "${adjustments.size} directed face ${if (adjustments.size == 1) "light" else "lights"} applied",
            ),
            elapsedNanos = System.nanoTime() - started,
        )
    }

    fun previewFaceLight(
        source: RasterImage,
        session: SmartMagicProductionSession,
        adjustment: FaceLightAdjustment,
    ): RasterImage = previewFaceLights(source, session, listOf(adjustment))

    fun previewFaceLights(
        source: RasterImage,
        session: SmartMagicProductionSession,
        adjustments: List<FaceLightAdjustment>,
    ): RasterImage = faceLightRenderer.renderAll(source, session.semanticResult.faces, adjustments)

    fun facesDarkestFirst(
        source: RasterImage,
        session: SmartMagicProductionSession,
    ): List<FaceEvidence> = session.semanticResult.faces.sortedBy { faceLightRenderer.meanFaceLuminance(source, it) }

    fun adjustmentDiagnosticReport(
        session: SmartMagicProductionSession,
        render: SmartMagicProductionRender,
        sourceGeneration: Long,
        renderGeneration: Long,
    ): AdjustmentDiagnosticReport = diagnosticBuilder.build(
        session, render, sourceGeneration, renderGeneration,
    )

    /**
     * Export reuses the preview-fitted residual grid. Regional decisions and bounded candidate
     * searches are preview work only; they are never repeated over the full-resolution image.
     */
    fun renderForExport(
        source: RasterImage,
        session: SmartMagicProductionSession,
        preview: SmartMagicProductionRender,
        shouldCancel: () -> Boolean = { false },
    ): SmartMagicProductionRender {
        require(preview.level in EnhancementLevel.entries)
        if (preview.strategy == ProductionRenderStrategy.NO_OP || preview.outcome in setOf(
                ProductionRenderOutcome.NO_CORRECTION_NEEDED,
                ProductionRenderOutcome.QUANTIZED_NO_OP,
                ProductionRenderOutcome.PERCEPTUALLY_NEGLIGIBLE,
                ProductionRenderOutcome.REJECTED_FOR_QUALITY,
                ProductionRenderOutcome.UNSUPPORTED,
            )) {
            return SmartMagicProductionRender(source, preview.level, ProductionRenderStrategy.NO_OP, preview.outcome,
                impactEstimator.estimate(source, source), preview.regionalResult, preview.explanation, 0)
        }
        requireCompatibleGeometry(source, session)
        val started = System.nanoTime()
        val finishGrid = cachedFinishGrid(session, preview.level)
        val faceLights = cachedFaceLights(session, preview.level)
        val wantsRegional = preview.strategy == ProductionRenderStrategy.COMBINED ||
            preview.strategy == ProductionRenderStrategy.REGIONAL_ONLY
        val globalImage = proFinishEngine.render(source, session.proFinishPlans.getValue(preview.level),
            if (wantsRegional) finishGrid else null, shouldCancel)
        checkCancelled(shouldCancel)
        // A bilateral grid is ideal for smooth scene corrections but can average away a small,
        // deliberately localized face light. Reapply that compact ROI operation exactly at source
        // resolution; its cost scales with the detected faces rather than the full photograph.
        val finalImage = if (faceLights.isEmpty()) globalImage else
            faceLightRenderer.renderAll(globalImage, session.semanticResult.faces, faceLights)
        val globalImpact = impactEstimator.estimate(source, finalImage)
        return if (!finalImage.argb.contentEquals(source.argb)) {
            SmartMagicProductionRender(
                finalImage, preview.level,
                if (wantsRegional && finishGrid != null) preview.strategy else ProductionRenderStrategy.GLOBAL_ONLY,
                ProductionRenderOutcome.APPLIED, globalImpact, null,
                preview.explanation + DecisionReason(
                    when {
                        faceLights.isNotEmpty() -> "EXPORT_EXACT_FACE_LIGHT"
                        wantsRegional && finishGrid != null -> "EXPORT_CACHED_BILATERAL_FINISH"
                        else -> "EXPORT_GLOBAL_FALLBACK"
                    },
                    when {
                        faceLights.isNotEmpty() -> "Directed face lights were reproduced exactly at full resolution after the fast scene pass"
                        wantsRegional && finishGrid != null -> "The approved preview correction was upsampled during the single full-resolution photographic pass"
                        else -> "The validated global plan was saved because no approved regional fit was cached"
                    },
                ),
                System.nanoTime() - started,
            )
        } else {
            SmartMagicProductionRender(
                source, preview.level, ProductionRenderStrategy.NO_OP,
                ProductionRenderOutcome.NO_CORRECTION_NEEDED, globalImpact, null,
                preview.explanation + DecisionReason("EXPORT_ORIGINAL_PRESERVED", "No safe full-resolution correction was available"),
                System.nanoTime() - started,
            )
        }
    }

    private fun cacheFinishGrid(
        session: SmartMagicProductionSession,
        level: EnhancementLevel,
        globalPreview: RasterImage,
        approvedPreview: RasterImage,
        shouldCancel: () -> Boolean,
    ) {
        synchronized(finishGridCache) {
            if (finishGridSessionId != session.sessionId) {
                finishGridCache.clear()
                finishGridSessionId = session.sessionId
            }
        }
        finishGridCache[level] = proFinishEngine.fitFinishGrid(globalPreview, approvedPreview, shouldCancel)
    }

    private fun cacheFaceLights(
        session: SmartMagicProductionSession,
        level: EnhancementLevel,
        adjustments: List<FaceLightAdjustment>,
    ) = synchronized(finishGridCache) {
        if (finishGridSessionId != session.sessionId) {
            finishGridCache.clear(); faceLightCache.clear(); finishGridSessionId = session.sessionId
        }
        faceLightCache[level] = adjustments.toList()
    }

    private fun cachedFaceLights(
        session: SmartMagicProductionSession,
        level: EnhancementLevel,
    ): List<FaceLightAdjustment> = synchronized(finishGridCache) {
        if (finishGridSessionId == session.sessionId) faceLightCache[level].orEmpty() else emptyList()
    }

    fun faceLightAdjustments(
        session: SmartMagicProductionSession,
        level: EnhancementLevel,
    ): List<FaceLightAdjustment> = cachedFaceLights(session, level).toList()

    fun restoreFaceLightAdjustments(
        session: SmartMagicProductionSession,
        level: EnhancementLevel,
        adjustments: List<FaceLightAdjustment>,
    ) = synchronized(finishGridCache) {
        if (finishGridSessionId != session.sessionId) {
            finishGridCache.clear(); faceLightCache.clear(); finishGridSessionId = session.sessionId
        }
        if (adjustments.isEmpty()) faceLightCache.remove(level) else faceLightCache[level] = adjustments.toList()
    }

    private fun removeFinishGrid(session: SmartMagicProductionSession, level: EnhancementLevel) {
        synchronized(finishGridCache) {
            if (finishGridSessionId != session.sessionId) {
                finishGridCache.clear(); faceLightCache.clear()
                finishGridSessionId = session.sessionId
            }
            finishGridCache.remove(level)
        }
    }

    private fun cachedFinishGrid(
        session: SmartMagicProductionSession,
        level: EnhancementLevel,
    ): AffineBilateralGrid? = synchronized(finishGridCache) {
        if (finishGridSessionId == session.sessionId) finishGridCache[level] else null
    }

    private fun requireCompatibleGeometry(source: RasterImage, session: SmartMagicProductionSession) {
        val sourceRatio = source.width.toDouble() / source.height
        val sessionRatio = session.sourceWidth.toDouble() / session.sourceHeight
        require(abs(sourceRatio - sessionRatio) <= .003) { "Source geometry changed; semantic session is stale" }
    }

    private fun fingerprint(source: RasterImage): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun updateInt(value: Int) {
            digest.update((value ushr 24).toByte()); digest.update((value ushr 16).toByte())
            digest.update((value ushr 8).toByte()); digest.update(value.toByte())
        }
        updateInt(source.width); updateInt(source.height)
        val step = ceil(source.argb.size / 4096.0).toInt().coerceAtLeast(1)
        for (index in source.argb.indices step step) updateInt(source.argb[index])
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }

    private fun RasterImage.scaledToMaxDimension(maxDimension: Int): RasterImage {
        val largest = maxOf(width, height)
        if (largest <= maxDimension) return this
        val scale = maxDimension.toFloat() / largest
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        val output = IntArray(targetWidth * targetHeight)
        for (y in 0 until targetHeight) {
            val sourceY = (y.toLong() * height / targetHeight).toInt().coerceAtMost(height - 1)
            for (x in 0 until targetWidth) {
                val sourceX = (x.toLong() * width / targetWidth).toInt().coerceAtMost(width - 1)
                output[y * targetWidth + x] = argb[sourceY * width + sourceX]
            }
        }
        return RasterImage(targetWidth, targetHeight, output, scaleFromOriginal * scale)
    }

    private fun checkCancelled(shouldCancel: () -> Boolean) {
        if (shouldCancel()) throw CancellationException("Smart Magic production processing cancelled")
    }

    /**
     * Regional operations are residual corrections after the global base. This prevents applying
     * the same exposure, shadow or midtone deficit twice while preserving independently justified
     * background operations.
     */
    private fun residualRegionalPlan(
        plan: com.restorex.app.domain.photointelligence.RegionalCorrectionPlan,
        global: com.restorex.app.domain.smartmagic.SmartMagicParameters,
        protectCorrectSubject: Boolean = false,
    ): com.restorex.app.domain.photointelligence.RegionalCorrectionPlan {
        val operations = plan.operations.mapNotNull { operation ->
            val residual = when (operation.type) {
                com.restorex.app.domain.photointelligence.RegionalOperationType.EXPOSURE ->
                    operation.amount - global.exposureEv.coerceAtLeast(0f) * .65f - global.brightness.coerceAtLeast(0f) * .35f
                com.restorex.app.domain.photointelligence.RegionalOperationType.SHADOWS ->
                    operation.amount - global.shadows.coerceAtLeast(0f) * .50f
                com.restorex.app.domain.photointelligence.RegionalOperationType.MIDTONE_GAMMA ->
                    operation.amount - (global.midtoneGamma - 1f).coerceAtLeast(0f) * .55f - global.brightness.coerceAtLeast(0f) * .25f
                com.restorex.app.domain.photointelligence.RegionalOperationType.HIGHLIGHTS ->
                    operation.amount - (-global.highlights).coerceAtLeast(0f) * .35f
                else -> operation.amount
            }.coerceAtLeast(0f)
            if (residual < .004f) null else operation.copy(amount = residual)
        }.toMutableList()
        val protectiveExposure = if (protectCorrectSubject)
            (-global.exposureEv).coerceAtLeast(0f) * .82f + (-global.brightness).coerceAtLeast(0f) * .35f else 0f
        if (protectiveExposure >= .015f && operations.none {
                it.type == com.restorex.app.domain.photointelligence.RegionalOperationType.EXPOSURE &&
                    it.target == com.restorex.app.domain.photointelligence.RegionalTarget.PERSON
            }) {
            operations += com.restorex.app.domain.photointelligence.RegionalOperation(
                "protect-subject-from-global-darkening",
                com.restorex.app.domain.photointelligence.RegionalOperationType.EXPOSURE,
                com.restorex.app.domain.photointelligence.RegionalTarget.PERSON,
                protectiveExposure.coerceAtMost(.42f), "EV", .92f,
                listOf("CORRECTLY_EXPOSED_SUBJECT", "GLOBAL_NEGATIVE_EXPOSURE"),
                listOf("NO_NEW_CLIPPING", "PROTECTED_INTERIOR", "BOUNDARY_DISCONTINUITY", "FINITE_OUTPUT"),
            )
        }
        return plan.copy(operations = operations, explanation = plan.explanation + DecisionReason(
            "REGIONAL_RESIDUAL_AFTER_GLOBAL", "Regional values account for the accepted global tonal correction"))
    }

    private fun faceIlluminationDeficit(source: RasterImage, faces: List<com.restorex.app.domain.photointelligence.FaceEvidence>): Float {
        if (faces.isEmpty()) return 0f
        val means = faces.map { face ->
            val left = (face.bounds.left * source.width).toInt().coerceIn(0, source.width - 1)
            val top = (face.bounds.top * source.height).toInt().coerceIn(0, source.height - 1)
            val right = (face.bounds.right * source.width).toInt().coerceIn(left + 1, source.width)
            val bottom = (face.bounds.bottom * source.height).toInt().coerceIn(top + 1, source.height)
            var sum = 0.0; var count = 0
            for (y in top until bottom) for (x in left until right) {
                val color = source.argb[y * source.width + x]
                val r = com.restorex.app.domain.smartmagic.SmartMagicColorMath.srgbToLinear((color ushr 16 and 255) / 255f)
                val g = com.restorex.app.domain.smartmagic.SmartMagicColorMath.srgbToLinear((color ushr 8 and 255) / 255f)
                val b = com.restorex.app.domain.smartmagic.SmartMagicColorMath.srgbToLinear((color and 255) / 255f)
                sum += com.restorex.app.domain.smartmagic.SmartMagicColorMath.luminance(r, g, b); count++
            }
            (sum / count.coerceAtLeast(1)).toFloat()
        }
        if (means.size == 1) {
            val face = faces.single()
            val left = (face.bounds.left * source.width).toInt().coerceIn(0, source.width - 1)
            val top = (face.bounds.top * source.height).toInt().coerceIn(0, source.height - 1)
            val right = (face.bounds.right * source.width).toInt().coerceIn(left + 1, source.width)
            val bottom = (face.bounds.bottom * source.height).toInt().coerceIn(top + 1, source.height)
            val luminance = FloatArray((right - left) * (bottom - top))
            var index = 0
            for (y in top until bottom) for (x in left until right) {
                val color = source.argb[y * source.width + x]
                val r = com.restorex.app.domain.smartmagic.SmartMagicColorMath.srgbToLinear((color ushr 16 and 255) / 255f)
                val g = com.restorex.app.domain.smartmagic.SmartMagicColorMath.srgbToLinear((color ushr 8 and 255) / 255f)
                val b = com.restorex.app.domain.smartmagic.SmartMagicColorMath.srgbToLinear((color and 255) / 255f)
                luminance[index++] = com.restorex.app.domain.smartmagic.SmartMagicColorMath.luminance(r, g, b)
            }
            luminance.sort()
            val median = luminance[luminance.size / 2]
            // Median facial tone is robust to bright hair, glasses and specular highlights while
            // the low target remains conservative across complexion differences.
            return (.22f - median).coerceIn(0f, .10f)
        }
        // Preserve genuine complexion differences: only recover the portion below 72% of the
        // brightest reliably detected face, rather than equalizing faces to one target.
        return (means.maxOrNull()!! * .72f - means.minOrNull()!!).coerceIn(0f, .16f)
    }

    private fun calibrateAdvanced(
        parameters: com.restorex.app.domain.smartmagic.SmartMagicParameters,
        level: EnhancementLevel,
        advanced: com.restorex.app.domain.photointelligence.AdvancedPhotometricEvidence,
    ): com.restorex.app.domain.smartmagic.SmartMagicParameters {
        val dehazeCap = when (level) {
            EnhancementLevel.NATURAL -> .025f
            EnhancementLevel.ENHANCED -> .050f
            EnhancementLevel.EXTREME -> .070f
        }
        val clarityCap = when (level) {
            EnhancementLevel.NATURAL -> .055f
            EnhancementLevel.ENHANCED -> .090f
            EnhancementLevel.EXTREME -> .115f
        }
        val textureCap = when (level) {
            EnhancementLevel.NATURAL -> .040f
            EnhancementLevel.ENHANCED -> .070f
            EnhancementLevel.EXTREME -> .090f
        }
        val intentGuard = 1f - advanced.atmosphericIntentRisk * .9f
        val dehaze = minOf(parameters.dehaze,
            advanced.hazeConfidence * intentGuard * dehazeCap).coerceAtLeast(0f)
        val detailGuard = advanced.detailConfidence * (1f - advanced.noiseConstraint * .8f)
        return parameters.copy(
            dehaze = dehaze,
            clarity = minOf(parameters.clarity, clarityCap * (.35f + detailGuard * .65f)),
            texture = minOf(parameters.texture, textureCap * detailGuard),
            highlights = parameters.highlights * (1f - advanced.highKeyIntent * .55f),
            shadows = parameters.shadows * (1f - advanced.lowKeyIntent * .78f) *
                (1f - advanced.noiseConstraint * .55f),
            redGain = 1f + (parameters.redGain - 1f) * (1f - advanced.warmLightIntent * .82f),
            greenGain = 1f + (parameters.greenGain - 1f) * (1f - advanced.warmLightIntent * .82f),
            blueGain = 1f + (parameters.blueGain - 1f) * (1f - advanced.warmLightIntent * .82f),
        )
    }

    private fun protectSemanticSubject(
        parameters: com.restorex.app.domain.smartmagic.SmartMagicParameters,
        decision: SemanticSubjectDecision?,
        level: EnhancementLevel,
    ): com.restorex.app.domain.smartmagic.SmartMagicParameters {
        if (decision != SemanticSubjectDecision.PROTECT_CORRECTLY_EXPOSED_SUBJECT) return parameters
        return parameters.copy(
            // Endpoint and highlight operators can control bright clothing/ceilings without
            // darkening a correctly exposed detected subject.
            exposureEv = parameters.exposureEv.coerceAtLeast(0f),
            brightness = parameters.brightness.coerceAtLeast(0f),
            // Subject-safe contrast is supplied through the background complement; a global
            // contrast/black-point move would darken facial midtones before that mask is applied.
            contrast = parameters.contrast.coerceAtMost(0f),
            blacks = parameters.blacks.coerceAtLeast(0f),
        )
    }
}
