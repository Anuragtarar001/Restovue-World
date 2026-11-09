package com.smartphotomagic.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val LOG2E = 1.4426950408889634f
fun log2f(x: Float): Float = (ln(x.toDouble()) * 1.4426950408889634).toFloat()
fun exp2f(x: Float): Float = exp(x * 0.6931471805599453f)

/** Per-face measurements taken on the white-balanced proxy. */
data class FaceStats(
    val box: FaceBox,
    /** median log2 luminance of skin pixels in the face core */
    val logLum: Float,
    /** base-layer log2 luminance at the face (illumination level) */
    val baseLog: Float,
    val skinHueDeg: Float,
    val skinChroma: Float,
    val skinFraction: Float,
)

/** Continuous scene descriptors (0..1 confidences, not hard labels). */
data class SceneScores(
    val portrait: Float,
    val group: Float,
    val backlit: Float,
    val night: Float,
    val lowLight: Float,
    val sunset: Float,
    val landscape: Float,
    val indoor: Float,
    val highKey: Float,
    val ice: Float,
    val sky: Float,
    val water: Float,
    val foliage: Float,
) {
    fun describe(): String = buildList {
        if (portrait > 0.4f) add(if (group > 0.5f) "group portrait" else "portrait")
        if (backlit > 0.4f) add("backlit")
        if (night > 0.4f) add("night")
        else if (lowLight > 0.4f) add("low light")
        if (sunset > 0.4f) add("sunset/golden hour")
        if (landscape > 0.4f) add("landscape")
        if (indoor > 0.4f) add("indoor/artificial light")
        if (highKey > 0.4f) add("high key")
    }.ifEmpty { listOf("general") }.joinToString(", ")
}

class SceneAnalysis(
    val fullWidth: Int,
    val fullHeight: Int,
    /** linear, NOT white balanced, proxy (long side <= PROXY_MAX) */
    val proxy: LinearImage,
    /** fraction of full-res pixels per proxy pixel that have a clipped channel (>= 250/255) */
    val clipMap: FloatArray,
    val illuminant: IlluminantEstimate,
    val faces: List<FaceStats>,
    /** log2 luminance of the white-balanced proxy */
    val logLum: FloatArray,
    /** edge-aware base layer (channel 0) evaluated per proxy pixel */
    val baseLog: FloatArray,
    val baseGrid: BilateralGrid,
    /** OKLab chroma of WB-corrected proxy, per pixel */
    val chroma: FloatArray,
    val hue: FloatArray,
    val noiseSigma: Float,
    val chromaNoiseSigma: Float,
    val clipFraction: Float,
    val blackFraction: Float,
    val scores: SceneScores,
    /** WB matrix used to compute the proxy-derived measures (full-strength correction). */
    val measureWb: FloatArray,
    /** mean local deviation (8-bit encoded levels) inside dark regions: ~0-1 for silhouettes, >3 for detailed shadows */
    val shadowDetail: Float,
    /** standard deviation of sRGB-encoded luminance of the original (global contrast) */
    val tonalSd: Float,
)

object Analyzer {
    const val PROXY_MAX = 768

