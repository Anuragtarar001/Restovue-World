package com.restorex.app.domain.model

import kotlin.math.abs

enum class AutoEnhanceMode(val label: String) {
    NONE("Off"), AUTO_ENHANCE("Auto Enhance"), AUTO_CONTRAST("Auto Contrast"),
    AUTO_LEVELS("Auto Levels"), AUTO_WHITE_BALANCE("White Balance"),
}

data class ChannelBalance(val red: Float = 0f, val green: Float = 0f, val blue: Float = 0f) {
    init { require(red in -1f..1f && green in -1f..1f && blue in -1f..1f) }
    val isNeutral get() = near(red, 0f) && near(green, 0f) && near(blue, 0f)
}

data class ColorBalance(
    val shadows: ChannelBalance = ChannelBalance(),
    val midtones: ChannelBalance = ChannelBalance(),
    val highlights: ChannelBalance = ChannelBalance(),
) {
    val isNeutral get() = shadows.isNeutral && midtones.isNeutral && highlights.isNeutral
}

data class SplitToning(
    val shadowHue: Float = 220f,
    val shadowSaturation: Float = 0f,
    val highlightHue: Float = 40f,
    val highlightSaturation: Float = 0f,
    val balance: Float = 0f,
) {
    init {
        require(shadowHue in 0f..360f && highlightHue in 0f..360f)
        require(shadowSaturation in 0f..1f && highlightSaturation in 0f..1f)
        require(balance in -1f..1f)
    }
    val isNeutral get() = near(shadowSaturation, 0f) && near(highlightSaturation, 0f)
}

data class FilmEffects(
    val grain: Float = 0f,
    val fade: Float = 0f,
    val tone: Float = 0f,
    val colorFade: Float = 0f,
    val highlightRolloff: Float = 0f,
    val shadowTintHue: Float = 220f,
    val shadowTint: Float = 0f,
    val lightLeak: Float = 0f,
    val chromaticAberration: Float = 0f,
    val seed: Int = 1337,
) {
    init {
        require(grain in 0f..1f && fade in 0f..1f && tone in -1f..1f && colorFade in 0f..1f)
        require(highlightRolloff in 0f..1f && shadowTintHue in 0f..360f && shadowTint in 0f..1f)
        require(lightLeak in 0f..1f && chromaticAberration in 0f..1f)
    }
    val isNeutral get() = near(grain, 0f) && near(fade, 0f) && near(tone, 0f) && near(colorFade, 0f) &&
        near(highlightRolloff, 0f) && near(shadowTint, 0f) && near(lightLeak, 0f) && near(chromaticAberration, 0f)
}

data class HslRangeAdjustment(val hue: Float = 0f, val saturation: Float = 0f, val luminance: Float = 0f) {
    init { require(hue in -60f..60f && saturation in -1f..1f && luminance in -1f..1f) }
    val isNeutral get() = near(hue, 0f) && near(saturation, 0f) && near(luminance, 0f)
}

enum class HslColorRange(val label: String, val centerHue: Float) {
    RED("Red", 0f), ORANGE("Orange", 30f), YELLOW("Yellow", 60f), GREEN("Green", 120f),
    AQUA("Aqua", 180f), BLUE("Blue", 240f), PURPLE("Purple", 280f), MAGENTA("Magenta", 320f),
}

data class HslAdjustments(
    val ranges: List<HslRangeAdjustment> = List(HslColorRange.entries.size) { HslRangeAdjustment() },
) {
    init { require(ranges.size == HslColorRange.entries.size) }
    fun update(range: HslColorRange, transform: (HslRangeAdjustment) -> HslRangeAdjustment): HslAdjustments =
        copy(ranges = ranges.toMutableList().also { it[range.ordinal] = transform(it[range.ordinal]) })
    operator fun get(range: HslColorRange) = ranges[range.ordinal]
    val isNeutral get() = ranges.all { it.isNeutral }
}

