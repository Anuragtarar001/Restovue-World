package com.restorex.app.domain.photointelligence

import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.smartmagic.SmartMagicColorMath
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PhotographicDiagnosisEngine {
    fun diagnose(e: PhotometricEvidence): List<ConditionDiagnosis> = buildList {
        val l = e.luminance; val c = e.color; val d = e.detail
        addIf("PHT_GLOBAL_UNDEREXPOSED", smooth(.30f, .08f, l.median), 1f - smooth(.01f, .12f, l.whiteClipping),
            metric("median_linear_luminance", l.median, "linear", "Median is below the neutral working range"),
            counter = metric("highlight_occupancy", l.highlightOccupancy, "fraction", "Bright content limits a global exposure interpretation"), intentional = true)
        addIf("PHT_GLOBAL_OVEREXPOSED", smooth(.52f, .78f, l.median), 1f - smooth(.08f, .35f, l.shadowOccupancy),
            metric("median_linear_luminance", l.median, "linear", "Median is above the neutral working range"),
            counter = metric("shadow_occupancy", l.shadowOccupancy, "fraction", "Dark content contradicts a uniformly bright image"), intentional = true)
        addIf("PHT_EXCESSIVE_SHADOW_OCCUPANCY", smooth(.36f, .72f, l.shadowOccupancy), 1f,
            metric("shadow_occupancy", l.shadowOccupancy, "fraction", "Large visible area lies below linear 0.035"), intentional = true)
        addIf("PHT_EXCESSIVE_HIGHLIGHT_OCCUPANCY", smooth(.12f, .42f, l.highlightOccupancy), 1f,
            metric("highlight_occupancy", l.highlightOccupancy, "fraction", "Large visible area lies above linear 0.80"), intentional = true)
        addIf("PHT_SEVERE_CLIPPING", smooth(.01f, .12f, max(l.blackClipping, l.whiteClipping)), 1f,
            metric("maximum_endpoint_clipping", max(l.blackClipping, l.whiteClipping), "fraction", "Endpoint clipping is measurable"), intentional = false)
        addIf("PHT_LOW_GLOBAL_CONTRAST", smooth(.19f, .055f, l.globalContrast), 1f - smooth(.02f, .16f, max(l.blackClipping, l.whiteClipping)),
            metric("interquartile_luminance_range", l.globalContrast, "linear", "Robust global contrast is narrow"), intentional = true)
        addIf("PHT_LOW_LOCAL_CONTRAST", smooth(.040f, .010f, d.localContrast), 1f,
            metric("mean_local_laplacian", d.localContrast, "linear", "Local luminance variation is weak"), intentional = true)
        addIf("PHT_FLAT_TONAL_DISTRIBUTION", smooth(.36f, .12f, l.robustRange) * smooth(.15f, .05f, l.globalContrast), 1f,
            metric("p95_minus_p05", l.robustRange, "linear", "Robust tonal range and interquartile range are both compressed"), intentional = true)
        addIf("PHT_WEAK_CHROMA", smooth(.16f, .045f, c.meanChroma), 1f,
            metric("mean_srgb_chroma", c.meanChroma, "fraction", "Mean channel spread is low"), intentional = true)
        addIf("PHT_EXCESSIVE_CHROMA", smooth(.32f, .58f, c.meanSaturation) * smooth(.08f, .30f, c.highSaturationFraction), 1f,
            metric("high_saturation_fraction", c.highSaturationFraction, "fraction", "Many pixels are already highly saturated"), intentional = true)
        val gainDeviation = max(abs(c.proposedRedGain - 1f), max(abs(c.proposedGreenGain - 1f), abs(c.proposedBlueGain - 1f)))
        addIf("PHT_RELIABLE_COLOR_CAST", smooth(.025f, .11f, gainDeviation) * c.whiteBalanceConfidence, c.whiteBalanceConfidence,
            metric("maximum_neutral_gain_deviation", gainDeviation, "gain", "Neutral evidence supports unequal channel gains"),
            counter = metric("white_balance_confidence", c.whiteBalanceConfidence, "confidence", "Correction is suppressed when neutral evidence is weak"), intentional = true)
        addIf("PHT_HIGH_NOISE_RISK", smooth(.20f, .65f, d.noiseRisk), 1f,
            metric("smooth_region_noise_proxy", d.noiseRisk, "normalized", "High-frequency energy occurs in low-gradient samples"), intentional = false)
        addIf("PHT_LOW_RECOVERABLE_DETAIL", smooth(.10f, .025f, d.meanGradient) * smooth(.10f, .025f, d.fineDetailEnergy), 1f,
            metric("fine_detail_energy", d.fineDetailEnergy, "normalized", "Edge and fine-detail evidence are both weak"), intentional = false)

        val medium = e.regions.filter { it.scale == 4 }
        if (medium.isNotEmpty()) {
            val brightest = medium.maxOf { it.luminance.mean }; val darkest = medium.minOf { it.luminance.mean }
            val spread = brightest - darkest
            addIf("PHT_UNEVEN_ILLUMINATION", smooth(.22f, .58f, spread), 1f,
                metric("regional_mean_luminance_spread", spread, "linear", "Coarse regions differ strongly in average brightness"), intentional = true,
                affected = medium.filter { abs(it.relativeBrightness) > .16f }.map { it.id })
            val center = medium.filter { it.row in 1..2 && it.column in 1..2 }.map { it.luminance.mean }.average().toFloat()
            val border = medium.filterNot { it.row in 1..2 && it.column in 1..2 }.map { it.luminance.mean }.average().toFloat()
            val deficit = border - center
            val brightSurroundEvidence = max(smooth(.40f, .72f, l.p95), smooth(.05f, .25f, l.highlightOccupancy))
            addIf("PHT_STRONG_BACKLIGHT_PATTERN", smooth(.10f, .34f, deficit) * brightSurroundEvidence, 1f,
                metric("border_minus_center_luminance", deficit, "linear", "Center is darker while bright content is present"),
                counter = metric("semantic_subject_confidence", 0f, "confidence", "No subject detector exists in Milestone 2"), intentional = true,
                affected = medium.filter { it.row in 1..2 && it.column in 1..2 }.map { it.id })
            val inverse = center - border
            addIf("PHT_DARK_BACKGROUND_BRIGHT_CENTER", smooth(.10f, .32f, inverse), 1f,
                metric("center_minus_border_luminance", inverse, "linear", "Center is photometrically dominant"), intentional = true)
        }
        val balancedSeverity = (1f - smooth(.18f, .38f, abs(l.median - .32f))) *
            (1f - smooth(.008f, .07f, max(l.blackClipping, l.whiteClipping))) * smooth(.12f, .32f, l.globalContrast)
        addIf("PHT_ALREADY_BALANCED_EXPOSURE", balancedSeverity, 1f,
            metric("median_linear_luminance", l.median, "linear", "Median, contrast, and endpoint occupancy are inside conservative ranges"), intentional = false)
    }

    private fun MutableList<ConditionDiagnosis>.addIf(
        id: String, severity: Float, confidenceFactor: Float, evidence: MetricEvidence,
        counter: MetricEvidence? = null, intentional: Boolean, affected: List<String> = emptyList(),
    ) {
        val s = severity.coerceIn(0f, 1f); if (s < .18f) return
        add(ConditionDiagnosis(id, listOf(evidence), listOfNotNull(counter), s,
            (s * .55f + confidenceFactor.coerceIn(0f, 1f) * .45f).coerceIn(0f, 1f), affected,
            intentional, listOf("Photometric evidence does not establish semantic scene content or artistic intent")))
    }
    private fun metric(name: String, value: Float, unit: String, rationale: String) = MetricEvidence(name, value, unit, rationale)
    private fun smooth(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) return if (value >= edge0) 1f else 0f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

class BrightRegionAnalyzer(private val longestSide: Int = 256) {
    init { require(longestSide in 32..512) }
    fun analyze(source: RasterImage, shouldCancel: () -> Boolean = { false }): List<BrightRegionEvidence> {
        val scale = min(1f, longestSide / max(source.width, source.height).toFloat())
        val width = max(1, (source.width * scale).roundToInt()); val height = max(1, (source.height * scale).roundToInt())
        val luma = FloatArray(width * height); val valid = BooleanArray(luma.size)
        for (y in 0 until height) {
            if (shouldCancel()) throw java.util.concurrent.CancellationException("Bright analysis cancelled")
            val sy = (y.toLong() * source.height / height).toInt().coerceAtMost(source.height - 1)
            for (x in 0 until width) {
                val sx = (x.toLong() * source.width / width).toInt().coerceAtMost(source.width - 1)
                val c = source.argb[sy * source.width + sx]
                if ((c ushr 24 and 0xff) == 0) continue
                valid[y * width + x] = true
                luma[y * width + x] = SmartMagicColorMath.luminance(
                    SmartMagicColorMath.srgbToLinear((c ushr 16 and 0xff) / 255f),
                    SmartMagicColorMath.srgbToLinear((c ushr 8 and 0xff) / 255f),
                    SmartMagicColorMath.srgbToLinear((c and 0xff) / 255f))
            }
        }
        val sorted = luma.indices.filter { valid[it] }.map { luma[it] }.sorted()
        if (sorted.isEmpty()) return emptyList()
        val p90 = sorted[((sorted.size - 1) * .90f).roundToInt()]
        val threshold = max(.72f, p90); val seen = BooleanArray(luma.size); val result = mutableListOf<BrightRegionEvidence>()
        val queue = IntArray(luma.size)
        for (start in luma.indices) {
            if (seen[start] || !valid[start] || luma[start] < threshold) continue
            var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true
            var count = 0; var core = 0; var peak = 0f; var sum = 0.0
            var minX = width; var minY = height; var maxX = 0; var maxY = 0; var boundarySum = 0.0; var boundaryCount = 0
            while (head < tail) {
                val index = queue[head++]; val x = index % width; val y = index / width; val value = luma[index]
                count++; sum += value; if (value >= .95f) core++; peak = max(peak, value)
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val ni = ny * width + nx
                    if (valid[ni] && luma[ni] >= threshold && !seen[ni]) { seen[ni] = true; queue[tail++] = ni }
                    else if (valid[ni] && luma[ni] < threshold) { boundarySum += luma[ni]; boundaryCount++ }
                }
            }
            val area = count / (width * height).toFloat(); if (area < 1f / (width * height)) continue
            val mean = (sum / count).toFloat(); val surround = (boundarySum / boundaryCount.coerceAtLeast(1)).toFloat()
            val dominance = (mean - surround).coerceIn(0f, 1f); val boxArea = (maxX - minX + 1) * (maxY - minY + 1)
            val compactness = count / boxArea.toFloat(); val coreFraction = core / count.toFloat()
            val kind = when {
                area >= .22f -> BrightRegionKind.BROAD_BRIGHT_SURFACE
                area <= .008f && dominance >= .28f -> BrightRegionKind.POINT_SOURCE
                coreFraction >= .22f && dominance >= .18f -> BrightRegionKind.SPECULAR_CANDIDATE
                compactness < .55f && dominance >= .12f -> BrightRegionKind.BLOOM
                else -> BrightRegionKind.DIFFUSE_HIGHLIGHT
            }
            result += BrightRegionEvidence("bright-${result.size}", kind,
                NormalizedRect(minX / width.toFloat(), minY / height.toFloat(), (maxX + 1) / width.toFloat(), (maxY + 1) / height.toFloat()),
                area, coreFraction, peak, mean, dominance, mean - surround, coreFraction,
                (dominance * .6f + min(1f, area * 8f) * .4f).coerceIn(0f, 1f))
        }
        return result.sortedByDescending { it.localDominance }
    }
}

