package com.restorex.app.domain.smartmagic

import kotlin.math.cbrt
import kotlin.math.pow

object SmartMagicColorMath {
    fun srgbToLinear(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f)
    }

    fun linearToSrgb(value: Float): Float {
        val v = value.coerceAtLeast(0f)
        return if (v <= .0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - .055f
    }

    fun luminance(red: Float, green: Float, blue: Float): Float =
        .2126f * red + .7152f * green + .0722f * blue

    /** Linear sRGB to OKLab. Values outside the display gamut are intentionally retained. */
    fun linearRgbToOklab(red: Float, green: Float, blue: Float): FloatArray {
        return FloatArray(3).also { linearRgbToOklab(red, green, blue, it) }
    }

    fun linearRgbToOklab(red: Float, green: Float, blue: Float, output: FloatArray) {
        require(output.size >= 3)
        val l = .41222147f * red + .53633255f * green + .05144599f * blue
        val m = .2119035f * red + .6806995f * green + .10739696f * blue
        val s = .08830246f * red + .28171885f * green + .6299787f * blue
        val lRoot = cbrt(l.toDouble()).toFloat()
        val mRoot = cbrt(m.toDouble()).toFloat()
        val sRoot = cbrt(s.toDouble()).toFloat()
        output[0] = .21045426f * lRoot + .7936178f * mRoot - .00407205f * sRoot
        output[1] = 1.9779985f * lRoot - 2.4285922f * mRoot + .4505937f * sRoot
        output[2] = .02590404f * lRoot + .78277177f * mRoot - .80867577f * sRoot
    }

    fun oklabToLinearRgb(lightness: Float, a: Float, b: Float): FloatArray {
        return FloatArray(3).also { oklabToLinearRgb(lightness, a, b, it) }
    }

    fun oklabToLinearRgb(lightness: Float, a: Float, b: Float, output: FloatArray) {
        require(output.size >= 3)
        val lRoot = lightness + .39633778f * a + .21580376f * b
        val mRoot = lightness - .10556135f * a - .06385417f * b
        val sRoot = lightness - .08948418f * a - 1.2914855f * b
        val l = lRoot * lRoot * lRoot
        val m = mRoot * mRoot * mRoot
        val s = sRoot * sRoot * sRoot
        output[0] = 4.0767417f * l - 3.3077116f * m + .23096994f * s
        output[1] = -1.268438f * l + 2.6097574f * m - .3413194f * s
        output[2] = -.00419609f * l - .7034186f * m + 1.7076147f * s
    }
}
