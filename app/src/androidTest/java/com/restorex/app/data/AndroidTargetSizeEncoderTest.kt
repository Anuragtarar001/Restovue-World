package com.restorex.app.data

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTargetSizeEncoderTest {
    @Test
    fun jpegOutputActuallyFitsRequestedKilobytes() {
        val width = 1200
        val height = 900
        var seed = 0x13579BDF
        val pixels = IntArray(width * height) {
            seed = seed * 1103515245 + 12345
            0xFF000000.toInt() or (seed and 0x00FFFFFF)
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try {
            val targetKb = 120
            val encoded = AndroidTargetSizeEncoder.encode(bitmap, Bitmap.CompressFormat.JPEG, 92, targetKb)
            println("TARGET_SIZE_RESULT targetBytes=${targetKb * 1024} actualBytes=${encoded.bytes.size} dimensions=${encoded.width}x${encoded.height}")
            assertTrue("${encoded.bytes.size} must fit ${targetKb * 1024}", encoded.bytes.size <= targetKb * 1024)
            assertTrue(encoded.bytes.isNotEmpty())
            assertTrue(encoded.width in 320..width)
            assertTrue(encoded.height in 320..height)
        } finally {
            bitmap.recycle()
        }
    }
}
