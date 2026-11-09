package com.smartphotomagic.engine

import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class IlluminantEstimate(
    /** Linear RGB of a white surface under the scene illuminant, normalised to luminance 1. */
    val rgb: FloatArray,
    /** Correlated colour temperature on the Planckian locus closest to the estimate (K). */
    val cctK: Float,
    /** Residual green(+)/magenta(-) tint after projection onto the locus, in log-ratio units. */
    val tint: Float,
    /** 0..1 reliability of the estimate. */
    val confidence: Float,
    /** Angle (deg) between the estimate and neutral – the size of the cast. */
    val castDeg: Float,
    val grayPixelFraction: Float,
    /** 0..1 evidence of two different illuminants (bright vs dark regions disagree). */
    val mixedLight: Float = 0f,
)

/**
 * Automatic illuminant estimation.
 *
 * Strategy (all in linear light on the analysis proxy):
 *  1. Seed with Shades-of-Grey (Minkowski p=6, Finlayson & Trezzi 2004) and Grey-Edge
 *     (van de Weijer et al. 2007) estimates on unclipped, non-skin, mid-tone pixels.
 *  2. Iteratively re-estimate from "near-grey" pixels only (pixels whose chromaticity, after
 *     dividing by the current estimate, is within a shrinking angle of neutral). Strongly
 *     coloured objects (foliage, sky, walls) therefore stop voting for the illuminant.
 *  3. Project the result onto the Planckian locus: real illuminants lie close to it, so a large
 *     green/magenta residual is mostly scene content and is only partially trusted.
 *  4. Confidence from estimator agreement and the amount of grey evidence.
 */
object WhiteBalance {

    fun estimate(img: LinearImage, clip: FloatArray, exclude: BooleanArray?): IlluminantEstimate {
        val n = img.size
        val lum = FloatArray(n) { ColorMath.luminance(img.r[it], img.g[it], img.b[it]) }
        val sorted = lum.copyOf().also { it.sort() }
        val lo = sorted[(n * 0.03).toInt()].coerceAtLeast(0.002f)
        val hi = sorted[min(n - 1, (n * 0.985).toInt())]
        val cand = BooleanArray(n) { i ->
            val y = lum[i]
            y >= lo && y <= hi && clip[i] < 0.02f &&
                img.r[i] > 0.0015f && img.g[i] > 0.0015f && img.b[i] > 0.0015f &&
                (exclude == null || !exclude[i])
        }
        var count = cand.count { it }
        if (count < n * 0.05) { // too restrictive – fall back to all unclipped pixels
            for (i in 0 until n) cand[i] = clip[i] < 0.02f && lum[i] > 0.001f
            count = cand.count { it }
        }
        if (count < 50) return IlluminantEstimate(floatArrayOf(1f, 1f, 1f), 6500f, 0f, 0f, 0f, 0f)

        // Shades of grey p = 6
        val p = 6.0
        var sr = 0.0; var sg = 0.0; var sb = 0.0
        for (i in 0 until n) if (cand[i]) {
            sr += img.r[i].toDouble().pow(p); sg += img.g[i].toDouble().pow(p); sb += img.b[i].toDouble().pow(p)
        }
        val sog = norm(floatArrayOf(sr.pow(1 / p).toFloat(), sg.pow(1 / p).toFloat(), sb.pow(1 / p).toFloat()))

        // Grey edge (1st order, p = 6)
        val w = img.width; val h = img.height
        var er = 0.0; var eg = 0.0; var eb = 0.0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (!cand[i]) continue
            val gr = abs(img.r[i + 1] - img.r[i - 1]) + abs(img.r[i + w] - img.r[i - w])
            val gg = abs(img.g[i + 1] - img.g[i - 1]) + abs(img.g[i + w] - img.g[i - w])
            val gb = abs(img.b[i + 1] - img.b[i - 1]) + abs(img.b[i + w] - img.b[i - w])
            er += gr.toDouble().pow(p); eg += gg.toDouble().pow(p); eb += gb.toDouble().pow(p)
        }
        val edge = if (eg > 0) norm(floatArrayOf(er.pow(1 / p).toFloat(), eg.pow(1 / p).toFloat(), eb.pow(1 / p).toFloat())) else sog

        // Iterative grey-pixel refinement
        var est = norm(geoMean(sog, edge))
        var grayFrac = 0f
        for (thr in floatArrayOf(14f, 9f, 6f)) {
            val cosThr = kotlin.math.cos(Math.toRadians(thr.toDouble())).toFloat()
            var ar = 0.0; var ag = 0.0; var ab = 0.0; var c = 0
            for (i in 0 until n) if (cand[i]) {
                val r = img.r[i] / est[0]; val g = img.g[i] / est[1]; val b = img.b[i] / est[2]
                val cosA = (r + g + b) / (sqrt(3f) * sqrt(r * r + g * g + b * b) + 1e-9f)
                if (cosA >= cosThr) {
                    val wt = sqrt(lum[i]).toDouble()
                    ar += img.r[i] * wt; ag += img.g[i] * wt; ab += img.b[i] * wt; c++
                }
            }
            grayFrac = c.toFloat() / count
            if (c < max(30, count / 200)) break
            est = norm(floatArrayOf(ar.toFloat(), ag.toFloat(), ab.toFloat()))
        }
        val gray = est
        // Blend: grey pixels are the most reliable when there are enough of them.
        val wGray = ColorMath.smoothstep(0.01f, 0.12f, grayFrac)
        val combined = norm(geoMeanW(gray, geoMean(sog, edge), wGray))

