package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Exact 8-bit decode and bounded 12-bit encode tables for the regional hot loops. */
private object RegionalColorLut {
    private const val ENCODE_MAX = 4095
    val linear = FloatArray(256) { SmartMagicColorMath.srgbToLinear(it / 255f) }
    private val encoded = IntArray(ENCODE_MAX + 1) { index ->
        (SmartMagicColorMath.linearToSrgb(index / ENCODE_MAX.toFloat()) * 255f + .5f).toInt().coerceIn(0, 255)
    }
    fun decode(value: Int): Float = linear[value and 255]
    fun encode(value: Float): Int = encoded[(value.coerceIn(0f, 1f) * ENCODE_MAX + .5f).toInt()]
}

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
                val visibilityThreshold = when (request.plan.level) {
                    EnhancementLevel.NATURAL -> .0030f
                    EnhancementLevel.ENHANCED -> .0040f
                    EnhancementLevel.EXTREME -> .0050f
                }
                val faceVisibilityChange = if (executable.all {
                        it.type == RegionalOperationType.MIDTONE_GAMMA
                    } && request.faces.isNotEmpty()
                ) maximumFaceMeanLuminanceChange(request.source, output, request.faces) else 0f
                val targetVisibilityChanged = max(
                    abs(metrics.targetMeanLuminanceChange),
                    faceVisibilityChange,
                ) >= visibilityThreshold
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
        // A mixed person/background proposal is validated as one candidate, while the validator
        // deliberately protects the complement of the first operation's target. If background
        // finishing fails (or a useful face lift changes that protected complement), do not throw
        // away an independently safe subject correction. Retry the person operations alone so
        // their target and protected regions are evaluated consistently.
        val personOperations = request.plan.operations.filter { it.target == RegionalTarget.PERSON }
        val hasBackgroundOperations = request.plan.operations.any { it.target == RegionalTarget.BACKGROUND }
        if (personOperations.isNotEmpty() && hasBackgroundOperations) {
            val personResult = render(
                request.copy(plan = request.plan.copy(operations = personOperations)),
                shouldCancel,
            )
            if (personResult.status == RegionalRenderStatus.APPLIED ||
                personResult.status == RegionalRenderStatus.REDUCED
            ) {
                return personResult.copy(decisions = personResult.decisions + DecisionReason(
                    "REGIONAL_PERSON_FALLBACK_APPLIED",
                    "Mixed regional finishing was rejected; the independently validated person correction was preserved",
                ))
            }
            val faceOperations = personOperations.filter {
                it.type == RegionalOperationType.MIDTONE_GAMMA
            }
            if (request.faces.isNotEmpty() && faceOperations.isNotEmpty() &&
                faceOperations.size < personOperations.size
            ) {
                val faceResult = render(
                    request.copy(plan = request.plan.copy(operations = faceOperations)),
                    shouldCancel,
                )
                if (faceResult.status == RegionalRenderStatus.APPLIED ||
                    faceResult.status == RegionalRenderStatus.REDUCED
                ) {
                    return faceResult.copy(decisions = faceResult.decisions + DecisionReason(
                        "REGIONAL_FACE_FALLBACK_APPLIED",
                        "Broader regional recovery was rejected; the independently validated localized face correction was preserved",
                    ))
                }
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
        // Never evaluate a 5x5 colour-conversion kernel independently for every source pixel.
        // At 12 MP that old path performed about 900 million sRGB conversions and accounted for
        // several minutes of export time. A bounded summed-area luminance proxy preserves the
        // same mid-frequency evidence and answers every neighbourhood query in constant time.
        val neighborhoodLuminance = if (needsNeighborhood) LocalLuminanceSummary.create(source) else null
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
                var red = RegionalColorLut.decode(argb ushr 16)
                var green = RegionalColorLut.decode(argb ushr 8)
                var blue = RegionalColorLut.decode(argb)
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
                            if (needsNeighborhood && cachedLocalMean < 0f) cachedLocalMean = localMeanLuminance(source, x, y, neighborhoodLuminance)
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
                            if (cachedLocalMean < 0f) cachedLocalMean = localMeanLuminance(source, x, y, neighborhoodLuminance)
                            val localMean = cachedLocalMean
                            val detail = (sourceLuminance - localMean).coerceIn(-.18f, .18f)
                            val skyAndSkinGuard = 1f - smoothStep(.62f, .93f, sourceLuminance)
                            val target = (sourceLuminance + detail * operation.amount * localStrength *
                                4.0f * skyAndSkinGuard).coerceIn(0f, 1.1f)
                            rgbScaleLuminance(red, green, blue, target, rgb)
                            red = rgb[0]; green = rgb[1]; blue = rgb[2]
                        }
                        RegionalOperationType.DETAIL -> {
                            if (cachedLocalMean < 0f) cachedLocalMean = localMeanLuminance(source, x, y, neighborhoodLuminance)
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
            val lr = RegionalColorLut.decode(color ushr 16)
            val lg = RegionalColorLut.decode(color ushr 8)
            val lb = RegionalColorLut.decode(color)
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
                RegionalColorLut.decode(color ushr 16),
                RegionalColorLut.decode(color ushr 8),
                RegionalColorLut.decode(color))
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
            val faceWidth = bounds.right - bounds.left
            val faceHeight = bounds.bottom - bounds.top
            val rx = max(.001f, faceWidth * .58f)
            val ry = max(.001f, faceHeight * .62f)
            val faceDistance = ((nx - cx) / rx).pow(2) + ((ny - cy) / ry).pow(2)
            val faceSpatial = if (faceDistance < 1f) smoothStep(1f, .22f, faceDistance) else 0f

            // A second, weaker lobe carries the virtual fill into the neck and upper torso.
            // The reliable person mask is still applied by the compositor, so this cannot
            // invent a body region or spill into the background. Keeping it weaker than the
            // face avoids a bright circular face disconnected from naturally shaded clothing.
            val neckCy = bounds.bottom + faceHeight * .34f
            val neckRx = max(.001f, faceWidth * .78f)
            val neckRy = max(.001f, faceHeight * .70f)
            val neckDistance = ((nx - cx) / neckRx).pow(2) + ((ny - neckCy) / neckRy).pow(2)
            val neckSpatial = if (neckDistance < 1f) smoothStep(1f, .18f, neckDistance) * .58f else 0f
            val deficit = smoothStep(.30f, .075f, midtones.getOrElse(index) { .24f })
            result = max(result, max(faceSpatial, neckSpatial) * deficit)
        }
        return result
    }

    private fun localMeanLuminance(source: RasterImage, x: Int, y: Int, summary: LocalLuminanceSummary?): Float {
        // Mid-frequency photographic contrast must scale with image dimensions. A fixed 5x5
        // kernel measures sensor/JPEG texture on a modern photograph rather than atmosphere.
        val radius = (minOf(source.width, source.height) / 48).coerceIn(2, 24)
        return requireNotNull(summary).mean(x, y, source.width, source.height, radius)
    }

    /** Bounded luminance integral image used only while a regional render is active. */
    private class LocalLuminanceSummary(
        private val width: Int,
        private val height: Int,
        private val integral: DoubleArray,
    ) {
        fun mean(x: Int, y: Int, sourceWidth: Int, sourceHeight: Int, radius: Int): Float {
            val left = ((x - radius).coerceAtLeast(0).toLong() * width / sourceWidth).toInt()
            val top = ((y - radius).coerceAtLeast(0).toLong() * height / sourceHeight).toInt()
            val right = ((((x + radius + 1).coerceAtMost(sourceWidth)).toLong() * width + sourceWidth - 1) /
                sourceWidth).toInt().coerceIn(left + 1, width)
            val bottom = ((((y + radius + 1).coerceAtMost(sourceHeight)).toLong() * height + sourceHeight - 1) /
                sourceHeight).toInt().coerceIn(top + 1, height)
            val stride = width + 1
            val sum = integral[bottom * stride + right] - integral[top * stride + right] -
                integral[bottom * stride + left] + integral[top * stride + left]
            return (sum / ((right - left) * (bottom - top))).toFloat()
        }

        companion object {
            private const val MAX_PROXY_PIXELS = 1_048_576

            fun create(source: RasterImage): LocalLuminanceSummary {
                val scale = kotlin.math.sqrt((MAX_PROXY_PIXELS.toDouble() / source.argb.size).coerceAtMost(1.0))
                val width = (source.width * scale).toInt().coerceAtLeast(1)
                val height = (source.height * scale).toInt().coerceAtLeast(1)
                val stride = width + 1
                val integral = DoubleArray(stride * (height + 1))
                for (y in 0 until height) {
                    val sourceY = ((y + .5f) * source.height / height).toInt().coerceIn(0, source.height - 1)
                    var row = 0.0
                    for (x in 0 until width) {
                        val sourceX = ((x + .5f) * source.width / width).toInt().coerceIn(0, source.width - 1)
                        val color = source.argb[sourceY * source.width + sourceX]
                        val luminance = if ((color ushr 24 and 255) == 0) 0f else SmartMagicColorMath.luminance(
                            RegionalColorLut.decode(color ushr 16),
                            RegionalColorLut.decode(color ushr 8),
                            RegionalColorLut.decode(color),
                        )
                        row += luminance
                        integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row
                    }
                }
                return LocalLuminanceSummary(width, height, integral)
            }
        }
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

    private fun maximumFaceMeanLuminanceChange(
        source: RasterImage,
        output: RasterImage,
        faces: List<FaceEvidence>,
    ): Float = faces.maxOfOrNull { face ->
        val left = (face.bounds.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (face.bounds.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (face.bounds.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (face.bounds.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        val area = (right - left).toLong() * (bottom - top)
        val step = kotlin.math.ceil(kotlin.math.sqrt((area / 65_536.0).coerceAtLeast(1.0))).toInt()
        var before = 0f
        var after = 0f
        var count = 0
        for (y in top until bottom step step) for (x in left until right step step) {
            val index = y * source.width + x
            fun luminance(color: Int): Float = SmartMagicColorMath.luminance(
                RegionalColorLut.decode(color ushr 16),
                RegionalColorLut.decode(color ushr 8),
                RegionalColorLut.decode(color),
            )
            before += luminance(source.argb[index])
            after += luminance(output.argb[index])
            count++
        }
        if (count == 0) 0f else abs(after / count - before / count)
    } ?: 0f

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

    private fun toSrgbByte(value: Float): Int = RegionalColorLut.encode(value)

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
        // Full-frame validation is statistically redundant at camera resolutions and used to add
        // another expensive colour conversion pass after rendering. Keep every pixel for small
        // previews; use a deterministic uniform grid capped near 500k samples for exports.
        val step = kotlin.math.ceil(kotlin.math.sqrt(
            (source.argb.size / MAX_VALIDATION_SAMPLES.toDouble()).coerceAtLeast(1.0),
        )).toInt()
        var sampleCount = 0
        for (y in 0 until source.height step step) for (x in 0 until source.width step step) {
            val i = y * source.width + x
            val beforeColor = source.argb[i]; val afterColor = output.argb[i]
            val beforeR = RegionalColorLut.decode(beforeColor ushr 16)
            val beforeG = RegionalColorLut.decode(beforeColor ushr 8)
            val beforeB = RegionalColorLut.decode(beforeColor)
            val afterR = RegionalColorLut.decode(afterColor ushr 16)
            val afterG = RegionalColorLut.decode(afterColor ushr 8)
            val afterB = RegionalColorLut.decode(afterColor)
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
                if (x >= step) {
                    val beforeLeftColor = source.argb[i - step]; val afterLeftColor = output.argb[i - step]
                    val beforeLeftLuma = SmartMagicColorMath.luminance(
                        RegionalColorLut.decode(beforeLeftColor ushr 16),
                        RegionalColorLut.decode(beforeLeftColor ushr 8),
                        RegionalColorLut.decode(beforeLeftColor))
                    val afterLeftLuma = SmartMagicColorMath.luminance(
                        RegionalColorLut.decode(afterLeftColor ushr 16),
                        RegionalColorLut.decode(afterLeftColor ushr 8),
                        RegionalColorLut.decode(afterLeftColor))
                    val beforeLocal = abs(beforeLuma - beforeLeftLuma)
                    val afterLocal = abs(afterLuma - afterLeftLuma)
                    shadowNoise += (afterLocal - beforeLocal).coerceAtLeast(0f); shadowNoiseWeight++
                }
            }
            sampleCount++
        }
        val pixels = sampleCount.toFloat().coerceAtLeast(1f)
        val newHighlights = ((outputHighlights - sourceHighlights).coerceAtLeast(0) / pixels)
        val newShadows = ((outputShadows - sourceShadows).coerceAtLeast(0) / pixels)
        val targetChange = if (targetWeight == 0f) 0f else targetAfter / targetWeight - targetBefore / targetWeight
        val protected = if (protectedWeight == 0f) 0f else protectedDifference / protectedWeight
        val boundary = if (boundaryWeight == 0f) 0f else boundaryDifference / boundaryWeight
        val chromaAmplification = if (shadowChromaWeight == 0f) 0f else shadowChroma / shadowChromaWeight
        val noiseAmplification = if (shadowNoiseWeight == 0f) 0f else shadowNoise / shadowNoiseWeight
        val chromaChanging = operations.any { operation ->
            operation.type == RegionalOperationType.VIBRANCE ||
                operation.type == RegionalOperationType.WHITE_BALANCE ||
                operation.type == RegionalOperationType.SELECTIVE_DEHAZE
        }
        val failures = buildList {
            if (!finite) add("NON_FINITE_OUTPUT")
            if (newHighlights > .012f) add("NEW_HIGHLIGHT_CLIPPING")
            if (newShadows > .012f) add("NEW_SHADOW_CLIPPING")
            if (protected > .0045f) add("PROTECTED_INTERIOR_CHANGED")
            if (boundary > .12f) add("BOUNDARY_DISCONTINUITY_RISK")
            val chromaLimit = if (groupMode) .025f else .035f
            // Exposure and tone operators multiply RGB together and cannot introduce a hue or
            // saturation shift in linear light. Dark 8-bit pixels can nevertheless report a
            // large relative saturation delta after quantization, so reserve this rejection for
            // operations that actually alter channel ratios or atmospheric color.
            if (chromaChanging && chromaAmplification > chromaLimit) add("SHADOW_CHROMA_AMPLIFICATION")
            if (noiseAmplification > .010f) add("SHADOW_NOISE_AMPLIFICATION")
            if (maximum > 1.0001f) add("OUTPUT_OUT_OF_GAMUT")
        }
        return RegionalQualityMetrics(newHighlights, newShadows, targetChange, protected, boundary,
            chromaAmplification, noiseAmplification, protected, maximum, finite, failures.isEmpty(), failures)
    }

    private companion object {
        const val MAX_VALIDATION_SAMPLES = 524_288
    }

}
