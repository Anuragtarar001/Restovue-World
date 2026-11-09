package com.smartphotomagic.engine

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Full-resolution renderer. Single pass over overlapping tiles: decode (8-bit sRGB -> linear float),
 * white balance, edge-aware local exposure (bilateral-grid slice), global tone curve, OKLab colour,
 * then noise-adaptive denoise + sharpening on the tile with an apron, and a single final
 * quantisation to 8 bits with TPDF dither. No intermediate 8-bit images exist.
 */
class Renderer(private val threads: Int = min(8, max(1, Runtime.getRuntime().availableProcessors()))) {

    private class TileBuffers(n: Int) {
        val px = IntArray(n)
        val L = FloatArray(n); val A = FloatArray(n); val B = FloatArray(n)
        val skin = FloatArray(n); val sig = FloatArray(n)
        val Ld = FloatArray(n)
        val affine = FloatArray(AffineBilateralGrid.COEFFICIENTS)
        val out = IntArray(TILE * TILE)
    }

    fun render(plan: RenderPlan, src: PixelSource, dst: PixelSink, cancel: CancelToken = CancelToken(),
               progress: ProgressListener? = null, finishGrid: AffineBilateralGrid? = null) {
        val w = src.width; val h = src.height
        val tilesX = (w + TILE - 1) / TILE; val tilesY = (h + TILE - 1) / TILE
        val total = tilesX * tilesY
        val done = AtomicInteger()
        val pool = Executors.newFixedThreadPool(threads)
        val local = ThreadLocal.withInitial { TileBuffers((TILE + 2 * APRON) * (TILE + 2 * APRON)) }
        try {
            val futures = ArrayList<Future<*>>(total)
            for (ty in 0 until tilesY) for (tx in 0 until tilesX) {
                futures.add(pool.submit {
                    if (cancel.cancelled) return@submit
                    renderTile(plan, src, dst, tx * TILE, ty * TILE, local.get(), finishGrid)
                    val d = done.incrementAndGet()
                    progress?.onProgress(d.toFloat() / total)
                })
            }
            for (f in futures) {
                try { f.get() } catch (e: ExecutionException) { throw e.cause ?: e }
                if (cancel.cancelled) throw CancellationException("cancelled")
            }
        } finally {
            pool.shutdownNow()
        }
        cancel.check()
    }

