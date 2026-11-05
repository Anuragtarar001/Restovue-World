package com.restorex.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.model.BrushEditType
import com.restorex.app.domain.model.BrushSettings
import com.restorex.app.domain.model.BrushStroke
import com.restorex.app.domain.model.NormalizedPoint
import com.restorex.app.domain.model.TextLayer
import com.restorex.app.domain.model.FrameSettings
import com.restorex.app.domain.model.FrameStyle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidImageProcessorTest {
    @Test
    fun localEditsMatchBetweenPreviewAndOwnedExportRendering() {
        val source = RasterImage(24, 18, IntArray(24 * 18) { 0xFF787878.toInt() })
        val original = source.argb.copyOf()
        val state = EditState(localEdits = listOf(
            BrushStroke(
                1,
                BrushEditType.DODGE,
                listOf(NormalizedPoint(.5f, .5f)),
                BrushSettings(radius = .15f, strength = .6f),
            ),
        ))
        val processor = AndroidImageProcessor()

        val preview = processor.render(source, state)
        val export = processor.renderForExport(source.copy(argb = source.argb.copyOf()), state) { false }

        assertArrayEquals(preview.argb, export.argb)
        assertArrayEquals(original, source.argb)
    }

    @Test
    fun rotateAndCropProducesExpectedDimensions() {
        val source = RasterImage(4, 2, IntArray(8) { 0xFF336699.toInt() })
        val result = AndroidImageProcessor().render(
            source,
            EditState(quarterTurnsClockwise = 1, cropAspect = CropAspect.SQUARE),
        )

        assertEquals(2, result.width)
        assertEquals(2, result.height)
    }

    @Test
    fun creativeLayersAndFramesMatchBetweenPreviewAndExport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = RasterImage(180, 120, IntArray(180 * 120) { 0xFF202020.toInt() })
        val state = EditState(
            creativeLayers = listOf(TextLayer(1, "Restovue ✓", size = .15f, outline = .4f)),
            frame = FrameSettings(FrameStyle.ROUNDED, .04f),
        )
        val processor = AndroidImageProcessor(creativePipeline = AndroidCreativePipeline(context.contentResolver))
        val preview = processor.render(source, state)
        val export = processor.renderForExport(source.copy(argb = source.argb.copyOf()), state) { false }
        assertArrayEquals(preview.argb, export.argb)
        org.junit.Assert.assertFalse(preview.argb.contentEquals(source.argb))
    }
}
