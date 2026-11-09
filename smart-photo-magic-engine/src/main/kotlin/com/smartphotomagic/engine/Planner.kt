package com.smartphotomagic.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * The three looks are ONE strategy with different calibration constants. Every adjustment is
 * computed from the scene measurements and then scaled by these factors, so a correctly exposed,
 * neutral photo receives small corrections in every mode.
 */
data class ModeProfile(
    val wbAdapt: Float, val wbDeadbandDeg: Float, val targetCct: Float,
    val evStrength: Float, val evDeadband: Float, val keyTarget: Float,
    val shadow: Float, val shadowMax: Float, val highlight: Float, val highlightMax: Float,
    val faceStrength: Float, val faceMaxLift: Float, val faceFloor: Float,
    val clarity: Float, val contrastGain: Float, val minSd: Float, val hlAllowance: Float,
    val blackStretch: Float, val whiteStretch: Float,
    val vibrance: Float, val saturation: Float, val skyDeepen: Float, val waterDeepen: Float, val foliage: Float, val warmBoost: Float,
    val lumaNR: Float, val chromaNR: Float, val sharpen: Float, val skinHue: Float,
) {
    companion object {
        fun of(m: Mode) = when (m) {
            Mode.NATURAL -> ModeProfile(
                wbAdapt = 0.80f, wbDeadbandDeg = 1.5f, targetCct = 6100f,
                evStrength = 0.75f, evDeadband = 0.35f, keyTarget = -2.75f,
                shadow = 0.55f, shadowMax = 0.30f, highlight = 0.6f, highlightMax = 0.35f,
                faceStrength = 0.7f, faceMaxLift = 1.3f, faceFloor = -3.9f,
                clarity = 0.06f, contrastGain = 1.0f, minSd = 0.16f, hlAllowance = 0.5f,
                blackStretch = 0.6f, whiteStretch = 0.35f,
                vibrance = 0.10f, saturation = 0.0f, skyDeepen = 0.0f, waterDeepen = 0.0f, foliage = 0.0f, warmBoost = 0.0f,
                lumaNR = 0.55f, chromaNR = 0.85f, sharpen = 0.25f, skinHue = 0.5f,
            )
            Mode.ENHANCED -> ModeProfile(
                wbAdapt = 0.88f, wbDeadbandDeg = 1.0f, targetCct = 6200f,
                evStrength = 0.9f, evDeadband = 0.2f, keyTarget = -2.6f,
                shadow = 0.8f, shadowMax = 0.42f, highlight = 0.8f, highlightMax = 0.45f,
                faceStrength = 0.85f, faceMaxLift = 1.8f, faceFloor = -3.6f,
                clarity = 0.16f, contrastGain = 1.07f, minSd = 0.18f, hlAllowance = 0.8f,
                blackStretch = 0.8f, whiteStretch = 0.6f,
                vibrance = 0.24f, saturation = 0.04f, skyDeepen = 0.55f, waterDeepen = 0.35f, foliage = 0.10f, warmBoost = 0.06f,
                lumaNR = 0.7f, chromaNR = 1.0f, sharpen = 0.42f, skinHue = 0.7f,
            )
            Mode.EXTREME -> ModeProfile(
                wbAdapt = 0.88f, wbDeadbandDeg = 1.0f, targetCct = 6200f,
                evStrength = 1.0f, evDeadband = 0.1f, keyTarget = -2.5f,
                shadow = 1.05f, shadowMax = 0.55f, highlight = 1.0f, highlightMax = 0.55f,
                faceStrength = 1.0f, faceMaxLift = 2.2f, faceFloor = -3.4f,
                clarity = 0.30f, contrastGain = 1.14f, minSd = 0.20f, hlAllowance = 1.1f,
                blackStretch = 0.9f, whiteStretch = 0.75f,
                vibrance = 0.38f, saturation = 0.08f, skyDeepen = 1.0f, waterDeepen = 1.0f, foliage = 0.28f, warmBoost = 0.12f,
                lumaNR = 0.8f, chromaNR = 1.0f, sharpen = 0.58f, skinHue = 0.8f,
            )
        }
    }
}

