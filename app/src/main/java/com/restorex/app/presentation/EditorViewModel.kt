package com.restorex.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restorex.app.AppContainer
import com.restorex.app.domain.engine.FilterCatalog
import com.restorex.app.domain.engine.CropCalculator
import com.restorex.app.domain.engine.SourceCoordinateMapper
import com.restorex.app.domain.history.EditSession
import com.restorex.app.domain.model.Adjustments
import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.ExportFormat
import com.restorex.app.domain.model.ExportOptions
import com.restorex.app.domain.model.ImportedLut
import com.restorex.app.domain.model.FilterPreset
import com.restorex.app.domain.model.FilterSelection
import com.restorex.app.domain.model.ImageSource
import com.restorex.app.domain.model.LutSelection
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.model.BrushEditType
import com.restorex.app.domain.model.BrushSettings
import com.restorex.app.domain.model.BrushStroke
import com.restorex.app.domain.model.LinearLight
import com.restorex.app.domain.model.LocalLighting
import com.restorex.app.domain.model.LocalEditOperation
import com.restorex.app.domain.model.NormalizedPoint
import com.restorex.app.domain.model.RadialLight
import com.restorex.app.domain.usecase.ExportImage
import com.restorex.app.domain.usecase.LoadImage
import com.restorex.app.domain.usecase.RenderEdits
import com.restorex.app.domain.usecase.RenderSharpenMask
import com.restorex.app.data.LutStore
import com.restorex.app.data.PerformanceDiagnostics
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.sqrt
import java.util.concurrent.ConcurrentHashMap

enum class RetouchTool(val label: String) {
    NONE("Navigate"), HEAL("Heal"), CLONE("Clone"), SMOOTH("Smooth"), DODGE("Dodge"), BURN("Burn"),
    RADIAL("Radial Light"), LINEAR("Linear Light"), BRUSH_LIGHT("Brush Mask"), ERASER("Eraser"),
}

data class RetouchUiState(
    val tool: RetouchTool = RetouchTool.NONE,
    val brush: BrushSettings = BrushSettings(),
    val cloneSource: NormalizedPoint? = null,
    val cloneSourceVisible: NormalizedPoint? = null,
    val cloneOffsetX: Float? = null,
    val cloneOffsetY: Float? = null,
    val selectingCloneSource: Boolean = false,
    val alignedSampling: Boolean = true,
    val showMaskOverlay: Boolean = true,
    val lightExposure: Float = .5f,
    val lightHighlights: Float = 0f,
    val lightShadows: Float = 0f,
    val lightTemperature: Float = 0f,
    val lightTint: Float = 0f,
    val radialWidth: Float = .35f,
    val radialHeight: Float = .25f,
    val rotation: Float = 0f,
    val feather: Float = .65f,
    val invert: Boolean = false,
)

data class EditorUiState(
    val preview: RasterImage? = null,
    val originalPreview: RasterImage? = null,
    val filterPreviews: Map<String, RasterImage> = emptyMap(),
    val edits: EditState = EditState(),
    val presets: List<FilterPreset> = emptyList(),
    val importedLuts: List<ImportedLut> = emptyList(),
    val sourceIdentity: Long = 0L,
    val showSharpenMask: Boolean = false,
    val retouch: RetouchUiState = RetouchUiState(),
    val localEditOverlay: List<LocalEditOperation> = emptyList(),
    val isBusy: Boolean = false,
    val isRendering: Boolean = false,
    val isExporting: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val shareUri: String? = null,
    val shareMimeType: String? = null,
    val message: String? = null,
) {
    val hasImage: Boolean get() = preview != null
}

