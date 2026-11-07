package com.restorex.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restorex.app.AppContainer
import com.restorex.app.data.SmartMagicExportManager
import com.restorex.app.domain.model.ImageSource
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicAnalysis
import com.restorex.app.domain.smartmagic.SmartMagicEngine
import com.restorex.app.domain.smartmagic.SmartMagicParameterEngine
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

enum class SmartMagicPhase { IDLE, PROCESSING, READY, SAVING, SAVED, ERROR }

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
    val exportElapsedMillis: Long? = null,
) {
    val canCompare get() = original != null && enhanced != null
    val isWorking get() = phase == SmartMagicPhase.PROCESSING || phase == SmartMagicPhase.SAVING
}

class SmartMagicViewModel(
    private val loadImage: LoadImage,
    private val engine: SmartMagicEngine,
    private val parameterEngine: SmartMagicParameterEngine,
    private val exportManager: SmartMagicExportManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SmartMagicUiState())
    val state: StateFlow<SmartMagicUiState> = _state.asStateFlow()
    private var processingJob: Job? = null
    private var exportJob: Job? = null
    private var generation = 0L

    fun process(source: String) {
        processingJob?.cancel(); exportJob?.cancel()
        val request = ++generation
        processingJob = viewModelScope.launch {
            val started = System.nanoTime()
            _state.value = SmartMagicUiState(phase = SmartMagicPhase.PROCESSING, stage = "Opening photo", source = source)
            try {
                val raster = loadImage(ImageSource(source), ANALYSIS_MAX_DIMENSION)
                if (request != generation) return@launch
                val previewSource = raster.scaledToMaxDimension(PREVIEW_MAX_DIMENSION)
                _state.update { it.copy(stage = "Analyzing light, color, and detail", original = previewSource) }
                val context = currentCoroutineContext()
                val analysis = withContext(Dispatchers.Default) { engine.analyze(raster) { !context.isActive || request != generation } }
                val parameters = parameterEngine.parametersFor(analysis)
                context.ensureActive()
                _state.update { it.copy(stage = "Applying natural enhancement", analysis = analysis, parameters = parameters) }
                val enhanced = withContext(Dispatchers.Default) {
                    engine.enhance(previewSource, parameters, shouldCancel = { !context.isActive || request != generation })
                }
                context.ensureActive()
                if (request == generation) _state.value = _state.value.copy(
                    phase = SmartMagicPhase.READY, enhanced = enhanced, stage = null,
                    previewElapsedMillis = (System.nanoTime() - started) / 1_000_000L,
                )
            } catch (cancelled: CancellationException) {
                if (request == generation) _state.update { it.copy(phase = SmartMagicPhase.IDLE, stage = null) }
            } catch (error: Throwable) {
                if (request == generation) _state.update {
                    it.copy(phase = SmartMagicPhase.ERROR, stage = null, message = error.message ?: "Smart Magic could not process this photo")
                }
            }
        }
    }

    fun save() {
        val source = _state.value.source ?: return
        val parameters = _state.value.parameters ?: return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _state.update { it.copy(phase = SmartMagicPhase.SAVING, stage = "Preparing full-resolution export", message = null) }
            try {
                val result = exportManager.export(source, parameters) { stage -> _state.update { it.copy(stage = stage) } }
                _state.update {
                    it.copy(phase = SmartMagicPhase.SAVED, stage = null, savedUri = result.uri,
                        savedMimeType = result.mimeType, message = "Saved ${result.displayName} to the gallery",
                        exportElapsedMillis = result.elapsedMillis)
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(phase = SmartMagicPhase.READY, stage = null, message = "Save cancelled") }
            } catch (error: Throwable) {
                _state.update { it.copy(phase = SmartMagicPhase.READY, stage = null, message = error.message ?: "Could not save the enhanced photo") }
            }
        }
    }

    fun cancel() { processingJob?.cancel(); exportJob?.cancel() }
    fun reset() { generation++; cancel(); _state.value = SmartMagicUiState() }
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
            container.loadImage, container.smartMagicPipeline, container.smartMagicParameterEngine,
            container.smartMagicExportManager,
        ) as T
    }

    private companion object {
        const val ANALYSIS_MAX_DIMENSION = 1280
        const val PREVIEW_MAX_DIMENSION = 1080
    }
}
