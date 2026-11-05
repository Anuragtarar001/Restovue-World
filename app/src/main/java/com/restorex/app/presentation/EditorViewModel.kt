package com.restorex.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restorex.app.AppContainer
import com.restorex.app.domain.engine.FilterCatalog
import com.restorex.app.domain.engine.CropCalculator
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
import com.restorex.app.domain.usecase.ExportImage
import com.restorex.app.domain.usecase.LoadImage
import com.restorex.app.domain.usecase.RenderEdits
import com.restorex.app.domain.usecase.RenderSharpenMask
import com.restorex.app.data.LutStore
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class EditorUiState(
    val preview: RasterImage? = null,
    val originalPreview: RasterImage? = null,
    val filterPreviews: Map<String, RasterImage> = emptyMap(),
    val edits: EditState = EditState(),
    val presets: List<FilterPreset> = emptyList(),
    val importedLuts: List<ImportedLut> = emptyList(),
    val sourceIdentity: Long = 0L,
    val showSharpenMask: Boolean = false,
    val isBusy: Boolean = false,
    val isRendering: Boolean = false,
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState(presets = filterCatalog.presets))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var sourceRef: ImageSource? = null
    private var sourceRaster: RasterImage? = null
    private var session = EditSession()
    private var renderJob: Job? = null
    private val thumbnailJobs = ConcurrentHashMap<String, Job>()
    private var thumbnailSource: RasterImage? = null
    private var sourceIdentity = 0L
    private var renderGeneration = 0L
    private val previewDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val thumbnailDispatcher = Dispatchers.Default.limitedParallelism(2)
    private val previewCache: MutableMap<EditState, RasterImage> = Collections.synchronizedMap(
        object : LinkedHashMap<EditState, RasterImage>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<EditState, RasterImage>?) = size > 6
        },
    )

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(importedLuts = lutStore.list()) }
        }
    }

    fun importImage(uri: String) {
        renderJob?.cancel()
        thumbnailJobs.values.forEach(Job::cancel)
        thumbnailJobs.clear()
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            runCatching {
                val source = ImageSource(uri)
                val raster = loadImage(source)
                sourceRef = source
                sourceRaster = raster
                session = EditSession()
                previewCache.clear()
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
        sourceRef = null
        sourceRaster = null
        thumbnailSource = null
        session = EditSession()
        previewCache.clear()
        _uiState.value = EditorUiState(presets = filterCatalog.presets, importedLuts = _uiState.value.importedLuts)
    }

    fun updateAdjustments(transform: (Adjustments) -> Adjustments) {
        session.updateDraft { it.copy(adjustments = transform(it.adjustments)) }
        publishEdits()
    }

    fun commitAdjustments() {
        session.commitDraft()
        publishHistoryFlags()
    }

    fun resetAdjustments(transform: (Adjustments) -> Adjustments) {
        commit(session.draft.copy(adjustments = transform(session.draft.adjustments)))
    }

    fun commitAdjustments(transform: (Adjustments) -> Adjustments) {
        commit(session.draft.copy(adjustments = transform(session.draft.adjustments)))
    }

    fun setSharpenMaskPreview(show: Boolean) {
        _uiState.update { it.copy(showSharpenMask = show) }
        scheduleRender(immediate = true)
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
        publishEdits()
    }

    fun commitFilterIntensity() {
        session.commitDraft()
        publishHistoryFlags()
    }

    fun requestFilterThumbnail(id: String) {
        if (_uiState.value.filterPreviews.containsKey(id) || thumbnailJobs[id]?.isActive == true) return
        val preset = filterCatalog.find(id) ?: return
        val source = thumbnailSource ?: return
        val identity = sourceIdentity
        thumbnailJobs[id] = viewModelScope.launch(thumbnailDispatcher) {
            val edit = EditState(filter = FilterSelection(preset.id, preset.defaultIntensity, preset.adjustments))
            val rendered = renderEdits(source, edit)
            if (identity == sourceIdentity) {
                _uiState.update { it.copy(filterPreviews = it.filterPreviews + (id to rendered)) }
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
        publishEdits()
    }

    fun commitLutIntensity() {
        session.commitDraft()
        publishHistoryFlags()
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

    fun undo() {
        session.undo()
        publishEdits(immediate = true)
    }

    fun redo() {
        session.redo()
        publishEdits(immediate = true)
    }

    fun export(destination: String, options: ExportOptions, shareAfterExport: Boolean = false) {
        val source = sourceRef ?: return
        val edits = session.draft
        val showMask = _uiState.value.showSharpenMask
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            runCatching { exportImage(source, edits, destination, options) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = "${options.format.name} exported",
                            shareUri = destination.takeIf { shareAfterExport },
                            shareMimeType = options.format.mimeType.takeIf { shareAfterExport },
                        )
                    }
                }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        _uiState.update { it.copy(isBusy = false, message = error.userMessage("Export failed")) }
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun consumeShareRequest() = _uiState.update { it.copy(shareUri = null, shareMimeType = null) }

    private fun commit(edits: EditState) {
        session.commit(edits)
        publishEdits(immediate = true)
    }

    private fun publishEdits(immediate: Boolean = false) {
        _uiState.update {
            it.copy(edits = session.draft, canUndo = session.canUndo, canRedo = session.canRedo)
        }
        scheduleRender(immediate)
    }

    private fun publishHistoryFlags() {
        _uiState.update { it.copy(canUndo = session.canUndo, canRedo = session.canRedo) }
    }

    private fun scheduleRender(immediate: Boolean = false) {
        val source = sourceRaster ?: return
        val edits = session.draft
        val showMask = _uiState.value.showSharpenMask
        val generation = ++renderGeneration
        renderJob?.cancel()
        if (!showMask) previewCache[edits]?.let { edited ->
            val geometry = edits.geometryOnly()
            val before = previewCache[geometry] ?: source.takeIf { geometry.isOriginal }
            if (before != null) {
                _uiState.update { it.copy(preview = edited, originalPreview = before, isRendering = false) }
                return
            }
        }
        renderJob = viewModelScope.launch {
            if (!immediate) delay(PREVIEW_DEBOUNCE_MS)
            _uiState.update { it.copy(isRendering = true) }
            try {
                val (edited, before) = withContext(previewDispatcher) {
                    val editedResult = if (showMask) renderSharpenMask(source, edits)
                    else previewCache[edits] ?: renderEdits(source, edits).also { previewCache[edits] = it }
                    coroutineContext.ensureActive()
                    val geometry = edits.geometryOnly()
                    val beforeResult = previewCache[geometry]
                        ?: if (geometry.isOriginal) source else renderEdits(source, geometry).also { previewCache[geometry] = it }
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

    private fun Throwable.userMessage(fallback: String) = message?.takeIf { it.isNotBlank() } ?: fallback

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(
            container.loadImage,
            container.renderEdits,
            container.renderSharpenMask,
            container.exportImage,
            container.filterCatalog,
            container.lutStore,
        ) as T
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 24L
        const val THUMBNAIL_WIDTH = 160
        const val THUMBNAIL_HEIGHT = 192
    }
}
