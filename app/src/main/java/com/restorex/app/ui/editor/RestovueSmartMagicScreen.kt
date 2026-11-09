package com.restorex.app.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.photointelligence.FaceEvidence
import com.restorex.app.domain.photointelligence.FaceLightAdjustment
import com.restorex.app.domain.photointelligence.FaceLightStyle
import com.restorex.app.presentation.SmartMagicPhase
import com.restorex.app.presentation.SmartMagicUiState
import com.restorex.app.ui.theme.*
import kotlin.math.roundToInt
import kotlin.math.max
import kotlinx.coroutines.delay

/** Approved Restovue Smart Magic experience. Every claim is derived from the active engine state. */
@Composable
fun RestovueSmartMagicScreen(
    state: SmartMagicUiState,
    onBack: () -> Unit,
    onChooseAnother: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String, String?) -> Unit,
    onShare: (String, String?) -> Unit,
    onSelectLevel: (EnhancementLevel) -> Unit = {},
    onComparisonChanged: (Boolean) -> Unit = {},
    onPreviewFaceLight: (List<FaceLightAdjustment>) -> Unit = {},
    onApplyFaceLight: (List<FaceLightAdjustment>) -> Unit = {},
    onUndoFaceLight: () -> Unit = {},
    onCancelFaceLight: () -> Unit = {},
) {
    var showFaceLight by rememberSaveable(state.sourceGeneration) { mutableStateOf(false) }
    if (showFaceLight) {
        GroupFaceLightScreen(
            photo = state.faceLightPreview ?: state.enhanced ?: state.original,
            beforePhoto = state.enhanced ?: state.original,
            faces = state.faces,
            working = state.faceLightWorking,
            onPreview = onPreviewFaceLight,
            onApply = {
                onApplyFaceLight(it)
                showFaceLight = false
            },
            onBack = {
                onCancelFaceLight()
                showFaceLight = false
            },
        )
        return
    }
    BackHandler(onBack = onBack)
    var divider by rememberSaveable(state.sourceGeneration) { mutableFloatStateOf(.5f) }
    var fillPhoto by rememberSaveable(state.sourceGeneration) { mutableStateOf(true) }
    var canvasWidth by remember(state.sourceGeneration) { mutableIntStateOf(1) }
    var canvasHeight by remember(state.sourceGeneration) { mutableIntStateOf(1) }
    var photoZoom by remember(state.sourceGeneration) { mutableFloatStateOf(1f) }
    var photoPan by remember(state.sourceGeneration) { mutableStateOf(Offset.Zero) }
    val compare = state.canCompare && state.displayedPreviewIsCurrent
    LaunchedEffect(compare) {
        if (compare) divider = .5f
        onComparisonChanged(compare)
    }
    LaunchedEffect(state.sourceGeneration) { divider = .5f }
    LaunchedEffect(state.faceLightRevision) { divider = .5f }
    val visible = if (state.displayedPreviewIsCurrent) state.enhanced else state.original

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF080E29), RestoreXBackground, Color(0xFF040515))),
        ).statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clickable(onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = RestoreXText, fontSize = 36.sp, lineHeight = 36.sp) }
            Text("Smart Magic", color = RestoreXText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(30.dp).border(1.dp, RestoreXMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("i", color = RestoreXText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }

        Box(
            Modifier.fillMaxWidth().weight(.44f).padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color.Black)
                .border(1.dp, Color(0xFF302A59), RoundedCornerShape(20.dp))
                .clipToBounds().onSizeChanged {
                    canvasWidth = it.width.coerceAtLeast(1)
                    canvasHeight = it.height.coerceAtLeast(1)
                },
            contentAlignment = Alignment.Center,
        ) {
            visible?.let {
                BrandComparisonPhoto(
                    processed = it,
                    original = state.original,
                    compare = compare,
                    divider = divider,
                    fillPhoto = fillPhoto,
                    zoom = photoZoom,
                    pan = photoPan,
                    onTransform = { panChange, zoomChange ->
                        val targetZoom = (photoZoom * zoomChange).coerceIn(1f, 6f)
                        if (targetZoom <= 1.001f) {
                            photoZoom = 1f
                            photoPan = Offset.Zero
                        } else {
                            val maxX = canvasWidth * (targetZoom - 1f) / 2f
                            val maxY = canvasHeight * (targetZoom - 1f) / 2f
                            photoZoom = targetZoom
                            photoPan = Offset(
                                (photoPan.x + panChange.x).coerceIn(-maxX, maxX),
                                (photoPan.y + panChange.y).coerceIn(-maxY, maxY),
                            )
                        }
                    },
                )
                if (compare) {
                    SharedComparisonDivider(
                        fraction = divider,
                        canvasWidthPx = canvasWidth,
                        onFractionChange = { divider = it },
                        modifier = Modifier.align(Alignment.TopStart).offset {
                            IntOffset((canvasWidth * divider - 24.dp.toPx()).roundToInt(), 0)
                        },
                    )
                    BrandCanvasLabel("Before", Modifier.align(Alignment.TopStart))
                    BrandCanvasLabel("After", Modifier.align(Alignment.TopEnd), accent = true)
                } else {
                    BrandCanvasLabel(if (state.displayedPreviewIsCurrent) "After" else "Original", Modifier.align(Alignment.TopStart))
                }
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp)
                        .background(Color(0xD91B1B26), RoundedCornerShape(12.dp)).padding(3.dp),
                ) {
                    PhotoScaleButton("Fit", selected = !fillPhoto) {
                        fillPhoto = false; photoZoom = 1f; photoPan = Offset.Zero
                    }
                    PhotoScaleButton("Fill", selected = fillPhoto) {
                        fillPhoto = true; photoZoom = 1f; photoPan = Offset.Zero
                    }
                }
            }
            if (visible == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = RestoreXAccent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(state.stage ?: "Preparing your photo", color = RestoreXText, fontSize = 12.sp)
                }
            }
            if (state.isWorking && visible != null) {
                Column(
                    Modifier.align(Alignment.Center).fillMaxWidth(.78f)
                        .background(Color(0x80000000), RoundedCornerShape(16.dp)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(state.progress?.label ?: state.stage ?: "Working on your photo", color = Color.White,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    state.progress?.detail?.let {
                        Text(it, color = Color.White.copy(alpha = .86f), fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    val progress = state.progress?.fraction
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = RestoreXAccent,
                            trackColor = Color.White.copy(alpha = .25f),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = RestoreXAccent,
                            trackColor = Color.White.copy(alpha = .25f),
                        )
                    }
                    TextButton(onClick = onCancel) { Text("Cancel", color = Color.White, fontSize = 11.sp) }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(.56f).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.analysis?.let { analysis ->
                val insight = remember(analysis) { sceneInsight(state) }
                Row(
                    Modifier.fillMaxWidth().background(RestoreXPanel, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF302A59), RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(42.dp).background(
                            Brush.linearGradient(listOf(RestoreXAccentViolet, RestoreXAccent)), RoundedCornerShape(12.dp),
                        ), contentAlignment = Alignment.Center,
                    ) { Text("✦", color = Color.White, fontSize = 20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("SCENE DETECTED", color = RestoreXMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        Text(insight.first, color = RestoreXText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(insight.second, color = RestoreXMuted, fontSize = 10.sp, maxLines = 1)
                    }
                    Text("✓ Auto", color = Color.White, fontSize = 11.sp, modifier = Modifier
                        .background(RestoreXAccent.copy(alpha = .32f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                EnhancementLevel.entries.forEach { level ->
                    val selected = state.selectedLevel == level
                    Row(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(22.dp))
                            .background(if (selected) RestoreXAccentViolet.copy(alpha = .72f) else RestoreXPanel)
                            .border(1.dp, if (selected) RestoreXAccent else Color(0xFF302A59), RoundedCornerShape(22.dp))
                            .clickable(enabled = state.modeSelectionEnabled && state.phase != SmartMagicPhase.SAVING) { onSelectLevel(level) }
                            .semantics { contentDescription = "${level.label()} enhancement"; stateDescription = if (selected) "Selected" else "Not selected" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(18.dp).border(1.5.dp, if (selected) Color.White else RestoreXMuted, CircleShape), contentAlignment = Alignment.Center) {
                            if (selected) Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(level.label(), color = RestoreXText, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }

            if (state.displayedPreviewIsCurrent) {
                val changes = remember(state.adjustments, state.parameters) { visibleChanges(state) }
                Column(
                    Modifier.fillMaxWidth().background(RestoreXPanel, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF302A59), RoundedCornerShape(16.dp)).padding(12.dp),
                ) {
                    Text("What changed", color = RestoreXText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    changes.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { change ->
                                ChangeChip(change, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                }
                if (state.faces.isNotEmpty()) {
                    BrandSecondaryButton("Group Face Light", Modifier.fillMaxWidth()) { showFaceLight = true }
                }
                if (state.canUndoFaceLight) {
                    BrandSecondaryButton("↶  Undo Face Light", Modifier.fillMaxWidth(), onClick = onUndoFaceLight)
                }
            }

            BrandPrimaryButton(
                label = when (state.phase) {
                    SmartMagicPhase.SAVING -> "Saving full-resolution photo…"
                    SmartMagicPhase.SAVED -> "Saved to Gallery"
                    else -> "↓   Save Photo"
                },
                enabled = state.phase == SmartMagicPhase.READY && state.displayedPreviewIsCurrent,
                onClick = onSave,
            )

            if (state.phase == SmartMagicPhase.SAVED && state.savedUri != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrandSecondaryButton("Open", Modifier.weight(1f)) { onOpen(state.savedUri, state.savedMimeType) }
                    BrandSecondaryButton("Share", Modifier.weight(1f)) { onShare(state.savedUri, state.savedMimeType) }
                }
            }
            state.message?.let { Text(it, color = RestoreXMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            TextButton(onClick = if (state.isWorking) onCancel else onChooseAnother, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (state.isWorking) "Cancel" else "Choose Another Photo", color = RestoreXMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GroupFaceLightScreen(
    photo: RasterImage?,
    beforePhoto: RasterImage?,
    faces: List<FaceEvidence>,
    working: Boolean,
    onPreview: (List<FaceLightAdjustment>) -> Unit,
    onApply: (List<FaceLightAdjustment>) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var selectedFaceId by rememberSaveable(faces) { mutableStateOf(faces.firstOrNull()?.id) }
    val lights = remember(faces) { mutableStateMapOf<String, FaceLightAdjustment>() }
    val selectedFace = faces.firstOrNull { it.id == selectedFaceId }
    val selectedLight = selectedFaceId?.let { lights[it] }
    val orderedLights = faces.mapNotNull { lights[it.id] }
    LaunchedEffect(faces) {
        faces.firstOrNull()?.let { face ->
            if (lights[face.id] == null) lights[face.id] = FaceLightAdjustment(face.id, FaceLightStyle.LIFT, .6f)
        }
    }
    LaunchedEffect(orderedLights) {
        if (orderedLights.isEmpty()) return@LaunchedEffect
        delay(90)
        onPreview(orderedLights)
    }
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF080E29), RestoreXBackground, Color(0xFF040515))),
        ).statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", color = RestoreXText, fontSize = 36.sp)
            }
            Text("Group Face Light", color = RestoreXText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.fillMaxWidth().weight(.48f).padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color.Black)
                .border(1.dp, Color(0xFF302A59), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            photo?.let { image ->
                TorchCanvas(
                    photo = image,
                    beforePhoto = beforePhoto,
                    face = selectedFace,
                    light = selectedLight,
                    onSourceChange = { x, y ->
                        selectedFaceId?.let { id -> lights[id] = (lights[id]
                            ?: FaceLightAdjustment(id, FaceLightStyle.LIFT, .6f)).copy(sourceX = x, sourceY = y) }
                    },
                )
            }
            if (working) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RestoreXAccent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().weight(.52f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .background(RestoreXPanel, RoundedCornerShape(20.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Face Light", color = RestoreXText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (faces.size > 1) "The darkest detected face is selected. Tap another face if needed."
                    else "Adjust soft, skin-aware light for the detected face.",
                    color = RestoreXMuted,
                    fontSize = 11.sp,
                )
                if (photo != null && faces.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        faces.forEachIndexed { index, face ->
                            FaceThumbnail(
                                photo, face, index + 1, face.id == selectedFaceId, lights.containsKey(face.id),
                            ) {
                                selectedFaceId = face.id
                                if (lights[face.id] == null) lights[face.id] = FaceLightAdjustment(face.id, FaceLightStyle.LIFT, .6f)
                            }
                        }
                    }
                } else {
                    Text("No reliable face was detected in this photo.", color = RestoreXMuted, fontSize = 11.sp)
                }
                Text("Light character", color = RestoreXText, fontWeight = FontWeight.SemiBold)
                Text("Lift is neutral, Soft is diffused, and Dramatic adds stronger direction. Strength is controlled separately below.", color = RestoreXMuted, fontSize = 10.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FaceLightStyle.entries.forEach { option ->
                        FaceLightStyleButton(option, selectedLight?.style == option, Modifier.weight(1f)) {
                            selectedFaceId?.let { id -> lights[id] = (lights[id]
                                ?: FaceLightAdjustment(id, option, .6f)).copy(style = option) }
                        }
                    }
                }
                Text("Light direction", color = RestoreXText, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Left" to Offset(-1.25f, 0f), "Front" to Offset.Zero, "Top" to Offset(0f, -1.25f), "Right" to Offset(1.25f, 0f)).forEach { (label, source) ->
                        val active = selectedLight != null && kotlin.math.abs(selectedLight.sourceX - source.x) < .08f && kotlin.math.abs(selectedLight.sourceY - source.y) < .08f
                        Box(
                            Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (active) RestoreXAccent else Color(0xFF171633))
                                .border(1.dp, if (active) RestoreXAccent else Color(0xFF3A355E), RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedFaceId?.let { id -> lights[id] = (lights[id]
                                        ?: FaceLightAdjustment(id, FaceLightStyle.LIFT, .6f)).copy(sourceX = source.x, sourceY = source.y) }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = Color.White, fontSize = 10.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Beam width", color = RestoreXText, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    val beam = selectedLight?.beamWidth ?: .65f
                    Text("${(beam * 100).roundToInt()}% · ${if (beam < .48f) "Narrow" else "Broad"}", color = RestoreXText)
                }
                Slider(
                    value = selectedLight?.beamWidth ?: .65f,
                    onValueChange = { value -> selectedFaceId?.let { id -> lights[id] = (lights[id]
                        ?: FaceLightAdjustment(id, FaceLightStyle.LIFT, .6f)).copy(beamWidth = value) } },
                    valueRange = 0f..1f,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Strength", color = RestoreXText, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${((selectedLight?.strength ?: .6f) * 100).roundToInt()}", color = RestoreXText)
                }
                Slider(
                    value = selectedLight?.strength ?: .6f,
                    onValueChange = { value -> selectedFaceId?.let { id -> lights[id] = (lights[id]
                        ?: FaceLightAdjustment(id, FaceLightStyle.LIFT, .6f)).copy(strength = value) } },
                    valueRange = 0f..1f,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Drag the light on the photo. Pinch or drag elsewhere to move the image.", color = RestoreXMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedFaceId?.let { id -> lights.remove(id) } }) { Text("Remove", color = RestoreXMuted) }
                }
            }
            Spacer(Modifier.height(10.dp))
            BrandPrimaryButton(
                label = if (working) "Preparing lights…" else "✓   Apply ${orderedLights.size} ${if (orderedLights.size == 1) "Light" else "Lights"}",
                enabled = orderedLights.isNotEmpty() && !working,
                onClick = { onApply(orderedLights) },
            )
        }
    }
}

@Composable
private fun TorchCanvas(
    photo: RasterImage,
    beforePhoto: RasterImage?,
    face: FaceEvidence?,
    light: FaceLightAdjustment?,
    onSourceChange: (Float, Float) -> Unit,
) {
    var zoom by remember(photo) { mutableFloatStateOf(1f) }
    var pan by remember(photo) { mutableStateOf(Offset.Zero) }
    var divider by remember(photo) { mutableFloatStateOf(.5f) }
    val latestLight by rememberUpdatedState(light)
    val latestSourceChange by rememberUpdatedState(onSourceChange)
    BoxWithConstraints(
        Modifier.fillMaxSize().clipToBounds().pointerInput(photo) {
            // A single finger belongs exclusively to a light handle. Requiring two fingers
            // here prevents the photograph from stealing a beam drag.
            awaitEachGesture {
                var navigating = false
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        navigating = true
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                        pan = if (zoom <= 1.01f) Offset.Zero else pan + panChange
                        event.changes.forEach { it.consume() }
                    } else if (navigating) {
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        },
    ) {
        val density = LocalDensity.current
        val canvasWidth = with(density) { maxWidth.toPx() }
        val canvasHeight = with(density) { maxHeight.toPx() }
        val cropScale = max(canvasWidth / photo.width, canvasHeight / photo.height)
        val imageWidth = photo.width * cropScale
        val imageHeight = photo.height * cropScale
        val imageOffsetX = (canvasWidth - imageWidth) * .5f
        val imageOffsetY = (canvasHeight - imageHeight) * .5f
        val faceWidth = face?.let { (it.bounds.right - it.bounds.left) * imageWidth } ?: 1f
        val faceHeight = face?.let { (it.bounds.bottom - it.bounds.top) * imageHeight } ?: 1f
        val faceCenterX = face?.let { imageOffsetX + (it.bounds.left + it.bounds.right) * .5f * imageWidth } ?: canvasWidth * .5f
        val faceCenterY = face?.let { imageOffsetY + (it.bounds.top + it.bounds.bottom) * .5f * imageHeight } ?: canvasHeight * .5f
        val frontFacing = light != null && kotlin.math.abs(light.sourceX) < .08f && kotlin.math.abs(light.sourceY) < .08f
        val displayedSourceX = if (frontFacing) 0f else light?.sourceX ?: 0f
        val displayedSourceY = if (frontFacing) 1.45f else light?.sourceY ?: 0f
        val sourceX = faceCenterX + displayedSourceX * faceWidth * .72f
        val sourceY = faceCenterY + displayedSourceY * faceHeight * .72f
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom
                translationX = pan.x; translationY = pan.y
            },
        ) {
            Image(
                bitmap = rememberBrandRaster(photo),
                contentDescription = "Group face light photograph",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
            beforePhoto?.let { before ->
                Image(
                    bitmap = rememberBrandRaster(before),
                    contentDescription = "Face light before photo",
                    modifier = Modifier.fillMaxSize().drawWithContent {
                        clipRect(right = size.width * divider) { this@drawWithContent.drawContent() }
                    },
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
            }
            if (face != null) FaceSelectionRing(photo, face)
            if (face != null && light != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val source = Offset(sourceX, sourceY)
                    val target = Offset(faceCenterX, faceCenterY)
                    val delta = target - source
                    val length = kotlin.math.sqrt(delta.x * delta.x + delta.y * delta.y).coerceAtLeast(1f)
                    val perpendicular = Offset(-delta.y / length, delta.x / length)
                    val sourceHalfWidth = 3.dp.toPx()
                    val styleSpread = when (light.style) {
                        FaceLightStyle.LIFT -> 1f
                        FaceLightStyle.SOFT -> 1.08f
                        FaceLightStyle.DRAMATIC -> .94f
                    }
                    // This is the exact footprint used by FaceLightRenderer: 50% is one face
                    // diameter and 100% is two. The on-photo guide therefore predicts export.
                    val beamRadius = light.beamWidth.coerceAtLeast(.18f) * styleSpread
                    val targetRadiusX = faceWidth * beamRadius
                    val targetRadiusY = faceHeight * beamRadius
                    val targetHalfWidth = max(targetRadiusX, targetRadiusY)
                    val cone = Path().apply {
                        moveTo(source.x + perpendicular.x * sourceHalfWidth, source.y + perpendicular.y * sourceHalfWidth)
                        lineTo(target.x + perpendicular.x * targetHalfWidth, target.y + perpendicular.y * targetHalfWidth)
                        lineTo(target.x - perpendicular.x * targetHalfWidth, target.y - perpendicular.y * targetHalfWidth)
                        lineTo(source.x - perpendicular.x * sourceHalfWidth, source.y - perpendicular.y * sourceHalfWidth)
                        close()
                    }
                    // A true 50%-opacity beam guide: visible enough to place precisely while
                    // still leaving the photograph readable underneath.
                    drawPath(cone, RestoreXAccent.copy(alpha = .50f))
                    drawLine(RestoreXAccent.copy(alpha = .72f), source + perpendicular * sourceHalfWidth, target + perpendicular * targetHalfWidth, 1.5.dp.toPx())
                    drawLine(RestoreXAccent.copy(alpha = .72f), source - perpendicular * sourceHalfWidth, target - perpendicular * targetHalfWidth, 1.5.dp.toPx())
                    drawOval(
                        RestoreXAccent.copy(alpha = .08f + light.strength * .28f),
                        topLeft = Offset(target.x - targetRadiusX, target.y - targetRadiusY),
                        size = androidx.compose.ui.geometry.Size(targetRadiusX * 2f, targetRadiusY * 2f),
                    )
                    drawOval(
                        RestoreXAccent.copy(alpha = .9f),
                        topLeft = Offset(target.x - targetRadiusX, target.y - targetRadiusY),
                        size = androidx.compose.ui.geometry.Size(targetRadiusX * 2f, targetRadiusY * 2f),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
                Box(
                    Modifier.offset { IntOffset((sourceX - 28.dp.toPx()).roundToInt(), (sourceY - 28.dp.toPx()).roundToInt()) }
                        .size(56.dp)
                        .pointerInput(face.id) {
                            var dragX = 0f; var dragY = 0f
                            detectDragGestures(
                                onDragStart = {
                                    val current = latestLight
                                    val isFront = current != null && kotlin.math.abs(current.sourceX) < .08f && kotlin.math.abs(current.sourceY) < .08f
                                    dragX = if (isFront) 0f else current?.sourceX ?: 0f
                                    dragY = if (isFront) 1.45f else current?.sourceY ?: 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val unitX = (faceWidth * .72f).coerceAtLeast(1f)
                                    val unitY = (faceHeight * .72f).coerceAtLeast(1f)
                                    val margin = 18.dp.toPx()
                                    val minX = ((margin - faceCenterX) / unitX).coerceAtLeast(-12f)
                                    val maxX = ((canvasWidth - margin - faceCenterX) / unitX).coerceAtMost(12f)
                                    val minY = ((margin - faceCenterY) / unitY).coerceAtLeast(-12f)
                                    val maxY = ((canvasHeight - margin - faceCenterY) / unitY).coerceAtMost(12f)
                                    dragX = (dragX + dragAmount.x / unitX).coerceIn(minX, maxX)
                                    dragY = (dragY + dragAmount.y / unitY).coerceIn(minY, maxY)
                                    latestSourceChange(dragX, dragY)
                                },
                            )
                        }.semantics { contentDescription = "Movable light for selected face" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(32.dp).background(RestoreXAccent, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("☀", color = Color.White, fontSize = 17.sp) }
                }
            }
        }
        SharedComparisonDivider(
            fraction = divider,
            canvasWidthPx = canvasWidth.roundToInt().coerceAtLeast(1),
            onFractionChange = { divider = it },
            modifier = Modifier.align(Alignment.TopStart).offset {
                IntOffset((canvasWidth * divider - 24.dp.toPx()).roundToInt(), 0)
            },
        )
        BrandCanvasLabel("Before", Modifier.align(Alignment.TopStart))
        BrandCanvasLabel("After", Modifier.align(Alignment.TopEnd), accent = true)
        if (light != null) {
            Text(
                "Intensity ${(light.strength * 100).roundToInt()}%  •  Beam ${(light.beamWidth * 100).roundToInt()}% ${if (light.beamWidth < .48f) "Narrow" else "Broad"}",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.FaceSelectionRing(photo: RasterImage, face: FaceEvidence) {
    Canvas(Modifier.fillMaxSize()) {
        val scale = max(size.width / photo.width, size.height / photo.height)
        val imageWidth = photo.width * scale; val imageHeight = photo.height * scale
        val offsetX = (size.width - imageWidth) * .5f; val offsetY = (size.height - imageHeight) * .5f
        val left = offsetX + face.bounds.left * imageWidth
        val top = offsetY + face.bounds.top * imageHeight
        val width = (face.bounds.right - face.bounds.left) * imageWidth
        val height = (face.bounds.bottom - face.bounds.top) * imageHeight
        drawOval(
            color = RestoreXAccent,
            topLeft = Offset(left - width * .08f, top - height * .08f),
            size = androidx.compose.ui.geometry.Size(width * 1.16f, height * 1.16f),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun FaceThumbnail(photo: RasterImage, face: FaceEvidence, number: Int, selected: Boolean, hasLight: Boolean, onClick: () -> Unit) {
    val bitmap = rememberBrandRaster(photo)
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier.size(72.dp, 82.dp).clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) RestoreXAccent else Color(0xFF504A73), shape)
            .clickable(onClick = onClick).semantics {
                contentDescription = "Face $number${if (selected) ", selected" else ""}"
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val padX = (face.bounds.right - face.bounds.left) * .28f
            val padY = (face.bounds.bottom - face.bounds.top) * .20f
            val left = ((face.bounds.left - padX) * photo.width).toInt().coerceIn(0, photo.width - 1)
            val top = ((face.bounds.top - padY) * photo.height).toInt().coerceIn(0, photo.height - 1)
            val right = ((face.bounds.right + padX) * photo.width).toInt().coerceIn(left + 1, photo.width)
            val bottom = ((face.bounds.bottom + padY) * photo.height).toInt().coerceIn(top + 1, photo.height)
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(left, top),
                srcSize = IntSize(right - left, bottom - top),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.High,
            )
        }
        if (hasLight) {
            Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).background(RestoreXAccent, CircleShape),
                contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun FaceLightStyleButton(style: FaceLightStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val icon = when (style) {
        FaceLightStyle.LIFT -> "☀"
        FaceLightStyle.SOFT -> "♧"
        FaceLightStyle.DRAMATIC -> "◐"
    }
    Column(
        modifier.height(70.dp).clip(RoundedCornerShape(13.dp))
            .background(if (selected) Brush.linearGradient(listOf(RestoreXAccentViolet, RestoreXAccent)) else Brush.linearGradient(listOf(RestoreXPanel, RestoreXPanel)))
            .border(1.dp, if (selected) RestoreXAccent else Color(0xFF3A355E), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick).semantics { contentDescription = "${style.label} face light" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, color = Color.White, fontSize = 24.sp)
        Text(style.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChangeChip(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier.height(42.dp).clip(RoundedCornerShape(11.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF201344), Color(0xFF171336))))
            .border(1.dp, Color(0xFF3B2864), RoundedCornerShape(11.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).background(RestoreXAccent.copy(alpha = .2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("✦", color = RestoreXAccent, fontSize = 12.sp) }
        Spacer(Modifier.width(7.dp))
        Text(label, color = RestoreXText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun sceneInsight(state: SmartMagicUiState): Pair<String, String> {
    val s = state.analysis?.scenes ?: return "Photo" to "Smart correction ready"
    val portrait = s.portrait >= .35f
    val outdoor = s.landscape >= .38f && s.document < .5f
    val primary = when {
        s.document >= .58f -> "Document"
        portrait && outdoor -> "Outdoor Portrait"
        portrait -> "Portrait"
        outdoor -> "Outdoor Scene"
        s.lowLight >= .52f -> "Low-Light Scene"
        else -> "General Photo"
    }
    val lighting = when {
        s.backlit >= .34f -> "Backlit"
        s.lowLight >= .52f -> "Low Light"
        s.highKey >= .62f -> "High Key"
        s.lowKey >= .62f -> "Low Key"
        else -> null
    }
    val label = if (lighting == null || primary.contains(lighting, true)) primary else "$primary • $lighting"
    val detail = when {
        portrait && s.backlit >= .34f -> "Face light balanced · Highlights protected"
        portrait -> "Skin protected · Natural detail retained"
        s.highKey >= .62f -> "Bright whites protected · Contrast restored"
        s.lowLight >= .52f -> "Shadows recovered · Noise controlled"
        else -> "Light, color, and detail balanced"
    }
    return label to detail
}

private fun visibleChanges(state: SmartMagicUiState): List<String> {
    val labels = buildList {
        state.adjustments.forEach { adjustment ->
            val source = adjustment.label.lowercase()
            val label = when {
                "face" in source || "person" in source || "skin" in source -> "Face Light"
                "highlight" in source || "white" in source -> "Highlight Recovery"
                "shadow" in source || "midtone" in source || "exposure" in source -> "Light Balance"
                "white balance" in source || "temperature" in source -> "Warm Balance"
                "dehaze" in source -> "Sky Cleanup"
                "vibrance" in source || "saturation" in source -> "Color Balance"
                "clarity" in source || "detail" in source || "sharp" in source -> "Detail Recovery"
                "noise" in source -> "Noise Cleanup"
                else -> null
            }
            if (label != null && label !in this) add(label)
        }
        state.parameters?.let { p ->
            if (p.dehaze > .005f && "Sky Cleanup" !in this) add("Sky Cleanup")
            if ((p.vibrance > .01f || p.saturation > .01f) && "Color Balance" !in this) add("Color Balance")
        }
    }
    return labels.take(4).ifEmpty { listOf("Natural Tone", "Detail Protection") }
}

@Composable
private fun BrandPrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(15.dp))
            .background(if (enabled) Brush.horizontalGradient(listOf(RestoreXAccentViolet, RestoreXAccent)) else Brush.horizontalGradient(listOf(RestoreXDisabled, RestoreXDisabled)))
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BrandSecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(44.dp).clip(RoundedCornerShape(13.dp)).background(RestoreXPanel)
        .border(1.dp, Color(0xFF302A59), RoundedCornerShape(13.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = RestoreXText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BrandCanvasLabel(label: String, modifier: Modifier, accent: Boolean = false) {
    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(12.dp).background(if (accent) RestoreXAccent else Color(0xB8000000), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
private fun BrandComparisonPhoto(
    processed: RasterImage,
    original: RasterImage?,
    compare: Boolean,
    divider: Float,
    fillPhoto: Boolean,
    zoom: Float,
    pan: Offset,
    onTransform: (pan: Offset, zoom: Float) -> Unit,
) {
    val processedImage = rememberBrandRaster(processed)
    val originalImage = original?.let { rememberBrandRaster(it) }
    val scale = if (fillPhoto) ContentScale.Crop else ContentScale.Fit
    Box(
        Modifier.fillMaxSize().testTag("smart_magic_comparison")
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ -> onTransform(panChange, zoomChange) }
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = pan.x
                translationY = pan.y
            },
        ) {
            Image(
                bitmap = processedImage,
                contentDescription = "Enhanced photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = scale,
                filterQuality = FilterQuality.High,
            )
            if (compare && originalImage != null) {
                Image(
                    bitmap = originalImage,
                    contentDescription = "Original photo",
                    modifier = Modifier.fillMaxSize().drawWithContent {
                        clipRect(right = size.width * divider) { this@drawWithContent.drawContent() }
                    },
                    contentScale = scale,
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
}

@Composable
private fun PhotoScaleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).width(52.dp).clip(RoundedCornerShape(9.dp))
            .background(if (selected) RestoreXAccent else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$label photo to comparison canvas"
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun rememberBrandRaster(raster: RasterImage): ImageBitmap {
    val bitmap = remember(raster) { Bitmap.createBitmap(raster.argb, raster.width, raster.height, Bitmap.Config.ARGB_8888) }
    DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
    return remember(bitmap) { bitmap.asImageBitmap() }
}

private fun EnhancementLevel.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
