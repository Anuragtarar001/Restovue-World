package com.restorex.app

import android.app.Application
import com.restorex.app.data.AndroidImageProcessor
import com.restorex.app.data.AndroidImageRepository
import com.restorex.app.data.DefaultFilterCatalog
import com.restorex.app.data.LutStore
import com.restorex.app.domain.usecase.ExportImage
import com.restorex.app.domain.usecase.LoadImage
import com.restorex.app.domain.usecase.RenderEdits
import com.restorex.app.domain.usecase.RenderSharpenMask

class RestoreXApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val lutStore = LutStore(application.contentResolver, application.filesDir)
    private val processor = AndroidImageProcessor(lutStore)
    private val repository = AndroidImageRepository(application.contentResolver, processor)

    val filterCatalog = DefaultFilterCatalog()
    val loadImage = LoadImage(repository)
    val renderEdits = RenderEdits(processor)
    val renderSharpenMask = RenderSharpenMask(processor)
    val exportImage = ExportImage(repository)
}
