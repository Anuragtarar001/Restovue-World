package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class FaceLightStyle(val label: String) { LIFT("Lift"), SOFT("Soft"), DRAMATIC("Dramatic") }

data class FaceLightAdjustment(
    val faceId: String,
    val style: FaceLightStyle,
    val strength: Float,
    /** Virtual source position in face-local coordinates: (-1, 0) is left, (0, -1) is above. */
    val sourceX: Float = 0f,
    val sourceY: Float = 0f,
    /** 0 is a narrow spot and 1 is a broad diffused beam. */
    val beamWidth: Float = .65f,
) {
    init {
        require(faceId.isNotBlank() && strength.isFinite() && strength in 0f..1f)
        // The source may be dragged to any canvas edge. Large values are safe because the
        // renderer normalizes the direction vector before using it.
        require(sourceX.isFinite() && sourceX in -12f..12f && sourceY.isFinite() && sourceY in -12f..12f)
        require(beamWidth.isFinite() && beamWidth in 0f..1f)
    }
}

/** Deterministic, texture-preserving virtual light for one detected face. */
class FaceLightRenderer {
    fun render(source: RasterImage, face: FaceEvidence, adjustment: FaceLightAdjustment): RasterImage {
        require(face.id == adjustment.faceId)
        if (adjustment.strength <= .001f) return source.copy(argb = source.argb.copyOf())
        val output = source.argb.copyOf()
        val bounds = face.bounds
        val faceWidth = max(2f, (bounds.right - bounds.left) * source.width)
        val faceHeight = max(2f, (bounds.bottom - bounds.top) * source.height)
        val cx = (bounds.left + bounds.right) * .5f * source.width
        val cy = (bounds.top + bounds.bottom) * .5f * source.height
        // Beam width is intentionally literal for the UI: 50% covers roughly one face diameter;
        // 100% covers two. Style changes edge character, not the promised physical footprint.
        val beamRadius = adjustment.beamWidth.coerceAtLeast(.18f)
        val styleSpread = when (adjustment.style) {
            FaceLightStyle.LIFT -> 1f
            FaceLightStyle.SOFT -> 1.08f
            FaceLightStyle.DRAMATIC -> .94f
        }
        val radiusX = faceWidth * beamRadius * styleSpread
        val radiusY = faceHeight * beamRadius * styleSpread
        val left = (cx - radiusX).toInt().coerceIn(0, source.width - 1)
        val right = (cx + radiusX).toInt().coerceIn(left + 1, source.width)
        val top = (cy - radiusY).toInt().coerceIn(0, source.height - 1)
        val bottom = (cy + radiusY).toInt().coerceIn(top + 1, source.height)
        val median = medianFaceLuminance(source, face).coerceAtLeast(.025f)
        val target = when (adjustment.style) {
            FaceLightStyle.LIFT -> .30f
            FaceLightStyle.SOFT -> .34f
            FaceLightStyle.DRAMATIC -> .31f
        }
        val maximumEv = when (adjustment.style) {
            FaceLightStyle.LIFT -> .78f
            FaceLightStyle.SOFT -> .92f
            FaceLightStyle.DRAMATIC -> 1.08f
        }
        val requiredEv = (ln((target / median).toDouble()) / ln(2.0)).toFloat().coerceIn(.10f, maximumEv)
        val selectedEv = requiredEv * adjustment.strength
        val sourceMagnitude = sqrt(adjustment.sourceX * adjustment.sourceX + adjustment.sourceY * adjustment.sourceY)
        val directionStrength = if (adjustment.style == FaceLightStyle.DRAMATIC) .52f else .34f

        for (y in top until bottom) {
            val ny = (y + .5f - cy) / radiusY
            for (x in left until right) {
                val nx = (x + .5f - cx) / radiusX
                val distance = nx * nx + ny * ny
                if (distance >= 1f) continue
                val feather = smoothstep(1f, .42f, distance)
                val index = y * source.width + x
                val color = source.argb[index]
                val r8 = color ushr 16 and 255; val g8 = color ushr 8 and 255; val b8 = color and 255
                var r = LINEAR[r8]; var g = LINEAR[g8]; var b = LINEAR[b8]
                val luminance = SmartMagicColorMath.luminance(r, g, b)
                val skin = skinLikelihood(r8, g8, b8, luminance)
                // Narrow light remains face/skin aware. A broad beam deliberately includes nearby
                // hair, clothing and environment so the saved result matches the visible cone.
                val surroundingLight = .08f + .38f * adjustment.beamWidth
                val region = feather * (surroundingLight + (1f - surroundingLight) * skin)
                val directional = if (sourceMagnitude < .08f) 1f else {
                    val facing = ((nx * adjustment.sourceX + ny * adjustment.sourceY) / sourceMagnitude).coerceIn(-1f, 1f)
                    1f - directionStrength * (1f - (facing + 1f) * .5f)
                }
                val highlightGuard = 1f - .82f * smoothstep(.55f, .88f, luminance)
                val ev = selectedEv * region * directional * highlightGuard
                val gain = 2.0.pow(ev.toDouble()).toFloat()
                r *= gain; g *= gain; b *= gain
                val warmth = when (adjustment.style) {
                    FaceLightStyle.LIFT -> .012f
                    FaceLightStyle.SOFT -> .022f
                    FaceLightStyle.DRAMATIC -> .032f
                } * region * adjustment.strength
                r *= 1f + warmth
                g *= 1f + warmth * .25f
                b *= 1f - warmth * .55f
                if (adjustment.style == FaceLightStyle.SOFT) {
                    val newLuminance = SmartMagicColorMath.luminance(r, g, b)
                    val soft = region * adjustment.strength * .08f
                    r += (newLuminance - r) * soft
                    g += (newLuminance - g) * soft
                    b += (newLuminance - b) * soft
                }
                output[index] = (color and -0x1000000) or
                    (encode(r) shl 16) or (encode(g) shl 8) or encode(b)
            }
        }
        return RasterImage(source.width, source.height, output, source.scaleFromOriginal)
    }

