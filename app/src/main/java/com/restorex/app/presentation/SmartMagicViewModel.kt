package com.restorex.app.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restorex.app.AppContainer
import com.restorex.app.data.SmartMagicExportManager
import com.restorex.app.data.SmartMagicProcessingCoordinator
import com.restorex.app.domain.model.ImageSource
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.FaceEvidence
import com.restorex.app.domain.photointelligence.FaceLightAdjustment
import com.restorex.app.domain.photointelligence.ProductionRenderOutcome
import com.restorex.app.domain.photointelligence.SmartMagicProductionSession
import com.restorex.app.domain.photointelligence.SmartMagicProductionRender
import com.restorex.app.domain.photointelligence.SmartMagicAdjustment
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticReport
import com.restorex.app.domain.photointelligence.SmartMagicWorkStage
import com.restorex.app.domain.photointelligence.SmartMagicWorkState
import com.restorex.app.domain.photointelligence.SmartMagicWorkUpdate
import com.restorex.app.domain.smartmagic.SmartMagicAnalysis
import com.restorex.app.domain.smartmagic.SmartMagicParameters
import com.restorex.app.domain.usecase.LoadImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class SmartMagicPhase {
    IDLE, LOADING_SOURCE, ORIGINAL_READY, ANALYZING, RENDERING,
    /** Kept for source compatibility with older UI/tests; new production code uses explicit phases. */
    PROCESSING,
    READY, SAVING, SAVED, ERROR,
}

data class SmartMagicProgressEvent(
    val sourceGeneration: Long,
    val processingGeneration: Long?,
    val level: EnhancementLevel?,
    val stage: SmartMagicWorkStage,
    val label: String,
    val detail: String?,
    val state: SmartMagicWorkState,
    val completedWorkUnits: Int? = null,
    val totalWorkUnits: Int? = null,
    val elapsedStageMillis: Long? = null,
) {
    val fraction: Float?
        get() = if (completedWorkUnits != null && totalWorkUnits != null) completedWorkUnits.toFloat() / totalWorkUnits else null
}

data class SmartMagicUiState(
    val phase: SmartMagicPhase = SmartMagicPhase.IDLE,
    val original: RasterImage? = null,
    val enhanced: RasterImage? = null,
    val analysis: SmartMagicAnalysis? = null,
    val parameters: SmartMagicParameters? = null,
    val stage: String? = null,
    val source: String? = null,
    val savedUri: String? = null,
    val savedMimeType: String? = null,
    val message: String? = null,
    val previewElapsedMillis: Long? = null,
    val sourceLoadElapsedMillis: Long? = null,
    val analysisElapsedMillis: Long? = null,
    val modeRenderElapsedMillis: Long? = null,
    val firstVisibleElapsedMillis: Long? = null,
    val exportElapsedMillis: Long? = null,
    val selectedLevel: EnhancementLevel = EnhancementLevel.NATURAL,
    val completedLevel: EnhancementLevel? = null,
    val renderOutcome: ProductionRenderOutcome? = null,
    val sourceGeneration: Long = 0,
    val activeRenderGeneration: Long = 0,
    val completedRenderGeneration: Long? = null,
    val sessionIdentity: String? = null,
    val planIdentity: String? = null,
    val modeSelectionEnabled: Boolean = false,
    val exportLevel: EnhancementLevel? = null,
    val progress: SmartMagicProgressEvent? = null,
    val adjustments: List<SmartMagicAdjustment> = emptyList(),
    val adjustmentReport: AdjustmentDiagnosticReport? = null,
    val faces: List<FaceEvidence> = emptyList(),
    val faceLightPreview: RasterImage? = null,
    val faceLightWorking: Boolean = false,
    val canUndoFaceLight: Boolean = false,
    val faceLightRevision: Long = 0,
) {
    val canCompare get() = original != null && enhanced != null && completedLevel == selectedLevel
    val isWorking get() = phase in setOf(SmartMagicPhase.LOADING_SOURCE, SmartMagicPhase.ANALYZING,
        SmartMagicPhase.RENDERING, SmartMagicPhase.PROCESSING, SmartMagicPhase.SAVING)
    val displayedPreviewIsCurrent get() = enhanced != null && completedLevel == selectedLevel &&
        completedRenderGeneration != null
    val adjustmentReportIsCurrent get() = adjustmentReport?.mode == selectedLevel &&
        adjustmentReport.sourceProcessingGeneration == sourceGeneration &&
        adjustmentReport.renderGeneration == completedRenderGeneration
}