class EditorViewModel(
    private val loadImage: LoadImage,
    private val renderEdits: RenderEdits,
    private val renderSharpenMask: RenderSharpenMask,
    private val exportImage: ExportImage,
    private val filterCatalog: FilterCatalog,
    private val lutStore: LutStore,
    private val memoryPressure: MemoryPressureRegistry,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState(presets = filterCatalog.presets))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var sourceRef: ImageSource? = null
    private var sourceRaster: RasterImage? = null
    private var interactiveSource: RasterImage? = null
    private var session = EditSession()
    private var renderJob: Job? = null
    private var exportJob: Job? = null
    private var previewResizeJob: Job? = null
    private val thumbnailJobs = ConcurrentHashMap<String, Job>()
    private var thumbnailSource: RasterImage? = null
    private var sourceIdentity = 0L
    private var renderGeneration = 0L
    private var nextLocalEditId = 1L
    private var reportedDiscardedHistory = 0
    private var viewportPreviewDimension = PreviewSizing.MIN_PREVIEW_DIMENSION
    private val previewDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val thumbnailDispatcher = Dispatchers.Default.limitedParallelism(2)
    private val previewCache = RasterMemoryCache<EditState>(PREVIEW_CACHE_BYTES, 3)
    private val thumbnailCache = RasterMemoryCache<String>(THUMBNAIL_CACHE_BYTES, 50)

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(importedLuts = lutStore.list()) }
        }
        viewModelScope.launch {
            memoryPressure.pressure.collect { level ->
                if (level >= MEMORY_RUNNING_LOW) {
                    thumbnailJobs.values.forEach(Job::cancel)
                    thumbnailJobs.clear()
                    previewCache.clear()
                    thumbnailCache.clear()
                    _uiState.update {
                        it.copy(
                            filterPreviews = emptyMap(),
                            message = if (level >= MEMORY_RUNNING_CRITICAL) {
                                "Preview caches released to protect memory"
                            } else it.message,
                        )
                    }
                }
            }
        }
    }

    fun importImage(uri: String, previewMaxDimension: Int = PreviewSizing.MIN_PREVIEW_DIMENSION) {
        renderJob?.cancel()
        thumbnailJobs.values.forEach(Job::cancel)
        thumbnailJobs.clear()
        previewResizeJob?.cancel()
        viewportPreviewDimension = previewMaxDimension
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            runCatching {
                val source = ImageSource(uri)
                val raster = loadImage(source, previewMaxDimension)
                sourceRef = source
                sourceRaster = raster
                interactiveSource = raster.scaledToMaxDimension(PreviewSizing.INTERACTIVE_MAX_DIMENSION)
                session = EditSession()
                nextLocalEditId = 1L
                reportedDiscardedHistory = 0
                previewCache.clear()
                thumbnailCache.clear()
                thumbnailSource = raster.centerCropScaled(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                sourceIdentity++
                raster
            }.onSuccess { raster ->
                _uiState.value = EditorUiState(
                    preview = raster,
                    originalPreview = raster,
                    presets = filterCatalog.presets,
                    importedLuts = _uiState.value.importedLuts,
                    sourceIdentity = sourceIdentity,
                )
            }.onFailure { error ->
                if (error !is CancellationException) {
                    _uiState.update { it.copy(isBusy = false, message = error.userMessage("Could not open image")) }
                }
            }
        }
    }

    fun clearImage() {
        renderJob?.cancel()
        thumbnailJobs.values.forEach(Job::cancel)
        thumbnailJobs.clear()
        previewResizeJob?.cancel()
        sourceRef = null
        sourceRaster = null
        interactiveSource = null
        thumbnailSource = null
        session = EditSession()
        nextLocalEditId = 1L
        reportedDiscardedHistory = 0
        previewCache.clear()
        thumbnailCache.clear()
        _uiState.value = EditorUiState(presets = filterCatalog.presets, importedLuts = _uiState.value.importedLuts)
    }

    fun updatePreviewViewport(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val source = sourceRaster ?: return
        val target = PreviewSizing.maxDimensionForViewport(widthPx, heightPx)
        viewportPreviewDimension = target
        if (maxOf(source.width, source.height) > target * 1.2f) {
            sourceRaster = source.scaledToMaxDimension(target)
            interactiveSource = sourceRaster?.scaledToMaxDimension(PreviewSizing.INTERACTIVE_MAX_DIMENSION)
            previewCache.clear()
            scheduleRender(RenderQuality.SETTLED)
        }
    }

    fun requestZoomDetail(zoom: Float) {
        val reference = sourceRef ?: return
        val current = sourceRaster ?: return
        val target = (viewportPreviewDimension * sqrt(zoom.coerceIn(1f, 2.5f)))
            .roundToInt().coerceIn(viewportPreviewDimension, ZOOM_PREVIEW_MAX_DIMENSION)
        val currentLargest = maxOf(current.width, current.height)
        if (target <= currentLargest * 1.2f && target >= currentLargest * .8f) return
        previewResizeJob?.cancel()
        val identity = sourceIdentity
        previewResizeJob = viewModelScope.launch {
            delay(ZOOM_RELOAD_SETTLE_MS)
            val resized = if (target < currentLargest) current.scaledToMaxDimension(target) else loadImage(reference, target)
            if (identity != sourceIdentity || sourceRef != reference) return@launch
            sourceRaster = resized
            interactiveSource = resized.scaledToMaxDimension(PreviewSizing.INTERACTIVE_MAX_DIMENSION)
            previewCache.clear()
            scheduleRender(RenderQuality.SETTLED)
        }
    }

    fun updateAdjustments(transform: (Adjustments) -> Adjustments) {
        session.updateDraft { it.copy(adjustments = transform(it.adjustments)) }
        publishEdits(RenderQuality.INTERACTIVE)
    }

    fun commitAdjustments() {
        session.commitDraft()
        reportBoundedHistoryIfNeeded()
        publishEdits(RenderQuality.SETTLED)
    }

    fun resetAdjustments(transform: (Adjustments) -> Adjustments) {
        commit(session.draft.copy(adjustments = transform(session.draft.adjustments)))
    }

    fun commitAdjustments(transform: (Adjustments) -> Adjustments) {
        commit(session.draft.copy(adjustments = transform(session.draft.adjustments)))
    }

    fun setSharpenMaskPreview(show: Boolean) {
        _uiState.update { it.copy(showSharpenMask = show) }
        scheduleRender(RenderQuality.SETTLED)
    }

    fun rotateClockwise() = commit(session.draft.rotatedClockwise())

    fun setCrop(aspect: CropAspect) = commit(session.draft.copy(cropAspect = aspect))
    fun resetGeometry() = commit(session.draft.copy(cropAspect = CropAspect.ORIGINAL, quarterTurnsClockwise = 0))

    fun applyPreset(id: String) {
        val preset = filterCatalog.find(id) ?: return
        commit(session.draft.copy(filter = FilterSelection(preset.id, preset.defaultIntensity, preset.adjustments)))
    }

    fun updateFilterIntensity(value: Float) {
        session.updateDraft { edits -> edits.copy(filter = edits.filter.copy(intensity = value.coerceIn(0f, 1f))) }
        publishEdits(RenderQuality.INTERACTIVE)
    }

    fun commitFilterIntensity() {
        session.commitDraft()
        reportBoundedHistoryIfNeeded()
        publishEdits(RenderQuality.SETTLED)
    }

    fun requestFilterThumbnail(id: String) {
        thumbnailCache[id]?.let { cached ->
            _uiState.update { it.copy(filterPreviews = it.filterPreviews + (id to cached)) }
            return
        }
        if (thumbnailJobs[id]?.isActive == true) return
        val preset = filterCatalog.find(id) ?: return
        val source = thumbnailSource ?: return
        val identity = sourceIdentity
        thumbnailJobs[id] = viewModelScope.launch(thumbnailDispatcher) {
            val edit = EditState(filter = FilterSelection(preset.id, preset.defaultIntensity, preset.adjustments))
            val rendered = renderEdits(source, edit)
            if (identity == sourceIdentity) {
                thumbnailCache.put(id, rendered)
                _uiState.update { it.copy(filterPreviews = thumbnailCache.snapshot()) }
            }
            thumbnailJobs.remove(id)
        }
    }

    fun importLut(uri: String) {
        viewModelScope.launch {
            runCatching { lutStore.import(uri.toUri(), null) }
                .onSuccess { imported ->
                    _uiState.update { it.copy(importedLuts = it.importedLuts + imported, message = "LUT imported") }
                }
                .onFailure { error -> _uiState.update { it.copy(message = error.userMessage("Invalid LUT file")) } }
        }
    }

    fun selectLut(lut: ImportedLut) = commit(
        session.draft.copy(lut = LutSelection(lut.id, lut.name, lut.path, 1f)),
    )

    fun clearLut() = commit(session.draft.copy(lut = null))
    fun resetFilterAndLut() = commit(session.draft.copy(filter = FilterSelection(), lut = null))

    fun updateLutIntensity(value: Float) {
        session.updateDraft { edits -> edits.copy(lut = edits.lut?.copy(intensity = value.coerceIn(0f, 1f))) }
        publishEdits(RenderQuality.INTERACTIVE)
    }

    fun commitLutIntensity() {
        session.commitDraft()
        reportBoundedHistoryIfNeeded()
        publishEdits(RenderQuality.SETTLED)
    }

    fun deleteLut(lut: ImportedLut) {
        viewModelScope.launch {
            val wasSelected = session.draft.lut?.id == lut.id
            if (lutStore.delete(lut)) {
                _uiState.update { it.copy(importedLuts = it.importedLuts.filterNot { item -> item.id == lut.id }, message = "LUT deleted") }
                if (wasSelected) clearLut()
            }
        }
    }

    fun resetAll() = commit(EditState())

    fun setRetouchTool(tool: RetouchTool) = _uiState.update {
        it.copy(retouch = it.retouch.copy(tool = tool, selectingCloneSource = false))
    }

    fun updateBrushSettings(transform: (BrushSettings) -> BrushSettings) = _uiState.update {
        it.copy(retouch = it.retouch.copy(brush = transform(it.retouch.brush)))
    }

    fun updateRetouch(transform: (RetouchUiState) -> RetouchUiState) = _uiState.update {
        it.copy(retouch = transform(it.retouch))
    }

    fun beginCloneSourceSelection() = _uiState.update {
        val sourceTool = if (it.retouch.tool == RetouchTool.HEAL) RetouchTool.HEAL else RetouchTool.CLONE
        it.copy(retouch = it.retouch.copy(tool = sourceTool, selectingCloneSource = true))
    }

    fun clearRetouchSource() = _uiState.update {
        it.copy(retouch = it.retouch.copy(
            cloneSource = null,
            cloneSourceVisible = null,
            cloneOffsetX = null,
            cloneOffsetY = null,
            selectingCloneSource = false,
        ))
    }

    fun setCloneSource(visiblePoint: NormalizedPoint) {
        val mapper = sourceCoordinateMapper() ?: return
        val sourcePoint = mapper.visibleToSource(visiblePoint)
        _uiState.update {
            it.copy(retouch = it.retouch.copy(
                tool = if (it.retouch.tool == RetouchTool.HEAL) RetouchTool.HEAL else RetouchTool.CLONE,
                cloneSource = sourcePoint,
                cloneSourceVisible = visiblePoint,
                cloneOffsetX = null,
                cloneOffsetY = null,
                selectingCloneSource = false,
            ))
        }
    }

    fun commitBrushStroke(visiblePoints: List<NormalizedPoint>) {
        if (visiblePoints.isEmpty()) return
        if (session.draft.localEdits.size >= EditState.MAX_LOCAL_OPERATIONS) {
            _uiState.update { it.copy(message = "Retouch limit reached; undo or reset local edits to continue") }
            return
        }
        val mapper = sourceCoordinateMapper() ?: return
        val retouch = _uiState.value.retouch
        if (retouch.selectingCloneSource) {
            setCloneSource(visiblePoints.first())
            return
        }
        val type = when (retouch.tool) {
            RetouchTool.HEAL -> BrushEditType.HEAL
            RetouchTool.CLONE -> BrushEditType.CLONE
            RetouchTool.SMOOTH -> BrushEditType.SMOOTH
            RetouchTool.DODGE -> BrushEditType.DODGE
            RetouchTool.BURN -> BrushEditType.BURN
            RetouchTool.BRUSH_LIGHT -> BrushEditType.BRUSH_LIGHT
            RetouchTool.ERASER -> BrushEditType.ERASER
            else -> return
        }
        if (type == BrushEditType.CLONE && retouch.cloneSource == null) {
            _uiState.update { it.copy(message = "Select a clone source first") }
            return
        }
        val points = visiblePoints.decimated(BrushStroke.MAX_POINTS_PER_STROKE).map(mapper::visibleToSource)
        val settings = mapper.visibleBrushToSource(
            retouch.brush.copy(strength = when (type) {
                BrushEditType.DODGE -> abs(retouch.brush.strength)
                BrushEditType.BURN -> -abs(retouch.brush.strength)
                BrushEditType.BRUSH_LIGHT -> retouch.lightExposure.coerceIn(-1f, 1f)
                else -> retouch.brush.strength
            }),
        )
        val cloneAnchor = if (type == BrushEditType.CLONE) {
            val selectedSource = requireNotNull(retouch.cloneSource)
            if (retouch.alignedSampling) {
                val destination = points.first()
                val offsetX = retouch.cloneOffsetX ?: (selectedSource.x - destination.x)
                val offsetY = retouch.cloneOffsetY ?: (selectedSource.y - destination.y)
                _uiState.update { it.copy(retouch = it.retouch.copy(cloneOffsetX = offsetX, cloneOffsetY = offsetY)) }
                NormalizedPoint((destination.x + offsetX).coerceIn(0f, 1f), (destination.y + offsetY).coerceIn(0f, 1f))
            } else selectedSource
        } else null
        val operation = BrushStroke(
            id = nextLocalEditId++,
            type = type,
            points = points,
            settings = settings,
            sourceAnchor = if (type == BrushEditType.HEAL) retouch.cloneSource else cloneAnchor,
            alignedSampling = retouch.alignedSampling,
            lighting = when (type) {
                BrushEditType.DODGE -> LocalLighting(exposure = abs(retouch.brush.strength).coerceAtLeast(.05f))
                BrushEditType.BURN -> LocalLighting(exposure = -abs(retouch.brush.strength).coerceAtLeast(.05f))
                BrushEditType.BRUSH_LIGHT -> LocalLighting(
                    exposure = retouch.lightExposure,
                    highlights = retouch.lightHighlights,
                    shadows = retouch.lightShadows,
                    temperature = retouch.lightTemperature,
                    tint = retouch.lightTint,
                )
                else -> LocalLighting()
            },
        )
        commit(session.draft.copy(localEdits = session.draft.localEdits + operation))
    }

    fun commitLightAt(visibleCenter: NormalizedPoint) {
        if (session.draft.localEdits.size >= EditState.MAX_LOCAL_OPERATIONS) {
            _uiState.update { it.copy(message = "Retouch limit reached; undo or reset local edits to continue") }
            return
        }
        val mapper = sourceCoordinateMapper() ?: return
        val retouch = _uiState.value.retouch
        val operation: LocalEditOperation = when (retouch.tool) {
            RetouchTool.RADIAL -> RadialLight(
                id = nextLocalEditId++,
                center = mapper.visibleToSource(visibleCenter),
                width = mapper.visibleWidthToSource(retouch.radialWidth),
                height = mapper.visibleHeightToSource(retouch.radialHeight),
                rotationDegrees = mapper.visibleRotationToSource(retouch.rotation),
                feather = retouch.feather,
                inverted = retouch.invert,
                exposure = retouch.lightExposure,
                highlights = retouch.lightHighlights,
                shadows = retouch.lightShadows,
            )
            RetouchTool.LINEAR -> LinearLight(
                id = nextLocalEditId++,
                center = mapper.visibleToSource(visibleCenter),
                rotationDegrees = mapper.visibleRotationToSource(retouch.rotation),
                feather = retouch.feather.coerceAtLeast(.01f),
                inverted = retouch.invert,
                exposure = retouch.lightExposure,
                temperature = retouch.lightTemperature,
                tint = retouch.lightTint,
            )
            else -> return
        }
        commit(session.draft.copy(localEdits = session.draft.localEdits + operation))
    }

    fun resetLocalEdits() = commit(session.draft.copy(localEdits = emptyList()))

    fun undo() {
        session.undo()
        publishEdits(RenderQuality.SETTLED)
    }

    fun redo() {
        session.redo()
        publishEdits(RenderQuality.SETTLED)
    }

    fun export(destination: String, options: ExportOptions, shareAfterExport: Boolean = false) {
        val source = sourceRef ?: return
        val edits = session.draft
        renderGeneration++
        renderJob?.cancel()
        exportJob?.cancel()
        previewResizeJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isExporting = true, isRendering = false, message = null) }
            try {
                exportImage(source, edits, destination, options)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        isExporting = false,
                        message = "${options.format.name} exported",
                        shareUri = destination.takeIf { shareAfterExport },
                        shareMimeType = options.format.mimeType.takeIf { shareAfterExport },
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(isBusy = false, isExporting = false, message = "Export cancelled") }
            } catch (error: Throwable) {
                _uiState.update { it.copy(isBusy = false, isExporting = false, message = error.userMessage("Export failed")) }
            } finally {
                if (sourceRef != null) scheduleRender(RenderQuality.SETTLED)
            }
        }
    }

    fun cancelExport() { exportJob?.cancel() }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun consumeShareRequest() = _uiState.update { it.copy(shareUri = null, shareMimeType = null) }

    private fun commit(edits: EditState) {
        session.commit(edits)
        reportBoundedHistoryIfNeeded()
        publishEdits(RenderQuality.SETTLED)
    }

    private fun publishEdits(quality: RenderQuality = RenderQuality.SETTLED) {
        _uiState.update {
            val refreshedRetouch = refreshRetouchCoordinates(it.retouch)
            it.copy(
                edits = session.draft,
                canUndo = session.canUndo,
                canRedo = session.canRedo,
                retouch = refreshedRetouch,
                localEditOverlay = visibleLocalEdits(),
            )
        }
        scheduleRender(quality)
    }

    private fun scheduleRender(quality: RenderQuality = RenderQuality.SETTLED) {
        if (_uiState.value.isExporting) return
        val source = when (quality) {
            RenderQuality.INTERACTIVE -> interactiveSource ?: sourceRaster
            RenderQuality.SETTLED -> sourceRaster
        } ?: return
        val edits = session.draft
        val showMask = _uiState.value.showSharpenMask
        val cacheEligible = quality == RenderQuality.SETTLED && edits.localEdits.isEmpty()
        val generation = ++renderGeneration
        renderJob?.cancel()
        if (cacheEligible && !showMask) previewCache[edits]?.let { edited ->
            val geometry = edits.geometryOnly()
            val before = previewCache[geometry] ?: source.takeIf { geometry.isOriginal }
            if (before != null) {
                _uiState.update { it.copy(preview = edited, originalPreview = before, isRendering = false) }
                return
            }
        }
        renderJob = viewModelScope.launch {
            _uiState.update { it.copy(isRendering = true) }
            try {
                val (edited, before) = withContext(previewDispatcher) {
                    val startedNanos = PerformanceDiagnostics.nowNanos()
                    val renderContext = coroutineContext
                    val shouldCancel = { generation != renderGeneration || !renderContext.isActive }
                    val editedResult = if (showMask) renderSharpenMask(source, edits, shouldCancel)
                    else if (cacheEligible) {
                        previewCache[edits] ?: renderEdits(source, edits, shouldCancel).also { previewCache.put(edits, it) }
                    } else renderEdits(source, edits, shouldCancel)
                    coroutineContext.ensureActive()
                    val geometry = edits.geometryOnly()
                    val beforeResult = if (quality == RenderQuality.SETTLED) {
                        previewCache[geometry]
                            ?: if (geometry.isOriginal) source else renderEdits(source, geometry, shouldCancel).also { previewCache.put(geometry, it) }
                    } else if (geometry.isOriginal) source else renderEdits(source, geometry, shouldCancel)
                    PerformanceDiagnostics.logRender(
                        label = quality.name.lowercase(),
                        source = source,
                        result = editedResult,
                        startedNanos = startedNanos,
                        previewCacheBytes = previewCache.sizeBytes,
                        thumbnailCacheBytes = thumbnailCache.sizeBytes,
                        retainedRasterBuffers = previewCache.count() + thumbnailCache.count() + 2,
                        activeJobs = thumbnailJobs.size + 1,
                    )
                    editedResult to beforeResult
                }
                if (generation == renderGeneration) {
                    _uiState.update { it.copy(preview = edited, originalPreview = before, isRendering = false) }
                }
            } catch (cancelled: CancellationException) {
                if (generation == renderGeneration) _uiState.update { it.copy(isRendering = false) }
                throw cancelled
            } catch (error: Throwable) {
                if (generation == renderGeneration) {
                    _uiState.update { it.copy(isRendering = false, message = error.userMessage("Preview failed")) }
                }
            }
        }
    }

    override fun onCleared() {
        renderJob?.cancel()
        exportJob?.cancel()
        thumbnailJobs.values.forEach(Job::cancel)
        thumbnailJobs.clear()
        previewCache.clear()
        thumbnailCache.clear()
        super.onCleared()
    }

    private fun RasterImage.centerCropScaled(targetWidth: Int, targetHeight: Int): RasterImage {
        val crop = CropCalculator.centeredToAspect(width, height, targetWidth, targetHeight)
        val cropWidth = crop.width
        val cropHeight = crop.height
        val left = crop.left
        val top = crop.top
        val pixels = IntArray(targetWidth * targetHeight)
        for (y in 0 until targetHeight) {
            val sourceY = (top + y.toLong() * cropHeight / targetHeight).toInt().coerceAtMost(height - 1)
            for (x in 0 until targetWidth) {
                val sourceX = (left + x.toLong() * cropWidth / targetWidth).toInt().coerceAtMost(width - 1)
                pixels[y * targetWidth + x] = argb[sourceY * width + sourceX]
            }
        }
        val additionalScale = minOf(targetWidth.toFloat() / cropWidth, targetHeight.toFloat() / cropHeight)
        return RasterImage(targetWidth, targetHeight, pixels, scaleFromOriginal * additionalScale)
    }

    private fun RasterImage.scaledToMaxDimension(maxDimension: Int): RasterImage {
        val largest = maxOf(width, height)
        if (largest <= maxDimension) return this
        val scale = maxDimension.toFloat() / largest
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val pixels = IntArray(targetWidth * targetHeight)
        for (y in 0 until targetHeight) {
            val sourceY = (y.toLong() * height / targetHeight).toInt().coerceAtMost(height - 1)
            for (x in 0 until targetWidth) {
                val sourceX = (x.toLong() * width / targetWidth).toInt().coerceAtMost(width - 1)
                pixels[y * targetWidth + x] = argb[sourceY * width + sourceX]
            }
        }
        return RasterImage(targetWidth, targetHeight, pixels, scaleFromOriginal * scale)
    }

    private fun sourceCoordinateMapper(): SourceCoordinateMapper? {
        val source = sourceRaster ?: return null
        return SourceCoordinateMapper(source.width, source.height, session.draft.quarterTurnsClockwise, session.draft.cropAspect)
    }

    private fun refreshRetouchCoordinates(retouch: RetouchUiState): RetouchUiState {
        val mapper = sourceCoordinateMapper() ?: return retouch
        return retouch.copy(cloneSourceVisible = retouch.cloneSource?.let(mapper::sourceToVisible))
    }

    private fun visibleLocalEdits(): List<LocalEditOperation> {
        val mapper = sourceCoordinateMapper() ?: return emptyList()
        return session.draft.localEdits.map { operation ->
            when (operation) {
                is BrushStroke -> operation.copy(
                    points = operation.points.map(mapper::sourceToVisible),
                    settings = mapper.sourceBrushToVisible(operation.settings),
                    sourceAnchor = operation.sourceAnchor?.let(mapper::sourceToVisible),
                )
                is RadialLight -> operation.copy(
                    center = mapper.sourceToVisible(operation.center),
                    width = mapper.sourceWidthToVisible(operation.width),
                    height = mapper.sourceHeightToVisible(operation.height),
                    rotationDegrees = mapper.sourceRotationToVisible(operation.rotationDegrees),
                )
                is LinearLight -> operation.copy(
                    center = mapper.sourceToVisible(operation.center),
                    rotationDegrees = mapper.sourceRotationToVisible(operation.rotationDegrees),
                )
            }
        }
    }

    private fun List<NormalizedPoint>.decimated(maxPoints: Int): List<NormalizedPoint> {
        if (size <= maxPoints) return this
        val step = (size - 1f) / (maxPoints - 1)
        return List(maxPoints) { index -> this[(index * step).roundToInt().coerceAtMost(lastIndex)] }
    }

    private fun Throwable.userMessage(fallback: String) = message?.takeIf { it.isNotBlank() } ?: fallback

    private fun reportBoundedHistoryIfNeeded() {
        if (session.discardedUndoCount > reportedDiscardedHistory) {
            reportedDiscardedHistory = session.discardedUndoCount
            _uiState.update { it.copy(message = "Undo history is limited to the 100 most recent edits") }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(
            container.loadImage,
            container.renderEdits,
            container.renderSharpenMask,
            container.exportImage,
            container.filterCatalog,
            container.lutStore,
            container.memoryPressure,
        ) as T
    }

    private companion object {
        const val THUMBNAIL_WIDTH = 160
        const val THUMBNAIL_HEIGHT = 192
        const val PREVIEW_CACHE_BYTES = 14L * 1024L * 1024L
        const val THUMBNAIL_CACHE_BYTES = 8L * 1024L * 1024L
        const val ZOOM_PREVIEW_MAX_DIMENSION = 1580
        const val ZOOM_RELOAD_SETTLE_MS = 220L
        const val MEMORY_RUNNING_LOW = 10
        const val MEMORY_RUNNING_CRITICAL = 15
    }

    private enum class RenderQuality { INTERACTIVE, SETTLED }
}
