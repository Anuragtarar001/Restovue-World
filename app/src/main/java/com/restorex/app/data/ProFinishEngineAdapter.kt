package com.restorex.app.data

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.FaceEvidence
import com.smartphotomagic.engine.CancelToken
import com.smartphotomagic.engine.AffineBilateralGrid
import com.smartphotomagic.engine.FaceBox
import com.smartphotomagic.engine.Mode
import com.smartphotomagic.engine.PixelSink
import com.smartphotomagic.engine.PixelSource
import com.smartphotomagic.engine.RenderPlan
import com.smartphotomagic.engine.SmartEngine
import java.util.concurrent.CancellationException

/** Bridges the proven Smart Photo Magic 0.3.5 photographic core into Restovue. */
class ProFinishEngineAdapter {
    fun planAll(
        source: RasterImage,
        faces: List<FaceEvidence>,
        shouldCancel: () -> Boolean,
    ): Map<EnhancementLevel, RenderPlan> {
        checkCancelled(shouldCancel)
        val token = CancelToken()
        val analysis = SmartEngine.analyze(source.asPixelSource(), faces.map { it.toFaceBox() }, token)
        checkCancelled(shouldCancel)
        return mapOf(
            EnhancementLevel.NATURAL to SmartEngine.plan(analysis, Mode.NATURAL),
            EnhancementLevel.ENHANCED to SmartEngine.plan(analysis, Mode.ENHANCED),
            EnhancementLevel.EXTREME to SmartEngine.plan(analysis, Mode.EXTREME),
        )
    }

    fun render(
        source: RasterImage,
        plan: RenderPlan,
        shouldCancel: () -> Boolean,
    ): RasterImage = render(source, plan, null, shouldCancel)

    fun render(
        source: RasterImage,
        plan: RenderPlan,
        finishGrid: AffineBilateralGrid?,
        shouldCancel: () -> Boolean,
    ): RasterImage {
        checkCancelled(shouldCancel)
        val output = IntArray(source.argb.size)
        val token = CancelToken()
        SmartEngine.render(plan, source.asPixelSource(), output.asPixelSink(source.width), token,
            progress = { if (shouldCancel()) token.cancel() }, finishGrid = finishGrid)
        checkCancelled(shouldCancel)
        return RasterImage(source.width, source.height, output, source.scaleFromOriginal)
    }

    fun fitFinishGrid(
        source: RasterImage,
        target: RasterImage,
        shouldCancel: () -> Boolean,
    ): AffineBilateralGrid {
        checkCancelled(shouldCancel)
        val token = CancelToken()
        return AffineBilateralGrid.fit(source.asPixelSource(), target.asPixelSource(), cancel = token).also {
            checkCancelled(shouldCancel)
        }
    }

    private fun RasterImage.asPixelSource() = object : PixelSource {
        override val width = this@asPixelSource.width
        override val height = this@asPixelSource.height
        override fun read(x: Int, y: Int, w: Int, h: Int, out: IntArray) {
            for (row in 0 until h) {
                System.arraycopy(argb, (y + row) * width + x, out, row * w, w)
            }
        }
    }

    private fun IntArray.asPixelSink(width: Int) = object : PixelSink {
        override fun write(x: Int, y: Int, w: Int, h: Int, data: IntArray) {
            for (row in 0 until h) {
                System.arraycopy(data, row * w, this@asPixelSink, (y + row) * width + x, w)
            }
        }
    }

    private fun FaceEvidence.toFaceBox() = FaceBox(
        left = bounds.left,
        top = bounds.top,
        right = bounds.right,
        bottom = bounds.bottom,
        score = detectorScore ?: heuristicQuality,
    )

    private fun checkCancelled(shouldCancel: () -> Boolean) {
        if (shouldCancel()) throw CancellationException("Pro finish processing cancelled")
    }
}
