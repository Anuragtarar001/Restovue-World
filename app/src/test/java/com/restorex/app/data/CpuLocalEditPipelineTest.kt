package com.restorex.app.data

import com.restorex.app.domain.model.BrushEditType
import com.restorex.app.domain.model.BrushSettings
import com.restorex.app.domain.model.BrushStroke
import com.restorex.app.domain.model.LinearLight
import com.restorex.app.domain.model.NormalizedPoint
import com.restorex.app.domain.model.RadialLight
import com.restorex.app.domain.model.RasterImage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CpuLocalEditPipelineTest {
    @Test
    fun healingReducesAnIsolatedBlemishWithoutChangingDistantPixels() {
        val source = solid(31, 31, 120).withPixel(15, 15, rgb(245, 245, 245))
        val heal = stroke(BrushEditType.HEAL, listOf(point(15, 15, source)), radius = .08f)

        val output = CpuLocalEditPipeline.apply(source, listOf(heal))

        assertTrue(abs(red(output[15, 15]) - 120) < abs(red(source[15, 15]) - 120))
        assertEquals(source[2, 2], output[2, 2])
    }

    @Test
    fun cloneUsesSelectedSourceOffsetAndDoesNotTouchOutsideBrush() {
        var source = solid(32, 20, 40)
        source = source.withPixel(5, 10, rgb(220, 80, 30))
        val clone = stroke(
            BrushEditType.CLONE,
            listOf(point(24, 10, source)),
            radius = .025f,
            sourceAnchor = point(5, 10, source),
        )

        val output = CpuLocalEditPipeline.apply(source, listOf(clone))

        assertTrue(red(output[24, 10]) > 180)
        assertEquals(source[15, 2], output[15, 2])
    }

    @Test
    fun opacityAndFeatherProduceAWeakerBrushEdge() {
        val source = solid(41, 41, 80)
        val dodge = stroke(
            BrushEditType.DODGE,
            listOf(point(20, 20, source)),
            radius = .25f,
            hardness = 0f,
            opacity = .6f,
            strength = .8f,
        )

        val output = CpuLocalEditPipeline.apply(source, listOf(dodge))

        val centerDelta = red(output[20, 20]) - 80
        val edgeDelta = red(output[29, 20]) - 80
        assertTrue(centerDelta > edgeDelta)
        assertTrue(edgeDelta >= 0)
        assertEquals(source[1, 1], output[1, 1])
    }

    @Test
    fun smoothingReducesLocalNoiseWhilePreservingOutsideRegion() {
        val pixels = IntArray(31 * 31) { index ->
            val x = index % 31; val y = index / 31
            val value = if ((x + y) % 2 == 0) 118 else 138
            rgb(value, value, value)
        }
        val source = RasterImage(31, 31, pixels)
        val smooth = stroke(BrushEditType.SMOOTH, listOf(point(15, 15, source)), radius = .22f, strength = 1f)

        val output = CpuLocalEditPipeline.apply(source, listOf(smooth))

        assertTrue(localRange(output, 12, 12, 19, 19) < localRange(source, 12, 12, 19, 19))
        assertEquals(source[0, 0], output[0, 0])
    }

    @Test
    fun dodgeAndBurnMoveExposureInOppositeDirections() {
        val source = solid(25, 25, 120)
        val center = listOf(point(12, 12, source))
        val dodge = CpuLocalEditPipeline.apply(source, listOf(stroke(BrushEditType.DODGE, center, strength = .8f)))
        val burn = CpuLocalEditPipeline.apply(source, listOf(stroke(BrushEditType.BURN, center, strength = .8f)))

        assertTrue(red(dodge[12, 12]) > 120)
        assertTrue(red(burn[12, 12]) < 120)
    }

    @Test
    fun eraserReducesPreviouslyPaintedLightingMask() {
        val source = solid(25, 25, 120)
        val center = listOf(point(12, 12, source))
        val dodge = stroke(BrushEditType.DODGE, center, strength = .8f)
        val erased = stroke(BrushEditType.ERASER, center, opacity = 1f)

        val lit = CpuLocalEditPipeline.apply(source, listOf(dodge))
        val afterErase = CpuLocalEditPipeline.apply(source, listOf(dodge, erased))

        assertTrue(abs(red(afterErase[12, 12]) - 120) < abs(red(lit[12, 12]) - 120))
    }

    @Test
    fun radialLightAffectsCenterMoreThanOutsideAndSupportsInversion() {
        val source = solid(40, 30, 100)
        val normal = CpuLocalEditPipeline.apply(source, listOf(RadialLight(1, exposure = 1f)))
        val inverted = CpuLocalEditPipeline.apply(source, listOf(RadialLight(2, exposure = 1f, inverted = true)))

        assertTrue(red(normal[20, 15]) > red(normal[1, 1]))
        assertTrue(red(inverted[20, 15]) < red(inverted[1, 1]))
    }

    @Test
    fun linearLightCreatesDirectionalGradient() {
        val source = solid(40, 20, 100)
        val output = CpuLocalEditPipeline.apply(
            source,
            listOf(LinearLight(1, rotationDegrees = 0f, feather = .4f, exposure = 1f)),
        )

        assertTrue(red(output[35, 10]) > red(output[4, 10]))
    }

    @Test
    fun localRenderingNeverMutatesOriginalSource() {
        val source = solid(24, 24, 110)
        val original = source.argb.copyOf()

        CpuLocalEditPipeline.apply(source, listOf(stroke(BrushEditType.DODGE, listOf(point(12, 12, source)))))

        assertArrayEquals(original, source.argb)
    }

    private fun stroke(
        type: BrushEditType,
        points: List<NormalizedPoint>,
        radius: Float = .12f,
        hardness: Float = .5f,
        opacity: Float = 1f,
        strength: Float = .5f,
        sourceAnchor: NormalizedPoint? = null,
    ) = BrushStroke(1, type, points, BrushSettings(radius, hardness, opacity, strength), sourceAnchor)

    private fun point(x: Int, y: Int, image: RasterImage) = NormalizedPoint(x.toFloat() / (image.width - 1), y.toFloat() / (image.height - 1))
    private fun solid(width: Int, height: Int, value: Int) = RasterImage(width, height, IntArray(width * height) { rgb(value, value, value) })
    private fun RasterImage.withPixel(x: Int, y: Int, color: Int) = copy(argb = argb.copyOf().also { it[y * width + x] = color })
    private operator fun RasterImage.get(x: Int, y: Int) = argb[y * width + x]
    private fun localRange(image: RasterImage, left: Int, top: Int, right: Int, bottom: Int): Int {
        val values = mutableListOf<Int>()
        for (y in top..bottom) for (x in left..right) values += red(image[x, y])
        return values.max() - values.min()
    }
    private fun red(color: Int) = color ushr 16 and 0xFF
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
