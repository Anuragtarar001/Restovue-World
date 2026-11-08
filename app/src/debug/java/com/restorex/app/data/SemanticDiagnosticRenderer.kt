package com.restorex.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.restorex.app.domain.photointelligence.CompositionPriority
import com.restorex.app.domain.photointelligence.SceneGraph
import com.restorex.app.domain.photointelligence.SemanticAnalysisResult

/** Debug-build-only overlay. It never enters release variants or user navigation. */
object SemanticDiagnosticRenderer {
    fun render(source: Bitmap, result: SemanticAnalysisResult, graph: SceneGraph? = null): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        result.personMask?.let { mask ->
            val paint = Paint().apply { color = Color.argb(110, 255, 181, 17) }
            val cellW = output.width / mask.width.toFloat(); val cellH = output.height / mask.height.toFloat()
            for (y in 0 until mask.height) for (x in 0 until mask.width) {
                val score = mask.score(x, y)
                if (score >= .08f) {
                    paint.alpha = (score * 120).toInt().coerceIn(0, 120)
                    canvas.drawRect(x * cellW, y * cellH, (x + 1) * cellW, (y + 1) * cellH, paint)
                }
            }
        }
        val facePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = (output.width / 240f).coerceAtLeast(2f) }
        val labelPaint = Paint().apply { color = Color.WHITE; textSize = (output.width / 32f).coerceAtLeast(18f) }
        result.faces.forEach { face ->
            canvas.drawRect(face.bounds.left * output.width, face.bounds.top * output.height,
                face.bounds.right * output.width, face.bounds.bottom * output.height, facePaint)
            canvas.drawText(face.id, face.bounds.left * output.width, face.bounds.top * output.height - 4f, labelPaint)
        }
        graph?.nodes?.filter { it.priority != CompositionPriority.UNRESOLVED }?.forEach { node ->
            canvas.drawText(node.priority.name, node.bounds.left * output.width, node.bounds.bottom * output.height, labelPaint)
        }
        return output
    }
}
