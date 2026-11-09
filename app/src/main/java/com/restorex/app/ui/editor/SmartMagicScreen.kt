package com.restorex.app.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticGroup
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticStatus
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticValue
import com.restorex.app.presentation.SmartMagicPhase
import com.restorex.app.presentation.SmartMagicUiState
import com.restorex.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SmartMagicScreen(
    state: SmartMagicUiState,
    onBack: () -> Unit,
    onChooseAnother: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String, String?) -> Unit,
    onShare: (String, String?) -> Unit,
    onSelectLevel: (EnhancementLevel) -> Unit = {},
    onComparisonChanged: (Boolean) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var compareEnabled by rememberSaveable(state.sourceGeneration) { mutableStateOf(false) }
    var dividerFraction by rememberSaveable(state.sourceGeneration) { mutableStateOf(.5f) }
    var adjustmentsExpanded by rememberSaveable(state.sourceGeneration) { mutableStateOf(false) }
    var technicalAdjustmentId by rememberSaveable(state.sourceGeneration) { mutableStateOf<String?>(null) }
    var zoom by rememberSaveable(state.sourceGeneration) { mutableStateOf(1f) }
    var panX by rememberSaveable(state.sourceGeneration) { mutableStateOf(0f) }
    var panY by rememberSaveable(state.sourceGeneration) { mutableStateOf(0f) }
    var viewport by remember(state.sourceGeneration) { mutableStateOf(IntSize.Zero) }
    val processedIsCurrent = state.displayedPreviewIsCurrent
    val visible = if (processedIsCurrent) state.enhanced else state.original
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
        val maxX = viewport.width * (nextZoom - 1f) * .5f
        val maxY = viewport.height * (nextZoom - 1f) * .5f
        zoom = nextZoom
        if (nextZoom <= 1.001f) { panX = 0f; panY = 0f } else {
            panX = (panX + panChange.x).coerceIn(-maxX, maxX)
            panY = (panY + panChange.y).coerceIn(-maxY, maxY)
        }
    }

    Column(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF121318), RestoreXBackground, Color(0xFF090A0D))),
    ).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "Back" }) {
                Text("\u2039", color = RestoreXText, fontSize = 36.sp, lineHeight = 36.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("Smart Magic", color = RestoreXText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Professional enhancement in one tap", color = RestoreXMuted, fontSize = 11.sp)
            }
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp)
                .background(Color(0xFF08090C), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFF36383E), RoundedCornerShape(18.dp))
                .clipToBounds().onSizeChanged { viewport = it }
                .pointerInput(state.sourceGeneration) {
                    detectTapGestures(onDoubleTap = {
                        if (zoom > 1.05f) { zoom = 1f; panX = 0f; panY = 0f }
                        else { zoom = 2f; panX = 0f; panY = 0f }
                    })
                }.transformable(transformState).semantics {
                    contentDescription = when {
                        compareEnabled && state.canCompare -> "Before and after comparison canvas"
                        processedIsCurrent -> "${state.selectedLevel.displayLabel} processed photo"
                        else -> "Original preview while ${state.selectedLevel.displayLabel} is processing"
                    }
                    stateDescription = "Zoom ${"%.1f".format(zoom)} times"
                }, contentAlignment = Alignment.Center,
        ) {
            visible?.let { displayed ->
                TransformedComparisonPhoto(
                    processed = displayed,
                    original = state.original,
                    comparisonEnabled = compareEnabled && state.canCompare,
                    dividerFraction = dividerFraction,
                    zoom = zoom,
                    panX = panX,
                    panY = panY,
                )
                if (compareEnabled && state.canCompare) {
                    Box(
                        Modifier.fillMaxHeight().width(48.dp).offset {
                            IntOffset((viewport.width * dividerFraction - 24.dp.toPx()).roundToInt(), 0)
                        }.pointerInput(viewport.width, state.sourceGeneration) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                if (viewport.width > 0) dividerFraction =
                                    (dividerFraction + dragAmount / viewport.width).coerceIn(0f, 1f)
                            }
                        }.semantics {
                            contentDescription = "Before and after divider"
                            stateDescription = "${(dividerFraction * 100).roundToInt()} percent before"
                            progressBarRangeInfo = ProgressBarRangeInfo(dividerFraction, 0f..1f, 0)
                            setProgress { requested ->
                                dividerFraction = requested.coerceIn(0f, 1f)
                                true
                            }
                        }, contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.fillMaxHeight().width(2.dp).background(Color.White.copy(alpha = .9f)))
                        Box(Modifier.size(48.dp).background(Color.White, CircleShape)
                            .border(2.dp, Color(0xFF25272D), CircleShape), contentAlignment = Alignment.Center) {
                            Text("\u2039  \u203a", color = Color(0xFF17181C), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                    ComparisonLabel("BEFORE", Modifier.align(Alignment.TopStart))
                    ComparisonLabel("AFTER", Modifier.align(Alignment.TopEnd))
                }
            }
            if (visible != null && !(compareEnabled && state.canCompare)) {
                ComparisonLabel(if (processedIsCurrent) "${state.selectedLevel.displayLabel.uppercase()} \u2022 AFTER" else "ORIGINAL PREVIEW",
                    Modifier.align(Alignment.TopStart))
            }
            if (zoom > 1.01f) Text("${"%.1f".format(zoom)}x", color = RestoreXText, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp)
                    .background(Color(0xBB111216), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
        }

        if (state.isWorking && state.progress != null) ProcessingMonitor(state)

        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (state.original != null && state.phase != SmartMagicPhase.ERROR) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnhancementLevel.entries.forEach { level ->
                        val selected = state.selectedLevel == level
                        Button(onClick = { onSelectLevel(level) },
                            enabled = state.modeSelectionEnabled && state.phase !in setOf(SmartMagicPhase.SAVING, SmartMagicPhase.SAVED),
                            modifier = Modifier.weight(1f).height(40.dp).semantics {
                                contentDescription = "${level.displayLabel} enhancement level"
                                stateDescription = when {
                                    !selected -> "Not selected"
                                    state.completedLevel == level && state.displayedPreviewIsCurrent -> "Selected and displayed"
                                    else -> "Selected, processing"
                                }
                            }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) RestoreXAccent else RestoreXPanel,
                                contentColor = if (selected) Color(0xFF171300) else RestoreXText,
                                disabledContainerColor = if (selected) RestoreXAccent.copy(alpha = .65f) else RestoreXPanel,
                            )) { Text(level.displayLabel, fontSize = 12.sp, maxLines = 1) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        compareEnabled = !compareEnabled
                        if (compareEnabled) dividerFraction = .5f
                        onComparisonChanged(compareEnabled)
                    }, enabled = state.canCompare, modifier = Modifier.height(38.dp)
                        .semantics { contentDescription = "Compare before and after" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (compareEnabled) RestoreXAccent else RestoreXPanel,
                            contentColor = if (compareEnabled) Color(0xFF171300) else RestoreXText,
                        )) { Text(if (compareEnabled) "Done Comparing" else "Compare", fontSize = 11.sp) }
                    Text("Drag divider \u2022 Pinch or double-tap to zoom", color = RestoreXMuted, fontSize = 10.sp,
                        modifier = Modifier.weight(1f))
                }
                if (state.adjustmentReportIsCurrent && state.displayedPreviewIsCurrent) {
                    TextButton(onClick = { adjustmentsExpanded = !adjustmentsExpanded }, modifier = Modifier.height(32.dp)
                        .semantics { contentDescription = "Adjustment Details" }) {
                        Text(if (adjustmentsExpanded) "Hide Adjustment Details" else "Adjustment Details", color = RestoreXAccent, fontSize = 11.sp)
                    }
                    if (adjustmentsExpanded) AdjustmentDetailsPanel(state, technicalAdjustmentId) {
                        technicalAdjustmentId = if (technicalAdjustmentId == it) null else it
                    }
                }
                Button(onClick = onSave, enabled = state.phase == SmartMagicPhase.READY && state.displayedPreviewIsCurrent,
                    colors = ButtonDefaults.buttonColors(containerColor = RestoreXAccent, contentColor = Color(0xFF171300)),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (state.phase == SmartMagicPhase.SAVED) "Saved to Gallery" else "Save Photo", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                if (state.phase == SmartMagicPhase.SAVED && state.savedUri != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOpen(state.savedUri, state.savedMimeType) }) { Text("Open", color = RestoreXAccent) }
                    TextButton(onClick = { onShare(state.savedUri, state.savedMimeType) }) { Text("Share", color = RestoreXAccent) }
                }
                if (state.isWorking) TextButton(onClick = onCancel, modifier = Modifier.height(36.dp)) { Text("Cancel", color = RestoreXAccent) }
                else TextButton(onClick = onChooseAnother, modifier = Modifier.height(36.dp)) { Text("Choose Another Photo", color = RestoreXText) }
            } else when (state.phase) {
                SmartMagicPhase.ERROR -> {
                    Text(state.message ?: "This photo could not be processed", color = Color(0xFFFF8A80), textAlign = TextAlign.Center)
                    Button(onClick = onChooseAnother, colors = ButtonDefaults.buttonColors(containerColor = RestoreXAccent)) { Text("Choose Another Photo") }
                }
                SmartMagicPhase.IDLE -> Text("Choose a photo to begin", color = RestoreXMuted)
                else -> if (state.isWorking) TextButton(onClick = onCancel) { Text("Cancel", color = RestoreXAccent) }
            }
            state.message?.takeIf { state.phase != SmartMagicPhase.ERROR }?.let {
                Text(it, color = RestoreXMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun AdjustmentDetailsPanel(
    state: SmartMagicUiState,
    expandedId: String?,
    onToggleTechnical: (String) -> Unit,
) {
    val report = state.adjustmentReport ?: return
    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).background(RestoreXPanel, RoundedCornerShape(10.dp))
        .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("${report.mode.name.lowercase().replaceFirstChar { it.uppercase() }} adjustment diagnostics",
            color = RestoreXText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        AdjustmentDiagnosticGroup.entries.forEach { group ->
            Text(group.label, color = RestoreXAccent, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            report.entries.filter { it.id.group == group }.forEach { entry ->
                val id = entry.id.name
                Column(Modifier.fillMaxWidth().clickable { onToggleTechnical(id) }
                    .semantics { contentDescription = "${entry.id.label}: ${entry.status.displayLabel}" }
                    .padding(vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.id.label, color = RestoreXText, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text(if (entry.status == AdjustmentDiagnosticStatus.APPLIED)
                            entry.values.joinToString("; ") { diagnosticValue(it, applied = true) }
                        else entry.status.displayLabel,
                            color = if (entry.status == AdjustmentDiagnosticStatus.APPLIED) RestoreXAccent else RestoreXMuted,
                            fontSize = 10.sp, textAlign = TextAlign.End)
                    }
                    if (expandedId == id) {
                        Text("Status: ${entry.status.name}", color = RestoreXMuted, fontSize = 9.sp)
                        Text("Region: ${entry.region}  |  Mode: ${report.mode.name}", color = RestoreXMuted, fontSize = 9.sp)
                        entry.values.forEach { value ->
                            Text("${value.component}: neutral ${formatRaw(value.neutralValue)} ${value.unit}; " +
                                "proposed ${value.proposedValue?.let { formatRaw(it) } ?: "none"}; " +
                                "applied ${value.appliedValue?.let { formatRaw(it) } ?: "none"}",
                                color = RestoreXMuted, fontSize = 9.sp)
                        }
                        Text(entry.decisionReason, color = RestoreXMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private val AdjustmentDiagnosticStatus.displayLabel: String
    get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun diagnosticValue(value: AdjustmentDiagnosticValue, applied: Boolean): String {
    val actual = if (applied) value.appliedValue else value.proposedValue
    return if (actual == null) "none" else "${if (actual - value.neutralValue >= 0f) "+" else ""}${formatRaw(actual - value.neutralValue)} ${value.unit}"
}

private fun formatRaw(value: Float): String = "%.3f".format(value)

@Composable
private fun ProcessingMonitor(state: SmartMagicUiState) {
    val progress = state.progress ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
        .background(RestoreXPanel, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(progress.label, color = RestoreXText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        progress.detail?.let { Text(it, color = RestoreXMuted, fontSize = 10.sp) }
        Spacer(Modifier.height(5.dp))
        progress.fraction?.let {
            LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = RestoreXAccent)
        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp), color = RestoreXAccent)
    }
}

@Composable
private fun TransformedComparisonPhoto(
    processed: RasterImage,
    original: RasterImage?,
    comparisonEnabled: Boolean,
    dividerFraction: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
) {
    val processedBitmap = rememberRasterImage(processed)
    val originalBitmap = original?.let { rememberRasterImage(it) }
    Canvas(Modifier.fillMaxSize().graphicsLayer {
        scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY
        transformOrigin = TransformOrigin.Center
    }) {
        val scale = minOf(size.width / processedBitmap.width, size.height / processedBitmap.height)
        val targetWidth = (processedBitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (processedBitmap.height * scale).roundToInt().coerceAtLeast(1)
        val targetOffset = IntOffset(
            ((size.width - targetWidth) * .5f).roundToInt(),
            ((size.height - targetHeight) * .5f).roundToInt(),
        )
        val targetSize = IntSize(targetWidth, targetHeight)
        drawImage(processedBitmap, dstOffset = targetOffset, dstSize = targetSize,
            filterQuality = FilterQuality.Medium)
        if (comparisonEnabled && originalBitmap != null) {
            clipRect(right = size.width * dividerFraction.coerceIn(0f, 1f)) {
                // The exact same destination rectangle and graphics transform are used for both
                // buffers. Moving the divider therefore cannot decode or reprocess either image.
                drawImage(originalBitmap, dstOffset = targetOffset, dstSize = targetSize,
                    filterQuality = FilterQuality.Medium)
            }
        }
    }
}

@Composable
private fun ComparisonLabel(label: String, modifier: Modifier) {
    Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp,
        modifier = modifier.padding(14.dp).background(Color(0xBB111216), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp))
}

private val EnhancementLevel.displayLabel: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun rememberRasterImage(raster: RasterImage): ImageBitmap {
    val bitmap = remember(raster) { Bitmap.createBitmap(raster.argb, raster.width, raster.height, Bitmap.Config.ARGB_8888) }
    DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
    return remember(bitmap) { bitmap.asImageBitmap() }
}