/** Immutable typed parameters for the deterministic edit pipeline. */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val exposure: Float = 0f,
    val saturation: Float = 1f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val gamma: Float = 1f,
    val toneCurve: Float = 0f,
    val vignette: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val hue: Float = 0f,
    val redChannel: Float = 0f,
    val greenChannel: Float = 0f,
    val blueChannel: Float = 0f,
    val hsl: HslAdjustments = HslAdjustments(),
    val colorBalance: ColorBalance = ColorBalance(),
    val splitToning: SplitToning = SplitToning(),
    val autoMode: AutoEnhanceMode = AutoEnhanceMode.NONE,
    val enhanceStrength: Float = 1f,
    val structure: Float = 0f,
    val clarity: Float = 0f,
    val texture: Float = 0f,
    val sharpening: Float = 0f,
    val sharpeningRadius: Float = 1f,
    val sharpeningThreshold: Float = 0.02f,
    val sharpeningMasking: Float = 0f,
    val luminanceNoiseReduction: Float = 0f,
    val colorNoiseReduction: Float = 0f,
    val dehaze: Float = 0f,
    val film: FilmEffects = FilmEffects(),
) {
    init {
        require(brightness in -1f..1f && contrast in 0.5f..1.5f && exposure in -2f..2f)
        require(saturation in 0f..2f && highlights in -1f..1f && shadows in -1f..1f)
        require(whites in -1f..1f && blacks in -1f..1f && gamma in 0.4f..2.5f)
        require(toneCurve in -1f..1f && vignette in -1f..1f)
        require(temperature in -1f..1f && tint in -1f..1f && vibrance in -1f..1f)
        require(hue in -180f..180f)
        require(redChannel in -1f..1f && greenChannel in -1f..1f && blueChannel in -1f..1f)
        require(enhanceStrength in 0f..1f && structure in 0f..1f && clarity in -1f..1f && texture in -1f..1f)
        require(sharpening in 0f..2f && sharpeningRadius in 0.3f..3f && sharpeningThreshold in 0f..1f)
        require(sharpeningMasking in 0f..1f && dehaze in 0f..1f)
        require(luminanceNoiseReduction in 0f..1f && colorNoiseReduction in 0f..1f)
    }

    val isNeutral: Boolean
        get() = near(brightness, 0f) && near(contrast, 1f) && near(exposure, 0f) && near(saturation, 1f) &&
            near(highlights, 0f) && near(shadows, 0f) && near(whites, 0f) && near(blacks, 0f) &&
            near(gamma, 1f) && near(toneCurve, 0f) && near(vignette, 0f) && near(temperature, 0f) &&
            near(tint, 0f) && near(vibrance, 0f) && near(hue, 0f) && near(redChannel, 0f) &&
            near(greenChannel, 0f) && near(blueChannel, 0f) && hsl.isNeutral && colorBalance.isNeutral &&
            splitToning.isNeutral && autoMode == AutoEnhanceMode.NONE && near(structure, 0f) && near(clarity, 0f) &&
            near(texture, 0f) && near(sharpening, 0f) && near(luminanceNoiseReduction, 0f) &&
            near(colorNoiseReduction, 0f) && near(dehaze, 0f)
            && film.isNeutral

    fun blendedWith(other: Adjustments, intensity: Float): Adjustments {
        require(intensity in 0f..1f)
        return copy(
            brightness = (brightness + other.brightness * intensity).coerceIn(-1f, 1f),
            contrast = (contrast * (1f + (other.contrast - 1f) * intensity)).coerceIn(0.5f, 1.5f),
            exposure = (exposure + other.exposure * intensity).coerceIn(-2f, 2f),
            saturation = (saturation * (1f + (other.saturation - 1f) * intensity)).coerceIn(0f, 2f),
            highlights = (highlights + other.highlights * intensity).coerceIn(-1f, 1f),
            shadows = (shadows + other.shadows * intensity).coerceIn(-1f, 1f),
            whites = (whites + other.whites * intensity).coerceIn(-1f, 1f),
            blacks = (blacks + other.blacks * intensity).coerceIn(-1f, 1f),
            gamma = (gamma * (1f + (other.gamma - 1f) * intensity)).coerceIn(0.4f, 2.5f),
            toneCurve = (toneCurve + other.toneCurve * intensity).coerceIn(-1f, 1f),
            vignette = (vignette + other.vignette * intensity).coerceIn(-1f, 1f),
            temperature = (temperature + other.temperature * intensity).coerceIn(-1f, 1f),
            tint = (tint + other.tint * intensity).coerceIn(-1f, 1f),
            vibrance = (vibrance + other.vibrance * intensity).coerceIn(-1f, 1f),
            hue = (hue + other.hue * intensity).coerceIn(-180f, 180f),
            redChannel = (redChannel + other.redChannel * intensity).coerceIn(-1f, 1f),
            greenChannel = (greenChannel + other.greenChannel * intensity).coerceIn(-1f, 1f),
            blueChannel = (blueChannel + other.blueChannel * intensity).coerceIn(-1f, 1f),
            hsl = HslAdjustments(hsl.ranges.zip(other.hsl.ranges) { base, filter ->
                HslRangeAdjustment(
                    (base.hue + filter.hue * intensity).coerceIn(-60f, 60f),
                    (base.saturation + filter.saturation * intensity).coerceIn(-1f, 1f),
                    (base.luminance + filter.luminance * intensity).coerceIn(-1f, 1f),
                )
            }),
            colorBalance = colorBalance.blendedWith(other.colorBalance, intensity),
            splitToning = splitToning.blendedWith(other.splitToning, intensity),
            structure = (structure + other.structure * intensity).coerceIn(0f, 1f),
            clarity = (clarity + other.clarity * intensity).coerceIn(-1f, 1f),
            texture = (texture + other.texture * intensity).coerceIn(-1f, 1f),
            sharpening = (sharpening + other.sharpening * intensity).coerceIn(0f, 2f),
            sharpeningMasking = (sharpeningMasking + other.sharpeningMasking * intensity).coerceIn(0f, 1f),
            luminanceNoiseReduction = (luminanceNoiseReduction + other.luminanceNoiseReduction * intensity).coerceIn(0f, 1f),
            colorNoiseReduction = (colorNoiseReduction + other.colorNoiseReduction * intensity).coerceIn(0f, 1f),
            dehaze = (dehaze + other.dehaze * intensity).coerceIn(0f, 1f),
            film = film.blendedWith(other.film, intensity),
        )
    }
}