class SmartMagicViewModel(
    private val loadImage: LoadImage,
    private val coordinator: SmartMagicProcessingCoordinator,
    private val exportManager: SmartMagicExportManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SmartMagicUiState())
    val state: StateFlow<SmartMagicUiState> = _state.asStateFlow()
    private var analysisJob: Job? = null
    private var renderJob: Job? = null
    private var exportJob: Job? = null
    private var faceLightJob: Job? = null
    private val generations = SmartMagicGenerationTracker()
    private var session: SmartMagicProductionSession? = null
    private var previewSource: RasterImage? = null
    private val previewCache = mutableMapOf<EnhancementLevel, SmartMagicProductionRender>()
    private data class FaceLightUndo(
        val render: SmartMagicProductionRender,
        val adjustments: List<FaceLightAdjustment>,
    )
    private val faceLightUndo = mutableMapOf<EnhancementLevel, MutableList<FaceLightUndo>>()

    fun process(source: String, initialLevel: EnhancementLevel = EnhancementLevel.NATURAL) {
        analysisJob?.cancel(); renderJob?.cancel(); exportJob?.cancel(); faceLightJob?.cancel()
        val sourceRequest = generations.newSource()
        previewCache.clear(); faceLightUndo.clear(); session = null; previewSource = null
        trace("source_selected", sourceRequest, null, "sourceKey=${source.hashCode()}")
        analysisJob = viewModelScope.launch {
            val started = System.nanoTime()
            _state.value = SmartMagicUiState(phase = SmartMagicPhase.LOADING_SOURCE, stage = "Loading photograph",
                source = source, sourceGeneration = sourceRequest, selectedLevel = initialLevel,
                progress = progressEvent(sourceRequest, null, null, SmartMagicWorkUpdate(
                    SmartMagicWorkStage.LOADING_PHOTOGRAPH, SmartMagicWorkState.STARTED,
                    detail = "Opening the selected photograph")))
            try {
                val raster = loadImage(ImageSource(source), coordinator.processingBudget.analysisMaxDimension)
                if (!generations.isSourceCurrent(sourceRequest)) return@launch
                val preview = raster.scaledToMaxDimension(coordinator.processingBudget.previewMaxDimension)
                previewSource = preview
                _state.update { it.copy(phase = SmartMagicPhase.ORIGINAL_READY, stage = "Original preview ready",
                    original = preview, enhanced = null, completedLevel = null,
                    sourceLoadElapsedMillis = (System.nanoTime() - started) / 1_000_000L,
                    firstVisibleElapsedMillis = (System.nanoTime() - started) / 1_000_000L) }
                trace("original_ready", sourceRequest, null, "${preview.width}x${preview.height}")
                _state.update { it.copy(phase = SmartMagicPhase.ANALYZING, stage = "Analyzing light, subject, and detail") }
                val context = currentCoroutineContext()
                val productionSession = coordinator.createSession(source, raster, shouldCancel = {
                    !context.isActive || !generations.isSourceCurrent(sourceRequest)
                }, onProgress = { update -> publishProgress(sourceRequest, null, null, update) })
                context.ensureActive()
                if (!generations.isSourceCurrent(sourceRequest)) return@launch
                session = productionSession
                _state.update { it.copy(analysis = productionSession.smartAnalysis,
                    sessionIdentity = productionSession.sessionId, modeSelectionEnabled = true,
                    faces = coordinator.facesDarkestFirst(preview, productionSession),
                    analysisElapsedMillis = productionSession.createdElapsedNanos / 1_000_000L) }
                trace("analysis_ready", sourceRequest, null, "session=${productionSession.sessionId}")
                requestLevelRender(initialLevel, productionSession, preview, sourceRequest, started)
            } catch (cancelled: CancellationException) {
                if (generations.isSourceCurrent(sourceRequest) && session == null) {
                    _state.update { it.copy(phase = if (it.original == null) SmartMagicPhase.IDLE else SmartMagicPhase.ORIGINAL_READY,
                        stage = null) }
                }
            } catch (error: Throwable) {
                if (generations.isSourceCurrent(sourceRequest)) _state.update {
                    it.copy(phase = SmartMagicPhase.ERROR, stage = null,
                        message = error.message ?: "Smart Magic could not process this photo",
                        progress = it.progress?.copy(state = SmartMagicWorkState.FAILED,
                            detail = error.message ?: "Photograph analysis failed"))
                }
            }
        }
    }

    fun selectLevel(level: EnhancementLevel) {
        if (_state.value.phase == SmartMagicPhase.SAVING) return
        val activeSession = session ?: return
        val source = previewSource ?: return
        faceLightJob?.cancel()
        _state.update { it.copy(faceLightPreview = null, faceLightWorking = false,
            canUndoFaceLight = faceLightUndo[level]?.isNotEmpty() == true) }
        requestLevelRender(level, activeSession, source, generations.currentSource(), null)
    }

    fun previewFaceLight(adjustment: FaceLightAdjustment) {
        previewFaceLights(listOf(adjustment))
    }

    fun previewFaceLights(adjustments: List<FaceLightAdjustment>) {
        if (adjustments.isEmpty()) {
            cancelFaceLight()
            return
        }
        val activeSession = session ?: return
        val base = previewCache[_state.value.selectedLevel] ?: return
        faceLightJob?.cancel()
        val sourceGeneration = _state.value.sourceGeneration
        faceLightJob = viewModelScope.launch {
            _state.update { it.copy(faceLightWorking = true, message = null) }
            try {
                val preview = withContext(Dispatchers.Default) {
                    coordinator.previewFaceLights(base.image, activeSession, adjustments)
                }
                if (generations.isSourceCurrent(sourceGeneration)) {
                    _state.update { it.copy(faceLightPreview = preview, faceLightWorking = false) }
                }
            } catch (_: CancellationException) {
                // A newer face/style/strength preview superseded this one.
            } catch (error: Throwable) {
                if (generations.isSourceCurrent(sourceGeneration)) _state.update {
                    it.copy(faceLightWorking = false, message = error.message ?: "Face light preview failed")
                }
            }
        }
    }

    fun cancelFaceLight() {
        faceLightJob?.cancel()
        _state.update { it.copy(faceLightPreview = null, faceLightWorking = false) }
    }

    fun applyFaceLight(adjustment: FaceLightAdjustment) {
        applyFaceLights(listOf(adjustment))
    }

    fun applyFaceLights(adjustments: List<FaceLightAdjustment>) {
        if (adjustments.isEmpty()) return
        val activeSession = session ?: return
        val source = previewSource ?: return
        val level = _state.value.selectedLevel
        val base = previewCache[level] ?: return
        val previousFaceLights = coordinator.faceLightAdjustments(activeSession, level)
        faceLightJob?.cancel()
        val sourceGeneration = _state.value.sourceGeneration
        faceLightJob = viewModelScope.launch {
            _state.update { it.copy(phase = SmartMagicPhase.RENDERING, faceLightWorking = true,
                faceLightPreview = null, stage = "Applying face light", message = null) }
            try {
                val context = currentCoroutineContext()
                val rendered = withContext(Dispatchers.Default) {
                    coordinator.applyFaceLights(source, activeSession, base, adjustments) {
                        !context.isActive || !generations.isSourceCurrent(sourceGeneration)
                    }
                }
                context.ensureActive()
                if (generations.isSourceCurrent(sourceGeneration)) {
                    faceLightUndo.getOrPut(level) { mutableListOf() } += FaceLightUndo(base, previousFaceLights)
                    previewCache[level] = rendered
                    val adjustments = coordinator.describeAdjustments(activeSession, rendered)
                    _state.update { it.copy(phase = SmartMagicPhase.READY, enhanced = rendered.image,
                        renderOutcome = rendered.outcome, adjustments = adjustments,
                        faceLightWorking = false, stage = null, message = "Face light applied",
                        canUndoFaceLight = true, faceLightRevision = it.faceLightRevision + 1) }
                }
            } catch (_: CancellationException) {
                if (generations.isSourceCurrent(sourceGeneration)) _state.update {
                    it.copy(phase = SmartMagicPhase.READY, faceLightWorking = false, stage = null)
                }
            } catch (error: Throwable) {
                if (generations.isSourceCurrent(sourceGeneration)) _state.update {
                    it.copy(phase = SmartMagicPhase.READY, faceLightWorking = false, stage = null,
                        message = error.message ?: "Could not apply face light")
                }
            }
        }
    }

    fun undoFaceLight() {
        val activeSession = session ?: return
        val level = _state.value.selectedLevel
        val stack = faceLightUndo[level] ?: return
        val undo = stack.removeLastOrNull() ?: return
        faceLightJob?.cancel()
        coordinator.restoreFaceLightAdjustments(activeSession, level, undo.adjustments)
        previewCache[level] = undo.render
        val described = coordinator.describeAdjustments(activeSession, undo.render)
        _state.update {
            it.copy(
                phase = SmartMagicPhase.READY,
                enhanced = undo.render.image,
                renderOutcome = undo.render.outcome,
                adjustments = described,
                faceLightPreview = null,
                faceLightWorking = false,
                stage = null,
                message = "Face light undone",
                canUndoFaceLight = stack.isNotEmpty(),
                faceLightRevision = it.faceLightRevision + 1,
            )
        }
    }

    private fun requestLevelRender(
        level: EnhancementLevel,
        activeSession: SmartMagicProductionSession,
        source: RasterImage,
        sourceRequest: Long,
        workflowStartedNanos: Long?,
    ) {
        renderJob?.cancel()
        val renderRequest = generations.newRender()
        val planId = planIdentity(activeSession, level)
        _state.update { it.copy(selectedLevel = level, activeRenderGeneration = renderRequest,
            parameters = activeSession.globalParameters.getValue(level), planIdentity = planId,
            phase = SmartMagicPhase.RENDERING, stage = "Applying ${level.name.lowercase()} correction",
            progress = progressEvent(sourceRequest, renderRequest, level, SmartMagicWorkUpdate(
                SmartMagicWorkStage.RENDERING_PREVIEW, SmartMagicWorkState.STARTED,
                detail = "Applying ${level.name.lowercase()} correction")),
            message = null, adjustments = emptyList(), adjustmentReport = null) }
        trace("mode_requested", sourceRequest, renderRequest, "mode=$level plan=$planId")
        previewCache[level]?.let { cached ->
            if (isCurrent(sourceRequest, renderRequest, activeSession, level)) publishRender(
                cached, level, sourceRequest, renderRequest, activeSession, cached = true)
            return
        }
        renderJob = viewModelScope.launch {
            val renderStarted = System.nanoTime()
            try {
                val context = currentCoroutineContext()
                val rendered = withContext(Dispatchers.Default) {
                    coordinator.render(source, activeSession, level, shouldCancel = {
                        !context.isActive || !isCurrent(sourceRequest, renderRequest, activeSession, level)
                    }, onProgress = { update -> publishProgress(sourceRequest, renderRequest, level, update) })
                }
                context.ensureActive()
                if (isCurrent(sourceRequest, renderRequest, activeSession, level)) {
                    previewCache[level] = rendered
                    publishRender(rendered, level, sourceRequest, renderRequest, activeSession, cached = false,
                        renderElapsedMillis = (System.nanoTime() - renderStarted) / 1_000_000L,
                        workflowElapsedMillis = workflowStartedNanos?.let { (System.nanoTime() - it) / 1_000_000L })
                } else trace("render_discarded", sourceRequest, renderRequest, "mode=$level")
            } catch (_: CancellationException) {
                trace("render_cancelled", sourceRequest, renderRequest, "mode=$level")
                // A cancelled render must never change phase or bitmap for a newer request.
            } catch (error: Throwable) {
                if (isCurrent(sourceRequest, renderRequest, activeSession, level)) _state.update {
                    it.copy(phase = if (it.enhanced == null) SmartMagicPhase.ORIGINAL_READY else SmartMagicPhase.READY,
                        stage = null, message = error.message ?: "Could not prepare this enhancement level",
                        progress = it.progress?.copy(state = SmartMagicWorkState.FAILED,
                            detail = error.message ?: "Preview rendering failed"))
                }
            }
        }
    }

    private fun publishRender(
        rendered: SmartMagicProductionRender,
        level: EnhancementLevel,
        sourceRequest: Long,
        renderRequest: Long,
        activeSession: SmartMagicProductionSession,
        cached: Boolean,
        renderElapsedMillis: Long = 0,
        workflowElapsedMillis: Long? = null,
    ) {
        if (!isCurrent(sourceRequest, renderRequest, activeSession, level)) return
        val adjustmentSummary = coordinator.describeAdjustments(activeSession, rendered)
        val diagnosticReport = coordinator.adjustmentDiagnosticReport(
            activeSession, rendered, sourceRequest, renderRequest,
        )
        _state.update { it.copy(phase = SmartMagicPhase.READY, enhanced = rendered.image,
            completedLevel = level, completedRenderGeneration = renderRequest, stage = null,
            renderOutcome = rendered.outcome, message = userMessage(rendered.outcome),
            adjustments = adjustmentSummary,
            adjustmentReport = diagnosticReport,
            progress = progressEvent(sourceRequest, renderRequest, level, SmartMagicWorkUpdate(
                if (cached) SmartMagicWorkStage.CACHED_PREVIEW else SmartMagicWorkStage.FINALIZING,
                SmartMagicWorkState.COMPLETED,
                detail = if (cached) "Cached ${level.name.lowercase()} preview displayed" else "Preview ready"))) }
        _state.update { it.copy(modeRenderElapsedMillis = renderElapsedMillis,
            previewElapsedMillis = workflowElapsedMillis ?: it.previewElapsedMillis) }
        trace("render_published", sourceRequest, renderRequest,
            "mode=$level cached=$cached outcome=${rendered.outcome} bitmap=${bitmapIdentity(rendered.image)}")
    }

    fun save() {
        val source = _state.value.source ?: return
        val activeSession = session ?: return
        val selectedLevel = _state.value.selectedLevel
        val selectedPreview = previewCache[selectedLevel] ?: return
        if (_state.value.completedLevel != selectedLevel || !_state.value.displayedPreviewIsCurrent) return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _state.update { it.copy(phase = SmartMagicPhase.SAVING, stage = "Preparing full-resolution export",
                exportLevel = selectedLevel, message = null,
                progress = progressEvent(it.sourceGeneration, it.activeRenderGeneration, selectedLevel,
                    SmartMagicWorkUpdate(SmartMagicWorkStage.PREPARING_EXPORT, SmartMagicWorkState.STARTED,
                        detail = "Preparing full-resolution image"))) }
            try {
                val sourceGeneration = _state.value.sourceGeneration
                val renderGeneration = _state.value.activeRenderGeneration
                val result = exportManager.export(source, activeSession, selectedPreview) { update ->
                    if (generations.isSourceCurrent(sourceGeneration)) {
                        val event = progressEvent(sourceGeneration, renderGeneration, selectedLevel, update)
                        _state.update { current -> current.copy(stage = event.label, progress = event) }
                    }
                }
                _state.update {
                    it.copy(phase = SmartMagicPhase.SAVED, stage = null, savedUri = result.uri,
                        savedMimeType = result.mimeType, message = "Saved ${result.displayName} to the gallery",
                        exportElapsedMillis = result.elapsedMillis, exportLevel = selectedLevel,
                        progress = progressEvent(it.sourceGeneration, it.activeRenderGeneration, selectedLevel,
                            SmartMagicWorkUpdate(SmartMagicWorkStage.FINALIZING, SmartMagicWorkState.COMPLETED,
                                detail = "Saved to gallery")))
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(phase = SmartMagicPhase.READY, stage = null, message = "Save cancelled") }
            } catch (error: Throwable) {
                _state.update { it.copy(phase = SmartMagicPhase.READY, stage = null,
                    message = error.message ?: "Could not save the enhanced photo",
                    progress = it.progress?.copy(state = SmartMagicWorkState.FAILED,
                        detail = error.message ?: "Export failed")) }
            }
        }
    }

    fun cancel() {
        analysisJob?.cancel(); renderJob?.cancel(); exportJob?.cancel(); faceLightJob?.cancel()
        _state.update { current ->
            if (!current.isWorking) current else current.copy(
                phase = if (current.enhanced != null) SmartMagicPhase.READY else if (current.original != null) SmartMagicPhase.ORIGINAL_READY else SmartMagicPhase.IDLE,
                stage = null,
                message = "Processing cancelled",
                progress = current.progress?.copy(state = SmartMagicWorkState.CANCELLED, detail = "Cancelled"),
            )
        }
    }
    fun reset() { generations.newSource(); cancel(); session = null; previewSource = null; previewCache.clear(); faceLightUndo.clear(); _state.value = SmartMagicUiState() }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() { cancel(); super.onCleared() }

    private fun RasterImage.scaledToMaxDimension(maxDimension: Int): RasterImage {
        val largest = maxOf(width, height)
        if (largest <= maxDimension) return this
        val scale = maxDimension.toFloat() / largest
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
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

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SmartMagicViewModel(
            container.loadImage, container.smartMagicCoordinator, container.smartMagicExportManager,
        ) as T
    }

    private companion object {
        const val TAG = "SmartMagicTrace"
    }

    private fun isCurrent(sourceRequest: Long, renderRequest: Long, activeSession: SmartMagicProductionSession,
        level: EnhancementLevel): Boolean = generations.isCurrent(sourceRequest, renderRequest) &&
        session?.sessionId == activeSession.sessionId && _state.value.selectedLevel == level

    private fun planIdentity(activeSession: SmartMagicProductionSession, level: EnhancementLevel): String =
        activeSession.regionalPlans[level]?.let { plan ->
            "${plan.schemaVersion}:${plan.operations.joinToString("+") { it.id }}"
        } ?: "global:${activeSession.globalParameters.getValue(level).hashCode()}"

    private fun bitmapIdentity(image: RasterImage): String {
        var hash = 17
        val step = (image.argb.size / 64).coerceAtLeast(1)
        for (index in image.argb.indices step step) hash = hash * 31 + image.argb[index]
        return "${image.width}x${image.height}:${hash.toUInt().toString(16)}"
    }

    private fun trace(event: String, sourceGeneration: Long, renderGeneration: Long?, detail: String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(
            TAG, "event=$event sourceGen=$sourceGeneration renderGen=${renderGeneration ?: "-"} $detail",
        )
    }

    private fun userMessage(outcome: ProductionRenderOutcome): String? = when (outcome) {
        ProductionRenderOutcome.NO_CORRECTION_NEEDED,
        ProductionRenderOutcome.QUANTIZED_NO_OP,
        ProductionRenderOutcome.PERCEPTUALLY_NEGLIGIBLE -> "This photo already looks balanced"
        ProductionRenderOutcome.REJECTED_FOR_QUALITY -> "The original was preserved because a stronger correction was not safe"
        ProductionRenderOutcome.UNSUPPORTED -> "This photo could not be corrected safely"
        else -> null
    }

    private fun publishProgress(
        sourceGeneration: Long,
        renderGeneration: Long?,
        level: EnhancementLevel?,
        update: SmartMagicWorkUpdate,
    ) {
        if (!generations.isSourceCurrent(sourceGeneration)) return
        if (renderGeneration != null && !generations.isCurrent(sourceGeneration, renderGeneration)) return
        val event = progressEvent(sourceGeneration, renderGeneration, level, update)
        _state.update { current -> current.copy(stage = event.label, progress = event) }
    }

    private fun progressEvent(
        sourceGeneration: Long,
        renderGeneration: Long?,
        level: EnhancementLevel?,
        update: SmartMagicWorkUpdate,
    ) = SmartMagicProgressEvent(
        sourceGeneration = sourceGeneration,
        processingGeneration = renderGeneration,
        level = level,
        stage = update.stage,
        label = when (update.stage) {
            SmartMagicWorkStage.LOADING_PHOTOGRAPH -> "Loading photograph"
            SmartMagicWorkStage.ANALYZING_LIGHTING -> "Analyzing lighting"
            SmartMagicWorkStage.DETECTING_SUBJECTS -> "Detecting faces and subjects"
            SmartMagicWorkStage.EVALUATING_TONE -> "Evaluating exposure and tone"
            SmartMagicWorkStage.OPTIMIZING_TONAL_BALANCE -> "Optimizing tonal balance"
            SmartMagicWorkStage.RENDERING_PREVIEW -> "Rendering corrected preview"
            SmartMagicWorkStage.PREPARING_EXPORT -> "Preparing full-resolution image"
            SmartMagicWorkStage.RENDERING_EXPORT -> "Applying selected corrections"
            SmartMagicWorkStage.ENCODING_AND_SAVING -> "Encoding and saving image"
            SmartMagicWorkStage.FINALIZING -> "Finalizing image"
            SmartMagicWorkStage.CACHED_PREVIEW -> "Cached preview ready"
        },
        detail = update.detail,
        state = update.state,
        completedWorkUnits = update.completedWorkUnits,
        totalWorkUnits = update.totalWorkUnits,
    )
}

/** Small deterministic gate used by production and race-condition tests. */
internal class SmartMagicGenerationTracker {
    private var sourceGeneration = 0L
    private var renderGeneration = 0L
    @Synchronized fun newSource(): Long { sourceGeneration++; renderGeneration++; return sourceGeneration }
    @Synchronized fun newRender(): Long { renderGeneration++; return renderGeneration }
    @Synchronized fun currentSource(): Long = sourceGeneration
    @Synchronized fun isSourceCurrent(source: Long): Boolean = source == sourceGeneration
    @Synchronized fun isCurrent(source: Long, render: Long): Boolean =
        source == sourceGeneration && render == renderGeneration
}
