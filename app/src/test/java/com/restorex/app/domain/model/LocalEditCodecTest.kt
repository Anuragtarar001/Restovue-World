package com.restorex.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalEditCodecTest {
    @Test
    fun operationsRoundTripWithoutRasterData() {
        val operations: List<LocalEditOperation> = listOf(
            BrushStroke(
                1,
                BrushEditType.CLONE,
                listOf(NormalizedPoint(.4f, .5f), NormalizedPoint(.6f, .7f, .8f)),
                BrushSettings(.03f, .4f, .8f, .5f),
                sourceAnchor = NormalizedPoint(.1f, .2f),
            ),
            RadialLight(2, exposure = .7f, inverted = true),
            LinearLight(3, rotationDegrees = 35f, temperature = .2f),
            LiquifyStroke(4, LiquifyType.TWIRL_CLOCKWISE, listOf(NormalizedPoint(.5f, .5f)), .1f, .4f, .7f),
            BackgroundMaskStroke(5, BackgroundBrushMode.REMOVE, listOf(NormalizedPoint(.2f, .3f)), .08f, .6f, .9f),
            BackgroundFill(6, BackgroundFillMode.GRADIENT, 0xFFFFFFFF.toInt(), 0xFF224466.toInt(), .8f),
        )

        assertEquals(operations, LocalEditCodec.decode(LocalEditCodec.encode(operations)))
    }

    @Test
    fun malformedDocumentIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { LocalEditCodec.decode(byteArrayOf(1, 2, 3, 4)) }
    }
}
