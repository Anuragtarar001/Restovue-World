package com.restorex.app.domain.smartmagic

import com.restorex.app.domain.model.RasterImage

data class SmartMagicLuminanceStatistics(
    val mean: Float,
    val median: Float,
    val p01: Float,
    val p05: Float,
    val p25: Float,
    val p50: Float,
    val p75: Float,
    val p95: Float,
    val p99: Float,
    val shadowClippingFraction: Float,
    val highlightClippingFraction: Float,
    val effectiveRange: Float,
    val globalContrast: Float,
    val localContrast: Float,
)

data class SmartMagicColorStatistics(
    val meanRed: Float,
    val meanGreen: Float,
    val meanBlue: Float,
    val colorfulness: Float,
    val meanChroma: Float,
    val meanSaturation: Float,
    val highlySaturatedFraction: Float,
    val neutralFraction: Float,
    val neutralRed: Float,
    val neutralGreen: Float,
    val neutralBlue: Float,
    val whiteBalanceConfidence: Float,
    val skinLikeFraction: Float,
    val centralSkinLikeFraction: Float,
    val warmLightConfidence: Float,
)

data class SmartMagicDetailStatistics(
    val edgeDensity: Float,
    val localVariance: Float,
    val fineDetail: Float,
    val estimatedNoise: Float,
    val highFrequencyEnergy: Float,
    val haloRisk: Float,
)

data class SmartMagicSceneScores(
    val general: Float,
    val portrait: Float,
    val landscape: Float,
    val lowLight: Float,
    val backlit: Float,
    val document: Float,
    val alreadyBalanced: Float,
    val highKey: Float,
    val lowKey: Float,
) {
    init {
        listOf(general, portrait, landscape, lowLight, backlit, document, alreadyBalanced, highKey, lowKey)
            .forEach { require(it in 0f..1f) }
    }
}

data class SmartMagicAnalysis(
    val width: Int,
    val height: Int,
    val sampledPixels: Int,
    val luminance: SmartMagicLuminanceStatistics,
    val color: SmartMagicColorStatistics,
    val detail: SmartMagicDetailStatistics,
    val scenes: SmartMagicSceneScores,
)

data class SmartMagicParameters(
    val exposureEv: Float = 0f,
    /** Perceptual luminance lift with protected black/white endpoints; distinct from exposure EV. */
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    /** Monotonic luminance gamma; 1 is neutral, values above 1 lift midtones. */
    val midtoneGamma: Float = 1f,
    val redGain: Float = 1f,
    val greenGain: Float = 1f,
    val blueGain: Float = 1f,
    val whiteBalanceConfidence: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    val texture: Float = 0f,
    val dehaze: Float = 0f,
    val luminanceNoiseReduction: Float = 0f,
    val chromaNoiseReduction: Float = 0f,
    val sharpening: Float = 0f,
    val sharpeningRadius: Float = .8f,
    val sharpeningThreshold: Float = 3f / 255f,
    val sharpeningMasking: Float = 0f,
    val portraitProtection: Float = 0f,
    val overallStrength: Float = .7f,
) {
    init {
        require(exposureEv in -.6f..0.8f && brightness in -.2f..0.25f && contrast in -.1f..0.22f)
        require(highlights in -.6f..0f && shadows in 0f..0.4f)
        require(whites in -.1f..0.15f && blacks in -.15f..0.1f)
        require(midtoneGamma in .8f..1.5f)
        require(redGain in .85f..1.15f && greenGain in .85f..1.15f && blueGain in .85f..1.15f)
        require(whiteBalanceConfidence in 0f..1f && saturation in -.05f..0.08f && vibrance in 0f..0.25f)
        require(clarity in 0f..0.15f && texture in 0f..0.12f && dehaze in 0f..0.1f)
        require(luminanceNoiseReduction in 0f..0.35f && chromaNoiseReduction in 0f..0.3f)
        require(sharpening in 0f..0.4f && sharpeningRadius in .3f..2f)
        require(sharpeningThreshold in 0f..0.1f && sharpeningMasking in 0f..1f)
        require(portraitProtection in 0f..1f && overallStrength in 0f..1f)
    }
}

data class SmartMagicSession(
    val sourceIdentity: String,
    val analysis: SmartMagicAnalysis,
    val parameters: SmartMagicParameters,
)

interface SmartMagicEngine {
    fun analyze(source: RasterImage, shouldCancel: () -> Boolean = { false }): SmartMagicAnalysis
    fun enhance(
        source: RasterImage,
        parameters: SmartMagicParameters,
        shouldCancel: () -> Boolean = { false },
        reuseSourcePixels: Boolean = false,
    ): RasterImage
}