enum class CropAspect(val label: String, val width: Int, val height: Int) {
    ORIGINAL("Original", 0, 0), SQUARE("1:1", 1, 1), FOUR_THREE("4:3", 4, 3), SIXTEEN_NINE("16:9", 16, 9),
}

data class EditState(
    val adjustments: Adjustments = Adjustments(),
    val quarterTurnsClockwise: Int = 0,
    val cropAspect: CropAspect = CropAspect.ORIGINAL,
    val filter: FilterSelection = FilterSelection(),
    val lut: LutSelection? = null,
) {
    init { require(quarterTurnsClockwise in 0..3) }
    fun rotatedClockwise() = copy(quarterTurnsClockwise = (quarterTurnsClockwise + 1) % 4)
    val effectiveAdjustments get() = adjustments.blendedWith(filter.adjustments, filter.intensity)
    fun geometryOnly() = copy(adjustments = Adjustments(), filter = FilterSelection(), lut = null)
    val isOriginal get() = adjustments.isNeutral && quarterTurnsClockwise == 0 && cropAspect == CropAspect.ORIGINAL &&
        filter.isOriginal && (lut == null || lut.intensity == 0f)
}

data class FilterSelection(
    val presetId: String = FilterPreset.ORIGINAL_ID,
    val intensity: Float = 1f,
    val adjustments: Adjustments = Adjustments(),
) {
    init { require(intensity in 0f..1f) }
    val isOriginal get() = presetId == FilterPreset.ORIGINAL_ID || adjustments.isNeutral || intensity == 0f
}

enum class FilterCategory(val label: String) { NATURAL("Natural"), PORTRAIT("Portrait"), CINEMATIC("Cinematic"), FILM("Film"), MONO("B&W"), LANDSCAPE("Landscape"), CREATIVE("Creative") }

data class FilterPreset(
    val id: String,
    val name: String,
    val adjustments: Adjustments,
    val category: FilterCategory = FilterCategory.NATURAL,
    val defaultIntensity: Float = 1f,
) {
    init { require(defaultIntensity in 0f..1f) }
    companion object { const val ORIGINAL_ID = "original" }
}

data class LutSelection(val id: String, val name: String, val path: String, val intensity: Float = 1f) {
    init { require(intensity in 0f..1f) }
}

data class ImportedLut(val id: String, val name: String, val path: String)

private fun ChannelBalance.blendedWith(other: ChannelBalance, amount: Float) = ChannelBalance(
    (red + other.red * amount).coerceIn(-1f, 1f),
    (green + other.green * amount).coerceIn(-1f, 1f),
    (blue + other.blue * amount).coerceIn(-1f, 1f),
)

private fun ColorBalance.blendedWith(other: ColorBalance, amount: Float) = ColorBalance(
    shadows.blendedWith(other.shadows, amount),
    midtones.blendedWith(other.midtones, amount),
    highlights.blendedWith(other.highlights, amount),
)

private fun SplitToning.blendedWith(other: SplitToning, amount: Float) = SplitToning(
    shadowHue = mixHue(shadowHue, other.shadowHue, amount),
    shadowSaturation = (shadowSaturation + other.shadowSaturation * amount).coerceIn(0f, 1f),
    highlightHue = mixHue(highlightHue, other.highlightHue, amount),
    highlightSaturation = (highlightSaturation + other.highlightSaturation * amount).coerceIn(0f, 1f),
    balance = (balance + other.balance * amount).coerceIn(-1f, 1f),
)

private fun FilmEffects.blendedWith(other: FilmEffects, amount: Float) = FilmEffects(
    grain = (grain + other.grain * amount).coerceIn(0f, 1f),
    fade = (fade + other.fade * amount).coerceIn(0f, 1f),
    tone = (tone + other.tone * amount).coerceIn(-1f, 1f),
    colorFade = (colorFade + other.colorFade * amount).coerceIn(0f, 1f),
    highlightRolloff = (highlightRolloff + other.highlightRolloff * amount).coerceIn(0f, 1f),
    shadowTintHue = mixHue(shadowTintHue, other.shadowTintHue, amount),
    shadowTint = (shadowTint + other.shadowTint * amount).coerceIn(0f, 1f),
    lightLeak = (lightLeak + other.lightLeak * amount).coerceIn(0f, 1f),
    chromaticAberration = (chromaticAberration + other.chromaticAberration * amount).coerceIn(0f, 1f),
    seed = other.seed,
)

private fun mixHue(base: Float, target: Float, amount: Float): Float {
    val delta = ((target - base + 540f) % 360f) - 180f
    return (base + delta * amount + 360f) % 360f
}

private fun near(value: Float, target: Float) = abs(value - target) < 0.0001f