    fun analyze(src: PixelSource, faces: List<FaceBox>, cancel: CancelToken = CancelToken()): SceneAnalysis {
        val (proxy, clipMap, blackFrac) = buildProxy(src, cancel)
        cancel.check()
        val (noise, chromaNoise) = NoiseEstimator.estimate(src)
        cancel.check()
        val pw = proxy.width; val ph = proxy.height; val n = pw * ph

        // Mask faces out of the illuminant estimate (skin biases grey-world methods warm).
        val faceMask = BooleanArray(n)
        for (f in faces) forEachInBox(f, pw, ph, 1.15f) { i -> faceMask[i] = true }
        val illum = WhiteBalance.estimate(proxy, clipMap, faceMask)

        // Measurement WB: full correction toward neutral, used only for colour measures.
        val wb = ColorMath.adaptationMatrix(illum.rgb, floatArrayOf(1f, 1f, 1f))
        val logLum = FloatArray(n)
        val chroma = FloatArray(n)
        val hue = FloatArray(n)
        val lab = FloatArray(3)
        for (i in 0 until n) {
            val r = proxy.r[i]; val g = proxy.g[i]; val b = proxy.b[i]
            val rr = wb[0] * r + wb[1] * g + wb[2] * b
            val gg = wb[3] * r + wb[4] * g + wb[5] * b
            val bb = wb[6] * r + wb[7] * g + wb[8] * b
            val y = ColorMath.luminance(rr, gg, bb)
            logLum[i] = log2f(max(y, 1f / 16384f))
            ColorMath.linearToOklab(rr, gg, bb, lab)
            chroma[i] = hypot(lab[1], lab[2])
            hue[i] = ColorMath.hueDeg(lab[1], lab[2])
        }
        val grid = BilateralGrid.buildBase(logLum, pw, ph)
        val baseLog = FloatArray(n)
        val tmp = FloatArray(2)
        for (y in 0 until ph) for (x in 0 until pw) {
            val i = y * pw + x
            grid.slice(x / (pw - 1f), y / (ph - 1f), logLum[i], tmp)
            baseLog[i] = tmp[0]
        }

        var sdSum = 0.0; var sdSum2 = 0.0; var shD = 0.0; var shN = 0
        for (i in 0 until n) {
            val e = ColorMath.encode(exp2f(logLum[i]))
            sdSum += e; sdSum2 += e * e
            if (baseLog[i] < -5f) { shD += abs(e - ColorMath.encode(exp2f(baseLog[i]))) * 255.0; shN++ }
        }
        val tonalSd = sqrt(max(0.0, sdSum2 / n - (sdSum / n) * (sdSum / n))).toFloat()
        val shadowDetail = if (shN > 0) (shD / shN).toFloat() else 0f

        val faceStats = faces.mapNotNull { measureFace(it, pw, ph, logLum, baseLog, chroma, hue) }
        val clipFrac = clipMap.average().toFloat()
        // Raw (uncorrected) colours: golden-hour warmth must be judged BEFORE white balance removes it.
        val rawChroma = FloatArray(n); val rawHue = FloatArray(n)
        for (i in 0 until n) {
            ColorMath.linearToOklab(proxy.r[i], proxy.g[i], proxy.b[i], lab)
            rawChroma[i] = hypot(lab[1], lab[2]); rawHue[i] = ColorMath.hueDeg(lab[1], lab[2])
        }
        val scores = scoreScene(pw, ph, logLum, baseLog, chroma, hue, rawChroma, rawHue, faceStats, illum, noise, clipMap)
        return SceneAnalysis(src.width, src.height, proxy, clipMap, illum, faceStats, logLum, baseLog, grid,
            chroma, hue, noise, chromaNoise, clipFrac, blackFrac, scores, wb, shadowDetail, tonalSd)
    }

    /** Area-average downsample in linear light; also records per-block clipping. */
    private fun buildProxy(src: PixelSource, cancel: CancelToken): Triple<LinearImage, FloatArray, Float> {
        val f = max(1, ceil(max(src.width, src.height).toDouble() / PROXY_MAX).toInt())
        val pw = max(1, src.width / f); val ph = max(1, src.height / f)
        val img = LinearImage(pw, ph)
        val clip = FloatArray(pw * ph)
        val row = IntArray(src.width * f)
        val lut = ColorMath.SRGB8_TO_LINEAR
        val accR = DoubleArray(pw); val accG = DoubleArray(pw); val accB = DoubleArray(pw); val accC = IntArray(pw)
        var black = 0L
        for (py in 0 until ph) {
            if (py % 16 == 0) cancel.check()
            src.read(0, py * f, src.width, f, row)
            java.util.Arrays.fill(accR, 0.0); java.util.Arrays.fill(accG, 0.0); java.util.Arrays.fill(accB, 0.0); java.util.Arrays.fill(accC, 0)
            for (yy in 0 until f) {
                val off = yy * src.width
                for (px in 0 until pw) {
                    var x = px * f
                    val end = x + f
                    while (x < end) {
                        val c = row[off + x]
                        val r8 = (c shr 16) and 255; val g8 = (c shr 8) and 255; val b8 = c and 255
                        accR[px] += lut[r8].toDouble(); accG[px] += lut[g8].toDouble(); accB[px] += lut[b8].toDouble()
                        if (r8 >= 250 || g8 >= 250 || b8 >= 250) accC[px]++
                        if (r8 + g8 + b8 <= 6) black++
                        x++
                    }
                }
            }
            val inv = 1.0 / (f * f)
            for (px in 0 until pw) {
                val i = py * pw + px
                img.r[i] = (accR[px] * inv).toFloat(); img.g[i] = (accG[px] * inv).toFloat(); img.b[i] = (accB[px] * inv).toFloat()
                clip[i] = (accC[px] * inv).toFloat()
            }
        }
        return Triple(img, clip, black.toFloat() / (pw.toLong() * ph * f * f))
    }