/** Uniformly sampled 1-D function on [x0, x1]. */
class Lut(val x0: Float, val x1: Float, val values: FloatArray) {
    private val scale = (values.size - 1) / (x1 - x0)
    fun eval(x: Float): Float {
        val t = ((x - x0) * scale).coerceIn(0f, (values.size - 1).toFloat())
        val i = min(t.toInt(), values.size - 2)
        val f = t - i
        return values[i] + (values[i + 1] - values[i]) * f
    }
    companion object {
        fun build(x0: Float, x1: Float, n: Int, fn: (Float) -> Float) = Lut(x0, x1, FloatArray(n) { fn(x0 + (x1 - x0) * it / (n - 1)) })
    }
}

class RenderPlan(
    val mode: Mode,
    val fullWidth: Int,
    val fullHeight: Int,
    val wb: FloatArray,
    val ev: Float,
    /** ch0 = base log2 luminance, ch1 = local (face) exposure offset in stops */
    val grid: BilateralGrid,
    val tone: Lut,
    val filmic: Lut,
    val clarity: Float,
    val vibrance: Float,
    val saturation: Float,
    val skinProtect: Float,
    val skinHuePull: Float,
    val sky: Float,
    val water: Float,
    val foliage: Float,
    val warm: Float,
    val shadowChromaReduce: Float,
    val faceProx: FloatArray, val faceProxW: Int, val faceProxH: Int,
    val hasFaces: Boolean,
    val noiseSigma8: Float,
    val chromaNoiseSigma8: Float,
    val lumaNR: Float,
    val chromaNR: Float,
    val sharpen: Float,
    val report: Map<String, Any>,
) {
    fun faceProxAt(u: Float, v: Float): Float {
        if (!hasFaces) return 0f
        val x = (u * (faceProxW - 1)).coerceIn(0f, faceProxW - 1f); val y = (v * (faceProxH - 1)).coerceIn(0f, faceProxH - 1f)
        val i = min(x.toInt(), faceProxW - 2); val j = min(y.toInt(), faceProxH - 2)
        val fx = x - i; val fy = y - j
        val a = faceProx[j * faceProxW + i]; val b = faceProx[j * faceProxW + i + 1]
        val c = faceProx[(j + 1) * faceProxW + i]; val d = faceProx[(j + 1) * faceProxW + i + 1]
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy
    }

    /** Log-domain tone mapping of one pixel: returns final display log2 luminance. Shared by planner & renderer. */
    fun toneLog(l: Float, base: Float, faceOffset: Float): Float {
        val b = base + ev + faceOffset
        val tb = tone.eval(b)
        val detail = l - base
        val gate = ColorMath.smoothstep(-9.5f, -6.5f, tb) // no clarity in deep (noisy) shadows
        // tiny deviations are noise / JPEG blocking in smooth areas (sky): do not amplify them
        val ad = if (detail < 0f) -detail else detail
        val lp = tb + detail * (1f + clarity * gate * ColorMath.smoothstep(0.03f, 0.12f, ad))
        return filmic.eval(lp)
    }
}

object Planner {
    private fun softplus(x: Float, w: Float): Float = if (x / w > 20f) x else w * kotlin.math.ln(1f + exp(x / w))

    /** Bounded shadow lift / highlight compression curve in log2 domain (monotonic, smooth). */
    fun toneCurve(b: Float, s0: Float, ks: Float, rs: Float, h0: Float, kh: Float, rh: Float): Float {
        val w = 0.8f
        val lift = ks * (softplus(s0 - b, w) - softplus(s0 - b - rs, w))
        val comp = kh * (softplus(b - h0, w) - softplus(b - h0 - rh, w))
        return b + lift - comp
    }

