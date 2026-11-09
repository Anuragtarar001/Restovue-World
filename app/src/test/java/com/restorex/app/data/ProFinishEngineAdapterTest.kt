package com.restorex.app.data

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.EnhancementLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProFinishEngineAdapterTest {
    @Test fun allModesRenderRealAndDistinctSceneFinishes() {
        val width = 96
        val height = 64
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            when {
                y < 25 -> rgb(125 + x / 5, 165 + x / 8, 190 + x / 10) // hazy blue sky
                y < 43 -> rgb(45 + x / 8, 98 + x / 7, 115 + x / 9) // blue-green water
                else -> rgb(35 + x / 9, 82 + x / 6, 36 + x / 12) // foliage
            }
        }
        val source = RasterImage(width, height, pixels)
        val engine = ProFinishEngineAdapter()
        val plans = engine.planAll(source, emptyList()) { false }
        val natural = engine.render(source, plans.getValue(EnhancementLevel.NATURAL)) { false }
        val enhanced = engine.render(source, plans.getValue(EnhancementLevel.ENHANCED)) { false }
        val extreme = engine.render(source, plans.getValue(EnhancementLevel.EXTREME)) { false }

        assertTrue(changedFraction(source, natural) > .10f)
        assertFalse(natural.argb.contentEquals(enhanced.argb))
        assertFalse(enhanced.argb.contentEquals(extreme.argb))
        assertTrue(changedFraction(source, extreme) > .25f)
    }

    private fun changedFraction(a: RasterImage, b: RasterImage): Float =
        a.argb.indices.count { a.argb[it] != b.argb[it] }.toFloat() / a.argb.size

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xff shl 24) or (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
