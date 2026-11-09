package com.smartphotomagic.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Colour-space primitives. All processing is done in linear-light sRGB/Rec.709 primaries
 * with float precision; 8-bit values only exist at decode and final encode.
 */
object ColorMath {
    /** 8-bit sRGB code value -> linear light. */
    val SRGB8_TO_LINEAR = FloatArray(256) { srgbToLinear(it / 255f) }

    fun srgbToLinear(v: Float): Float =
        if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    fun linearToSrgb(v: Float): Float =
        if (v <= 0.0031308f) v * 12.92f else (1.055 * v.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()

    // Encode LUT indexed on sqrt(linear) so dark values get fine resolution.
    private const val ENC_N = 4096
    private val ENC_LUT = FloatArray(ENC_N + 2) { i ->
        val t = min(i, ENC_N) / ENC_N.toFloat()
        linearToSrgb(t * t)
    }

    /** linear [0,1] -> sRGB encoded [0,1] (fast, ~1e-5 accurate). */
    fun encode(v: Float): Float {
        if (v <= 0f) return 0f
        if (v >= 1f) return 1f
        val t = sqrt(v) * ENC_N
        val i = t.toInt()
        val f = t - i
        val a = ENC_LUT[i]
        return a + (ENC_LUT[i + 1] - a) * f
    }

    fun luminance(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

    /** Fast cube root for x >= 0 (2 Newton steps after bit-hack seed, rel. err ~1e-7). */
    fun cbrt(x: Float): Float {
        if (x <= 0f) return 0f
        var y = java.lang.Float.intBitsToFloat(java.lang.Float.floatToRawIntBits(x) / 3 + 709958130)
        y = y - (y * y * y - x) / (3f * y * y)
        y = y - (y * y * y - x) / (3f * y * y)
        y = y - (y * y * y - x) / (3f * y * y)
        return y
    }

    /** Linear sRGB -> OKLab (Ottosson 2020). Writes L,a,b into out[o..o+2]. */
    fun linearToOklab(r: Float, g: Float, b: Float, out: FloatArray, o: Int = 0) {
        val l = cbrt(max(0f, 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b))
        val m = cbrt(max(0f, 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b))
        val s = cbrt(max(0f, 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b))
        out[o] = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        out[o + 1] = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        out[o + 2] = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    }

    /** OKLab -> linear sRGB (may be out of gamut). */
    fun oklabToLinear(L: Float, a: Float, b: Float, out: FloatArray, o: Int = 0) {
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_
        out[o] = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        out[o + 1] = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        out[o + 2] = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
    }

    fun hueDeg(a: Float, b: Float): Float {
        val h = Math.toDegrees(atan2(b.toDouble(), a.toDouble())).toFloat()
        return if (h < 0f) h + 360f else h
    }

    /**
     * Luminance-preserving gamut compression in linear RGB: pixels that leave [0,1] are
     * pulled toward the neutral of equal luminance (hue is approximately preserved).
     * A soft knee starts at [knee] so saturated highlights do not hit a hard wall.
     */
    fun gamutMap(rgb: FloatArray, o: Int = 0, knee: Float = 0.96f) {
        var r = rgb[o]; var g = rgb[o + 1]; var b = rgb[o + 2]
        val y = min(1f, max(0f, luminance(r, g, b)))
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        var t = 1f
        if (mx > knee && mx > y + 1e-6f) {
            // soft target: values above knee are compressed asymptotically to 1
            val over = mx - knee
            val range = 1f - knee
            val target = knee + range * (over / (over + range))
            t = min(t, max(0f, (target - y) / (mx - y)))
        }
        if (mn < 0f && y > mn + 1e-6f) {
            t = min(t, y / (y - mn))
        }
        if (t < 1f) {
            r = y + (r - y) * t; g = y + (g - y) * t; b = y + (b - y) * t
        }
        rgb[o] = min(1f, max(0f, r)); rgb[o + 1] = min(1f, max(0f, g)); rgb[o + 2] = min(1f, max(0f, b))
    }

    // ---------- Chromatic adaptation (Bradford) ----------
    private val RGB2XYZ = floatArrayOf(
        0.4124564f, 0.3575761f, 0.1804375f,
        0.2126729f, 0.7151522f, 0.0721750f,
        0.0193339f, 0.1191920f, 0.9503041f
    )
    private val XYZ2RGB = invert3(RGB2XYZ)
    private val BRADFORD = floatArrayOf(
        0.8951f, 0.2664f, -0.1614f,
        -0.7502f, 1.7135f, 0.0367f,
        0.0389f, -0.0685f, 1.0296f
    )
    private val BRADFORD_INV = invert3(BRADFORD)

    /**
     * Linear-RGB 3x3 matrix adapting colours seen under illuminant [src] (linear RGB of a white
     * surface) to appear as under [dst]. Luminance of white is preserved.
     */
    fun adaptationMatrix(src: FloatArray, dst: FloatArray): FloatArray {
        val sx = mul3v(RGB2XYZ, src); val dx = mul3v(RGB2XYZ, dst)
        val sc = mul3v(BRADFORD, sx); val dc = mul3v(BRADFORD, dx)
        val scaleY = sx[1] / max(1e-6f, dx[1])
        val diag = floatArrayOf(
            dc[0] / sc[0] * scaleY, 0f, 0f,
            0f, dc[1] / sc[1] * scaleY, 0f,
            0f, 0f, dc[2] / sc[2] * scaleY
        )
        return mul33(XYZ2RGB, mul33(BRADFORD_INV, mul33(diag, mul33(BRADFORD, RGB2XYZ))))
    }

    /** CIE xy chromaticity -> linear sRGB white (normalised so Y = 1). */
    fun xyToLinearRgb(x: Float, y: Float): FloatArray {
        val X = x / y; val Z = (1f - x - y) / y
        return mul3v(XYZ2RGB, floatArrayOf(X, 1f, Z))
    }

    /** Planckian-locus chromaticity (Kim et al. 2002 cubic approximation), 1667K..25000K. */
    fun planckianXy(tK: Float): FloatArray {
        val t = tK.toDouble().coerceIn(1667.0, 25000.0)
        val x = if (t <= 4000) -0.2661239e9 / (t * t * t) - 0.2343589e6 / (t * t) + 0.8776956e3 / t + 0.179910
        else -3.0258469e9 / (t * t * t) + 2.1070379e6 / (t * t) + 0.2226347e3 / t + 0.240390
        val y = when {
            t <= 2222 -> -1.1063814 * x * x * x - 1.34811020 * x * x + 2.18555832 * x - 0.20219683
            t <= 4000 -> -0.9549476 * x * x * x - 1.37418593 * x * x + 2.09137015 * x - 0.16748867
            else -> 3.0817580 * x * x * x - 5.87338670 * x * x + 3.75112997 * x - 0.37001483
        }
        return floatArrayOf(x.toFloat(), y.toFloat())
    }

    /**
     * Chromaticity of a "white" at CCT: blackbody below 4000K, CIE daylight (D-series) above 5000K,
     * blended in between. Using the daylight locus keeps D65 exactly neutral (the blackbody at
     * 6500K is slightly magenta relative to D65).
     */
    fun whiteXy(tK: Float): FloatArray {
        val p = planckianXy(tK)
        if (tK <= 4000f) return p
        val t = tK.toDouble().coerceIn(4000.0, 25000.0)
        val xd = if (t <= 7000) -4.6070e9 / (t * t * t) + 2.9678e6 / (t * t) + 0.09911e3 / t + 0.244063
        else -2.0064e9 / (t * t * t) + 1.9018e6 / (t * t) + 0.24748e3 / t + 0.237040
        val yd = -3.0 * xd * xd + 2.870 * xd - 0.275
        val w = smoothstep(4000f, 5000f, tK)
        return floatArrayOf(lerp(p[0], xd.toFloat(), w), lerp(p[1], yd.toFloat(), w))
    }

    fun mul3v(m: FloatArray, v: FloatArray): FloatArray = floatArrayOf(
        m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
        m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
        m[6] * v[0] + m[7] * v[1] + m[8] * v[2]
    )

    fun mul33(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var s = 0f
            for (k in 0 until 3) s += a[i * 3 + k] * b[k * 3 + j]
            r[i * 3 + j] = s
        }
        return r
    }

    fun invert3(m: FloatArray): FloatArray {
        val a = m[0].toDouble(); val b = m[1].toDouble(); val c = m[2].toDouble()
        val d = m[3].toDouble(); val e = m[4].toDouble(); val f = m[5].toDouble()
        val g = m[6].toDouble(); val h = m[7].toDouble(); val i = m[8].toDouble()
        val A = e * i - f * h; val B = -(d * i - f * g); val C = d * h - e * g
        val det = a * A + b * B + c * C
        return floatArrayOf(
            (A / det).toFloat(), (-(b * i - c * h) / det).toFloat(), ((b * f - c * e) / det).toFloat(),
            (B / det).toFloat(), ((a * i - c * g) / det).toFloat(), (-(a * f - c * d) / det).toFloat(),
            (C / det).toFloat(), (-(a * h - b * g) / det).toFloat(), ((a * e - b * d) / det).toFloat()
        )
    }

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Rotate OKLab (a,b) by hue delta in degrees. */
    fun rotate(a: Float, b: Float, deg: Float, out: FloatArray, o: Int) {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        out[o] = a * c - b * s; out[o + 1] = a * s + b * c
    }
}