    fun plan(a: SceneAnalysis, mode: Mode): RenderPlan {
        val m = ModeProfile.of(mode)
        val s = a.scores
        val washedHighKey = s.highKey * (1f - ColorMath.smoothstep(0.155f, 0.205f, a.tonalSd))
        val report = LinkedHashMap<String, Any>()
        report["scene"] = s.describe()
        val pw = a.proxy.width; val ph = a.proxy.height; val n = pw * ph

        // ---------------- 1. White balance ----------------
        val ill = a.illuminant
        var adapt = m.wbAdapt * (0.3f + 0.7f * ill.confidence)
        adapt *= 1f - 0.95f * s.sunset                      // keep golden-hour warmth
        if (ill.cctK < 2700f) adapt *= 0.9f                  // candle / very warm ambience: keep some
        adapt *= 1f - 0.55f * ill.mixedLight
        // Viewers prefer warm renditions and warm light is usually part of the scene's mood, so warm
        // casts are only partly neutralised. With faces present the skin colour matters more.
        if (ill.cctK < m.targetCct) adapt *= ColorMath.lerp(1f, if (s.portrait > 0.4f) 0.92f else 0.72f, ColorMath.smoothstep(6000f, 4800f, ill.cctK))                 // two light sources: a global fix can only compromise
        adapt *= ColorMath.smoothstep(m.wbDeadbandDeg, m.wbDeadbandDeg + 3f, ill.castDeg)
        val miredEst = 1e6f / ill.cctK; val miredTarget = 1e6f / m.targetCct
        val cct = 1e6f / ColorMath.lerp(miredEst, miredTarget, adapt)
        val tint = ill.tint * (1f - 0.6f * adapt) // green/magenta residuals are often scene content (foliage)
        val targetWhite = WhiteBalance.fromLocus(cct, tint)
        val wb = ColorMath.adaptationMatrix(ill.rgb, targetWhite)
        report["illuminant_cct"] = ill.cctK.toInt(); report["wb_confidence"] = r2(ill.confidence)
        report["wb_adaptation"] = r2(adapt); report["mixed_light"] = r2(ill.mixedLight); report["output_cct"] = cct.toInt()

        // ---------------- 2. Global exposure ----------------
        val sortedL = a.logLum.copyOf().also { it.sort() }
        val pTop = sortedL[(n * 0.99f).toInt()]; val pBot = sortedL[(n * 0.005f).toInt()]
        var ks0 = 0.0; var kw = 0.0
        for (y in 0 until ph) for (x in 0 until pw) {
            val i = y * pw + x
            val l = a.logLum[i]
            if (l > pTop || l < pBot) continue
            val du = x / (pw - 1f) - 0.5f; val dv = y / (ph - 1f) - 0.55f
            val w = 0.5f + exp(-(du * du + dv * dv) / (2 * 0.28f * 0.28f))
            ks0 += l * w; kw += w
        }
        val key = (ks0 / max(kw, 1e-9)).toFloat()
        var target = m.keyTarget
        target = ColorMath.lerp(target, max(key, target), s.highKey * 0.8f) // do not darken high-key scenes
        target = ColorMath.lerp(target, min(target, key + 0.9f), s.night)  // night stays night
        val dev = target - key
        var ev = sign(dev) * max(0f, abs(dev) - m.evDeadband) * m.evStrength
        // Highlight headroom: exposure may not push the bright part of the scene (98.5th pct) far past
        // white. Beyond the allowance, local tone mapping (shadow lift) must do the work instead.
        val p985 = sortedL[(n * 0.985f).toInt()]
        val headroom = log2f(0.8f) - p985
        if (ev > 0f) ev = min(ev, max(0f, headroom + m.hlAllowance))
        ev = ev.coerceIn(-1.2f, 2.6f)
        report["headroom_stops"] = r2(headroom)
        report["key_log2"] = r2(key); report["ev"] = r2(ev)

        // ---------------- 3. Local tone curve (shadows / highlights) ----------------
        var dark = 0; var hi = 0
        val bSorted = FloatArray(n)
        for (i in 0 until n) {
            val b = a.baseLog[i] + ev
            bSorted[i] = b
            if (b < -5.5f) dark++
            if (b > -1.4f && a.clipMap[i] < 0.5f) hi++
        }
        bSorted.sort()
        val dr = bSorted[(n * 0.99f).toInt()] - bSorted[(n * 0.01f).toInt()]
        val darkFrac = dark.toFloat() / n; val hiFrac = hi.toFloat() / n
        var ks = ((0.06f + 0.9f * darkFrac + 0.05f * max(0f, dr - 7f)) * m.shadow).coerceIn(0f, m.shadowMax)
        // Shadows without recoverable texture (silhouettes, black backgrounds) stay dark.
        val detailF = 0.25f + 0.75f * ColorMath.smoothstep(0.8f, 3.5f, a.shadowDetail)
        ks = max(ks, s.backlit * m.shadowMax * 0.8f)
        ks *= detailF * (1f - 0.6f * s.night) * (1f - 0.6f * s.sunset)
        report["shadow_detail"] = r2(a.shadowDetail)
        val kh = ((0.06f + 0.9f * hiFrac + 0.05f * max(0f, dr - 7f)) * m.highlight).coerceIn(0f, m.highlightMax)
        val s0 = -4.6f; val rs = 4.5f; val h0 = -1.7f; val rh = 3f
        val tone = Lut.build(-16f, 6f, 1409) { toneCurve(it, s0, ks, rs, h0, kh, rh) }
        report["shadow_lift"] = r2(ks); report["highlight_compress"] = r2(kh); report["dynamic_range_stops"] = r2(dr)

        // ---------------- 4. Subject (face) local exposure ----------------
        val grid = a.baseGrid.copy()
        for (k in 0 until grid.nz) for (j in 0 until grid.ny) for (i in 0 until grid.nx) grid.data[grid.index(i, j, k) + 1] = 0f
        val bgBright = bSorted[(n * 0.8f).toInt()]
        // A high-key room can make a face look dim even when the face is not technically
        // under-exposed. The generic faceFloor values are deliberately conservative and, for
        // this case, allowed papa's face to receive only 0.04-0.08 stops: visually a no-op.
        // Raise only detected faces in high-key portraits; luminance affinity in the bilateral
        // grid prevents the lift from spilling onto the already-bright white clothing.
        val highKeyFaceFloor = when (mode) {
            Mode.NATURAL -> -1.45f
            Mode.ENHANCED -> -1.25f
            Mode.EXTREME -> -1.00f
        }
        val effectiveFaceFloor = ColorMath.lerp(m.faceFloor, highKeyFaceFloor, washedHighKey * s.portrait)
        report["face_target_floor"] = r2(effectiveFaceFloor)
        // In a group, use the upper-middle well-lit skin value as the local lighting reference.
        // A generous deadband avoids "correcting" natural differences in skin reflectance; this
        // path is reserved for a clearly under-lit person among otherwise well-lit faces.
        val skinPost = a.faces.map { tone.eval(it.logLum + ev) }
        val validSkin = a.faces.indices.filter { a.faces[it].skinFraction >= 0.08f }.map { skinPost[it] }.sorted()
        val validSkinFractions = a.faces.map { it.skinFraction }.filter { it >= 0.08f }.sorted()
        val groupSkinReference = if (validSkin.size >= 2) validSkin[(validSkin.size * 2 / 3).coerceAtMost(validSkin.lastIndex)] else null
        val groupSkinFractionReference = if (validSkinFractions.size >= 2) validSkinFractions[validSkinFractions.size / 2] else null
        val groupDeadband = when (mode) {
            Mode.NATURAL -> 0.55f
            Mode.ENHANCED -> 0.40f
            Mode.EXTREME -> 0.30f
        }
        val groupStrength = when (mode) {
            Mode.NATURAL -> 0.65f
            Mode.ENHANCED -> 0.85f
            Mode.EXTREME -> 1.00f
        }
        val groupMaxLift = when (mode) {
            Mode.NATURAL -> 0.70f
            Mode.ENHANCED -> 1.10f
            Mode.EXTREME -> 1.50f
        }
        val groupEvidence = a.faces.indices.map { fi ->
            if (groupSkinFractionReference == null) 0f else {
                val ratio = a.faces[fi].skinFraction / max(0.08f, groupSkinFractionReference)
                ColorMath.smoothstep(0.75f, 0.45f, ratio).let { if (it >= 0.35f) it else 0f }
            }
        }
        val groupNeeds = a.faces.indices.map { fi ->
            if (groupSkinReference == null || a.faces[fi].skinFraction < 0.08f) 0f
            else ((max(0f, groupSkinReference - skinPost[fi] - groupDeadband) * groupStrength).coerceAtMost(groupMaxLift) * groupEvidence[fi])
        }
        report["face_skin_log2"] = skinPost.map { r2(it) }
        report["face_base_log2"] = a.faces.map { r2(tone.eval(it.baseLog + ev)) }
        report["face_skin_chroma"] = a.faces.map { r3(it.skinChroma) }
        report["face_skin_fraction"] = a.faces.map { r2(it.skinFraction) }
        report["group_skin_reference"] = groupSkinReference?.let { r2(it) } ?: "none"
        report["group_underlight_evidence"] = groupEvidence.map { r2(it) }
        report["group_face_needs_stops"] = groupNeeds.map { r2(it) }
        val lifts = ArrayList<Float>()
        for ((fi, f) in a.faces.withIndex()) {
            val fb = f.baseLog + ev
            val post = tone.eval(fb)
            val lift1 = max(0f, (bgBright - post) - 1.3f) * 0.6f   // backlight deficit beyond natural fall-off
            val lift2 = max(0f, effectiveFaceFloor - post)         // scene-aware under-exposure floor
            var want = min(max(lift1, lift2), m.faceMaxLift)
            if (post > -0.9f) want = -min(1f, (post + 0.9f) * 0.7f)  // over-bright face (flash / sun)
            want *= m.faceStrength
            if (groupSkinReference != null && want > 0f) want *= groupEvidence[fi]
            want = max(want, groupNeeds[fi])
            // solve T(fb + x) = post + want
            var lo = -3f; var hiX = 6f
            repeat(40) { val mid = (lo + hiX) / 2; if (tone.eval(fb + mid) < post + want) lo = mid else hiX = mid }
            lifts.add((lo + hiX) / 2)
        }
        val faceProxW = 64; val faceProxH = max(8, (64f * ph / pw).toInt())
        val faceProx = FloatArray(faceProxW * faceProxH)
        if (a.faces.isNotEmpty()) {
            val aspect = pw.toFloat() / ph
            for (k in 0 until grid.nz) for (j in 0 until grid.ny) for (i in 0 until grid.nx) {
                val u = i / (grid.nx - 1f); val v = j / (grid.ny - 1f)
                val idx = grid.index(i, j, k)
                val cellBase = grid.data[idx]
                var sw = 0f; var sl = 0f
                for ((fi, f) in a.faces.withIndex()) {
                    val wsp = faceSpatial(f.box, u, v, aspect)
                    val d = cellBase - f.baseLog
                    val aff = exp(-d * d / (2f * 1.1f * 1.1f))
                    val w = wsp * aff
                    sw += w; sl += w * lifts[fi]
                }
                grid.data[idx + 1] = sl / max(1f, sw)
            }
            for (y in 0 until faceProxH) for (x in 0 until faceProxW) {
                var mx = 0f
                for (f in a.faces) mx = max(mx, faceSpatial(f.box, x / (faceProxW - 1f), y / (faceProxH - 1f), aspect))
                faceProx[y * faceProxW + x] = mx
            }
        }
        report["face_lifts_stops"] = lifts.map { r2(it) }

        // ---------------- 5. Clarity (large-scale local contrast) ----------------
        var lcm = 0.0
        for (i in 0 until n) lcm += abs(a.logLum[i] - a.baseLog[i])
        lcm /= n
        val clarity = m.clarity * (1.2f - 0.7f * ColorMath.smoothstep(0.25f, 0.7f, lcm.toFloat())) * (1f - 0.5f * s.lowLight)
        report["local_contrast"] = r2(lcm.toFloat()); report["clarity"] = r2(clarity)

        // ---------------- 6. Simulate on proxy -> global black/white/contrast ----------------
        val identityFilmic = Lut(-16f, 6f, floatArrayOf(-16f, 6f))
        val tmpPlan = RenderPlan(mode, a.fullWidth, a.fullHeight, wb, ev, grid, tone, identityFilmic, clarity, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            faceProx, faceProxW, faceProxH, a.faces.isNotEmpty(), 0f, 0f, 0f, 0f, 0f, emptyMap())
        val lp = FloatArray(n)
        val lpMax = FloatArray(n)
        val clippedVals = ArrayList<Float>()
        val sl = FloatArray(2)
        for (y in 0 until ph) for (x in 0 until pw) {
            val i = y * pw + x
            grid.slice(x / (pw - 1f), y / (ph - 1f), a.logLum[i], sl)
            lp[i] = tmpPlan.toneLog(a.logLum[i], sl[0], sl[1])
            // brightest channel relative to luminance: the white point must not push coloured
            // highlights (cream paint, skin, sunsets) into channel clipping, which would bleach them
            val r0 = a.proxy.r[i]; val g0 = a.proxy.g[i]; val b0 = a.proxy.b[i]
            val rr = wb[0] * r0 + wb[1] * g0 + wb[2] * b0; val gg = wb[3] * r0 + wb[4] * g0 + wb[5] * b0; val bb = wb[6] * r0 + wb[7] * g0 + wb[8] * b0
            val yy = max(1e-6f, ColorMath.luminance(rr, gg, bb))
            lpMax[i] = lp[i] + log2f(max(1f, max(rr, max(gg, bb)) / yy).coerceAtMost(4f))
            if (a.clipMap[i] > 0.5f) clippedVals.add(lpMax[i])
        }
        val lpS = lpMax.copyOf().also { it.sort() }
        val yLo = exp2f(lp.copyOf().also { it.sort() }[(n * 0.003f).toInt()])
        val yHi = exp2f(lpS[min(n - 1, (n * 0.997f).toInt())])
        val yMax = exp2f(lpS[min(n - 1, (n * 0.9995f).toInt())])
        val clipLevel = if (clippedVals.size > n / 1000) { clippedVals.sort(); exp2f(clippedVals[clippedVals.size / 4]) } else Float.MAX_VALUE

        val nightKeep = 1f - 0.8f * s.night
        // High-key is not the same as washed out. A finished snow/sky photograph can be bright
        // and still have strong tonal separation, while a veiled indoor frame has a high median
        // and a compressed tonal standard deviation. Gate the stronger finish on both signals.
        val recoveryBlackCap = when (mode) {
            Mode.NATURAL -> 0.022f
            Mode.ENHANCED -> 0.030f
            Mode.EXTREME -> 0.038f
        }
        val blackCap = ColorMath.lerp(0.015f, recoveryBlackCap, washedHighKey)
        val black = min(blackCap, yLo * m.blackStretch * (0.5f + 0.5f * nightKeep))
        report["washed_high_key"] = r2(washedHighKey)
        var white = when {
            // "auto whites": only partial, never for high-key scenes, and at most +0.5 stop
            yHi < 0.92f -> { val wt = ColorMath.lerp(1f, yHi / 0.92f, m.whiteStretch * nightKeep * (1f - 0.7f * s.highKey)); max(wt, 1f / 1.41f) }
            else -> ColorMath.lerp(1f, yHi / 0.95f, 0.4f)
        }
        white = min(white, clipLevel) // clipped highlights must reach white
        white = max(white, black * 4f + 0.05f)
        val wmax = max(1.05f, max(yMax, min(clipLevel, 8f)) / white).coerceAtMost(6f)

        // Global contrast: local tone compression flattens the image, so choose the S-curve that
        // restores (Natural) or moderately raises (Enhanced/Extreme) the original tonal spread,
        // with a floor for genuinely flat (hazy, foggy) originals.
        val step = max(1, n / 12000)
        val sample = FloatArray((n + step - 1) / step) { exp2f(lp[it * step]) }
        val sorted = sample.map { Planner.filmicCurve(it, black, white, wmax, 0f, 0.5f) }.map { exp2f(it).toDouble().pow(1 / 2.2).toFloat() }.sorted()
        val pivot = sorted[sorted.size / 2].coerceIn(0.3f, 0.6f)
        var targetSd = max(a.tonalSd * m.contrastGain, m.minSd)
        val recoveryTargetSd = when (mode) {
            Mode.NATURAL -> 0.180f
            Mode.ENHANCED -> 0.205f
            Mode.EXTREME -> 0.230f
        }
        targetSd = max(targetSd, ColorMath.lerp(targetSd, recoveryTargetSd, washedHighKey))
        targetSd = ColorMath.lerp(targetSd, a.tonalSd, 0.5f * s.lowLight)
        var contrast = 0f; var bestErr = Float.MAX_VALUE; var sd = 0f
        val contrastMax = ColorMath.lerp(0.62f, 1.02f, washedHighKey)
        var c = 0f
        while (c <= contrastMax + 0.0001f) {
            var s1 = 0.0; var s2 = 0.0
            for (yv in sample) {
                val p = exp2f(filmicCurve(yv, black, white, wmax, c, pivot)).toDouble().pow(1 / 2.2)
                s1 += p; s2 += p * p
            }
            val sdc = sqrt(max(0.0, s2 / sample.size - (s1 / sample.size) * (s1 / sample.size))).toFloat()
            if (abs(sdc - targetSd) < bestErr) { bestErr = abs(sdc - targetSd); contrast = c; sd = sdc }
            c += 0.04f
        }
        report["orig_tonal_sd"] = r3(a.tonalSd)
        val filmic = Lut.build(-16f, 6f, 1409) { l -> filmicCurve(exp2f(l), black, white, wmax, contrast, pivot) }
        report["black"] = r4(black); report["white"] = r3(white); report["contrast"] = r2(contrast); report["tonal_sd"] = r3(sd)

        // ---------------- 7. Colour ----------------
        val cs = a.chroma.copyOf().also { it.sort() }
        val medC = cs[n / 2]; val p95C = cs[(n * 0.95f).toInt()]
        var vib = m.vibrance * (0.3f + 0.7f * ((0.075f - medC) / 0.05f).coerceIn(0f, 1f))
        vib *= 1f - 0.3f * s.portrait
        var sat = 1f + m.saturation
        if (p95C > 0.19f) sat -= ((p95C - 0.19f) * 1.8f).coerceAtMost(0.2f)   // tame unnaturally saturated sources
        if (s.lowLight > 0.3f) vib *= 1f - 0.4f * s.lowLight
        report["median_chroma"] = r3(medC); report["p95_chroma"] = r3(p95C); report["vibrance"] = r2(vib); report["saturation"] = r2(sat)
        val skyEffect = if (s.sky > 0f) m.skyDeepen * (0.35f + 0.65f * s.sky) else 0f
        // A broad blue field is strong water/underwater evidence even when bright neutral sand is
        // present. Only let the ice score veto water when blue-water evidence itself is weak; this
        // separates the snow reference (small lower-frame blue fraction) from an underwater scene.
        val iceWaterVeto = s.ice * (1f - ColorMath.smoothstep(0.25f, 0.65f, s.water))
        val waterEffect = if (s.water > 0f) m.waterDeepen * (0.45f + 0.55f * s.water) * (1f - iceWaterVeto) else 0f
        val foliageEffect = if (s.foliage > 0f) m.foliage * (0.35f + 0.65f * s.foliage) else 0f
        report["scene_ice"] = r3(s.ice); report["ice_water_veto"] = r3(iceWaterVeto); report["scene_sky"] = r3(s.sky); report["scene_water"] = r3(s.water); report["scene_foliage"] = r3(s.foliage)
        report["sky_effect"] = r3(skyEffect); report["water_effect"] = r3(waterEffect); report["foliage_effect"] = r3(foliageEffect)

        // ---------------- 8. Detail ----------------
        val sig = a.noiseSigma
        val lumaNR = m.lumaNR * ColorMath.smoothstep(1.6f, 5f, sig)
        val chromaNR = m.chromaNR * ColorMath.smoothstep(1.2f, 4f, a.chromaNoiseSigma)
        val sharpen = m.sharpen * (1f - 0.6f * ColorMath.smoothstep(3f, 8f, sig))
        report["noise_sigma8"] = r2(sig); report["chroma_noise_sigma8"] = r2(a.chromaNoiseSigma)
        report["luma_nr"] = r2(lumaNR); report["chroma_nr"] = r2(chromaNR); report["sharpen"] = r2(sharpen)

        return RenderPlan(mode, a.fullWidth, a.fullHeight, wb, ev, grid, tone, filmic, clarity, vib, sat, 0.75f,
            m.skinHue, skyEffect, waterEffect, foliageEffect, m.warmBoost * s.sunset, 0.25f,
            faceProx, faceProxW, faceProxH, a.faces.isNotEmpty(), sig, a.chromaNoiseSigma, lumaNR, chromaNR, sharpen, report)
    }

