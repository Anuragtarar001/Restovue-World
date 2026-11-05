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
import com.restorex.app.domain.model.BackgroundBrushMode
import com.restorex.app.domain.model.BackgroundFill
import com.restorex.app.domain.model.BackgroundFillMode
import com.restorex.app.domain.model.BackgroundMaskStroke
import com.restorex.app.domain.model.CreativeLayer
import com.restorex.app.domain.model.FrameSettings
import com.restorex.app.domain.model.ShapeLayer
import com.restorex.app.domain.model.ShapeType
import com.restorex.app.domain.model.TextLayer
import com.restorex.app.domain.model.StickerLayer
import com.restorex.app.domain.model.BrushSettings
import com.restorex.app.domain.model.BrushStroke
import com.restorex.app.domain.model.LinearLight
import com.restorex.app.domain.model.LocalLighting
import com.restorex.app.domain.model.LocalEditOperation
import com.restorex.app.domain.model.NormalizedPoint
import com.restorex.app.domain.model.RadialLight
import com.restorex.app.domain.model.NormalizedRect
import com.restorex.app.domain.model.PerspectiveQuad
import com.restorex.app.domain.model.LiquifyStroke
import com.restorex.app.domain.model.LiquifyType
import com.restorex.app.domain.usecase.ExportImage
import com.restorex.app.domain.usecase.LoadImage
import com.restorex.app.domain.usecase.RenderEdits
import com.restorex.app.domain.usecase.RenderSharpenMask
import com.restorex.app.data.LutStore
import com.restorex.app.data.PerformanceDiagnostics
import com.restorex.app.data.AndroidUtilityProcessor
import com.restorex.app.domain.model.UtilityMode
import com.restorex.app.domain.model.UtilityOptions
import com.restorex.app.domain.model.BatchOptions
import com.restorex.app.data.AndroidBatchProcessor
import com.restorex.app.domain.repository.PresetRepository
import com.restorex.app.domain.model.SavedPreset
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

data class LiquifyUiState(
    val type: LiquifyType = LiquifyType.PUSH,
    val radius: Float = .08f,
    val strength: Float = .35f,
    val feather: Float = .65f,
)