    private fun renderTile(
        p: RenderPlan,
        src: PixelSource,
        dst: PixelSink,
        tx0: Int,
        ty0: Int,
        t: TileBuffers,
        finishGrid: AffineBilateralGrid?,
    ) {
        val W = src.width; val H = src.height
        val tw = min(TILE, W - tx0); val th = min(TILE, H - ty0)
        val ex0 = max(0, tx0 - APRON); val ey0 = max(0, ty0 - APRON)
        val ex1 = min(W, tx0 + tw + APRON); val ey1 = min(H, ty0 + th + APRON)
        val ew = ex1 - ex0; val eh = ey1 - ey0
        src.read(ex0, ey0, ew, eh, t.px)

        val lut = ColorMath.SRGB8_TO_LINEAR
        val wb = p.wb
        val scale = min(1f, W.toFloat() / p.fullWidth)
        // noise sigma in OKLab-L units at this rendering scale (downsampled previews are cleaner)
        val sigmaIn = p.noiseSigma8 / 255f * 0.9f * scale
        val invW = 1f / max(1, W - 1); val invH = 1f / max(1, H - 1)
        val sl = FloatArray(2); val lab = FloatArray(3)
        val gamma = 1f / 3f

        // ---------- per-pixel colour & tone ----------
        for (yy in 0 until eh) {
            val v = (ey0 + yy) * invH
            for (xx in 0 until ew) {
                val e = yy * ew + xx
                val u = (ex0 + xx) * invW
                val c = t.px[e]
                val r8 = (c shr 16) and 255; val g8 = (c shr 8) and 255; val b8 = c and 255
                val r0 = lut[r8]; val g0 = lut[g8]; val b0 = lut[b8]
                var r = wb[0] * r0 + wb[1] * g0 + wb[2] * b0
                var g = wb[3] * r0 + wb[4] * g0 + wb[5] * b0
                var b = wb[6] * r0 + wb[7] * g0 + wb[8] * b0
                // Clipped source highlights carry no colour information: keep them neutral so WB
                // does not tint blown skies / lamps.
                // Only near-white pixels (ALL channels high) are neutralised; a saturated colour with
                // one clipped channel (orange cloth, red light) keeps its hue.
                val mn8 = min(r8, min(g8, b8))
                if (mn8 > 190) {
                    val mx8 = max(r8, max(g8, b8))
                    val cw = ColorMath.smoothstep(238f, 254f, mx8.toFloat()) * ColorMath.smoothstep(190f, 245f, mn8.toFloat())
                    val yv = ColorMath.luminance(r, g, b)
                    r += (yv - r) * cw; g += (yv - g) * cw; b += (yv - b) * cw
                }
                val y = max(ColorMath.luminance(r, g, b), 1f / 16384f)
                val l = (ln(y.toDouble()) * 1.4426950408889634).toFloat()
                p.grid.slice(u, v, l, sl)
                val lf = p.toneLog(l, sl[0], sl[1])
                val ratio = exp((lf - l) * 0.6931471805599453f)
                r *= ratio; g *= ratio; b *= ratio
                ColorMath.linearToOklab(r, g, b, lab)
                var L = lab[0]; var a = lab[1]; var bb = lab[2]
                val C = sqrt(a * a + bb * bb)
                var skin = 0f
                if (C > 1e-4f) {
                    val hue = ColorMath.hueDeg(a, bb)
                    skin = Analyzer.skinLikelihood(hue, C, lf) * (if (p.hasFaces) 0.25f + 0.75f * p.faceProxAt(u, v) else 0.35f)
                    // chroma gain
                    var f = p.saturation * (1f + p.vibrance * (1f - ColorMath.smoothstep(0f, 0.17f, C)))
                    f = 1f + (f - 1f) * (1f - p.skinProtect * skin)
                    // Brightening with fixed RGB ratios raises OKLab chroma in proportion; dark scenes
                    // also look less saturated than they are (Hunt effect). Compensate partially.
                    if (ratio > 1f) f *= max(0.72f, Math.pow(ratio.toDouble(), -0.17).toFloat())
                    // Bright, nearly neutral material is snow/ice/cloud evidence. Preserve its
                    // neutrality and detail even when a blue sky or water is enhanced nearby.
                    // Chromatic green pixels remain eligible, so leaves showing through ice can
                    // still become richer without colouring the ice itself.
                    val iceProtect = ColorMath.smoothstep(0.70f, 0.90f, L) *
                        (1f - 0.35f * ColorMath.smoothstep(0.02f, 0.10f, C))
                    val personProtect = if (p.hasFaces) 0.85f * p.faceProxAt(u, v) else 0f
                    val sceneProtect = (1f - iceProtect) * (1f - personProtect)
                    var skyHaze = 0f
                    var skyHueShift = 0f
                    if (p.sky > 0f) {
                        val upper = 1f - ColorMath.smoothstep(0.48f, 0.78f, v)
                        val blueHue = bump(hue, 237f, 46f, 20f)
                        val ws = blueHue * ColorMath.smoothstep(0.014f, 0.055f, C) *
                            ColorMath.smoothstep(0.24f, 0.58f, L) * upper * sceneProtect
                        // A hazy sky already contains weak blue evidence, but multiplying its tiny
                        // chroma is not enough to clear the grey veil. Give only those faint-blue,
                        // upper-frame pixels a small chroma floor and tonal separation. Near-neutral
                        // clouds remain excluded by hue/chroma and bright-white protection.
                        skyHaze = blueHue * ColorMath.smoothstep(0.006f, 0.026f, C) *
                            (1f - ColorMath.smoothstep(0.055f, 0.11f, C)) *
                            ColorMath.smoothstep(0.42f, 0.78f, L) * upper * sceneProtect
                        skyHueShift = (237f - hue).coerceIn(-10f, 14f) * p.sky * skyHaze
                        f *= 1f + 0.20f * p.sky * ws
                        f *= 1f + 0.48f * p.sky * skyHaze
                        L -= 0.045f * p.sky * ws + 0.025f * p.sky * skyHaze
                    }
                    if (p.water > 0f) {
                        val lower = ColorMath.smoothstep(0.38f, 0.70f, v)
                        val ww = bump(hue, 225f, 40f, 20f) * ColorMath.smoothstep(0.014f, 0.055f, C) *
                            (1f - ColorMath.smoothstep(0.78f, 0.94f, L)) * lower * sceneProtect
                        f *= 1f + 0.18f * p.water * ww
                        L -= 0.100f * p.water * ww
                    }
                    if (p.foliage > 0f) {
                        val wf = bump(hue, 132f, 28f, 14f) * ColorMath.smoothstep(0.025f, 0.065f, C) * sceneProtect
                        f *= 1f + p.foliage * wf
                    }
                    if (p.warm > 0f) f *= 1f + p.warm * bump(hue, 55f, 35f, 15f) * (1f - skin)
                    f *= 1f - p.shadowChromaReduce * (1f - ColorMath.smoothstep(0.06f, 0.22f, L))
                    // Near-black 8-bit source pixels carry quantisation/noise, not colour: do not let
                    // WB gains and shadow lift turn them into coloured (often magenta) mush.
                    f *= 0.25f + 0.75f * ColorMath.smoothstep(-10.5f, -7f, l)
                    var cn = C * f + 0.014f * p.sky * skyHaze
                    val c0 = 0.2f
                    if (cn > c0) cn = c0 + (cn - c0) / (1f + (cn - c0) / 0.08f)   // soft chroma ceiling
                    // skin hue guard: pull implausibly pink or yellow-green skin toward the skin line
                    var hueShift = 0f
                    if (skin > 0.2f && p.hasFaces && p.skinHuePull > 0f) {
                        if (hue < 42f) hueShift = min(42f - hue, 7f) * skin * p.skinHuePull
                        else if (hue > 70f && hue < 100f) hueShift = -min(hue - 70f, 7f) * skin * p.skinHuePull
                    }
                    val k = cn / C
                    a *= k; bb *= k
                    if (skyHueShift != 0f) { ColorMath.rotate(a, bb, skyHueShift, lab, 1); a = lab[1]; bb = lab[2] }
                    if (hueShift != 0f) { ColorMath.rotate(a, bb, hueShift, lab, 1); a = lab[1]; bb = lab[2] }
                }
                t.L[e] = L; t.A[e] = a; t.B[e] = bb; t.skin[e] = skin
                t.sig[e] = sigmaIn * ColorMath.cbrt(ratio)
            }
        }

        // ---------- luma denoise (edge-preserving bilateral, noise-adaptive) on inner+1 ----------
        val ix0 = tx0 - ex0; val iy0 = ty0 - ey0
        val rx0 = max(0, ix0 - 1); val ry0 = max(0, iy0 - 1)
        val rx1 = min(ew, ix0 + tw + 1); val ry1 = min(eh, iy0 + th + 1)
        val doLuma = p.lumaNR > 0.01f
        for (yy in ry0 until ry1) for (xx in rx0 until rx1) {
            val e = yy * ew + xx
            val L0 = t.L[e]
            val s = t.sig[e]
            if (!doLuma || s < 0.0015f) { t.Ld[e] = L0; continue }
            val inv2 = 1f / (2f * (2.2f * s) * (2.2f * s))
            var sw = 0f; var sv = 0f
            for (dy in -2..2) {
                val y2 = yy + dy
                if (y2 < 0 || y2 >= eh) continue
                for (dx in -2..2) {
                    val x2 = xx + dx
                    if (x2 < 0 || x2 >= ew) continue
                    val q = t.L[y2 * ew + x2]
                    val d = q - L0
                    val wgt = SPATIAL5[(dy + 2) * 5 + dx + 2] * fastExpNeg(d * d * inv2)
                    sw += wgt; sv += wgt * q
                }
            }
            t.Ld[e] = L0 + p.lumaNR * (sv / sw - L0)
        }

        // ---------- chroma denoise, sharpening, output ----------
        val doChroma = p.chromaNR > 0.01f
        val chromaSig = p.chromaNoiseSigma8 / 255f * 0.35f * scale
        val rgb = FloatArray(3)
        for (yy in iy0 until iy0 + th) {
            for (xx in ix0 until ix0 + tw) {
                val e = yy * ew + xx
                var a = t.A[e]; var bb = t.B[e]
                val Ld = t.Ld[e]
                val s = t.sig[e]
                if (doChroma) {
                    val gain = s / max(1e-6f, sigmaIn)
                    val cs = chromaSig * gain
                    if (cs > 0.0008f) {
                        val invL = 1f / (2f * (3f * s + 0.015f) * (3f * s + 0.015f))
                        val invC = 1f / (2f * (3f * cs + 0.01f) * (3f * cs + 0.01f))
                        var sw = 0f; var sa = 0f; var sb = 0f
                        var dy = -4
                        while (dy <= 4) {
                            val y2 = yy + dy
                            if (y2 in 0 until eh) {
                                var dx = -4
                                while (dx <= 4) {
                                    val x2 = xx + dx
                                    if (x2 in 0 until ew) {
                                        val q = y2 * ew + x2
                                        val dl = t.L[q] - t.L[e]; val da = t.A[q] - t.A[e]; val db = t.B[q] - t.B[e]
                                        val wgt = SPATIAL_DIL[((dy + 4) / 2) * 5 + (dx + 4) / 2] * fastExpNeg(dl * dl * invL + (da * da + db * db) * invC)
                                        sw += wgt; sa += wgt * t.A[q]; sb += wgt * t.B[q]
                                    }
                                    dx += 2
                                }
                            }
                            dy += 2
                        }
                        val k = min(1f, p.chromaNR * ColorMath.smoothstep(0.0008f, 0.004f, cs) * 1.0f)
                        a += (sa / sw - a) * k; bb += (sb / sw - bb) * k
                    }
                }
                // unsharp mask on denoised L with noise coring + overshoot clamp
                var Lout = Ld
                if (p.sharpen > 0.01f && yy > 0 && yy < eh - 1 && xx > 0 && xx < ew - 1) {
                    val i0 = e - ew; val i2 = e + ew
                    val blur = (t.Ld[i0 - 1] + 2 * t.Ld[i0] + t.Ld[i0 + 1] +
                        2 * t.Ld[e - 1] + 4 * Ld + 2 * t.Ld[e + 1] +
                        t.Ld[i2 - 1] + 2 * t.Ld[i2] + t.Ld[i2 + 1]) / 16f
                    val d = Ld - blur
                    val thr = max(1.4f * s * (1f - 0.5f * p.lumaNR), 0.0035f) // >= ~1 code value: JPEG/quantisation floor
                    val ad = abs(d) - thr
                    if (ad > 0f) {
                        val amt = p.sharpen * 1.6f * (1f - 0.7f * t.skin[e])
                        Lout = Ld + amt * (if (d > 0) ad else -ad)
                        val ld = t.Ld
                        val mn = min(min(min(ld[i0 - 1], ld[i0]), min(ld[i0 + 1], ld[e - 1])), min(min(ld[e + 1], ld[i2 - 1]), min(ld[i2], min(ld[i2 + 1], Ld))))
                        val mx = max(max(max(ld[i0 - 1], ld[i0]), max(ld[i0 + 1], ld[e - 1])), max(max(ld[e + 1], ld[i2 - 1]), max(ld[i2], max(ld[i2 + 1], Ld))))
                        Lout = Lout.coerceIn(mn - 0.012f, mx + 0.012f)
                    }
                }
                ColorMath.oklabToLinear(Lout, a, bb, rgb)
                ColorMath.gamutMap(rgb)
                val gx = tx0 + xx - ix0; val gy = ty0 + yy - iy0
                val dth = dither(gx, gy)
                var encodedR = ColorMath.encode(rgb[0])
                var encodedG = ColorMath.encode(rgb[1])
                var encodedB = ColorMath.encode(rgb[2])
                if (finishGrid != null) {
                    finishGrid.apply(gx * invW, gy * invH, encodedR, encodedG, encodedB, t.affine)
                    encodedR = t.affine[0]; encodedG = t.affine[1]; encodedB = t.affine[2]
                }
                val ro = (encodedR * 255f + 0.5f + dth).toInt().coerceIn(0, 255)
                val go = (encodedG * 255f + 0.5f + dither(gx + 7919, gy)).toInt().coerceIn(0, 255)
                val bo = (encodedB * 255f + 0.5f + dither(gx, gy + 7919)).toInt().coerceIn(0, 255)
                t.out[(yy - iy0) * tw + (xx - ix0)] = (0xFF shl 24) or (ro shl 16) or (go shl 8) or bo
            }
        }
        synchronized(dst) { dst.write(tx0, ty0, tw, th, t.out) }
    }

