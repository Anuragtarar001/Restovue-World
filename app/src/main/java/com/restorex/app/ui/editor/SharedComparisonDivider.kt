package com.restorex.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restorex.app.ui.theme.RestoreXAccent

/**
 * The single before/after divider used by both Smart Magic and Manual Edit.
 * Image rendering differs between editors, but hit testing, drag accumulation,
 * semantics, visuals, and the full edge-to-edge range live only here.
 */
@Composable
internal fun SharedComparisonDivider(
    fraction: Float,
    canvasWidthPx: Int,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = RestoreXAccent,
) {
    val latestFraction = rememberUpdatedState(fraction)
    Box(
        modifier.width(48.dp).fillMaxHeight()
            .testTag("shared_comparison_divider")
            .pointerInput(canvasWidthPx) {
                var dragOrigin = 0f
                var accumulatedDrag = 0f
                detectDragGestures(
                    onDragStart = {
                        dragOrigin = latestFraction.value
                        accumulatedDrag = 0f
                    },
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount.x
                    onFractionChange((dragOrigin + accumulatedDrag / canvasWidthPx.coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            }
            .semantics {
                contentDescription = "Before and after divider"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress {
                    onFractionChange(it.coerceIn(0f, 1f))
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxHeight().width(2.dp).background(color))
        Box(Modifier.size(36.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text("↔", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
