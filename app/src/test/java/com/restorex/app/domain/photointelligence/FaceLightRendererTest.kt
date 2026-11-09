package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceLightRendererTest {
    private val renderer = FaceLightRenderer()

    @Test fun `lift brightens selected dark face without changing distant face`() {
        val source = fixture()
        val selected = face("dark", .12f, .20f, .38f, .72f)
        val other = face("bright", .64f, .20f, .90f, .72f)
        val result = renderer.render(source, selected, FaceLightAdjustment(selected.id, FaceLightStyle.LIFT, .7f))

        val beforeSelected = renderer.meanFaceLuminance(source, selected)
        val afterSelected = renderer.meanFaceLuminance(result, selected)
        val beforeOther = renderer.meanFaceLuminance(source, other)
        val afterOther = renderer.meanFaceLuminance(result, other)
        assertTrue("selected face did not brighten", afterSelected > beforeSelected + .018f)
        assertTrue("distant face changed", kotlin.math.abs(afterOther - beforeOther) < .002f)
    }

    @Test fun `face light is deterministic and protects highlights`() {
        val source = fixture()
        val selected = face("dark", .12f, .20f, .38f, .72f)
        val adjustment = FaceLightAdjustment(selected.id, FaceLightStyle.DRAMATIC, 1f)
        val first = renderer.render(source, selected, adjustment)
        val second = renderer.render(source, selected, adjustment)
        assertArrayEquals(first.argb, second.argb)
        val clipped = first.argb.count { color ->
            (color ushr 16 and 255) == 255 || (color ushr 8 and 255) == 255 || (color and 255) == 255
        }
        assertTrue("face light introduced excessive clipping", clipped < first.argb.size / 100)
    }

    @Test fun `multiple directed lights are retained and rendered together`() {
        val source = fixture()
        val left = face("left", .12f, .20f, .38f, .72f)
        val right = face("right", .64f, .20f, .90f, .72f)
        val result = renderer.renderAll(source, listOf(left, right), listOf(
            FaceLightAdjustment(left.id, FaceLightStyle.SOFT, .7f, sourceX = -1.25f, beamWidth = .8f),
            FaceLightAdjustment(right.id, FaceLightStyle.DRAMATIC, .7f, sourceY = -1.25f, beamWidth = .4f),
        ))
        assertTrue(renderer.meanFaceLuminance(result, left) > renderer.meanFaceLuminance(source, left))
        assertTrue(renderer.meanFaceLuminance(result, right) > renderer.meanFaceLuminance(source, right))
    }

    @Test fun `narrow and wide beams produce measurably different masks`() {
        val source = fixture()
        val selected = face("dark", .12f, .20f, .38f, .72f)
        val narrow = renderer.render(source, selected, FaceLightAdjustment(selected.id, FaceLightStyle.LIFT, .8f, beamWidth = .1f))
        val wide = renderer.render(source, selected, FaceLightAdjustment(selected.id, FaceLightStyle.LIFT, .8f, beamWidth = 1f))
        val narrowChanged = narrow.argb.indices.count { narrow.argb[it] != source.argb[it] }
        val wideChanged = wide.argb.indices.count { wide.argb[it] != source.argb[it] }
        assertTrue("beam width did not expand the lit region", wideChanged > narrowChanged)
    }

    @Test fun `broad beam illuminates surrounding non skin area beyond face`() {
        val source = fixture()
        val selected = face("dark", .12f, .20f, .38f, .72f)
        val narrow = renderer.render(source, selected, FaceLightAdjustment(selected.id, FaceLightStyle.LIFT, 1f, beamWidth = .5f))
        val broad = renderer.render(source, selected, FaceLightAdjustment(selected.id, FaceLightStyle.LIFT, 1f, beamWidth = 1f))

        // This point is outside the detected face rectangle but inside a 100% (two-diameter)
        // beam. It represents nearby hair, clothing or environment rather than skin.
        val x = 24
        val y = 73
        val index = y * source.width + x
        assertTrue("50 percent beam unexpectedly reached outside one face diameter", narrow.argb[index] == source.argb[index])
        assertTrue("100 percent beam did not illuminate surrounding area", broad.argb[index] != source.argb[index])
    }

    private fun fixture(): RasterImage {
        val width = 240; val height = 160
        val pixels = IntArray(width * height) { 0xff303840.toInt() }
        fun paint(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            for (y in top until bottom) for (x in left until right) pixels[y * width + x] = color
        }
        paint(29, 32, 91, 115, 0xff694a3e.toInt())
        paint(154, 32, 216, 115, 0xffc69076.toInt())
        return RasterImage(width, height, pixels)
    }

    private fun face(id: String, left: Float, top: Float, right: Float, bottom: Float) =
        FaceEvidence(id, NormalizedRect(left, top, right, bottom), .95f, .95f, emptyList())
}