    inline fun forEachInBox(f: FaceBox, pw: Int, ph: Int, scale: Float, action: (Int) -> Unit) {
        val hw = f.w * scale / 2; val hh = f.h * scale / 2
        val x0 = ((f.cx - hw) * pw).toInt().coerceIn(0, pw - 1); val x1 = ((f.cx + hw) * pw).toInt().coerceIn(0, pw - 1)
        val y0 = ((f.cy - hh) * ph).toInt().coerceIn(0, ph - 1); val y1 = ((f.cy + hh) * ph).toInt().coerceIn(0, ph - 1)
        for (y in y0..y1) for (x in x0..x1) action(y * pw + x)
    }

    /** Skin likelihood from OKLab hue/chroma/lightness. Skin hue is consistent (~40-75 deg) across skin tones. */
    fun skinLikelihood(hueDeg: Float, chroma: Float, logLum: Float): Float {
        val hw = 1f - ColorMath.smoothstep(14f, 30f, abs(hueDeg - 56f))
        val cw = ColorMath.smoothstep(0.015f, 0.035f, chroma) * (1f - ColorMath.smoothstep(0.17f, 0.24f, chroma))
        val lw = ColorMath.smoothstep(-11f, -8.5f, logLum) * (1f - ColorMath.smoothstep(-0.3f, 0f, logLum))
        return hw * cw * lw
    }

    private fun measureFace(f: FaceBox, pw: Int, ph: Int, logLum: FloatArray, baseLog: FloatArray,
                            chroma: FloatArray, hue: FloatArray): FaceStats? {
        // face core: central ellipse avoiding hair/background
        val cx = f.cx * pw; val cy = (f.cy + 0.05f * f.h) * ph
        val rx = 0.33f * f.w * pw; val ry = 0.38f * f.h * ph
        if (rx < 1.5f || ry < 1.5f) return null
        val lums = ArrayList<Float>(); val bases = ArrayList<Float>()
        var sa = 0.0; var sb = 0.0; var sw = 0.0; var total = 0
        for (y in (cy - ry).toInt()..(cy + ry).toInt()) for (x in (cx - rx).toInt()..(cx + rx).toInt()) {
            if (x !in 0 until pw || y !in 0 until ph) continue
            val dx = (x - cx) / rx; val dy = (y - cy) / ry
            if (dx * dx + dy * dy > 1f) continue
            total++
            val i = y * pw + x
            val s = skinLikelihood(hue[i], chroma[i], logLum[i])
            bases.add(baseLog[i])
            if (s > 0.3f) {
                lums.add(logLum[i])
                val hr = Math.toRadians(hue[i].toDouble())
                sa += kotlin.math.cos(hr) * chroma[i] * s; sb += kotlin.math.sin(hr) * chroma[i] * s; sw += s
            }
        }
        if (total == 0) return null
        val skinFrac = lums.size.toFloat() / total
        // fall back to all core pixels if skin classification failed (e.g. strong cast, mono)
        val useL = if (lums.size >= max(4, total / 8)) lums else run {
            val all = ArrayList<Float>()
            for (y in (cy - ry).toInt()..(cy + ry).toInt()) for (x in (cx - rx).toInt()..(cx + rx).toInt())
                if (x in 0 until pw && y in 0 until ph) all.add(logLum[y * pw + x])
            all
        }
        useL.sort(); bases.sort()
        val hueDeg = if (sw > 0) ColorMath.hueDeg((sa / sw).toFloat(), (sb / sw).toFloat()) else 56f
        val chr = if (sw > 0) hypot(sa / sw, sb / sw).toFloat() else 0f
        return FaceStats(f, useL[useL.size / 2], bases[bases.size / 2], hueDeg, chr, skinFrac)
    }