class CompositionReasoningEngine {
    fun build(evidence: PhotometricEvidence, brightRegions: List<BrightRegionEvidence>): SceneGraph {
        val medium = evidence.regions.filter { it.scale == 4 }
        val candidates = medium.mapNotNull { region ->
            val contrastCue = abs(region.relativeBrightness); val focusCue = region.edgeDensity
            val centerX = (region.bounds.left + region.bounds.right) / 2f; val centerY = (region.bounds.top + region.bounds.bottom) / 2f
            val centerCue = (1f - sqrt((centerX - .5f) * (centerX - .5f) + (centerY - .5f) * (centerY - .5f)) * 1.4f).coerceIn(0f, 1f)
            val salience = (contrastCue * .45f + focusCue * .35f + centerCue * .20f).coerceIn(0f, 1f)
            if (salience < .25f) null else RegionNode(region.id, RegionKind.PHOTOMETRIC_SALIENCE_CANDIDATE,
                region.bounds, salience, (salience * .65f).coerceAtMost(.65f), CompositionPriority.UNRESOLVED)
        }
        val brightNodes = brightRegions.map { RegionNode(it.id, RegionKind.BRIGHT_COMPONENT, it.bounds,
            it.localDominance, it.confidence, CompositionPriority.UNRESOLVED) }
        val nodes = candidates + brightNodes
        val edges = mutableListOf<RegionEdge>()
        for (i in nodes.indices) for (j in i + 1 until nodes.size) if (touches(nodes[i].bounds, nodes[j].bounds))
            edges += RegionEdge(nodes[i].id, nodes[j].id, "ADJACENT_OR_OVERLAPPING", min(nodes[i].confidence, nodes[j].confidence))
        return SceneGraph(nodes, edges, semanticPrimarySubjectId = null, unresolvedPrimarySubject = true)
    }
    private fun touches(a: NormalizedRect, b: NormalizedRect): Boolean =
        a.left <= b.right && a.right >= b.left && a.top <= b.bottom && a.bottom >= b.top
}