    /** Face + body spatial support (normalised coordinates). */
    private fun faceSpatial(f: FaceBox, u: Float, v: Float, aspect: Float): Float {
        fun g(cx: Float, cy: Float, rx: Float, ry: Float): Float {
            val dx = (u - cx) / rx; val dy = (v - cy) / ry
            return exp(-(dx * dx + dy * dy) / 2f)
        }
        val face = g(f.cx, f.cy, 0.8f * f.w + 0.02f, 0.85f * f.h + 0.02f)
        val body = 0.85f * g(f.cx, f.cy + 2.0f * f.h, 1.7f * f.w + 0.03f, 2.2f * f.h + 0.03f)
        return max(face, body)
    }

    /** Black/white normalisation, extended-Reinhard shoulder, and S-contrast in a gamma-2.2 domain. Returns log2 Y. */
    fun filmicCurve(yIn: Float, black: Float, white: Float, wmax: Float, contrast: Float, pivot: Float): Float {
        var y = (yIn - black) / (white - black)
        if (y <= 0f) return -16f
        val sh = 0.7f
        if (y > sh) {
            val m = (wmax - sh) / (1f - sh)
            val t = min((y - sh) / (1f - sh), m)
            y = sh + (1f - sh) * t * (1f + t / (m * m)) / (1f + t)
        }
        y = min(y, 1f)
        var p = y.toDouble().pow(1 / 2.2).toFloat()
        p += contrast * 2.2f * (p - pivot) * p * (1f - p)
        p = p.coerceIn(0f, 1f)
        val out = p.toDouble().pow(2.2).toFloat()
        return log2f(max(out, 1e-5f))
    }

    private fun r2(v: Float) = Math.round(v * 100f) / 100f
    private fun r3(v: Float) = Math.round(v * 1000f) / 1000f
    private fun r4(v: Float) = Math.round(v * 10000f) / 10000f
}
