package com.restorex.app.data

import com.restorex.app.domain.model.Adjustments
import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class DetailsPipelineTest {
    @Test
    fun neutralDetailsLeavePixelsUnchanged() {
        val source = patterned(17, 13)

        val output = CpuImagePipeline.apply(source, Adjustments())

        assertArrayEquals(source.argb, output.argb)
    }

    @Test
    fun structureIncreasesFineAndMediumDetailEnergy() {
        val source = lowContrastChecker(24, 24)

        val output = CpuImagePipeline.apply(source, Adjustments(structure = 1f))

        assertTrue(detailEnergy(output) > detailEnergy(source))
    }

    @Test
    fun positiveAndNegativeClarityMoveLocalContrastInOppositeDirections() {
        val source = broadBands(32, 24)
        val positive = CpuImagePipeline.apply(source, Adjustments(clarity = 1f))
        val negative = CpuImagePipeline.apply(source, Adjustments(clarity = -1f))

        assertTrue(detailEnergy(positive) > detailEnergy(negative))
        assertNotEquals(positive.argb.toList(), negative.argb.toList())
    }

    @Test
    fun texturePositiveAndNegativeProduceOppositeFineDetailResults() {
        val source = lowContrastChecker(24, 24)
        val positive = CpuImagePipeline.apply(source, Adjustments(texture = 1f))
        val negative = CpuImagePipeline.apply(source, Adjustments(texture = -1f))

        assertTrue(detailEnergy(positive) > detailEnergy(negative))
    }

    @Test
    fun sharpeningAmountAndRadiusBothAffectOutput() {
        val source = impulse(25, 25)
        val lowAmount = CpuImagePipeline.apply(source, Adjustments(sharpening = .5f, sharpeningRadius = .5f))
        val highAmount = CpuImagePipeline.apply(source, Adjustments(sharpening = 1.5f, sharpeningRadius = .5f))
        val wideRadius = CpuImagePipeline.apply(source, Adjustments(sharpening = 1.5f, sharpeningRadius = 2.5f))

        assertNotEquals(lowAmount.argb.toList(), highAmount.argb.toList())
        assertNotEquals(highAmount.argb.toList(), wideRadius.argb.toList())
    }

    @Test
    fun sharpeningThresholdSuppressesLowAmplitudeDetail() {
        val source = lowContrastChecker(20, 20, 126, 130)
        val unthresholded = CpuImagePipeline.apply(source, Adjustments(sharpening = 2f, sharpeningThreshold = 0f))
        val thresholded = CpuImagePipeline.apply(source, Adjustments(sharpening = 2f, sharpeningThreshold = 1f))

        assertTrue(pixelDistance(source, thresholded) < pixelDistance(source, unthresholded))
    }

    @Test
    fun edgeMaskProtectsSmoothAreasAndSelectsEdges() {
        val source = hardEdge(24, 16)
        val mask = CpuImagePipeline.sharpeningMask(source, Adjustments(sharpeningMasking = 1f))
        val smooth = red(mask.argb[4 * mask.width + 3])
        val edge = red(mask.argb[8 * mask.width + 11])

        assertTrue(smooth < 20)
        assertTrue(edge > 180)
    }

    @Test
    fun luminanceNoiseReductionReducesLumaVariance() {
        val source = grayscaleNoise(28, 20)
        val output = CpuImagePipeline.apply(source, Adjustments(luminanceNoiseReduction = 1f))

        assertTrue(lumaDeviation(output) < lumaDeviation(source))
    }

    @Test
    fun colorNoiseReductionReducesChromaVariationWithoutBlurringLuminanceGlobally() {
        val source = chromaNoise(28, 20)
        val output = CpuImagePipeline.apply(source, Adjustments(colorNoiseReduction = 1f))

        assertTrue(chromaDeviation(output) < chromaDeviation(source))
        assertTrue(abs(averageLuma(output) - averageLuma(source)) < 3f)
    }

    @Test
    fun dehazeIncreasesContrastOfSyntheticHazyScene() {
        val source = hazyScene(30, 20)
        val output = CpuImagePipeline.apply(source, Adjustments(dehaze = .8f))

        assertTrue(lumaRange(output) > lumaRange(source))
    }

    private fun patterned(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { i ->
        rgb((i * 19) % 210 + 20, (i * 11) % 200 + 25, (i * 7) % 190 + 30)
    })

    private fun lowContrastChecker(width: Int, height: Int, low: Int = 108, high: Int = 146) =
        RasterImage(width, height, IntArray(width * height) { i ->
            val x = i % width; val y = i / width
            val value = if ((x + y) % 2 == 0) low else high
            rgb(value, value, value)
        })

    private fun broadBands(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { i ->
        val x = i % width
        val value = when { x < width / 3 -> 82; x < width * 2 / 3 -> 132; else -> 184 }
        rgb(value, value, value)
    })

    private fun impulse(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { i ->
        val x = i % width; val y = i / width
        val value = if (x == width / 2 && y == height / 2) 190 else 104
        rgb(value, value, value)
    })

    private fun hardEdge(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { i ->
        val value = if (i % width < width / 2) 45 else 215
        rgb(value, value, value)
    })

    private fun grayscaleNoise(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { i ->
        val value = 128 + (((i * 37 + i / width * 17) % 31) - 15)
        rgb(value, value, value)
    })

    private fun chromaNoise(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { i ->
        if (i % 2 == 0) rgb(148, 118, 128) else rgb(108, 138, 128)
    })

    private fun hazyScene(width: Int, height: Int) = RasterImage(width, height, IntArray(width * height) { i ->
        val x = i % width
        if (x < width / 2) rgb(125, 135, 145) else rgb(174, 184, 194)
    })

    private fun detailEnergy(image: RasterImage): Float {
        var total = 0f
        var count = 0
        for (y in 0 until image.height) for (x in 1 until image.width) {
            total += abs(luma(image.argb[y * image.width + x]) - luma(image.argb[y * image.width + x - 1]))
            count++
        }
        return total / count
    }

    private fun pixelDistance(first: RasterImage, second: RasterImage): Float = first.argb.indices.sumOf { i ->
        abs(red(first.argb[i]) - red(second.argb[i])).toDouble()
    }.toFloat()

    private fun lumaDeviation(image: RasterImage): Float {
        val mean = averageLuma(image)
        return sqrt(image.argb.sumOf { color ->
            val delta = luma(color) - mean
            (delta * delta).toDouble()
        }.toFloat() / image.argb.size)
    }

    private fun chromaDeviation(image: RasterImage): Float = image.argb.sumOf { color ->
        abs(red(color) - green(color)).toDouble() + abs(blue(color) - green(color)).toDouble()
    }.toFloat() / image.argb.size

    private fun averageLuma(image: RasterImage) = image.argb.sumOf { luma(it).toDouble() }.toFloat() / image.argb.size
    private fun lumaRange(image: RasterImage) = image.argb.maxOf(::luma) - image.argb.minOf(::luma)
    private fun luma(color: Int) = red(color) * .2126f + green(color) * .7152f + blue(color) * .0722f
    private fun red(color: Int) = color ushr 16 and 0xFF
    private fun green(color: Int) = color ushr 8 and 0xFF
    private fun blue(color: Int) = color and 0xFF
    private fun rgb(red: Int, green: Int, blue: Int) = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
