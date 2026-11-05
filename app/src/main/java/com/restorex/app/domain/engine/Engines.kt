package com.restorex.app.domain.engine

import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.FilterPreset
import com.restorex.app.domain.model.RasterImage

interface ImageProcessor {
    fun render(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean = { false }): RasterImage
    fun renderSharpenMask(source: RasterImage, edits: EditState, shouldCancel: () -> Boolean = { false }): RasterImage
}

interface FilterCatalog {
    val presets: List<FilterPreset>
    fun find(id: String): FilterPreset?
}

/** Boundary for a future bundled NCNN model. Implementations must run fully on-device. */
interface OfflineRestorationEngine {
    val isAvailable: Boolean
    suspend fun restore(source: RasterImage, model: RestorationModel): RasterImage
}

enum class RestorationModel { FACE, DENOISE, UPSCALE_2X }
