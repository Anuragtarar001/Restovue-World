package com.restorex.app.data

import com.smartphotomagic.engine.AffineBilateralGrid
import com.smartphotomagic.engine.IntArrayImage
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AffineBilateralGridTest {
    @Test
    fun identityFitDoesNotDarkenSparseOrEmptyCells() {
        val source = image(96, 64) { x, y ->
            val r = (20 + x * 180 / 95).coerceIn(0, 255)
            val g = (15 + y * 210 / 63).coerceIn(0, 255)
            val b = (30 + (x + y) * 150 / 158).coerceIn(0, 255)
            argb(r, g, b)
        }
        val grid = AffineBilateralGrid.fit(source, source)
        val out = FloatArray(AffineBilateralGrid.COEFFICIENTS)
        var worst = 0f
        for (v in listOf(0f, .17f, .5f, .91f, 1f)) for (u in listOf(0f, .11f, .5f, .87f, 1f)) {
            for (value in listOf(.01f, .22f, .55f, .94f)) {
                grid.apply(u, v, value, value * .7f, value * .4f, out)
                worst = maxOf(worst, abs(out[0] - value), abs(out[1] - value * .7f), abs(out[2] - value * .4f))
            }
        }
        assertTrue("identity prior drifted by $worst", worst < .012f)
    }

    @Test
    fun fittedGridReproducesSmoothRegionalLift() {
        val source = image(160, 120) { x, y ->
            val base = (35 + x * 120 / 159 + y * 45 / 119).coerceIn(0, 255)
            argb(base, (base * .86f).toInt(), (base * .72f).toInt())
        }
        val target = image(160, 120) { x, y ->
            val c = source.pixels[y * 160 + x]
            val dx = (x - 80f) / 52f; val dy = (y - 62f) / 46f
            val mask = (1f - (dx * dx + dy * dy)).coerceIn(0f, 1f)
            val lift = 24f * mask
            argb(((c ushr 16 and 255) + lift).toInt(), ((c ushr 8 and 255) + lift).toInt(), ((c and 255) + lift).toInt())
        }
        val grid = AffineBilateralGrid.fit(source, target)
        val out = FloatArray(AffineBilateralGrid.COEFFICIENTS)
        var error = 0.0
        var count = 0
        for (y in 0 until 120 step 3) for (x in 0 until 160 step 3) {
            val s = source.pixels[y * 160 + x]
            val t = target.pixels[y * 160 + x]
            val r = (s ushr 16 and 255) / 255f; val g = (s ushr 8 and 255) / 255f; val b = (s and 255) / 255f
            grid.apply(x / 159f, y / 119f, r, g, b, out)
            error += abs(out[0] - (t ushr 16 and 255) / 255f)
            error += abs(out[1] - (t ushr 8 and 255) / 255f)
            error += abs(out[2] - (t and 255) / 255f)
            count += 3
        }
        assertTrue("mean absolute error was ${error / count}", error / count < .018)
    }

    @Test
    fun spatialFitKeepsRegionalLiftOutOfDistantBackground() {
        val source = image(192, 128) { _, _ -> argb(82, 70, 62) }
        val target = image(192, 128) { x, y ->
            val inside = x in 52..116 && y in 20..108
            if (inside) argb(112, 100, 92) else argb(82, 70, 62)
        }
        val grid = AffineBilateralGrid.fit(source, target)
        val out = FloatArray(AffineBilateralGrid.COEFFICIENTS)
        var distantLeak = 0f
        for (y in 0 until 128 step 4) for (x in 0 until 192 step 4) {
            if (x in 35..133 && y in 4..124) continue
            grid.apply(x / 191f, y / 127f, 82 / 255f, 70 / 255f, 62 / 255f, out)
            distantLeak = maxOf(distantLeak, abs(out[0] - 82 / 255f), abs(out[1] - 70 / 255f), abs(out[2] - 62 / 255f))
        }
        assertTrue("regional correction leaked into distant background by $distantLeak", distantLeak < .018f)
    }

    private fun image(width: Int, height: Int, pixel: (Int, Int) -> Int) =
        IntArrayImage(width, height, IntArray(width * height) { pixel(it % width, it / width) })

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or
        (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
