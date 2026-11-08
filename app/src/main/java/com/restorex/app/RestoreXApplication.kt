package com.restorex.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.restorex.app.data.AndroidImageProcessor
import com.restorex.app.data.AndroidImageRepository
import com.restorex.app.data.DefaultFilterCatalog
import com.restorex.app.data.LutStore
import com.restorex.app.data.PerformanceDiagnostics
import com.restorex.app.domain.usecase.ExportImage
import com.restorex.app.domain.usecase.LoadImage
import com.restorex.app.domain.usecase.RenderEdits
import com.restorex.app.domain.usecase.RenderSharpenMask
import com.restorex.app.presentation.MemoryPressureRegistry

class RestoreXApplication : Application() {
    private val containerDelegate = lazy { AppContainer(this) }
    val container by containerDelegate

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (containerDelegate.isInitialized()) container.memoryPressure.notify(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (containerDelegate.isInitialized()) container.memoryPressure.notify(80)
    }
}

class AppContainer(application: Application) {
    init {
        PerformanceDiagnostics.configure(application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
    }
    val memoryPressure = MemoryPressureRegistry()
    val smartMagicPipeline = com.restorex.app.data.SmartMagicPipeline()
    val smartMagicParameterEngine = com.restorex.app.domain.smartmagic.SmartMagicParameterEngine()
    val semanticAnalyzer = com.restorex.app.data.LiteRtSemanticAnalyzer(application)
    val smartMagicCoordinator = com.restorex.app.data.SmartMagicProcessingCoordinator(smartMagicPipeline, semanticAnalyzer)
    val smartMagicExportManager = com.restorex.app.data.SmartMagicExportManager(
        application.contentResolver, smartMagicPipeline, smartMagicCoordinator,
    )
    val lutStore = LutStore(application.contentResolver, application.filesDir)
    private val processor = AndroidImageProcessor(
        lutStore,
        com.restorex.app.data.AndroidBackgroundPipeline(application.contentResolver),
        com.restorex.app.data.AndroidCreativePipeline(application.contentResolver),
    )
    private val repository = AndroidImageRepository(application.contentResolver, processor)
    val utilityProcessor = com.restorex.app.data.AndroidUtilityProcessor(application.contentResolver)
    val batchProcessor = com.restorex.app.data.AndroidBatchProcessor(application.contentResolver, processor)
    val presetRepository = com.restorex.app.data.FilePresetRepository(application.filesDir)

    val filterCatalog = DefaultFilterCatalog()
    val loadImage = LoadImage(repository)
    val renderEdits = RenderEdits(processor)
    val renderSharpenMask = RenderSharpenMask(processor)
    val exportImage = ExportImage(repository)
}