    fun renderAll(
        source: RasterImage,
        faces: List<FaceEvidence>,
        adjustments: List<FaceLightAdjustment>,
    ): RasterImage {
        val byId = faces.associateBy { it.id }
        return adjustments.fold(source) { current, adjustment ->
            val face = requireNotNull(byId[adjustment.faceId]) { "The selected face is no longer available" }
            render(current, face, adjustment)
        }
    }

    fun meanFaceLuminance(source: RasterImage, face: FaceEvidence): Float {
        val left = (face.bounds.left * source.width).toInt().coerceIn(0, source.width - 1)
        val right = (face.bounds.right * source.width).toInt().coerceIn(left + 1, source.width)
        val top = (face.bounds.top * source.height).toInt().coerceIn(0, source.height - 1)
        val bottom = (face.bounds.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        var sum = 0.0; var count = 0
        val step = max(1, min(right - left, bottom - top) / 24)
        for (y in top until bottom step step) for (x in left until right step step) {
            val c = source.argb[y * source.width + x]
            sum += SmartMagicColorMath.luminance(LINEAR[c ushr 16 and 255], LINEAR[c ushr 8 and 255], LINEAR[c and 255])
            count++
        }
        return (sum / max(1, count)).toFloat()
    }

    private fun medianFaceLuminance(source: RasterImage, face: FaceEvidence): Float {
        val left = (face.bounds.left * source.width).toInt().coerceIn(0, source.width - 1)
        val right = (face.bounds.right * source.width).toInt().coerceIn(left + 1, source.width)
        val top = (face.bounds.top * source.height).toInt().coerceIn(0, source.height - 1)
        val bottom = (face.bounds.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        val step = max(1, min(right - left, bottom - top) / 32)
        val values = ArrayList<Float>()
        val cx = (left + right) * .5f; val cy = (top + bottom) * .5f
        val rx = max(1f, (right - left) * .42f); val ry = max(1f, (bottom - top) * .44f)
        for (y in top until bottom step step) for (x in left until right step step) {
            val dx = (x - cx) / rx; val dy = (y - cy) / ry
            if (dx * dx + dy * dy > 1f) continue
            val c = source.argb[y * source.width + x]
            values += SmartMagicColorMath.luminance(LINEAR[c ushr 16 and 255], LINEAR[c ushr 8 and 255], LINEAR[c and 255])
        }
        if (values.isEmpty()) return meanFaceLuminance(source, face)
        values.sort()
        return values[values.size / 2]
    }

    private fun skinLikelihood(r: Int, g: Int, b: Int, luminance: Float): Float {
        if (luminance < .018f || luminance > .91f) return .15f
        val maxChannel = max(r, max(g, b)); val minChannel = min(r, min(g, b))
        val spread = (maxChannel - minChannel) / 255f
        val redLead = ((r - b) / 52f).coerceIn(0f, 1f)
        val greenRelation = (1f - kotlin.math.abs(g - (r + b) * .46f) / 90f).coerceIn(0f, 1f)
        return (.18f + .42f * redLead + .25f * greenRelation + .15f * smoothstep(.03f, .24f, spread)).coerceIn(0f, 1f)
    }

    private fun encode(value: Float): Int = (SmartMagicColorMath.linearToSrgb(value.coerceIn(0f, 1f)) * 255f + .5f)
        .toInt().coerceIn(0, 255)

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object {
        val LINEAR = FloatArray(256) { SmartMagicColorMath.srgbToLinear(it / 255f) }
    }
}
