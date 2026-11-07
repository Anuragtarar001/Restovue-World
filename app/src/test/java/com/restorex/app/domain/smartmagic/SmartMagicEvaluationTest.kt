package com.restorex.app.domain.smartmagic

import com.restorex.app.data.SmartMagicPipeline
import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

/** Deterministic synthetic calibration set; stdout is consumed by the checked-in evaluation report. */
class SmartMagicEvaluationTest {
    private val analyzer = SmartMagicAnalyzer()
    private val parameterEngine = SmartMagicParameterEngine()
    private val pipeline = SmartMagicPipeline(analyzer)

    @Test fun evaluateRepresentativeSyntheticScenes() {
        val cases = linkedMapOf(
            "well_exposed_portrait_like" to portraitLike(192, 128, 1f),
            "underexposed_portrait_like" to portraitLike(192, 128, .42f),
            "backlit_portrait_like" to backlit(192, 128),
            "daylight_landscape" to landscape(192, 128, sunset = false),
            "sunset" to landscape(192, 128, sunset = true),
            "night" to night(192, 128),
            "high_contrast" to splitField(192, 128, 15, 245),
            "low_contrast" to splitField(192, 128, 104, 145),
            "already_saturated" to saturated(192, 128),
            "washed_out" to splitField(192, 128, 165, 208),
            "noisy" to noisy(192, 128),
            "document" to document(192, 128),
            "transparent_png" to transparent(192, 128),
        )
        cases.forEach { (name, source) ->
            val analysis = analyzer.analyze(source)
            val parameters = parameterEngine.parametersFor(analysis)
            val enhanced = pipeline.enhance(source, parameters)
            val after = analyzer.analyze(enhanced)
            assertEquals(source.width, enhanced.width); assertEquals(source.height, enhanced.height)
            println(
                "SMART_MAGIC_CASE $name ev=${f(parameters.exposureEv)} contrast=${f(parameters.contrast)} " +
                    "hi=${f(parameters.highlights)} sh=${f(parameters.shadows)} vib=${f(parameters.vibrance)} " +
                    "clarity=${f(parameters.clarity)} texture=${f(parameters.texture)} dehaze=${f(parameters.dehaze)} " +
                    "nr=${f(parameters.luminanceNoiseReduction)}/${f(parameters.chromaNoiseReduction)} sharp=${f(parameters.sharpening)} " +
                    "clip=${f(analysis.luminance.shadowClippingFraction)}/${f(analysis.luminance.highlightClippingFraction)}->" +
                    "${f(after.luminance.shadowClippingFraction)}/${f(after.luminance.highlightClippingFraction)}",
            )
            assertTrue("$name introduced excessive shadow clipping", after.luminance.shadowClippingFraction <= (analysis.luminance.shadowClippingFraction + .10f))
            assertTrue("$name introduced excessive highlight clipping", after.luminance.highlightClippingFraction <= (analysis.luminance.highlightClippingFraction + .08f))
        }
    }

    private fun portraitLike(w: Int, h: Int, exposure: Float) = raster(w, h) { x, y ->
        val dx = (x - w / 2f) / (w * .25f); val dy = (y - h * .45f) / (h * .38f)
        val face = dx * dx + dy * dy < 1f
        if (face) rgb(205 * exposure, 142 * exposure, 112 * exposure) else rgb(72 * exposure, 93 * exposure, 118 * exposure)
    }
    private fun backlit(w: Int, h: Int) = raster(w, h) { x, y ->
        val border = x < w / 5 || x > w * 4 / 5 || y < h / 5
        if (border) rgb(240f, 226f, 188f) else if ((x - w / 2) * (x - w / 2) + (y - h / 2) * (y - h / 2) < h * h / 8) rgb(85f, 56f, 45f) else rgb(105f, 120f, 125f)
    }
    private fun landscape(w: Int, h: Int, sunset: Boolean) = raster(w, h) { x, y ->
        if (y < h / 2) {
            if (sunset) rgb(220f + x * 25f / w, 105f + y * 70f / h, 60f + y * 70f / h)
            else rgb(65f + y * 30f / h, 130f + y * 35f / h, 205f + y * 25f / h)
        } else rgb(45f + x * 35f / w, 115f + (y % 9) * 5f, 55f)
    }
    private fun night(w: Int, h: Int) = raster(w, h) { x, y -> if ((x * 17 + y * 31) % 97 == 0) rgb(235f, 190f, 95f) else rgb(12f + y * 14f / h, 19f, 34f + x * 12f / w) }
    private fun splitField(w: Int, h: Int, low: Int, high: Int) = raster(w, h) { x, _ -> val v = low + x * (high - low) / (w - 1); rgb(v.toFloat(), v.toFloat(), v.toFloat()) }
    private fun saturated(w: Int, h: Int) = raster(w, h) { x, y -> when ((x / 24 + y / 24) % 3) { 0 -> rgb(250f, 12f, 25f); 1 -> rgb(15f, 242f, 28f); else -> rgb(18f, 35f, 250f) } }
    private fun noisy(w: Int, h: Int): RasterImage { val random = Random(73); return raster(w, h) { _, _ -> val n = random.nextInt(-30, 31); rgb((72 + n).toFloat(), (78 - n / 2).toFloat(), (88 + n / 3).toFloat()) } }
    private fun document(w: Int, h: Int) = raster(w, h) { x, y -> val line = y % 18 in 8..10 && x in 18 until w - 18; if (line) rgb(25f, 25f, 25f) else rgb(242f, 240f, 234f) }
    private fun transparent(w: Int, h: Int) = RasterImage(w, h, IntArray(w * h) { index -> val x = index % w; val y = index / w; val alpha = if ((x + y) % 7 == 0) 0 else 170; (alpha shl 24) or (rgb(95f + x * 80f / w, 120f, 180f) and 0x00FFFFFF) })
    private fun raster(w: Int, h: Int, pixel: (Int, Int) -> Int) = RasterImage(w, h, IntArray(w * h) { pixel(it % w, it / w) })
    private fun rgb(r: Float, g: Float, b: Float) = (0xFF shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
    private fun f(value: Float) = "%.3f".format(java.util.Locale.US, value)
}