    private fun scoreScene(pw: Int, ph: Int, logLum: FloatArray, base: FloatArray, chroma: FloatArray, hue: FloatArray,
                           rawChroma: FloatArray, rawHue: FloatArray,
                           faces: List<FaceStats>, illum: IlluminantEstimate, noise: Float, clip: FloatArray): SceneScores {
        val n = pw * ph
        val sorted = logLum.copyOf().also { it.sort() }
        val key = run { var s = 0.0; var c = 0; for (i in 0 until n) { if (logLum[i] < sorted[(n * 0.99).toInt()]) { s += logLum[i]; c++ } }; (s / max(1, c)).toFloat() }
        val p995 = sorted[min(n - 1, (n * 0.995).toInt())]
        val median = sorted[n / 2]

        // faces
        val faceArea = faces.sumOf { (it.box.w * it.box.h).toDouble() }.toFloat()
        val largest = faces.maxOfOrNull { it.box.w * it.box.h } ?: 0f
        val portrait = ColorMath.smoothstep(0.003f, 0.04f, largest).coerceAtLeast(ColorMath.smoothstep(0.004f, 0.06f, faceArea) * 0.8f)
        val group = if (faces.size >= 3) 1f else if (faces.size == 2) 0.5f else 0f

        // Sky is blue/cyan evidence in the upper frame. Water uses similar hues lower in the
        // frame, but excludes very bright pixels so snow, ice and cloud reflections do not vote.
        var skyC = 0; var waterC = 0; var waterArea = 0; var iceC = 0
        var sunC = 0; var folC = 0; var upper = 0; var warmAll = 0
        for (y in 0 until ph) for (x in 0 until pw) {
            val i = y * pw + x
            val h = hue[i]; val c = chroma[i]; val l = logLum[i]
            if (y < ph * 0.5f) {
                upper++
                if (h in 190f..285f && c > 0.014f && l > -4.5f) skyC++
                val rh = rawHue[i]; val rc = rawChroma[i]
                if (((rh in 15f..90f) || rh > 345f) && rc > 0.06f && l > -4.5f) sunC++
            }
            if (y > ph * 0.38f) {
                waterArea++
                if (h in 185f..265f && c > 0.014f && l > -6f && l < -0.35f) waterC++
                if (l > -1.65f && c < 0.035f) iceC++
            }
            val rh2 = rawHue[i]
            if (((rh2 in 15f..90f) || rh2 > 345f) && rawChroma[i] > 0.05f && l > -6f) warmAll++
            if (h in 105f..160f && c > 0.04f && l > -7f && l < -1f) folC++
        }
        val sky = ColorMath.smoothstep(0.025f, 0.24f, skyC.toFloat() / max(1, upper))
        val water = ColorMath.smoothstep(0.018f, 0.22f, waterC.toFloat() / max(1, waterArea))
        val ice = ColorMath.smoothstep(0.16f, 0.48f, iceC.toFloat() / max(1, waterArea))
        val foliage = ColorMath.smoothstep(0.012f, 0.24f, folC.toFloat() / n)
        val warmIllum = ColorMath.smoothstep(4800f, 3200f, illum.cctK)
        // A sunset/golden-hour sky is clearly brighter than the foreground; a uniform tungsten cast is not.
        val topMed = run { val v = FloatArray(pw * (ph / 3)) { logLum[it] }; v.sort(); v[v.size / 2] }
        val botMed = run { val off = pw * (ph - ph / 3); val v = FloatArray(pw * (ph / 3)) { logLum[off + it] }; v.sort(); v[v.size / 2] }
        val skyAbove = ColorMath.smoothstep(0.5f, 1.8f, topMed - botMed)
        val sunset = skyAbove * (if (faces.isNotEmpty()) 0.5f else 1f) * ColorMath.smoothstep(0.08f, 0.3f, sunC.toFloat() / max(1, upper)) *
            ColorMath.smoothstep(0.15f, 0.4f, warmAll.toFloat() / n) *
            ColorMath.smoothstep(-4f, -1.5f, p995) * (0.4f + 0.6f * warmIllum) * (1f - portrait * 0.5f)

        // backlighting: subject (faces or centre-bottom) much darker than the bright surround
        val subjLog = if (faces.isNotEmpty()) faces.map { it.baseLog }.sorted()[faces.size / 2] else {
            val v = ArrayList<Float>()
            for (y in (ph * 0.35f).toInt() until (ph * 0.9f).toInt()) for (x in (pw * 0.3f).toInt() until (pw * 0.7f).toInt()) v.add(base[y * pw + x])
            v.sort(); if (v.isEmpty()) median else v[v.size / 2]
        }
        val brightSurround = sorted[(n * 0.85).toInt()]
        val backlit = ColorMath.smoothstep(2.0f, 4.0f, brightSurround - subjLog) * ColorMath.smoothstep(-4.5f, -2.5f, brightSurround)

        val night = ColorMath.smoothstep(-5.0f, -7.5f, key) * (0.5f + 0.5f * ColorMath.smoothstep(-3f, -1f, p995))
        val lowLight = ColorMath.smoothstep(-5.0f, -7.5f, key).coerceAtLeast(ColorMath.smoothstep(4f, 9f, noise) * 0.8f)
        val indoor = warmIllum * (1f - sky) * (1f - sunset) * ColorMath.smoothstep(-1.5f, -4f, p995 - 0.5f).coerceAtLeast(0.5f)
        val landscape = (1f - portrait) * max(max(sky, water), foliage).coerceAtLeast(sunset)
        val highKey = ColorMath.smoothstep(-2.3f, -1.4f, median)
        return SceneScores(portrait, group, backlit, night, lowLight, sunset, landscape, indoor, highKey, ice, sky, water, foliage)
    }
}

