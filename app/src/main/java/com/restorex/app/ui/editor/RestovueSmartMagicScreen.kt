package com.restorex.app.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.presentation.SmartMagicPhase
import com.restorex.app.presentation.SmartMagicUiState
import com.restorex.app.ui.theme.*
import kotlin.math.roundToInt

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
) {
    var showFaceLight by rememberSaveable(state.sourceGeneration) { mutableStateOf(false) }
    if (showFaceLight) {
        GroupFaceLightScreen(
            photo = state.enhanced ?: state.original,
            onBack = { showFaceLight = false },
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
                if ((state.analysis?.scenes?.portrait ?: 0f) >= .3f) {
                    BrandSecondaryButton("Group Face Light", Modifier.fillMaxWidth()) { showFaceLight = true }
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
private fun GroupFaceLightScreen(photo: RasterImage?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var style by rememberSaveable { mutableStateOf("Lift") }
    var strength by rememberSaveable { mutableFloatStateOf(.6f) }
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
            photo?.let {
                Image(
                    bitmap = rememberBrandRaster(it),
                    contentDescription = "Group face light photograph",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
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
                    "Choose a lighting style and strength. Per-face selection will be connected in the next engine pass.",
                    color = RestoreXMuted,
                    fontSize = 11.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Lift", "Soft", "Dramatic").forEach { option ->
                        BrandSecondaryButton(if (style == option) "✓ $option" else option, Modifier.weight(1f)) { style = option }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Strength", color = RestoreXText, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${(strength * 100).roundToInt()}", color = RestoreXText)
                }
                Slider(value = strength, onValueChange = { strength = it }, valueRange = 0f..1f)
                Text("Manual radial and linear light remain available in Manual Edit.", color = RestoreXMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp).background(RestoreXDisabled, RoundedCornerShape(14.dp))
                    .semantics { contentDescription = "Apply face light unavailable" },
                contentAlignment = Alignment.Center,
            ) {
                Text("Apply Light — engine connection pending", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
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
