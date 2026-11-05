package com.restorex.app.domain.repository

import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.ExportOptions
import com.restorex.app.domain.model.ImageSource
import com.restorex.app.domain.model.RasterImage

interface ImageRepository {
    suspend fun load(source: ImageSource, maxDimension: Int): RasterImage
    suspend fun export(source: ImageSource, edits: EditState, destination: String, options: ExportOptions)
}
