package com.restorex.app.domain.engine

import com.restorex.app.domain.model.CropAspect
import kotlin.math.roundToInt

data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int)

object CropCalculator {
    fun centered(width: Int, height: Int, aspect: CropAspect): PixelRect {
        require(width > 0 && height > 0)
        if (aspect == CropAspect.ORIGINAL) return PixelRect(0, 0, width, height)
        return centeredToAspect(width, height, aspect.width, aspect.height)
    }

    fun centeredToAspect(width: Int, height: Int, targetWidth: Int, targetHeight: Int): PixelRect {
        require(width > 0 && height > 0 && targetWidth > 0 && targetHeight > 0)
        val target = targetWidth.toFloat() / targetHeight
        val source = width.toFloat() / height
        val cropWidth: Int
        val cropHeight: Int
        if (source > target) {
            cropHeight = height
            cropWidth = (height * target).roundToInt().coerceAtMost(width)
        } else {
            cropWidth = width
            cropHeight = (width / target).roundToInt().coerceAtMost(height)
        }
        return PixelRect((width - cropWidth) / 2, (height - cropHeight) / 2, cropWidth, cropHeight)
    }
}