        // Agreement between independent estimators.
        val spread = max(angleDeg(sog, edge), max(angleDeg(sog, gray), angleDeg(edge, gray)))
        var conf = 1f - ColorMath.smoothstep(4f, 14f, spread)
        conf *= 0.35f + 0.65f * ColorMath.smoothstep(0.005f, 0.08f, grayFrac)
        conf = conf.coerceIn(0f, 1f)

        // Mixed lighting: near-grey pixels in the bright third vs the dark third of the image.
        val candLum = ArrayList<Float>()
        for (i in 0 until n) if (cand[i]) candLum.add(lum[i])
        candLum.sort()
        val t1 = candLum[candLum.size / 3]; val t2 = candLum[candLum.size * 2 / 3]
        val cosM = kotlin.math.cos(Math.toRadians(15.0)).toFloat()
        val lowE = DoubleArray(3); val hiE = DoubleArray(3); var nl = 0; var nh = 0
        for (i in 0 until n) if (cand[i]) {
            val r = img.r[i] / combined[0]; val g = img.g[i] / combined[1]; val b = img.b[i] / combined[2]
            val cosA = (r + g + b) / (sqrt(3f) * sqrt(r * r + g * g + b * b) + 1e-9f)
            if (cosA < cosM) continue
            val tgt = if (lum[i] <= t1) lowE else if (lum[i] >= t2) hiE else continue
            tgt[0] += (img.r[i] / lum[i]).toDouble(); tgt[1] += (img.g[i] / lum[i]).toDouble(); tgt[2] += (img.b[i] / lum[i]).toDouble()
            if (lum[i] <= t1) nl++ else nh++
        }
        val mixed = if (nl > 50 && nh > 50) {
            val a = floatArrayOf(lowE[0].toFloat(), lowE[1].toFloat(), lowE[2].toFloat())
            val b = floatArrayOf(hiE[0].toFloat(), hiE[1].toFloat(), hiE[2].toFloat())
            ColorMath.smoothstep(5f, 14f, angleDeg(a, b))
        } else 0f

        // Planckian projection.
        val (tK, tintResidual) = projectToLocus(combined)
        val tintKept = (tintResidual * 0.4f).coerceIn(-0.045f, 0.045f)
        val projected = fromLocus(tK, tintKept)
        return IlluminantEstimate(projected, tK, tintKept, conf, angleDeg(projected, floatArrayOf(1f, 1f, 1f)), grayFrac, mixed)
    }

    // ---- locus helpers (log-ratio space: temp = ln(b/r), tint = ln g - (ln r + ln b)/2) ----
    private val locusT = FloatArray(117) { 2000f + it * 100f } // 2000K .. 13600K (below ~1950K the locus leaves the sRGB gamut)
    private val locusTemp = FloatArray(locusT.size)
    private val locusTint = FloatArray(locusT.size)

    init {
        for (i in locusT.indices) {
            val xy = ColorMath.whiteXy(locusT[i])
            val rgb = ColorMath.xyToLinearRgb(xy[0], xy[1])
            locusTemp[i] = ln(rgb[2] / rgb[0])
            locusTint[i] = ln(rgb[1]) - 0.5f * (ln(rgb[0]) + ln(rgb[2]))
        }
    }

    fun tempOf(rgb: FloatArray) = ln(max(1e-6f, rgb[2]) / max(1e-6f, rgb[0]))
    fun tintOf(rgb: FloatArray) = ln(max(1e-6f, rgb[1])) - 0.5f * (ln(max(1e-6f, rgb[0])) + ln(max(1e-6f, rgb[2])))

    /** Returns (CCT, tint residual relative to locus). */
    fun projectToLocus(rgb: FloatArray): Pair<Float, Float> {
        val t = tempOf(rgb)
        // temp is monotonic increasing with CCT
        var i = 0
        while (i < locusT.size - 2 && locusTemp[i + 1] < t) i++
        val f = ((t - locusTemp[i]) / (locusTemp[i + 1] - locusTemp[i])).coerceIn(0f, 1f)
        val tK = locusT[i] + f * (locusT[i + 1] - locusT[i])
        val tintL = locusTint[i] + f * (locusTint[i + 1] - locusTint[i])
        return Pair(tK.coerceIn(locusT.first(), locusT.last()), tintOf(rgb) - tintL)
    }

    /** White point on the locus at [tK] plus a tint offset, as linear RGB with luminance 1. */
    fun fromLocus(tK: Float, tint: Float): FloatArray {
        val xy = ColorMath.whiteXy(tK)
        val rgb = ColorMath.xyToLinearRgb(xy[0], xy[1])
        // apply tint offset: raise/lower green relative to r,b
        rgb[1] *= exp(tint)
        return normLum(rgb)
    }

    fun normLum(v: FloatArray): FloatArray {
        val y = ColorMath.luminance(v[0], v[1], v[2])
        return floatArrayOf(v[0] / y, v[1] / y, v[2] / y)
    }

    private fun norm(v: FloatArray): FloatArray = normLum(floatArrayOf(max(v[0], 1e-6f), max(v[1], 1e-6f), max(v[2], 1e-6f)))
    private fun geoMean(a: FloatArray, b: FloatArray) = FloatArray(3) { sqrt(a[it] * b[it]) }
    private fun geoMeanW(a: FloatArray, b: FloatArray, wa: Float) = FloatArray(3) { exp(wa * ln(a[it]) + (1 - wa) * ln(b[it])) }
    fun angleDeg(a: FloatArray, b: FloatArray): Float {
        val d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val na = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); val nb = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
        return Math.toDegrees(acos((d / (na * nb)).coerceIn(-1f, 1f).toDouble())).toFloat()
    }
}