    companion object {
        const val TILE = 256
        const val APRON = 5
        private val SPATIAL5 = FloatArray(25) { val dx = it % 5 - 2; val dy = it / 5 - 2; exp(-(dx * dx + dy * dy) / (2f * 1.3f * 1.3f)) }
        private val SPATIAL_DIL = FloatArray(25) { val dx = (it % 5 - 2) * 2; val dy = (it / 5 - 2) * 2; exp(-(dx * dx + dy * dy) / (2f * 3.0f * 3.0f)) }

        /** Raised-cosine window on hue (degrees, circular). */
        fun bump(h: Float, center: Float, half: Float, feather: Float): Float {
            var d = abs(h - center); if (d > 180f) d = 360f - d
            return 1f - ColorMath.smoothstep(half, half + feather, d)
        }

        private val EXP_LUT = FloatArray(1025) { exp(-it / 64f) } // exp(-x) for x in [0, 16]
        fun fastExpNeg(x: Float): Float {
            if (x >= 16f) return 0f
            val t = x * 64f; val i = t.toInt(); val f = t - i
            return EXP_LUT[i] + (EXP_LUT[i + 1] - EXP_LUT[i]) * f
        }

        /** Triangular-PDF dither in [-1, 1] LSB from a coordinate hash. */
        fun dither(x: Int, y: Int): Float {
            var hsh = x * 374761393 + y * 668265263
            hsh = (hsh xor (hsh ushr 13)) * 1274126177
            hsh = hsh xor (hsh ushr 16)
            val a = (hsh and 0xFFFF) / 65535f; val b = ((hsh ushr 16) and 0xFFFF) / 65535f
            return (a + b - 1f) * 0.5f
        }
    }
}
