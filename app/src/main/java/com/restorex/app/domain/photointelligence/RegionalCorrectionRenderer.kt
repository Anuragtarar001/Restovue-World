package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Executes validated regional plans. Pixel math and compositing are Android-independent so the
 * same implementation can be used by reduced previews and source-resolution export.
 */
class RegionalCorrectionRenderer(
    private val maskRefiner: EdgeAwareMaskRefiner = EdgeAwareMaskRefiner(),
    private val impactEstimator: PerceptualImpactEstimator = PerceptualImpactEstimator(),
) {
    fun render(request: RegionalRenderRequest, shouldCancel: () -> Boolean = { false }): RegionalRenderResult {
        val rejected = validate(request)
        if (rejected != null) return unchanged(request, rejected.first, rejected.second)
        if (request.plan.operations.isEmpty()) {
            return unchanged(request, RegionalRenderStatus.NO_OP, "REGIONAL_PLAN_EMPTY")
        }
        if (shouldCancel()) throw CancellationException("Regional rendering cancelled")
        val refined = maskRefiner.refine(request.source, request.personMask, shouldCancel)
        val executable = request.plan.operations.filterNot { operation ->
            request.plan.protections.any { protection ->
                protection.disposition == DecisionDisposition.PROTECT &&
                    (protection.affectedOperationIds.isEmpty() || operation.id in protection.affectedOperationIds)
            }
        }
        if (executable.isEmpty()) {
            return unchanged(request, RegionalRenderStatus.SKIPPED_PROTECTION, "PROTECTION_VETO", refined)
        }

        var lastMetrics: RegionalQualityMetrics? = null
        val candidates = mutableListOf<RegionalCandidateEvaluation>()
        val strengths = request.preferredStrength?.let {
            require(it in .01f..1f) { "Preferred regional strength must be in (0, 1]" }
            listOf(it)
        } ?: listOf(1f, .70f, .45f, .30f).take(request.maxCandidateAttempts.coerceIn(1, 4))
        for ((attempt, strength) in strengths.withIndex()) {
            val candidateStarted = System.nanoTime()
            val output = execute(request, refined, executable, strength, shouldCancel)
            val metrics = RegionalQualityValidator().evaluate(request.source, output, refined, executable, request.faces.size > 1)
            val impact = impactEstimator.estimate(request.source, output)
            candidates += RegionalCandidateEvaluation(strength, metrics.passed, metrics.failureReasons, impact,
                System.nanoTime() - candidateStarted)
            lastMetrics = metrics
            if (metrics.passed) {
                if (output.argb.contentEquals(request.source.argb)) {
                    return RegionalRenderResult(
                        image = request.source,
                        status = RegionalRenderStatus.NO_OP,
                        appliedStrength = 0f,
                        refinedMask = refined,
                        metrics = metrics,
                        decisions = request.plan.explanation + DecisionReason(
                            "REGIONAL_QUANTIZED_NO_OP",
                            "The bounded correction produced no changed output pixels after 8-bit quantization",
                        ),
                        candidateEvaluations = candidates,
                    )
                }
                // A small primary subject can be photographically important while contributing
                // little to a global average. Do not discard a safe local correction solely
                // because full-frame OKLab impact is small when the target interior changed by a
                // meaningful amount. This is an objective correction, not a strength multiplier.
                val targetVisibilityChanged = abs(metrics.targetMeanLuminanceChange) >= when (request.plan.level) {
                    EnhancementLevel.NATURAL -> .0030f
                    EnhancementLevel.ENHANCED -> .0040f
                    EnhancementLevel.EXTREME -> .0050f
                }
                if (impact.perceptuallyNegligible && !targetVisibilityChanged) {
                    return RegionalRenderResult(
                        image = request.source,
                        status = RegionalRenderStatus.PERCEPTUALLY_NEGLIGIBLE,
                        appliedStrength = 0f,
                        refinedMask = refined,
                        metrics = metrics,
                        decisions = request.plan.explanation + DecisionReason(
                            "REGIONAL_PERCEPTUALLY_NEGLIGIBLE",
                            "The correction passed safety checks but remained below the bounded perceptual-impact threshold",
                        ),
                        candidateEvaluations = candidates,
                    )
                }
                return RegionalRenderResult(
                    image = output,
                    status = if (attempt == 0) RegionalRenderStatus.APPLIED else RegionalRenderStatus.REDUCED,
                    appliedStrength = strength,
                    refinedMask = refined,
                    metrics = metrics,
                    decisions = request.plan.explanation + DecisionReason(
                        if (attempt == 0) "REGIONAL_RENDER_APPLIED" else "REGIONAL_RENDER_REDUCED",
                        if (attempt == 0) "Regional plan passed quality validation" else "Regional plan passed bounded candidate search at strength $strength",
                    ),
                    candidateEvaluations = candidates,
                )
            }
        }
        return RegionalRenderResult(
            image = request.source,
            status = RegionalRenderStatus.SKIPPED_PROTECTION,
            appliedStrength = 0f,
            refinedMask = refined,
            metrics = lastMetrics ?: emptyMetrics(false, listOf("VALIDATION_NOT_RUN")),
            decisions = request.plan.explanation + DecisionReason(
                "REGIONAL_QUALITY_REJECTED",
                "Original preserved after ${strengths.size} bounded render candidates failed quality validation",
            ),
            candidateEvaluations = candidates,
        )
    }

    private fun validate(request: RegionalRenderRequest): Pair<RegionalRenderStatus, String>? {
        if (request.sourceIdentity != request.plan.sourceIdentity ||
            request.transformationVersion != request.plan.transformationVersion
        ) return RegionalRenderStatus.SKIPPED_STALE_SESSION to "SOURCE_OR_TRANSFORM_VERSION_MISMATCH"
        if (request.personMask.id != request.plan.maskId || !request.maskQuality.accepted ||
            request.maskQuality.weightedAreaFraction !in .004f.. .96f
        ) return RegionalRenderStatus.SKIPPED_INVALID_MASK to "MASK_ID_OR_QUALITY_INVALID"
        return null
    }

    private fun execute(
        request: RegionalRenderRequest,
        refined: ConfidenceMask,
        operations: List<RegionalOperation>,
        strength: Float,
        shouldCancel: () -> Boolean,
    ): RasterImage {
        val source = request.source
        val out = IntArray(source.argb.size)
        val lab = FloatArray(3)
        val rgb = FloatArray(3)
        val personSampler = RegionalMaskSampler(refined, RegionalTarget.PERSON)
        val backgroundSampler = RegionalMaskSampler(refined, RegionalTarget.BACKGROUND)
        val groupGuard = if (request.faces.size > 1) .78f else 1f
        val needsNeighborhood = operations.any { it.type == RegionalOperationType.SELECTIVE_DEHAZE ||
            it.type == RegionalOperationType.LOCAL_CONTRAST || it.type == RegionalOperationType.DETAIL }
        val previewLuminance = if (needsNeighborhood && source.argb.size <= 1_200_000) {
            FloatArray(source.argb.size) { index ->
                val color = source.argb[index]
                SmartMagicColorMath.luminance(
                    SmartMagicColorMath.srgbToLinear((color ushr 16 and 255) / 255f),
                    SmartMagicColorMath.srgbToLinear((color ushr 8 and 255) / 255f),
                    SmartMagicColorMath.srgbToLinear((color and 255) / 255f),
                )
            }
        } else null
        val atmosphere = if (operations.any { it.type == RegionalOperationType.SELECTIVE_DEHAZE })
            estimateAtmosphericLight(source, backgroundSampler) else floatArrayOf(1f, 1f, 1f)
        val faceMidtones = if (operations.any { it.type == RegionalOperationType.MIDTONE_GAMMA })
            estimateFaceMidtones(source, request.faces, personSampler) else FloatArray(0)
        for (y in 0 until source.height) {
            if (y and 15 == 0 && shouldCancel()) throw CancellationException("Regional rendering cancelled")
            for (x in 0 until source.width) {
                val index = y * source.width + x
                val argb = source.argb[index]
                val alpha = argb ushr 24 and 255
                if (alpha == 0) { out[index] = argb; continue }
                var red = SmartMagicColorMath.srgbToLinear((argb ushr 16 and 255) / 255f)
                var green = SmartMagicColorMath.srgbToLinear((argb ushr 8 and 255) / 255f)
                var blue = SmartMagicColorMath.srgbToLinear((argb and 255) / 255f)
                var cachedLocalMean = -1f
                for (operation in operations) {
                    val sampler = if (operation.target == RegionalTarget.PERSON) personSampler else backgroundSampler
                    var mask = sampler.sample(x, y, source.width, source.height)
                    if (mask <= .001f) continue
                    var localStrength = strength * operation.confidence.coerceIn(.25f, 1f)
                    val sourceLuminance = SmartMagicColorMath.luminance(red, green, blue)
                    val sourcePeak = max(red, max(green, blue)).coerceAtLeast(1e-5f)
                    val sourceSaturation = (sourcePeak - minOf(red, green, blue)) / sourcePeak
                    if (operation.type == RegionalOperationType.EXPOSURE || operation.type == RegionalOperationType.SHADOWS) {
                        // Suppress recovery only on highly chromatic deep-shadow outliers. Blending
                        // toward the original avoids magnifying JPEG/chroma speckles without borrowing
                        // colors from neighboring hair, skin, or clothing pixels.
                        val shadowWeight = smoothStep(.16f, .015f, sourceLuminance)
                        val outlierWeight = smoothStep(.48f, .88f, sourceSaturation)
                        localStrength *= 1f - shadowWeight * outlierWeight * .88f
                    }
                    if (operation.target == RegionalTarget.PERSON) {
                        val faceInfluence = faceInfluence(x, y, source.width, source.height, request.faces)
                        if (faceInfluence > 0f) {
                            val faceHeadroomGuard = (1f - request.regionalEvidence.person.highlightOccupancy * 3f).coerceIn(.38f, 1f)
                            localStrength *= 1f - faceInfluence * (1f - faceHeadroomGuard)
                        }
                        localStrength *= groupGuard
                    }
                    val originalRed = red; val originalGreen = green; val originalBlue = blue
                    when (operation.type) {
                        RegionalOperationType.EXPOSURE -> {
                            val factor = 2f.pow(operation.amount * localStrength)
                            red *= factor; green *= factor; blue *= factor
                            val luma = SmartMagicColorMath.luminance(red, green, blue)
                            if (luma > .82f) {
                                val rolled = .82f + (luma - .82f) / (1f + (luma - .82f) * 2.5f)
                                val scale = rolled / luma.coerceAtLeast(1e-6f)
                                red *= scale; green *= scale; blue *= scale
                            }
                        }
                        RegionalOperationType.SHADOWS -> rgbScaleLuminance(red, green, blue,
                            tonalLuminance(red, green, blue) { value ->
                                value + operation.amount * localStrength * .22f * (1f - smoothStep(0f, .55f, value))
                            }, rgb).also { red = rgb[0]; green = rgb[1]; blue = rgb[2] }
                        RegionalOperationType.HIGHLIGHTS -> rgbScaleLuminance(red, green, blue,
                            tonalLuminance(red, green, blue) { value ->
                                value - operation.amount * localStrength * .20f * smoothStep(.45f, 1f, value)
                            }, rgb).also { red = rgb[0]; green = rgb[1]; blue = rgb[2] }
                        RegionalOperationType.CONTRAST -> rgbScaleLuminance(red, green, blue,
                            tonalLuminance(red, green, blue) { value ->
                                (value + operation.amount * localStrength * (value - .42f) * 2.6f * value * (1f - value)).coerceAtLeast(0f)
                            }, rgb).also { red = rgb[0]; green = rgb[1]; blue = rgb[2] }
                        RegionalOperationType.VIBRANCE -> {
                            SmartMagicColorMath.linearRgbToOklab(red, green, blue, lab)
                            val chroma = sqrt(lab[1] * lab[1] + lab[2] * lab[2])
                            val gain = 1f + operation.amount * localStrength * (1f - (chroma / .32f).coerceIn(0f, 1f))
                            SmartMagicColorMath.oklabToLinearRgb(lab[0], lab[1] * gain, lab[2] * gain, rgb)
                            red = rgb[0]; green = rgb[1]; blue = rgb[2]
                        }
                        RegionalOperationType.WHITE_BALANCE -> {
                            val delta = operation.amount * localStrength
                            red *= 1f + delta; blue *= 1f - delta; green *= 1f - abs(delta) * .08f
                        }
                        RegionalOperationType.SELECTIVE_DEHAZE -> {
                            // Conservative atmospheric-scattering inversion. The estimate is global
                            // and the bounded transmission is locally guarded by highlights and
                            // smooth bright areas. This is not a depth estimate.
                            if (needsNeighborhood && cachedLocalMean < 0f) cachedLocalMean = localMeanLuminance(source, x, y, previewLuminance)
                            val localMean = if (cachedLocalMean >= 0f) cachedLocalMean else sourceLuminance
                            val highlightGuard = 1f - smoothStep(.58f, .92f, sourceLuminance)
                            val flatBrightGuard = 1f - smoothStep(.45f, .80f, localMean) *
                                (1f - smoothStep(.005f, .035f, abs(sourceLuminance - localMean)))
                            val darkCue = minOf(red, green, blue).coerceIn(0f, 1f)
                            val evidence = (.35f + darkCue * .55f) * highlightGuard * flatBrightGuard
                            val transmission = (1f - operation.amount * localStrength * evidence * 1.8f)
                                .coerceIn(.78f, 1f)
                            red = ((red - atmosphere[0]) / transmission + atmosphere[0]).coerceAtLeast(0f)
                            green = ((green - atmosphere[1]) / transmission + atmosphere[1]).coerceAtLeast(0f)
                            blue = ((blue - atmosphere[2]) / transmission + atmosphere[2]).coerceAtLeast(0f)
                        }
                        RegionalOperationType.LOCAL_CONTRAST -> {
                            if (cachedLocalMean < 0f) cachedLocalMean = localMeanLuminance(source, x, y, previewLuminance)
                            val localMean = cachedLocalMean
                            val detail = (sourceLuminance - localMean).coerceIn(-.18f, .18f)
                            val skyAndSkinGuard = 1f - smoothStep(.62f, .93f, sourceLuminance)
                            val target = (sourceLuminance + detail * operation.amount * localStrength *
                                4.0f * skyAndSkinGuard).coerceIn(0f, 1.1f)
                            rgbScaleLuminance(red, green, blue, target, rgb)
                            red = rgb[0]; green = rgb[1]; blue = rgb[2]
                        }
                        RegionalOperationType.DETAIL -> {
                            if (cachedLocalMean < 0f) cachedLocalMean = localMeanLuminance(source, x, y, previewLuminance)
                            val localMean = cachedLocalMean
                            val detail = (sourceLuminance - localMean).coerceIn(-.10f, .10f)
                            val noiseGuard = smoothStep(.018f, .055f, abs(detail)) *
                                (1f - smoothStep(.62f, .94f, sourceLuminance))
                            val target = (sourceLuminance + detail * operation.amount * localStrength *
                                2.2f * noiseGuard).coerceIn(0f, 1.1f)
                            rgbScaleLuminance(red, green, blue, target, rgb)
                            red = rgb[0]; green = rgb[1]; blue = rgb[2]
                        }
                        RegionalOperationType.MIDTONE_GAMMA -> {
                            val faceWeight = if (request.faces.isEmpty()) 1f else
                                faceRecoveryInfluence(x, y, source.width, source.height, request.faces, faceMidtones)
                            val shadowReliability = smoothStep(.006f, .045f, sourceLuminance)
                            val highlightGuard = 1f - smoothStep(.48f, .88f, sourceLuminance)
                            val effectiveDelta = operation.amount * localStrength * faceWeight *
                                shadowReliability * highlightGuard
                            if (effectiveDelta > .0001f) {
                                val target = sourceLuminance.coerceIn(0f, 1f).pow(1f / (1f + effectiveDelta))
                                rgbScaleLuminance(red, green, blue, target, rgb)
                                red = rgb[0]; green = rgb[1]; blue = rgb[2]
                            }
                        }
                    }
                    // Each operation is composited in linear light through the soft mask.
                    red = originalRed + (red - originalRed) * mask
                    green = originalGreen + (green - originalGreen) * mask
                    blue = originalBlue + (blue - originalBlue) * mask
                }
                out[index] = (alpha shl 24) or
                    (toSrgbByte(red) shl 16) or (toSrgbByte(green) shl 8) or toSrgbByte(blue)
            }
        }
        return RasterImage(source.width, source.height, out, source.scaleFromOriginal)
    }

    private fun estimateAtmosphericLight(source: RasterImage, background: RegionalMaskSampler): FloatArray {
        var r = 0.0; var g = 0.0; var b = 0.0; var weight = 0.0
        val step = max(1, sqrt(source.argb.size / 65_536.0).toInt())
        for (y in 0 until source.height step step) for (x in 0 until source.width step step) {
            val color = source.argb[y * source.width + x]
            if ((color ushr 24 and 255) == 0) continue
            val lr = SmartMagicColorMath.srgbToLinear((color ushr 16 and 255) / 255f)
            val lg = SmartMagicColorMath.srgbToLinear((color ushr 8 and 255) / 255f)
            val lb = SmartMagicColorMath.srgbToLinear((color and 255) / 255f)
            val luma = SmartMagicColorMath.luminance(lr, lg, lb)
            val w = background.sample(x, y, source.width, source.height) * smoothStep(.48f, .82f, luma)
            if (w > .01f) { r += lr * w; g += lg * w; b += lb * w; weight += w }
        }
        return if (weight <= 1e-4) floatArrayOf(.85f, .85f, .85f) else floatArrayOf(
            (r / weight).toFloat().coerceIn(.55f, 1f),
            (g / weight).toFloat().coerceIn(.55f, 1f),
            (b / weight).toFloat().coerceIn(.55f, 1f),
        )
    }

    private fun estimateFaceMidtones(
        source: RasterImage,
        faces: List<FaceEvidence>,
        person: RegionalMaskSampler,
    ): FloatArray = FloatArray(faces.size) { faceIndex ->
        val bounds = faces[faceIndex].bounds
        val left = (bounds.left * source.width).toInt().coerceIn(0, source.width - 1)
        val right = (bounds.right * source.width).toInt().coerceIn(left + 1, source.width)
        val top = (bounds.top * source.height).toInt().coerceIn(0, source.height - 1)
        val bottom = (bounds.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        val step = max(1, max(right - left, bottom - top) / 48)
        var sum = 0.0; var weight = 0.0
        for (y in top until bottom step step) for (x in left until right step step) {
            val mask = person.sample(x, y, source.width, source.height)
            if (mask < .25f) continue
            val color = source.argb[y * source.width + x]
            val luma = SmartMagicColorMath.luminance(
                SmartMagicColorMath.srgbToLinear((color ushr 16 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((color ushr 8 and 255) / 255f),
                SmartMagicColorMath.srgbToLinear((color and 255) / 255f))
            sum += luma * mask; weight += mask
        }
        if (weight <= 1e-5) .24f else (sum / weight).toFloat().coerceIn(0f, 1f)
    }

    private fun faceRecoveryInfluence(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        faces: List<FaceEvidence>,
        midtones: FloatArray,
    ): Float {
        val nx = (x + .5f) / width; val ny = (y + .5f) / height
        var result = 0f
        faces.forEachIndexed { index, face ->
            val bounds = face.bounds
            val cx = (bounds.left + bounds.right) * .5f; val cy = (bounds.top + bounds.bottom) * .52f
            val rx = max(.001f, (bounds.right - bounds.left) * .58f)
            val ry = max(.001f, (bounds.bottom - bounds.top) * .62f)
            val distance = ((nx - cx) / rx).pow(2) + ((ny - cy) / ry).pow(2)
            if (distance < 1f) {
                val spatial = smoothStep(1f, .22f, distance)
                val deficit = smoothStep(.30f, .075f, midtones.getOrElse(index) { .24f })
                result = max(result, spatial * deficit)
            }
        }
        return result
    }

    private fun localMeanLuminance(source: RasterImage, x: Int, y: Int, luminance: FloatArray?): Float {
        var sum = 0f; var count = 0
        // Mid-frequency photographic contrast must scale with image dimensions. A fixed 5x5
        // kernel measures sensor/JPEG texture on a modern photograph rather than atmosphere.
        val radius = (minOf(source.width, source.height) / 48).coerceIn(2, 24)
        for (dy in -2..2) for (dx in -2..2) {
            val sx = (x + dx * radius / 2).coerceIn(0, source.width - 1)
            val sy = (y + dy * radius / 2).coerceIn(0, source.height - 1)
            val index = sy * source.width + sx
            if (luminance != null) { sum += luminance[index]; count++; continue }
            val c = source.argb[index]
            if ((c ushr 24 and 255) == 0) continue
            val r = SmartMagicColorMath.srgbToLinear((c ushr 16 and 255) / 255f)
            val g = SmartMagicColorMath.srgbToLinear((c ushr 8 and 255) / 255f)
            val b = SmartMagicColorMath.srgbToLinear((c and 255) / 255f)
            sum += SmartMagicColorMath.luminance(r, g, b); count++
        }
        return if (count == 0) 0f else sum / count
    }

    private fun faceInfluence(x: Int, y: Int, width: Int, height: Int, faces: List<FaceEvidence>): Float {
        val nx = (x + .5f) / width; val ny = (y + .5f) / height
        var influence = 0f
        for (face in faces) {
            val bounds = face.bounds
            val cx = (bounds.left + bounds.right) * .5f; val cy = (bounds.top + bounds.bottom) * .5f
            val rx = max(.001f, (bounds.right - bounds.left) * .62f)
            val ry = max(.001f, (bounds.bottom - bounds.top) * .70f)
            val distance = ((nx - cx) / rx).pow(2) + ((ny - cy) / ry).pow(2)
            if (distance < 1f) influence = max(influence, smoothStep(1f, .15f, distance))
        }
        return influence
    }

    private fun tonalLuminance(r: Float, g: Float, b: Float, transform: (Float) -> Float): Float =
        transform(SmartMagicColorMath.luminance(r, g, b)).coerceIn(0f, 1.35f)

    private fun rgbScaleLuminance(r: Float, g: Float, b: Float, target: Float, output: FloatArray) {
        val current = SmartMagicColorMath.luminance(r, g, b)
        val scale = if (current <= 1e-6f) 1f else target / current
        output[0] = r * scale; output[1] = g * scale; output[2] = b * scale
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = if (edge0 < edge1) ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        else ((edge0 - value) / (edge0 - edge1)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun toSrgbByte(value: Float): Int =
        (SmartMagicColorMath.linearToSrgb(value.coerceIn(0f, 1f)) * 255f + .5f).toInt().coerceIn(0, 255)

    private fun unchanged(
        request: RegionalRenderRequest,
        status: RegionalRenderStatus,
        code: String,
        mask: ConfidenceMask? = null,
    ) = RegionalRenderResult(request.source, status, 0f, mask, emptyMetrics(true, emptyList()),
        request.plan.explanation + DecisionReason(code, "Regional renderer preserved the source image"))

    private fun emptyMetrics(passed: Boolean, failures: List<String>) = RegionalQualityMetrics(
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, true, passed, failures)
}

/** Local checks deliberately focus on target/protected interiors and mask boundaries, not global PSNR. */
class RegionalQualityValidator {
    fun evaluate(
        source: RasterImage,
        output: RasterImage,
        personMask: ConfidenceMask,
        operations: List<RegionalOperation>,
        groupMode: Boolean = false,
    ): RegionalQualityMetrics {
        var sourceHighlights = 0; var outputHighlights = 0; var sourceShadows = 0; var outputShadows = 0
        var targetBefore = 0f; var targetAfter = 0f; var targetWeight = 0f
        var protectedDifference = 0f; var protectedWeight = 0f
        var boundaryDifference = 0f; var boundaryWeight = 0f
        var shadowChroma = 0f; var shadowChromaWeight = 0f
        var shadowNoise = 0f; var shadowNoiseWeight = 0f
        var maximum = 0f; var finite = true
        val target = operations.first().target
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val i = y * source.width + x
            val beforeColor = source.argb[i]; val afterColor = output.argb[i]
            val beforeR = SmartMagicColorMath.srgbToLinear((beforeColor ushr 16 and 255) / 255f)
            val beforeG = SmartMagicColorMath.srgbToLinear((beforeColor ushr 8 and 255) / 255f)
            val beforeB = SmartMagicColorMath.srgbToLinear((beforeColor and 255) / 255f)
            val afterR = SmartMagicColorMath.srgbToLinear((afterColor ushr 16 and 255) / 255f)
            val afterG = SmartMagicColorMath.srgbToLinear((afterColor ushr 8 and 255) / 255f)
            val afterB = SmartMagicColorMath.srgbToLinear((afterColor and 255) / 255f)
            if (!beforeR.isFinite() || !beforeG.isFinite() || !beforeB.isFinite() ||
                !afterR.isFinite() || !afterG.isFinite() || !afterB.isFinite()) finite = false
            maximum = max(maximum, max(afterR, max(afterG, afterB)))
            val beforeLuma = SmartMagicColorMath.luminance(beforeR, beforeG, beforeB)
            val afterLuma = SmartMagicColorMath.luminance(afterR, afterG, afterB)
            if (beforeLuma >= .985f) sourceHighlights++
            if (afterLuma >= .985f) outputHighlights++
            if (beforeLuma <= .0025f) sourceShadows++
            if (afterLuma <= .0025f) outputShadows++
            val person = personMask.sample((x + .5f) / source.width, (y + .5f) / source.height)
            val targetMask = if (target == RegionalTarget.PERSON) person else 1f - person
            if (targetMask >= .8f) { targetBefore += beforeLuma; targetAfter += afterLuma; targetWeight++ }
            if (targetMask <= .05f) {
                protectedDifference += (abs(afterR - beforeR) + abs(afterG - beforeG) + abs(afterB - beforeB)) / 3f
                protectedWeight++
            }
            if (person in .2f.. .8f) {
                boundaryDifference += abs(afterLuma - beforeLuma); boundaryWeight++
            }
            if (beforeLuma < .08f && targetMask >= .2f) {
                val beforePeak = max(beforeR, max(beforeG, beforeB)).coerceAtLeast(1e-5f)
                val afterPeak = max(afterR, max(afterG, afterB)).coerceAtLeast(1e-5f)
                val beforeSaturation = (beforePeak - minOf(beforeR, beforeG, beforeB)) / beforePeak
                val afterSaturation = (afterPeak - minOf(afterR, afterG, afterB)) / afterPeak
                shadowChroma += (afterSaturation - beforeSaturation).coerceAtLeast(0f); shadowChromaWeight++
                if (x > 0) {
                    val beforeLeftColor = source.argb[i - 1]; val afterLeftColor = output.argb[i - 1]
                    val beforeLeftLuma = SmartMagicColorMath.luminance(
                        SmartMagicColorMath.srgbToLinear((beforeLeftColor ushr 16 and 255) / 255f),
                        SmartMagicColorMath.srgbToLinear((beforeLeftColor ushr 8 and 255) / 255f),
                        SmartMagicColorMath.srgbToLinear((beforeLeftColor and 255) / 255f))
                    val afterLeftLuma = SmartMagicColorMath.luminance(
                        SmartMagicColorMath.srgbToLinear((afterLeftColor ushr 16 and 255) / 255f),
                        SmartMagicColorMath.srgbToLinear((afterLeftColor ushr 8 and 255) / 255f),
                        SmartMagicColorMath.srgbToLinear((afterLeftColor and 255) / 255f))
                    val beforeLocal = abs(beforeLuma - beforeLeftLuma)
                    val afterLocal = abs(afterLuma - afterLeftLuma)
                    shadowNoise += (afterLocal - beforeLocal).coerceAtLeast(0f); shadowNoiseWeight++
                }
            }
        }
        val pixels = source.argb.size.toFloat()
        val newHighlights = ((outputHighlights - sourceHighlights).coerceAtLeast(0) / pixels)
        val newShadows = ((outputShadows - sourceShadows).coerceAtLeast(0) / pixels)
        val targetChange = if (targetWeight == 0f) 0f else targetAfter / targetWeight - targetBefore / targetWeight
        val protected = if (protectedWeight == 0f) 0f else protectedDifference / protectedWeight
        val boundary = if (boundaryWeight == 0f) 0f else boundaryDifference / boundaryWeight
        val chromaAmplification = if (shadowChromaWeight == 0f) 0f else shadowChroma / shadowChromaWeight
        val noiseAmplification = if (shadowNoiseWeight == 0f) 0f else shadowNoise / shadowNoiseWeight
        val failures = buildList {
            if (!finite) add("NON_FINITE_OUTPUT")
            if (newHighlights > .012f) add("NEW_HIGHLIGHT_CLIPPING")
            if (newShadows > .012f) add("NEW_SHADOW_CLIPPING")
            if (protected > .0045f) add("PROTECTED_INTERIOR_CHANGED")
            if (boundary > .12f) add("BOUNDARY_DISCONTINUITY_RISK")
            val chromaLimit = if (groupMode) .025f else .035f
            if (chromaAmplification > chromaLimit) add("SHADOW_CHROMA_AMPLIFICATION")
            if (noiseAmplification > .010f) add("SHADOW_NOISE_AMPLIFICATION")
            if (maximum > 1.0001f) add("OUTPUT_OUT_OF_GAMUT")
        }
        return RegionalQualityMetrics(newHighlights, newShadows, targetChange, protected, boundary,
            chromaAmplification, noiseAmplification, protected, maximum, finite, failures.isEmpty(), failures)
    }

}
