package com.restorex.app.domain.usecase

import com.restorex.app.domain.engine.ImageProcessor
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.ExportOptions
import com.restorex.app.domain.model.ImageSource
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.repository.ImageRepository

class LoadImage(private val repository: ImageRepository) {
    suspend operator fun invoke(source: ImageSource, maxDimension: Int = 2048) = repository.load(source, maxDimension)
}

class RenderEdits(private val processor: ImageProcessor) {
    operator fun invoke(source: RasterImage, edits: EditState) = processor.render(source, edits)
}

class RenderSharpenMask(private val processor: ImageProcessor) {
    operator fun invoke(source: RasterImage, edits: EditState) = processor.renderSharpenMask(source, edits)
}

class ExportImage(private val repository: ImageRepository) {
    suspend operator fun invoke(source: ImageSource, edits: EditState, destination: String, options: ExportOptions) =
        repository.export(source, edits, destination, options)
}