data class BackgroundUiState(
    val brushMode: BackgroundBrushMode = BackgroundBrushMode.REMOVE,
    val fillMode: BackgroundFillMode = BackgroundFillMode.TRANSPARENT,
    val radius: Float = .06f,
    val hardness: Float = .55f,
    val opacity: Float = 1f,
    val startColor: Int = 0xFFFFFFFF.toInt(),
    val endColor: Int = 0xFFB0B0B0.toInt(),
    val imageUri: String? = null,
    val imageScale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val blur: Float = 0f,
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
    val liquify: LiquifyUiState = LiquifyUiState(),
    val background: BackgroundUiState = BackgroundUiState(),
    val selectedCreativeLayerId: Long? = null,
    val savedPresets: List<SavedPreset> = emptyList(),
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
    private val utilityProcessor: AndroidUtilityProcessor,
    private val batchProcessor: AndroidBatchProcessor,
    private val presetRepository: PresetRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState(presets = filterCatalog.presets, savedPresets = presetRepository.load()))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var sourceRef: ImageSource? = null
    private var sourceRaster: RasterImage? = null
    private var interactiveSource: RasterImage? = null
    private var session = EditSession()
    private var renderJob: Job? = null
    private var exportJob: Job? = null
    private var utilityJob: Job? = null
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

    fun rotateCounterClockwise() = commit(session.draft.copy(quarterTurnsClockwise = (session.draft.quarterTurnsClockwise + 3) % 4))
    fun rotate180() = commit(session.draft.copy(quarterTurnsClockwise = (session.draft.quarterTurnsClockwise + 2) % 4))
    fun flipHorizontal() = commit(session.draft.copy(flipHorizontal = !session.draft.flipHorizontal))
    fun flipVertical() = commit(session.draft.copy(flipVertical = !session.draft.flipVertical))

    fun updateFineRotation(value: Float) {
        session.updateDraft { it.copy(fineRotationDegrees = value.coerceIn(-45f, 45f)) }
        publishEdits(RenderQuality.INTERACTIVE)
    }

    fun updateCropRect(value: NormalizedRect) {
        session.updateDraft { it.copy(cropRect = value) }
        _uiState.update { it.copy(edits = session.draft) }
    }

    fun updatePerspective(value: PerspectiveQuad) {
        session.updateDraft { it.copy(perspective = value) }
        publishEdits(RenderQuality.INTERACTIVE)
    }

    fun setCustomCropRatio(value: Float) {
        session.updateDraft { it.copy(cropAspect = CropAspect.CUSTOM, customCropRatio = value.coerceIn(.05f, 20f)) }
        publishEdits(RenderQuality.INTERACTIVE)
    }
    fun setCustomCropPixels(width: Int, height: Int) {
        if (width !in 1..20000 || height !in 1..20000) return
        session.updateDraft { it.copy(cropAspect = CropAspect.CUSTOM, customCropRatio = width.toFloat() / height, cropOutputWidth = width, cropOutputHeight = height) }
        _uiState.update { it.copy(edits = session.draft) }
    }

    fun commitGeometryDraft() {
        session.commitDraft()
        reportBoundedHistoryIfNeeded()
        publishEdits(RenderQuality.SETTLED)
    }

    fun cancelGeometryDraft() {
        session.discardDraft()
        publishEdits(RenderQuality.SETTLED)
    }

    fun setCrop(aspect: CropAspect) {
        session.updateDraft { it.copy(cropAspect = aspect) }
        _uiState.update { it.copy(edits = session.draft) }
    }
    fun resetGeometry() = commit(session.draft.copy(
        cropAspect = CropAspect.ORIGINAL,
        quarterTurnsClockwise = 0,
        cropRect = NormalizedRect(),
        customCropRatio = null,
        flipHorizontal = false,
        flipVertical = false,
        fineRotationDegrees = 0f,
        perspective = PerspectiveQuad(),
        cropOutputWidth = null,
        cropOutputHeight = null,
    ))

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

    fun updateLiquify(transform: (LiquifyUiState) -> LiquifyUiState) = _uiState.update {
        it.copy(liquify = transform(it.liquify))
    }

    fun commitLiquifyStroke(visiblePoints: List<NormalizedPoint>) {
        if (visiblePoints.isEmpty()) return
        if (session.draft.localEdits.size >= EditState.MAX_LOCAL_OPERATIONS) {
            _uiState.update { it.copy(message = "Local edit limit reached; undo or reset local edits to continue") }
            return
        }
        val mapper = sourceCoordinateMapper() ?: return
        val settings = _uiState.value.liquify
        val operation = LiquifyStroke(
            id = nextLocalEditId++,
            type = settings.type,
            points = visiblePoints.decimated(BrushStroke.MAX_POINTS_PER_STROKE).map(mapper::visibleToSource),
            radius = mapper.visibleWidthToSource(settings.radius).coerceIn(.005f, .5f),
            strength = settings.strength,
            feather = settings.feather,
        )
        commit(session.draft.copy(localEdits = session.draft.localEdits + operation))
    }

    fun updateBackground(transform: (BackgroundUiState) -> BackgroundUiState) = _uiState.update {
        it.copy(background = transform(it.background))
    }

    fun commitBackgroundStroke(visiblePoints: List<NormalizedPoint>) {
        if (visiblePoints.isEmpty() || session.draft.localEdits.size >= EditState.MAX_LOCAL_OPERATIONS) return
        val mapper = sourceCoordinateMapper() ?: return
        val settings = _uiState.value.background
        val operation = BackgroundMaskStroke(
            nextLocalEditId++, settings.brushMode,
            visiblePoints.decimated(BrushStroke.MAX_POINTS_PER_STROKE).map(mapper::visibleToSource),
            mapper.visibleWidthToSource(settings.radius).coerceIn(.002f, .5f), settings.hardness, settings.opacity,
        )
        commit(session.draft.copy(localEdits = session.draft.localEdits + operation))
    }

    fun commitBackgroundFill() {
        val settings = _uiState.value.background
        if (settings.fillMode == BackgroundFillMode.IMAGE && settings.imageUri == null) {
            _uiState.update { it.copy(message = "Choose a background image first") }
            return
        }
        val withoutPreviousFill = session.draft.localEdits.filterNot { it is BackgroundFill }
        commit(session.draft.copy(localEdits = withoutPreviousFill + BackgroundFill(
            nextLocalEditId++, settings.fillMode, settings.startColor, settings.endColor, settings.opacity,
            settings.imageUri, settings.imageScale, settings.offsetX, settings.offsetY, settings.blur,
        )))
    }

    fun setBackgroundImage(uri: String) {
        _uiState.update { it.copy(background = it.background.copy(fillMode = BackgroundFillMode.IMAGE, imageUri = uri)) }
        commitBackgroundFill()
    }

    fun addTextLayer(text: String) {
        if (text.isBlank() || session.draft.creativeLayers.size >= EditState.MAX_CREATIVE_LAYERS) return
        val layer = TextLayer(nextLocalEditId++, text.trim(), zIndex = session.draft.creativeLayers.size)
        commit(session.draft.copy(creativeLayers = session.draft.creativeLayers + layer))
        _uiState.update { it.copy(selectedCreativeLayerId = layer.id) }
    }

    fun addShapeLayer(type: ShapeType) {
        if (session.draft.creativeLayers.size >= EditState.MAX_CREATIVE_LAYERS) return
        val layer = ShapeLayer(nextLocalEditId++, type, zIndex = session.draft.creativeLayers.size)
        commit(session.draft.copy(creativeLayers = session.draft.creativeLayers + layer))
        _uiState.update { it.copy(selectedCreativeLayerId = layer.id) }
    }

    fun addStickerLayer(uri: String) {
        if (session.draft.creativeLayers.size >= EditState.MAX_CREATIVE_LAYERS) return
        val layer = StickerLayer(nextLocalEditId++, uri, zIndex = session.draft.creativeLayers.size)
        commit(session.draft.copy(creativeLayers = session.draft.creativeLayers + layer)); _uiState.update { it.copy(selectedCreativeLayerId = layer.id) }
    }

    fun selectCreativeLayer(id: Long?) = _uiState.update { it.copy(selectedCreativeLayerId = id) }

    fun updateCreativeLayer(transform: (CreativeLayer) -> CreativeLayer) {
        val id = _uiState.value.selectedCreativeLayerId ?: return
        session.updateDraft { edits -> edits.copy(creativeLayers = edits.creativeLayers.map { if (it.id == id) transform(it) else it }) }
        publishEdits(RenderQuality.INTERACTIVE)
    }

    fun commitCreativeLayer() {
        session.commitDraft(); reportBoundedHistoryIfNeeded(); publishEdits(RenderQuality.SETTLED)
    }

    fun deleteSelectedCreativeLayer() {
        val id = _uiState.value.selectedCreativeLayerId ?: return
        commit(session.draft.copy(creativeLayers = session.draft.creativeLayers.filterNot { it.id == id }))
        _uiState.update { it.copy(selectedCreativeLayerId = null) }
    }

    fun moveSelectedCreativeLayer(delta: Int) {
        val id = _uiState.value.selectedCreativeLayerId ?: return
        val layers = session.draft.creativeLayers.toMutableList(); val index = layers.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, layers.lastIndex)
        if (target == index) return
        val item = layers.removeAt(index); layers.add(target, item)
        commit(session.draft.copy(creativeLayers = layers.mapIndexed { z, layer -> when (layer) {
            is TextLayer -> layer.copy(zIndex = z); is ShapeLayer -> layer.copy(zIndex = z); is StickerLayer -> layer.copy(zIndex = z)
        } }))
    }

    fun updateFrame(value: FrameSettings, commitNow: Boolean = false) {
        session.updateDraft { it.copy(frame = value) }
        if (commitNow) { session.commitDraft(); reportBoundedHistoryIfNeeded(); publishEdits(RenderQuality.SETTLED) }
        else publishEdits(RenderQuality.INTERACTIVE)
    }

    fun resetCreative() = commit(session.draft.copy(creativeLayers = emptyList(), frame = FrameSettings())).also {
        _uiState.update { state -> state.copy(selectedCreativeLayerId = null) }
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

    fun runUtility(mode: UtilityMode, sources: List<String>, destination: String, options: UtilityOptions = UtilityOptions()) {
        utilityJob?.cancel()
        utilityJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = "${mode.label} processing started") }
            try {
                utilityProcessor.process(mode, sources, destination, options)
                _uiState.update { it.copy(isBusy = false, message = "${mode.label} saved successfully") }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(isBusy = false, message = "${mode.label} cancelled") }; throw cancelled
            } catch (error: Throwable) {
                _uiState.update { it.copy(isBusy = false, message = error.userMessage("${mode.label} failed")) }
            }
        }
    }

    fun cancelUtility() { utilityJob?.cancel() }
    fun showMessage(message: String) = _uiState.update { it.copy(message = message) }

    fun runBatch(sources: List<String>, destinationTree: String, options: BatchOptions) {
        utilityJob?.cancel()
        utilityJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val result = batchProcessor.process(sources, destinationTree, options, session.draft) { done, total ->
                    _uiState.update { it.copy(message = if (done == total) null else "Batch $done of $total") }
                }
                _uiState.update { it.copy(isBusy = false, message = "Batch saved ${result.completed} image(s)${if (result.failures.isEmpty()) "" else "; ${result.failures.size} failed"}") }
            } catch (cancelled: CancellationException) { _uiState.update { it.copy(isBusy = false, message = "Batch cancelled") }; throw cancelled }
            catch (error: Throwable) { _uiState.update { it.copy(isBusy = false, message = error.userMessage("Batch failed")) } }
        }
    }

    fun saveCurrentPreset(name: String) {
        if (name.isBlank()) return
        val current = _uiState.value.savedPresets
        val preset = SavedPreset(System.currentTimeMillis(), name.trim().take(60), session.draft.adjustments, session.draft.filter.presetId, session.draft.filter.intensity)
        val updated = (current + preset).takeLast(100); presetRepository.save(updated); _uiState.update { it.copy(savedPresets = updated, message = "Preset saved") }
    }

    fun applySavedPreset(id: Long) {
        val preset = _uiState.value.savedPresets.firstOrNull { it.id == id } ?: return
        val catalogPreset = filterCatalog.find(preset.filterPresetId)
        commit(session.draft.copy(adjustments = preset.adjustments, filter = if (catalogPreset != null) FilterSelection(catalogPreset.id, preset.filterIntensity, catalogPreset.adjustments) else FilterSelection()))
    }

    fun renameSavedPreset(id: Long, name: String) {
        if (name.isBlank()) return
        val updated = _uiState.value.savedPresets.map { if (it.id == id) it.copy(name = name.trim().take(60)) else it }
        presetRepository.save(updated); _uiState.update { it.copy(savedPresets = updated) }
    }

    fun deleteSavedPreset(id: Long) {
        val updated = _uiState.value.savedPresets.filterNot { it.id == id }; presetRepository.save(updated); _uiState.update { it.copy(savedPresets = updated) }
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
        utilityJob?.cancel()
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
        return SourceCoordinateMapper(
            source.width, source.height, session.draft.quarterTurnsClockwise, session.draft.cropAspect,
            session.draft.cropRect, session.draft.flipHorizontal, session.draft.flipVertical,
            session.draft.customCropRatio, session.draft.fineRotationDegrees, session.draft.perspective,
        )
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
                is LiquifyStroke -> operation.copy(
                    points = operation.points.map(mapper::sourceToVisible),
                    radius = mapper.sourceWidthToVisible(operation.radius),
                )
                is BackgroundMaskStroke -> operation.copy(
                    points = operation.points.map(mapper::sourceToVisible),
                    radius = mapper.sourceWidthToVisible(operation.radius),
                )
                is BackgroundFill -> operation
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
            container.utilityProcessor,
            container.batchProcessor,
            container.presetRepository,
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