/**
 * Noise estimation at FULL resolution (downsampling hides noise). Samples a grid of tiles and
 * applies Immerkaer's fast Laplacian-difference estimator (1996) to sRGB-encoded luma, ignoring
 * edge pixels. The low percentile across tiles is used because textured tiles overestimate.
 * Result is sigma in 8-bit code values.
 */
object NoiseEstimator {
    fun estimate(src: PixelSource): Pair<Float, Float> {
        val ts = 96
        if (src.width < ts + 4 || src.height < ts + 4) return Pair(1f, 1f)
        val tiles = 7
        val buf = IntArray(ts * ts)
        val lum = FloatArray(ts * ts); val cr = FloatArray(ts * ts); val cb = FloatArray(ts * ts)
        val sig = ArrayList<Float>(); val csig = ArrayList<Float>()
        for (ty in 0 until tiles) for (tx in 0 until tiles) {
            val x0 = ((src.width - ts) * (tx + 0.5f) / tiles).toInt()
            val y0 = ((src.height - ts) * (ty + 0.5f) / tiles).toInt()
            src.read(x0, y0, ts, ts, buf)
            var mean = 0f; var clipped = 0
            for (i in 0 until ts * ts) {
                val c = buf[i]
                val r = ((c shr 16) and 255).toFloat(); val g = ((c shr 8) and 255).toFloat(); val b = (c and 255).toFloat()
                lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
                cr[i] = r - g; cb[i] = b - g
                mean += lum[i]
                if (r >= 250 || g >= 250 || b >= 250 || lum[i] < 4) clipped++
            }
            mean /= ts * ts
            if (clipped > ts * ts / 10 || mean < 8 || mean > 240) continue
            sig.add(immerkaer(lum, ts)); csig.add((immerkaer(cr, ts) + immerkaer(cb, ts)) / 2)
        }
        if (sig.isEmpty()) return Pair(1f, 1f)
        sig.sort(); csig.sort()
        return Pair(sig[(sig.size * 0.3f).toInt()], csig[(csig.size * 0.3f).toInt()])
    }

    private fun immerkaer(p: FloatArray, s: Int): Float {
        // gradient threshold to exclude edges: use 90th percentile of sobel magnitude
        val grads = FloatArray((s - 2) * (s - 2))
        var k = 0
        for (y in 1 until s - 1) for (x in 1 until s - 1) {
            val i = y * s + x
            val gx = p[i - s + 1] + 2 * p[i + 1] + p[i + s + 1] - p[i - s - 1] - 2 * p[i - 1] - p[i + s - 1]
            val gy = p[i + s - 1] + 2 * p[i + s] + p[i + s + 1] - p[i - s - 1] - 2 * p[i - s] - p[i - s + 1]
            grads[k++] = abs(gx) + abs(gy)
        }
        val sortedG = grads.copyOf().also { it.sort() }
        val thr = sortedG[(sortedG.size * 0.6f).toInt()]
        var sum = 0.0; var cnt = 0; k = 0
        for (y in 1 until s - 1) for (x in 1 until s - 1) {
            val i = y * s + x
            if (grads[k++] > thr) continue
            val v = p[i - s - 1] - 2 * p[i - s] + p[i - s + 1] - 2 * p[i - 1] + 4 * p[i] - 2 * p[i + 1] + p[i + s - 1] - 2 * p[i + s] + p[i + s + 1]
            sum += abs(v); cnt++
        }
        if (cnt == 0) return 0f
        // Immerkaer: sigma = sqrt(pi/2) * mean|N*I| / 6. Restricting to low-gradient pixels biases
        // low; the 1.25 factor is an empirical correction measured on synthetic noise.
        return (sqrt(Math.PI / 2) * sum / cnt / 6.0 * 1.25).toFloat()
    }
}
