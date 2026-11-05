package com.restorex.app.data

import com.restorex.app.domain.model.Adjustments
import com.restorex.app.domain.model.AutoEnhanceMode
import com.restorex.app.domain.model.ChannelBalance
import com.restorex.app.domain.model.ColorBalance
import com.restorex.app.domain.model.FilmEffects
import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CpuImagePipelineTest {
    @Test
    fun exposureUsesEvGain() {
        val source = solid(1, 1, rgb(64, 64, 64))
        val output = CpuImagePipeline.apply(source, Adjustments(exposure = 1f))

        assertTrue(abs(128 - red(output.argb[0])) <= 1)
    }

    @Test
    fun contrastSeparatesDarkAndBrightValues() {
        val source = row(rgb(64, 64, 64), rgb(192, 192, 192))
        val output = CpuImagePipeline.apply(source, Adjustments(contrast = 1.5f))

        assertTrue(red(output.argb[0]) < 64)
        assertTrue(red(output.argb[1]) > 192)
    }

    @Test
    fun highlightsAndShadowsTargetDifferentTonalRanges() {
        val source = row(rgb(45, 45, 45), rgb(210, 210, 210))
        val highlights = CpuImagePipeline.apply(source, Adjustments(highlights = 0.7f))
        val shadows = CpuImagePipeline.apply(source, Adjustments(shadows = 0.7f))

        val highlightDarkDelta = red(highlights.argb[0]) - 45
        val highlightBrightDelta = red(highlights.argb[1]) - 210
        val shadowDarkDelta = red(shadows.argb[0]) - 45
        val shadowBrightDelta = red(shadows.argb[1]) - 210
        assertTrue(highlightBrightDelta > highlightDarkDelta)
        assertTrue(shadowDarkDelta > shadowBrightDelta)
    }

    @Test
    fun saturationAndVibranceProduceDistinctColorResults() {
        val source = row(rgb(125, 110, 95), rgb(210, 45, 45))
        val monochrome = CpuImagePipeline.apply(source, Adjustments(saturation = 0f))
        val vibrant = CpuImagePipeline.apply(source, Adjustments(vibrance = 0.8f))

        assertEquals(red(monochrome.argb[0]), green(monochrome.argb[0]))
        assertEquals(green(monochrome.argb[0]), blue(monochrome.argb[0]))
        assertTrue(chroma(vibrant.argb[0]) > chroma(source.argb[0]))
    }

    @Test
    fun colorBalanceCanWarmMidtonesWithoutMovingAllChannelsEqually() {
        val source = solid(1, 1, rgb(128, 128, 128))
        val output = CpuImagePipeline.apply(
            source,
            Adjustments(colorBalance = ColorBalance(midtones = ChannelBalance(red = 0.6f))),
        )

        assertTrue(red(output.argb[0]) > green(output.argb[0]))
        assertTrue(abs(green(output.argb[0]) - blue(output.argb[0])) <= 1)
    }

    @Test
    fun autoEnhanceExpandsLowContrastImageAndIsDeterministic() {
        val source = row(rgb(96, 92, 90), rgb(108, 105, 103), rgb(122, 120, 118), rgb(138, 136, 134))
        val adjustments = Adjustments(autoMode = AutoEnhanceMode.AUTO_ENHANCE, enhanceStrength = 0.8f)
        val first = CpuImagePipeline.apply(source, adjustments)
        val second = CpuImagePipeline.apply(source, adjustments)

        assertTrue(lumaRange(first) > lumaRange(source))
        assertArrayEquals(first.argb, second.argb)
    }

    @Test
    fun detailAndNoiseControlsHaveRealEffects() {
        val source = RasterImage(
            3,
            3,
            intArrayOf(
                rgb(100, 100, 100), rgb(104, 96, 102), rgb(100, 100, 100),
                rgb(98, 105, 96), rgb(180, 180, 180), rgb(102, 95, 104),
                rgb(100, 100, 100), rgb(96, 104, 98), rgb(100, 100, 100),
            ),
        )
        val detailed = CpuImagePipeline.apply(source, Adjustments(sharpening = 0.8f))
        val denoised = CpuImagePipeline.apply(source, Adjustments(luminanceNoiseReduction = 0.8f, colorNoiseReduction = 0.8f))

        assertTrue(detailed.argb[4] != source.argb[4])
        assertTrue(denoised.argb[1] != source.argb[1])
    }

    @Test
    fun renderingNeverMutatesOriginalPixels() {
        val source = row(rgb(20, 30, 40), rgb(220, 180, 90))
        val original = source.argb.copyOf()

        CpuImagePipeline.apply(source, Adjustments(exposure = 1f, vibrance = 0.5f, clarity = 0.4f))

        assertArrayEquals(original, source.argb)
    }

    @Test
    fun previewAndExportRenderingAreConsistentForTheSameState() {
        val source = row(rgb(18, 42, 80), rgb(120, 110, 90), rgb(245, 225, 190))
        val state = Adjustments(
            exposure = 0.35f,
            highlights = -0.2f,
            shadows = 0.25f,
            temperature = 0.15f,
            vibrance = 0.3f,
            sharpening = 0.25f,
        )

        val previewResult = CpuImagePipeline.apply(source, state)
        val exportResult = CpuImagePipeline.apply(source, state)

        assertArrayEquals(previewResult.argb, exportResult.argb)
    }

    @Test
    fun deterministicFilmGrainMatchesAcrossRenders() {
        val source = solid(32, 24, rgb(120, 130, 140))
        val adjustments = Adjustments(film = FilmEffects(grain = .7f, lightLeak = .2f, seed = 991))

        assertArrayEquals(
            CpuImagePipeline.apply(source, adjustments).argb,
            CpuImagePipeline.apply(source, adjustments).argb,
        )
    }

    @Test
    fun mediumLargeWorkingImageRetainsDimensions() {
        val source = RasterImage(512, 384, IntArray(512 * 384) { index -> rgb(index % 256, index / 7 % 256, index / 13 % 256) })
        val output = CpuImagePipeline.apply(source, Adjustments(clarity = .25f, sharpening = .2f, film = FilmEffects(grain = .1f)))

        assertEquals(source.width, output.width)
        assertEquals(source.height, output.height)
        assertEquals(source.argb.size, output.argb.size)
    }

    private fun solid(width: Int, height: Int, color: Int) = RasterImage(width, height, IntArray(width * height) { color })
    private fun row(vararg colors: Int) = RasterImage(colors.size, 1, colors)
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(color: Int) = color ushr 16 and 0xFF
    private fun green(color: Int) = color ushr 8 and 0xFF
    private fun blue(color: Int) = color and 0xFF
    private fun chroma(color: Int) = maxOf(red(color), green(color), blue(color)) - minOf(red(color), green(color), blue(color))
    private fun luma(color: Int) = red(color) * 0.2126f + green(color) * 0.7152f + blue(color) * 0.0722f
    private fun lumaRange(image: RasterImage): Float = image.argb.maxOf(::luma) - image.argb.minOf(::luma)
}
