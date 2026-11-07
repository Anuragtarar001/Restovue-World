package com.restorex.app.ui.editor

import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.restorex.app.domain.engine.EditorCoordinates
import com.restorex.app.domain.engine.CanvasScaleMode
import com.restorex.app.domain.engine.FloatPoint
import com.restorex.app.domain.engine.FloatSize
import com.restorex.app.domain.model.CropAspect
import com.restorex.app.domain.model.AutoEnhanceMode
import com.restorex.app.domain.model.ChannelBalance
import com.restorex.app.domain.model.ExportOptions
import com.restorex.app.domain.model.ExportFormat
import com.restorex.app.domain.model.FilterCategory
import com.restorex.app.domain.model.HslColorRange
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.model.BrushEditType
import com.restorex.app.domain.model.BrushStroke
import com.restorex.app.domain.model.LinearLight
import com.restorex.app.domain.model.LocalEditOperation
import com.restorex.app.domain.model.NormalizedPoint
import com.restorex.app.domain.model.RadialLight
import com.restorex.app.domain.model.NormalizedRect
import com.restorex.app.domain.model.PerspectiveQuad
import com.restorex.app.domain.model.LiquifyStroke
import com.restorex.app.domain.model.LiquifyType
import com.restorex.app.domain.model.BackgroundBrushMode
import com.restorex.app.domain.model.BackgroundFill
import com.restorex.app.domain.model.BackgroundFillMode
import com.restorex.app.domain.model.BackgroundMaskStroke
import com.restorex.app.domain.model.CreativeLayer
import com.restorex.app.domain.model.FrameSettings
import com.restorex.app.domain.model.FrameStyle
import com.restorex.app.domain.model.ShapeLayer
import com.restorex.app.domain.model.ShapeType
import com.restorex.app.domain.model.TextFont
import com.restorex.app.domain.model.TextLayer
import com.restorex.app.domain.model.StickerLayer
import com.restorex.app.domain.model.UtilityMode
import com.restorex.app.domain.model.UtilityOptions
import com.restorex.app.domain.model.BatchOptions
import com.restorex.app.domain.model.BatchFormat
import com.restorex.app.R
import com.restorex.app.presentation.EditorUiState
import com.restorex.app.presentation.EditorViewModel
import com.restorex.app.presentation.SmartMagicViewModel
import com.restorex.app.presentation.EditorChromeState
import com.restorex.app.presentation.PreviewSizing
import com.restorex.app.presentation.RetouchTool
import com.restorex.app.presentation.RetouchUiState
import com.restorex.app.presentation.LiquifyUiState
import com.restorex.app.presentation.BackgroundUiState
import com.restorex.app.ui.theme.RestoreXAccent
import com.restorex.app.ui.theme.RestoreXBackground
import com.restorex.app.ui.theme.RestoreXDisabled
import com.restorex.app.ui.theme.RestoreXMuted
import com.restorex.app.ui.theme.RestoreXPanel
import com.restorex.app.ui.theme.RestoreXPanelRaised
import com.restorex.app.ui.theme.RestoreXText
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class EditorTool(val label: String, val icon: AppIcon, val available: Boolean) {
    CROP("Crop", AppIcon.Crop, true),
    ENHANCE("Enhance", AppIcon.Enhance, true),
    LIGHT("Light", AppIcon.Light, true),
    COLOR("Color", AppIcon.Color, true),
    FILTERS("Filters", AppIcon.Filters, true),
    FILM("Film", AppIcon.Filters, true),
    DETAILS("Details", AppIcon.Details, true),
    RETOUCH("Retouch", AppIcon.Retouch, true),
    LIQUIFY("Liquify", AppIcon.Retouch, true),
    BACKGROUND("Background", AppIcon.Color, true),
    CREATIVE("Creative", AppIcon.Image, true),
    UTILITIES("Utilities", AppIcon.Image, true),
    GEOMETRY("Geometry", AppIcon.Crop, true),
}
private enum class ToolCategory(val label: String, val icon: AppIcon) {
    CROP("Crop", AppIcon.Crop), ADJUST("Adjust", AppIcon.Light), FILTERS("Filters", AppIcon.Filters), DETAILS("Details", AppIcon.Details),
    RETOUCH("Retouch", AppIcon.Retouch), GEOMETRY("Geometry", AppIcon.Rotate), CREATIVE("Creative", AppIcon.Image), UTILITIES("Utilities", AppIcon.More),
}
private val EditorTool.category: ToolCategory get() = when (this) {
    EditorTool.CROP -> ToolCategory.CROP
    EditorTool.ENHANCE, EditorTool.LIGHT, EditorTool.COLOR -> ToolCategory.ADJUST
    EditorTool.FILTERS, EditorTool.FILM -> ToolCategory.FILTERS
    EditorTool.DETAILS -> ToolCategory.DETAILS
    EditorTool.RETOUCH, EditorTool.BACKGROUND -> ToolCategory.RETOUCH
    EditorTool.LIQUIFY, EditorTool.GEOMETRY -> ToolCategory.GEOMETRY
    EditorTool.CREATIVE -> ToolCategory.CREATIVE
    EditorTool.UTILITIES -> ToolCategory.UTILITIES
}
private enum class GeometryGridMode(val label: String) { NONE("No grid"), THIRDS("Thirds"), GOLDEN("Golden") }
private enum class HomeUtility(val label: String, val icon: AppIcon) {
    COLLAGE("Collage", AppIcon.Collage),
    HDR("HDR", AppIcon.Layers),
    PANORAMA("Panorama", AppIcon.Image),
    BATCH("Batch Edit", AppIcon.MultipleImages),
    PASSPORT("Passport Photo", AppIcon.IdCard),
    DOCUMENT("Document Cleanup", AppIcon.Document),
    COMPRESS("Compress & Convert", AppIcon.Swap),
    MORE("More Tools", AppIcon.More),
}

@Composable
fun RestoreXEditorScreen(viewModel: EditorViewModel, smartMagicViewModel: SmartMagicViewModel, onExit: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val smartMagicState by smartMagicViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedTool by remember { mutableStateOf(EditorTool.LIGHT) }
    var holdOriginal by remember { mutableStateOf(false) }
    var splitComparison by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSmartMagic by rememberSaveable { mutableStateOf(false) }
    var toolPanelCollapsed by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var showResetAllConfirmation by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf(ExportOptions(ExportFormat.JPEG)) }
    var pendingShare by remember { mutableStateOf(false) }
    var showUtilityChooser by remember { mutableStateOf(false) }
    var utilityChooserTitle by remember { mutableStateOf("Utilities") }
    var utilityChooserModes by remember { mutableStateOf(UtilityMode.entries.toList()) }
    var showUtilityOptions by remember { mutableStateOf(false) }
    var pendingUtilityMode by remember { mutableStateOf<UtilityMode?>(null) }
    var pendingUtilitySources by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingUtilityOptions by remember { mutableStateOf(UtilityOptions()) }
    var showBatchOptions by remember { mutableStateOf(false) }
    var pendingBatchOptions by remember { mutableStateOf(BatchOptions()) }
    var geometryGrid by remember { mutableStateOf(GeometryGridMode.THIRDS) }
    val context = LocalContext.current
    val windowSize = LocalWindowInfo.current.containerSize
    val previewMaxDimension = remember(windowSize) {
        PreviewSizing.maxDimensionForViewport(
            windowSize.width.coerceAtLeast(1),
            windowSize.height.coerceAtLeast(1),
        )
    }
    val developerUrl = stringResource(R.string.developer_website_url)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.importImage(it.toString(), previewMaxDimension) }
    }
    val smartMagicPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            showSmartMagic = true
            smartMagicViewModel.process(it.toString())
        }
    }
    val jpegExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        uri?.let { viewModel.export(it.toString(), pendingExport, pendingShare) }
    }
    val pngExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let { viewModel.export(it.toString(), pendingExport, pendingShare) }
    }
    val webpExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp")) { uri ->
        uri?.let { viewModel.export(it.toString(), pendingExport, pendingShare) }
    }
    val lutImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importLut(it.toString()) }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.setBackgroundImage(it.toString()) }
    }
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addStickerLayer(it.toString()) }
    }
    val utilityDestination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val mode = pendingUtilityMode
        if (uri != null && mode != null) viewModel.runUtility(mode, pendingUtilitySources, uri.toString(), pendingUtilityOptions)
    }
    val batchDestination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.runBatch(pendingUtilitySources, uri.toString(), pendingBatchOptions)
    }
    val utilityPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        val mode = pendingUtilityMode ?: return@rememberLauncherForActivityResult
        if (uris.size !in mode.minimumImages..mode.maximumImages) {
            viewModel.showMessage("${mode.label} needs ${mode.minimumImages}${if (mode.minimumImages == mode.maximumImages) "" else "-${mode.maximumImages}"} image(s)")
        } else {
            pendingUtilitySources = uris.map(Uri::toString)
            if (mode == UtilityMode.BATCH) showBatchOptions = true else showUtilityOptions = true
        }
    }

    if (showSmartMagic) {
        SmartMagicScreen(
            state = smartMagicState,
            onBack = { smartMagicViewModel.reset(); showSmartMagic = false },
            onChooseAnother = { smartMagicPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onSave = smartMagicViewModel::save,
            onCancel = smartMagicViewModel::cancel,
            onOpen = { uri, mime ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).apply { setDataAndType(uri.toUri(), mime ?: "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
                    .onFailure { Toast.makeText(context, "No app can open this photo", Toast.LENGTH_SHORT).show() }
            },
            onShare = { uri, mime ->
                runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = mime ?: "image/*"; putExtra(Intent.EXTRA_STREAM, uri.toUri()); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share Smart Magic photo"))
                }.onFailure { Toast.makeText(context, "No app can share this photo", Toast.LENGTH_SHORT).show() }
            },
        )
        return
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        val share = Intent(Intent.ACTION_SEND).apply {
            type = state.shareMimeType ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Share Restovue export"))
        viewModel.consumeShareRequest()
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { options, share ->
                showExportDialog = false
                pendingExport = options
                pendingShare = share
                when (options.format) {
                    ExportFormat.JPEG -> jpegExporter.launch(exportName("jpg"))
                    ExportFormat.PNG -> pngExporter.launch(exportName("png"))
                    ExportFormat.WEBP -> webpExporter.launch(exportName("webp"))
                }
            },
        )
    }

    if (showUtilityChooser) UtilityChooserDialog(
        title = utilityChooserTitle,
        modes = utilityChooserModes,
        onDismiss = { showUtilityChooser = false },
        onSelected = { mode ->
            showUtilityChooser = false; pendingUtilityMode = mode
            utilityPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
    if (showUtilityOptions) UtilityOptionsDialog(
        mode = requireNotNull(pendingUtilityMode),
        onDismiss = { showUtilityOptions = false },
        onRun = { options ->
            showUtilityOptions = false; pendingUtilityOptions = options
            utilityDestination.launch("Restovue_${pendingUtilityMode!!.name.lowercase()}_${System.currentTimeMillis()}.png")
        },
    )
    if (showBatchOptions) BatchOptionsDialog(
        onDismiss = { showBatchOptions = false },
        onRun = { options -> showBatchOptions = false; pendingBatchOptions = options; batchDestination.launch(null) },
    )

    BackHandler(enabled = showSettings) { showSettings = false }

    if (showResetAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetAllConfirmation = false },
            containerColor = RestoreXPanelRaised,
            title = { Text("Reset all edits?", color = RestoreXText) },
            text = { Text("Every adjustment, crop, filter, LUT, and film effect will be reset. You can undo this action.", color = RestoreXMuted) },
            confirmButton = {
                Button(onClick = {
                    showResetAllConfirmation = false
                    viewModel.setSharpenMaskPreview(false)
                    viewModel.resetAll()
                }) { Text("Reset all") }
            },
            dismissButton = { TextButton(onClick = { showResetAllConfirmation = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        containerColor = RestoreXBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.navigationBarsPadding()) },
    ) { padding ->
        val chrome = EditorChromeState(fullScreen)
        Box(Modifier.fillMaxSize().padding(padding).background(RestoreXBackground)) {
            Column(Modifier.fillMaxSize()) {
                if ((state.hasImage && !fullScreen) || showSettings) Spacer(Modifier.statusBarsPadding().height(64.dp))
                if (showSettings) {
                    SettingsScreen(onDeveloperClick = { openDeveloperWebsite(context, developerUrl) })
                } else if (state.hasImage) {
                    ImageWorkspace(
                        edited = state.preview!!,
                        original = state.originalPreview ?: state.preview!!,
                        holdOriginal = holdOriginal,
                        splitComparison = splitComparison,
                        isRendering = state.isRendering || state.isBusy,
                        processingLabel = state.busyLabel,
                        processingCancellable = state.isUtilityProcessing,
                        onCancelProcessing = viewModel::cancelUtility,
                        sourceIdentity = state.sourceIdentity,
                        retouch = state.retouch,
                        retouchActive = selectedTool == EditorTool.RETOUCH,
                        liquify = state.liquify,
                        liquifyActive = selectedTool == EditorTool.LIQUIFY,
                        background = state.background,
                        backgroundActive = selectedTool == EditorTool.BACKGROUND,
                        geometryActive = selectedTool == EditorTool.GEOMETRY || selectedTool == EditorTool.CROP,
                        geometryRect = state.edits.cropRect,
                        geometryAspect = state.edits.cropAspect,
                        geometryCustomRatio = state.edits.customCropRatio,
                        geometryGrid = geometryGrid,
                        localEditOverlay = state.localEditOverlay,
                        onBrushStroke = viewModel::commitBrushStroke,
                        onLiquifyStroke = viewModel::commitLiquifyStroke,
                        onBackgroundStroke = viewModel::commitBackgroundStroke,
                        onCropRectChange = viewModel::updateCropRect,
                        onLightPlaced = viewModel::commitLightAt,
                        onViewportChanged = viewModel::updatePreviewViewport,
                        onZoomChanged = viewModel::requestZoomDetail,
                    )
                    if (chrome.showBottomTools) {
                        ContextualPanel(
                            selectedTool,
                            state,
                            viewModel,
                            collapsed = toolPanelCollapsed,
                            onToggleCollapsed = { toolPanelCollapsed = !toolPanelCollapsed },
                            onResetImage = { showResetAllConfirmation = true },
                            onImportLut = { lutImporter.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                            onImportBackground = { backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onUtilities = {
                                utilityChooserTitle = "Utilities"
                                utilityChooserModes = UtilityMode.entries.toList()
                                showUtilityChooser = true
                            },
                            onImportSticker = { stickerPicker.launch(arrayOf("image/png", "image/webp", "image/*")) },
                            geometryGrid = geometryGrid,
                            onGeometryGridChanged = { geometryGrid = it },
                        )
                        ToolNavigation(selectedTool, onSelected = {
                            if (it != EditorTool.DETAILS) viewModel.setSharpenMaskPreview(false)
                            selectedTool = it
                        })
                    }
                } else {
                    EmptyEditor(
                        isBusy = state.isBusy,
                        busyLabel = state.busyLabel,
                        canCancel = state.isUtilityProcessing,
                        onCancel = viewModel::cancelUtility,
                        onSettings = { showSettings = true },
                        onDeveloperClick = { openDeveloperWebsite(context, developerUrl) },
                        onImport = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onSmartMagic = { smartMagicPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onUtility = { utility ->
                            when (utility) {
                                HomeUtility.COLLAGE -> {
                                    pendingUtilityMode = UtilityMode.COLLAGE
                                    utilityPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                HomeUtility.HDR -> {
                                    utilityChooserTitle = "HDR merge"
                                    utilityChooserModes = listOf(UtilityMode.HDR_NATURAL, UtilityMode.HDR_DRAMATIC)
                                    showUtilityChooser = true
                                }
                                HomeUtility.PANORAMA -> {
                                    pendingUtilityMode = UtilityMode.PANORAMA
                                    utilityPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                HomeUtility.BATCH, HomeUtility.COMPRESS -> {
                                    pendingUtilityMode = UtilityMode.BATCH
                                    utilityPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                HomeUtility.PASSPORT -> {
                                    utilityChooserTitle = "Passport photo"
                                    utilityChooserModes = listOf(UtilityMode.PASSPORT_INDIA, UtilityMode.PASSPORT_JAPAN)
                                    showUtilityChooser = true
                                }
                                HomeUtility.DOCUMENT -> {
                                    pendingUtilityMode = UtilityMode.DOCUMENT
                                    utilityPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                HomeUtility.MORE -> {
                                    utilityChooserTitle = "More tools"
                                    utilityChooserModes = listOf(UtilityMode.SIGNATURE)
                                    showUtilityChooser = true
                                }
                            }
                        },
                    )
                }
            }
            if (state.hasImage && chrome.showTopToolbar) EditorTopBar(
                state = state,
                splitComparison = splitComparison,
                fullScreen = fullScreen,
                onBack = {
                    when {
                        fullScreen -> fullScreen = false
                        showSettings -> showSettings = false
                        state.hasImage -> viewModel.clearImage()
                        else -> onExit()
                    }
                },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onHoldOriginal = { holdOriginal = it },
                onToggleSplit = { splitComparison = !splitComparison },
                onExport = { showExportDialog = true },
                onToggleFullScreen = { fullScreen = !fullScreen },
                modifier = Modifier.align(Alignment.TopCenter).zIndex(10f).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            )
            else if (showSettings) SettingsTopBar(
                onBack = { showSettings = false },
                modifier = Modifier.align(Alignment.TopCenter).zIndex(10f).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            )
            if (state.isExporting) ExportProgressOverlay(
                onCancel = viewModel::cancelExport,
                modifier = Modifier.align(Alignment.Center).zIndex(20f),
            )
        }
    }
}

@Composable
private fun ExportProgressOverlay(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color(0xF21C1D20), RoundedCornerShape(18.dp)).padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = RestoreXAccent, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        Text("Exporting full-resolution photo", color = RestoreXText, fontWeight = FontWeight.SemiBold)
        Text("Processing stays on this device", color = RestoreXMuted, fontSize = 11.sp)
        TextButton(onClick = onCancel) { Text("Cancel export", color = RestoreXAccent) }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(Color(0xE61C1D20), RoundedCornerShape(16.dp)).height(56.dp).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIconButton(AppIcon.Back, "Back to Restovue home", onClick = onBack)
        Text("Settings", color = RestoreXText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditorTopBar(
    state: EditorUiState,
    splitComparison: Boolean,
    fullScreen: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHoldOriginal: (Boolean) -> Unit,
    onToggleSplit: () -> Unit,
    onExport: () -> Unit,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().background(Color(0xE61C1D20), RoundedCornerShape(16.dp)).height(56.dp).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIconButton(AppIcon.Back, "Back", onClick = onBack)
        Text(
            stringResource(R.string.app_name_short),
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            color = RestoreXText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        ToolIconButton(AppIcon.Undo, "Undo", enabled = state.canUndo, onClick = onUndo)
        CompareButton(
            enabled = state.hasImage,
            selected = splitComparison,
            onHeld = onHoldOriginal,
            onClick = onToggleSplit,
        )
        if (fullScreen) ToolIconButton(
            AppIcon.Fullscreen,
            "Exit Full Screen",
            enabled = state.hasImage,
            onClick = onToggleFullScreen,
        )
        Box(
            Modifier.width(56.dp).height(48.dp)
                .clickable(enabled = state.hasImage && !state.isBusy, onClick = onExport)
                .semantics { contentDescription = "Export photo" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Export",
                color = if (state.hasImage && !state.isBusy) RestoreXAccent else RestoreXDisabled,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Box {
            ToolIconButton(AppIcon.More, "More editor actions", onClick = { overflowExpanded = true })
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
                containerColor = RestoreXPanelRaised,
            ) {
                DropdownMenuItem(
                    text = { Text("Redo", color = if (state.canRedo) RestoreXText else RestoreXDisabled) },
                    enabled = state.canRedo,
                    onClick = { overflowExpanded = false; onRedo() },
                )
                if (!fullScreen) DropdownMenuItem(
                    text = { Text("Enter Full Screen", color = if (state.hasImage) RestoreXText else RestoreXDisabled) },
                    enabled = state.hasImage,
                    onClick = { overflowExpanded = false; onToggleFullScreen() },
                )
            }
        }
    }
}

@Composable
private fun CompareButton(
    enabled: Boolean,
    selected: Boolean,
    onHeld: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val description = "Before and after: hold for original, tap for split comparison"
    Box(
        modifier = Modifier.size(48.dp)
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            onHeld(true)
                            tryAwaitRelease()
                            onHeld(false)
                        },
                        onTap = { onClick() },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIconCanvas(
            icon = AppIcon.Compare,
            tint = when {
                !enabled -> RestoreXDisabled
                selected -> RestoreXAccent
                else -> RestoreXText
            },
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun ColumnScope.EmptyEditor(
    isBusy: Boolean,
    busyLabel: String?,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onImport: () -> Unit,
    onSmartMagic: () -> Unit,
    onSettings: () -> Unit,
    onDeveloperClick: () -> Unit,
    onUtility: (HomeUtility) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().weight(1f)
            .background(Brush.verticalGradient(listOf(Color(0xFF121318), Color(0xFF0D0E12)))),
    ) {
        val columns = if (maxWidth >= 340.dp) 4 else 2
        val cardHeight = if (columns == 4) 86.dp else 94.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(34.dp))
            Image(
                painter = painterResource(R.drawable.restovue_logo),
                contentDescription = "Restovue : Photo Editor logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(.78f).widthIn(max = 340.dp).aspectRatio(3f),
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "Edit beautifully. Stay private.",
                color = Color(0xFFF5F5F7),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Professional photo editing, completely on your device.",
                color = Color(0xFFA4A5B0),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(.88f),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onImport,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB511), contentColor = Color(0xFF171300)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(.72f).widthIn(min = 240.dp, max = 330.dp).height(58.dp),
            ) {
                AppIconCanvas(AppIcon.Image, Color(0xFF171300), Modifier.size(27.dp))
                Spacer(Modifier.width(12.dp))
                Text("Choose Photo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(.88f).height(66.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF262229), Color(0xFF1C1D22))), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFFFB511), RoundedCornerShape(16.dp))
                    .clickable(enabled = !isBusy, onClick = onSmartMagic)
                    .semantics { contentDescription = "Smart Magic. Professional photo enhancement in one tap" }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconCanvas(AppIcon.Magic, Color(0xFFFFB511), Modifier.size(31.dp))
                Spacer(Modifier.width(13.dp))
                Column {
                    Text("Smart Magic", color = Color(0xFFF5F5F7), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Professional photo enhancement in one tap", color = Color(0xFFA4A5B0), fontSize = 11.sp, maxLines = 1)
                }
            }
            if (isBusy) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = RestoreXAccent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(busyLabel ?: "Processing", color = RestoreXText, fontSize = 12.sp)
                    if (canCancel) TextButton(onClick = onCancel) { Text("Cancel", color = RestoreXAccent) }
                }
            }
            Spacer(Modifier.height(if (isBusy) 14.dp else 28.dp))
            HomeUtility.entries.toList().chunked(columns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    rowItems.forEach { utility ->
                        HomeUtilityCard(
                            utility = utility,
                            enabled = !isBusy,
                            height = cardHeight,
                            modifier = Modifier.weight(1f),
                            onClick = { onUtility(utility) },
                        )
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 7.dp, end = 8.dp)
                .size(48.dp).clickable(onClick = onSettings)
                .semantics { contentDescription = "Open settings" },
            contentAlignment = Alignment.Center,
        ) { AppIconCanvas(AppIcon.Settings, Color(0xFFF5F5F7), Modifier.size(27.dp)) }
    }
    HomeFooter(onDeveloperClick)
}

@Composable
private fun HomeUtilityCard(
    utility: HomeUtility,
    enabled: Boolean,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.height(height).background(
            Brush.verticalGradient(listOf(Color(0xFF24262C), Color(0xFF191A1F))), shape,
        ).border(1.dp, Color(0xFF555861), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "${utility.label} utility" }
            .padding(horizontal = 3.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIconCanvas(utility.icon, Color(0xFFFFB511), Modifier.size(29.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            utility.label,
            color = Color(0xFFF5F5F7),
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun ColumnScope.HomeFooter(onDeveloperClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF0D0E12)).navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), color = Color(0xFFD2D3D8), fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Developed by ", color = Color(0xFFA4A5B0), fontSize = 11.sp)
            Text(
                stringResource(R.string.developer_name),
                color = Color(0xFFFFB511),
                fontSize = 11.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onDeveloperClick).padding(vertical = 4.dp)
                    .semantics { contentDescription = "Open developer website" },
            )
        }
    }
}

@Composable
private fun ColumnScope.SettingsScreen(onDeveloperClick: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val version = packageInfo.versionName ?: "Unknown"
    Column(
        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", color = RestoreXText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        AboutSection("Default export", "Choose JPEG, PNG or WebP quality and dimensions for each export. Restovue never silently overwrites the original photo.")
        AboutSection("Privacy", stringResource(R.string.privacy_statement))
        Text("About Restovue", color = RestoreXText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        AboutSection("Application", stringResource(R.string.app_name))
        AboutSection("Version", version)
        Column {
            Text("Developer", color = RestoreXMuted, fontSize = 12.sp)
            Text(
                stringResource(R.string.developer_name),
                color = RestoreXAccent,
                modifier = Modifier.clickable(onClick = onDeveloperClick).padding(vertical = 6.dp)
                    .semantics { contentDescription = "Open developer website" },
            )
        }
        AboutSection("Offline processing", stringResource(R.string.offline_statement))
        AboutSection(
            "Open-source licenses",
            "AndroidX, Jetpack Compose, Kotlin Coroutines, and AndroidX ExifInterface are used under the Apache License 2.0. JUnit is used for testing under the Eclipse Public License 1.0.",
        )
        AboutSection("Build version", packageInfo.longVersionCode.toString())
    }
    HomeFooter(onDeveloperClick)
}

@Composable
private fun AboutSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = RestoreXMuted, fontSize = 12.sp)
        Text(body, color = RestoreXText, fontSize = 14.sp)
    }
}

@Composable
private fun ColumnScope.ImageWorkspace(
    edited: RasterImage,
    original: RasterImage,
    holdOriginal: Boolean,
    splitComparison: Boolean,
    isRendering: Boolean,
    processingLabel: String?,
    processingCancellable: Boolean,
    onCancelProcessing: () -> Unit,
    sourceIdentity: Long,
    retouch: RetouchUiState,
    retouchActive: Boolean,
    liquify: LiquifyUiState,
    liquifyActive: Boolean,
    background: BackgroundUiState,
    backgroundActive: Boolean,
    geometryActive: Boolean,
    geometryRect: NormalizedRect,
    geometryAspect: CropAspect,
    geometryCustomRatio: Float?,
    geometryGrid: GeometryGridMode,
    localEditOverlay: List<LocalEditOperation>,
    onBrushStroke: (List<NormalizedPoint>) -> Unit,
    onLiquifyStroke: (List<NormalizedPoint>) -> Unit,
    onBackgroundStroke: (List<NormalizedPoint>) -> Unit,
    onCropRectChange: (NormalizedRect) -> Unit,
    onLightPlaced: (NormalizedPoint) -> Unit,
    onViewportChanged: (Int, Int) -> Unit,
    onZoomChanged: (Float) -> Unit,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(sourceIdentity) { mutableFloatStateOf(1f) }
    var pan by remember(sourceIdentity) { mutableStateOf(FloatPoint(0f, 0f)) }
    var scaleMode by remember(sourceIdentity) { mutableStateOf(CanvasScaleMode.FIT) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var activeStroke by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var activeCropCorner by remember { mutableStateOf<Int?>(null) }
    val (editedBitmap, originalBitmap) = rememberRasterPair(edited, original)

    fun coordinates(currentZoom: Float = zoom, currentPan: FloatPoint = pan): EditorCoordinates? {
        if (viewport.width <= 0 || viewport.height <= 0) return null
        return EditorCoordinates(
            imageSize = FloatSize(edited.width.toFloat(), edited.height.toFloat()),
            viewportSize = FloatSize(viewport.width.toFloat(), viewport.height.toFloat()),
            zoom = currentZoom,
            pan = currentPan,
            scaleMode = scaleMode,
        )
    }

    fun normalizedImagePoint(viewportPoint: Offset): NormalizedPoint? {
        val mapping = coordinates() ?: return null
        val imagePoint = mapping.viewportToImage(FloatPoint(viewportPoint.x, viewportPoint.y))
        if (imagePoint.x !in 0f..mapping.imageSize.width || imagePoint.y !in 0f..mapping.imageSize.height) return null
        return NormalizedPoint(
            (imagePoint.x / mapping.imageSize.width).coerceIn(0f, 1f),
            (imagePoint.y / mapping.imageSize.height).coerceIn(0f, 1f),
        )
    }
    fun aspectBounds(): NormalizedRect {
        val ratio = when (geometryAspect) {
            CropAspect.ORIGINAL, CropAspect.FREE -> return NormalizedRect()
            CropAspect.CUSTOM -> geometryCustomRatio ?: return NormalizedRect()
            else -> geometryAspect.width.toFloat() / geometryAspect.height
        }
        val imageRatio = edited.width.toFloat() / edited.height
        return if (imageRatio > ratio) {
            val normalizedWidth = ratio / imageRatio; NormalizedRect((1f - normalizedWidth) / 2f, 0f, (1f + normalizedWidth) / 2f, 1f)
        } else {
            val normalizedHeight = imageRatio / ratio; NormalizedRect(0f, (1f - normalizedHeight) / 2f, 1f, (1f + normalizedHeight) / 2f)
        }
    }
    fun displayedCropRect(): NormalizedRect {
        val base = aspectBounds()
        return NormalizedRect(base.left + geometryRect.left * base.width, base.top + geometryRect.top * base.height, base.left + geometryRect.right * base.width, base.top + geometryRect.bottom * base.height)
    }

    Box(
        Modifier.fillMaxWidth().weight(1f).background(RestoreXBackground)
            .onSizeChanged {
                viewport = it
                onViewportChanged(it.width, it.height)
                coordinates()?.let { mapping -> pan = mapping.clampedPan(pan) }
            }
            .pointerInput(geometryActive, geometryRect, geometryAspect, geometryCustomRatio, viewport, edited.width, edited.height) {
                if (geometryActive) detectDragGestures(
                    onDragStart = { offset ->
                        val point = normalizedImagePoint(offset) ?: return@detectDragGestures
                        val display = displayedCropRect()
                        val corners = listOf(display.left to display.top, display.right to display.top, display.right to display.bottom, display.left to display.bottom)
                        activeCropCorner = corners.indices.minBy { index -> val c = corners[index]; (point.x - c.first) * (point.x - c.first) + (point.y - c.second) * (point.y - c.second) }
                    },
                    onDrag = { change, _ ->
                        val point = normalizedImagePoint(change.position) ?: return@detectDragGestures
                        val gap = .02f; val display = displayedCropRect(); val base = aspectBounds()
                        fun rawX(value: Float) = ((value - base.left) / base.width).coerceIn(0f, 1f)
                        fun rawY(value: Float) = ((value - base.top) / base.height).coerceIn(0f, 1f)
                        val updated = when (activeCropCorner) {
                            0 -> NormalizedRect(rawX(point.x.coerceAtMost(display.right - gap)), rawY(point.y.coerceAtMost(display.bottom - gap)), geometryRect.right, geometryRect.bottom)
                            1 -> NormalizedRect(geometryRect.left, rawY(point.y.coerceAtMost(display.bottom - gap)), rawX(point.x.coerceAtLeast(display.left + gap)), geometryRect.bottom)
                            2 -> NormalizedRect(geometryRect.left, geometryRect.top, rawX(point.x.coerceAtLeast(display.left + gap)), rawY(point.y.coerceAtLeast(display.top + gap)))
                            3 -> NormalizedRect(rawX(point.x.coerceAtMost(display.right - gap)), geometryRect.top, geometryRect.right, rawY(point.y.coerceAtLeast(display.top + gap)))
                            else -> geometryRect
                        }
                        onCropRectChange(updated); change.consume()
                    },
                    onDragEnd = { activeCropCorner = null },
                    onDragCancel = { activeCropCorner = null },
                )
            }
            .pointerInput(edited.width, edited.height, viewport, retouchActive, retouch.tool, retouch.selectingCloneSource, liquifyActive, liquify.type, backgroundActive, background.brushMode, geometryActive) {
                val painting = liquifyActive || backgroundActive || (retouchActive && retouch.tool != RetouchTool.NONE)
                if (geometryActive) Unit
                else if (!painting) {
                    detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                        val old = coordinates() ?: return@detectTransformGestures
                        val targetZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                        val around = old.panForZoomAround(FloatPoint(centroid.x, centroid.y), targetZoom)
                        val target = EditorCoordinates(old.imageSize, old.viewportSize, targetZoom, around, scaleMode)
                        pan = target.clampedPan(FloatPoint(around.x + gesturePan.x, around.y + gesturePan.y))
                        zoom = targetZoom
                        onZoomChanged(targetZoom)
                    }
                } else {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val stroke = mutableListOf<NormalizedPoint>()
                        normalizedImagePoint(down.position)?.let { stroke += it.copy(pressure = down.pressure.coerceIn(0f, 1f)) }
                        activeStroke = stroke.toList()
                        var navigating = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                navigating = true
                                stroke.clear()
                                activeStroke = emptyList()
                                val old = coordinates() ?: continue
                                val gestureZoom = event.calculateZoom()
                                val gesturePan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                val targetZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                                val around = old.panForZoomAround(FloatPoint(centroid.x, centroid.y), targetZoom)
                                val target = EditorCoordinates(old.imageSize, old.viewportSize, targetZoom, around, scaleMode)
                                pan = target.clampedPan(FloatPoint(around.x + gesturePan.x, around.y + gesturePan.y))
                                zoom = targetZoom
                                onZoomChanged(targetZoom)
                                event.changes.forEach { it.consume() }
                            } else if (!navigating) {
                                val change = pressed.first()
                                normalizedImagePoint(change.position)?.let { point ->
                                    val withPressure = point.copy(pressure = change.pressure.coerceIn(0f, 1f))
                                    if (stroke.lastOrNull() != withPressure) stroke += withPressure
                                    activeStroke = stroke.toList()
                                }
                                change.consume()
                            }
                        }
                        if (!navigating && stroke.isNotEmpty()) {
                            if (retouch.tool == RetouchTool.RADIAL || retouch.tool == RetouchTool.LINEAR) onLightPlaced(stroke.first())
                            else if (liquifyActive) onLiquifyStroke(stroke)
                            else if (backgroundActive) onBackgroundStroke(stroke)
                            else onBrushStroke(stroke)
                        }
                        activeStroke = emptyList()
                    }
                }
            }
            .pointerInput(edited.width, edited.height, viewport, retouchActive, retouch.tool, liquifyActive, backgroundActive) {
                if (!liquifyActive && !backgroundActive && (!retouchActive || retouch.tool == RetouchTool.NONE)) {
                    detectTapGestures(onDoubleTap = { point ->
                        val mapping = coordinates() ?: return@detectTapGestures
                        val targetZoom = if (zoom > 1.05f) 1f else 2.5f
                        pan = if (targetZoom == 1f) FloatPoint(0f, 0f)
                        else mapping.panForZoomAround(FloatPoint(point.x, point.y), targetZoom)
                        zoom = targetZoom
                        onZoomChanged(targetZoom)
                    })
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Photo editing preview" }) {
            drawCheckerboard()
            val mapping = coordinates() ?: return@Canvas
            drawMappedImage(editedBitmap, mapping)
            when {
                holdOriginal -> drawMappedImage(originalBitmap, mapping)
                splitComparison -> clipRect(right = size.width * split) { drawMappedImage(originalBitmap, mapping) }
            }
            if ((retouchActive && retouch.showMaskOverlay) || liquifyActive || backgroundActive) {
                drawLocalEditOverlay(localEditOverlay, mapping, edited.width, edited.height)
            }
            if (geometryActive) drawCropOverlay(displayedCropRect(), geometryGrid, mapping, edited.width, edited.height)
            if (retouchActive && (retouch.tool == RetouchTool.CLONE || retouch.tool == RetouchTool.HEAL)) {
                retouch.cloneSourceVisible?.let { source ->
                    val mapped = mapping.imageToViewport(FloatPoint(source.x * edited.width, source.y * edited.height))
                    val radius = (retouch.brush.radius * minOf(edited.width, edited.height) * mapping.displayScale).coerceAtLeast(10.dp.toPx())
                    drawCircle(RestoreXAccent, radius, Offset(mapped.x, mapped.y), style = Stroke(2.dp.toPx()))
                    drawLine(RestoreXAccent, Offset(mapped.x - 8.dp.toPx(), mapped.y), Offset(mapped.x + 8.dp.toPx(), mapped.y), 1.5.dp.toPx())
                    drawLine(RestoreXAccent, Offset(mapped.x, mapped.y - 8.dp.toPx()), Offset(mapped.x, mapped.y + 8.dp.toPx()), 1.5.dp.toPx())
                }
            }
            if ((retouchActive || liquifyActive || backgroundActive) && activeStroke.isNotEmpty()) {
                drawActiveStroke(activeStroke, when { liquifyActive -> liquify.radius; backgroundActive -> background.radius; else -> retouch.brush.radius }, mapping, edited.width, edited.height)
            }
        }

        if (holdOriginal || splitComparison) {
            ComparisonLabel(if (holdOriginal) "BEFORE" else "BEFORE", Modifier.align(Alignment.TopStart))
            if (splitComparison) ComparisonLabel("AFTER", Modifier.align(Alignment.TopEnd))
        }

        if (splitComparison && viewport.width > 0) {
            val density = LocalDensity.current
            val handleWidth = with(density) { 40.dp.roundToPx() }
            Box(
                Modifier.fillMaxHeight().width(40.dp)
                    .offset { IntOffset((viewport.width * split).roundToInt() - handleWidth / 2, 0) }
                    .pointerInput(viewport.width) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            split = (split + dragAmount.x / viewport.width).coerceIn(0.05f, 0.95f)
                        }
                    }
                    .semantics { contentDescription = "Before and after divider" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxHeight().width(2.dp).background(RestoreXAccent))
                Box(
                    Modifier.size(30.dp).background(RestoreXAccent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("↔", color = Color(0xFF251A00), fontWeight = FontWeight.Bold) }
            }
        }

        ProcessingBadge(
            isRendering,
            processingLabel,
            processingCancellable,
            onCancelProcessing,
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        )

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .background(Color(0xC8101114), RoundedCornerShape(12.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CanvasModeButton("Fit", scaleMode == CanvasScaleMode.FIT) {
                scaleMode = CanvasScaleMode.FIT; zoom = 1f; pan = FloatPoint(0f, 0f); onZoomChanged(1f)
            }
            CanvasModeButton("Fill", scaleMode == CanvasScaleMode.FILL) {
                scaleMode = CanvasScaleMode.FILL; zoom = 1f; pan = FloatPoint(0f, 0f); onZoomChanged(1f)
            }
            CanvasModeButton("1×", false, enabled = zoom > 1.001f || pan != FloatPoint(0f, 0f)) {
                zoom = 1f; pan = FloatPoint(0f, 0f); onZoomChanged(1f)
            }
        }
    }
}

@Composable
private fun ProcessingBadge(
    isRendering: Boolean,
    label: String?,
    cancellable: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isRendering) {
        if (!isRendering) visible = false
        else {
            delay(160)
            visible = true
        }
    }
    if (visible && isRendering) {
        Row(
            modifier.background(Color(0xE61C1D20), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(color = RestoreXAccent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            Text(label ?: "Processing", color = RestoreXText, fontSize = 12.sp)
            if (cancellable) TextButton(onClick = onCancel) { Text("Cancel", color = RestoreXAccent) }
        }
    }
}

@Composable
private fun CanvasModeButton(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        color = when { !enabled -> RestoreXDisabled; selected -> Color(0xFF251A00); else -> RestoreXText },
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(if (selected) RestoreXAccent else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics { contentDescription = "$label image display mode" },
    )
}

private fun DrawScope.drawCheckerboard() {
    val tile = 14.dp.toPx()
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var column = 0
        while (x < size.width) {
            drawRect(
                color = if ((row + column) % 2 == 0) Color(0xFF17181B) else Color(0xFF202125),
                topLeft = Offset(x, y),
                size = Size(tile.coerceAtMost(size.width - x), tile.coerceAtMost(size.height - y)),
            )
            x += tile
            column++
        }
        y += tile
        row++
    }
}

private fun DrawScope.drawMappedImage(bitmap: ImageBitmap, mapping: EditorCoordinates) {
    val topLeft = mapping.imageToViewport(FloatPoint(0f, 0f))
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(
            (mapping.imageSize.width * mapping.displayScale).roundToInt().coerceAtLeast(1),
            (mapping.imageSize.height * mapping.displayScale).roundToInt().coerceAtLeast(1),
        ),
        filterQuality = FilterQuality.High,
    )
}

private fun DrawScope.drawLocalEditOverlay(
    operations: List<LocalEditOperation>,
    mapping: EditorCoordinates,
    imageWidth: Int,
    imageHeight: Int,
) {
    operations.forEach { operation ->
        when (operation) {
            is BrushStroke -> {
                val radius = operation.settings.radius * minOf(imageWidth, imageHeight) * mapping.displayScale
                val points = operation.points.map { point ->
                    mapping.imageToViewport(FloatPoint(point.x * imageWidth, point.y * imageHeight)).let { Offset(it.x, it.y) }
                }
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(RestoreXAccent.copy(alpha = .26f), start, end, radius * 2f, StrokeCap.Round)
                }
                points.forEach { drawCircle(RestoreXAccent.copy(alpha = .22f), radius.coerceAtLeast(2f), it) }
                operation.sourceAnchor?.let { source ->
                    val mapped = mapping.imageToViewport(FloatPoint(source.x * imageWidth, source.y * imageHeight))
                    drawCircle(RestoreXAccent, radius.coerceAtLeast(8f), Offset(mapped.x, mapped.y), style = Stroke(2.dp.toPx()))
                    drawLine(RestoreXAccent, Offset(mapped.x - 7.dp.toPx(), mapped.y), Offset(mapped.x + 7.dp.toPx(), mapped.y), 1.5.dp.toPx())
                    drawLine(RestoreXAccent, Offset(mapped.x, mapped.y - 7.dp.toPx()), Offset(mapped.x, mapped.y + 7.dp.toPx()), 1.5.dp.toPx())
                }
            }
            is LiquifyStroke -> {
                val radius = operation.radius * minOf(imageWidth, imageHeight) * mapping.displayScale
                val points = operation.points.map { point ->
                    mapping.imageToViewport(FloatPoint(point.x * imageWidth, point.y * imageHeight)).let { Offset(it.x, it.y) }
                }
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(RestoreXAccent.copy(alpha = .3f), start, end, radius * 2f, StrokeCap.Round)
                }
                points.lastOrNull()?.let { drawCircle(RestoreXAccent.copy(alpha = .45f), radius.coerceAtLeast(2f), it) }
            }
            is BackgroundMaskStroke -> {
                val radius = operation.radius * minOf(imageWidth, imageHeight) * mapping.displayScale
                val color = if (operation.mode == BackgroundBrushMode.REMOVE) Color.Red else Color.Green
                val points = operation.points.map { point ->
                    mapping.imageToViewport(FloatPoint(point.x * imageWidth, point.y * imageHeight)).let { Offset(it.x, it.y) }
                }
                points.zipWithNext().forEach { (start, end) -> drawLine(color.copy(alpha = .28f), start, end, radius * 2f, StrokeCap.Round) }
                points.forEach { drawCircle(color.copy(alpha = .2f), radius.coerceAtLeast(2f), it) }
            }
            is BackgroundFill -> Unit
            is RadialLight -> {
                val center = mapping.imageToViewport(FloatPoint(operation.center.x * imageWidth, operation.center.y * imageHeight))
                val ellipseWidth = operation.width * imageWidth * 2f * mapping.displayScale
                val ellipseHeight = operation.height * imageHeight * 2f * mapping.displayScale
                rotate(operation.rotationDegrees, Offset(center.x, center.y)) {
                    drawOval(
                        RestoreXAccent.copy(alpha = .8f),
                        topLeft = Offset(center.x - ellipseWidth / 2f, center.y - ellipseHeight / 2f),
                        size = Size(ellipseWidth, ellipseHeight),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            is LinearLight -> {
                val center = mapping.imageToViewport(FloatPoint(operation.center.x * imageWidth, operation.center.y * imageHeight))
                val angle = Math.toRadians(operation.rotationDegrees.toDouble())
                val direction = Offset(kotlin.math.sin(angle).toFloat(), -kotlin.math.cos(angle).toFloat())
                val length = size.maxDimension
                drawLine(
                    RestoreXAccent.copy(alpha = .8f),
                    Offset(center.x - direction.x * length, center.y - direction.y * length),
                    Offset(center.x + direction.x * length, center.y + direction.y * length),
                    2.dp.toPx(),
                )
            }
        }
    }
}

private fun DrawScope.drawActiveStroke(
    points: List<NormalizedPoint>,
    radiusFraction: Float,
    mapping: EditorCoordinates,
    imageWidth: Int,
    imageHeight: Int,
) {
    val radius = radiusFraction * minOf(imageWidth, imageHeight) * mapping.displayScale
    val mapped = points.map { point ->
        mapping.imageToViewport(FloatPoint(point.x * imageWidth, point.y * imageHeight)).let { Offset(it.x, it.y) }
    }
    mapped.zipWithNext().forEach { (start, end) ->
        drawLine(RestoreXAccent.copy(alpha = .38f), start, end, radius * 2f, StrokeCap.Round)
    }
    mapped.lastOrNull()?.let { drawCircle(RestoreXAccent.copy(alpha = .32f), radius.coerceAtLeast(2f), it) }
}

private fun DrawScope.drawCropOverlay(rect: NormalizedRect, grid: GeometryGridMode, mapping: EditorCoordinates, imageWidth: Int, imageHeight: Int) {
    fun mapped(x: Float, y: Float): Offset = mapping.imageToViewport(FloatPoint(x * imageWidth, y * imageHeight)).let { Offset(it.x, it.y) }
    val topLeft = mapped(rect.left, rect.top); val bottomRight = mapped(rect.right, rect.bottom)
    val cropWidth = bottomRight.x - topLeft.x; val cropHeight = bottomRight.y - topLeft.y
    val shade = Color.Black.copy(alpha = .55f)
    drawRect(shade, Offset.Zero, Size(size.width, topLeft.y.coerceAtLeast(0f)))
    drawRect(shade, Offset(0f, bottomRight.y), Size(size.width, (size.height - bottomRight.y).coerceAtLeast(0f)))
    drawRect(shade, Offset(0f, topLeft.y), Size(topLeft.x.coerceAtLeast(0f), cropHeight))
    drawRect(shade, Offset(bottomRight.x, topLeft.y), Size((size.width - bottomRight.x).coerceAtLeast(0f), cropHeight))
    drawRect(RestoreXText, topLeft, Size(cropWidth, cropHeight), style = Stroke(1.5.dp.toPx()))
    val fractions = when (grid) { GeometryGridMode.NONE -> emptyList(); GeometryGridMode.THIRDS -> listOf(1f / 3f, 2f / 3f); GeometryGridMode.GOLDEN -> listOf(.382f, .618f) }
    fractions.forEach { fraction ->
        drawLine(RestoreXText.copy(alpha = .7f), Offset(topLeft.x + cropWidth * fraction, topLeft.y), Offset(topLeft.x + cropWidth * fraction, bottomRight.y), 1.dp.toPx())
        drawLine(RestoreXText.copy(alpha = .7f), Offset(topLeft.x, topLeft.y + cropHeight * fraction), Offset(bottomRight.x, topLeft.y + cropHeight * fraction), 1.dp.toPx())
    }
    listOf(topLeft, Offset(bottomRight.x, topLeft.y), bottomRight, Offset(topLeft.x, bottomRight.y)).forEach { corner ->
        drawCircle(RestoreXAccent, 7.dp.toPx(), corner); drawCircle(Color.Black, 3.dp.toPx(), corner)
    }
}

@Composable
private fun ComparisonLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier.padding(12.dp).background(Color(0xB3101114), RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = RestoreXText,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun ContextualPanel(
    tool: EditorTool,
    state: EditorUiState,
    viewModel: EditorViewModel,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onResetImage: () -> Unit,
    onImportLut: () -> Unit,
    onImportBackground: () -> Unit,
    onUtilities: () -> Unit,
    geometryGrid: GeometryGridMode,
    onGeometryGridChanged: (GeometryGridMode) -> Unit,
    onImportSticker: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(RestoreXPanel)) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).clickable(onClick = onToggleCollapsed).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (collapsed) "Show ${tool.label} controls" else "Hide controls", color = RestoreXMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("Reset image", color = RestoreXAccent, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onResetImage).padding(6.dp))
            Text(if (collapsed) "⌃" else "⌄", color = RestoreXAccent)
        }
        if (collapsed) return@Column
        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        when (tool) {
            EditorTool.CROP -> CropPanel(state, viewModel)
            EditorTool.ENHANCE -> EnhancePanel(state, viewModel)
            EditorTool.LIGHT -> LightPanel(state, viewModel)
            EditorTool.COLOR -> ColorPanel(state, viewModel)
            EditorTool.FILTERS -> FilterPanel(state, viewModel, onImportLut)
            EditorTool.FILM -> FilmPanel(state, viewModel)
            EditorTool.DETAILS -> DetailsPanel(state, viewModel)
            EditorTool.RETOUCH -> RetouchPanel(state, viewModel)
            EditorTool.LIQUIFY -> LiquifyPanel(state, viewModel)
            EditorTool.BACKGROUND -> BackgroundPanel(state, viewModel, onImportBackground)
            EditorTool.CREATIVE -> CreativePanel(state, viewModel, onImportSticker)
            EditorTool.UTILITIES -> UtilitiesPanel(state, viewModel, onUtilities)
            EditorTool.GEOMETRY -> GeometryPanel(state, viewModel, geometryGrid, onGeometryGridChanged)
        }
        }
    }
}

private enum class GeometryControl(val label: String) {
    STRAIGHTEN("Straighten"), FREE_CROP("Free Crop"), CUSTOM_RATIO("Custom Ratio"),
    H_KEYSTONE("Horizontal"), V_KEYSTONE("Vertical"), CORNERS("Corners"),
}

private enum class CropEdge(val label: String) { LEFT("Left"), TOP("Top"), RIGHT("Right"), BOTTOM("Bottom") }
private enum class QuadCorner(val label: String) { TOP_LEFT("Top Left"), TOP_RIGHT("Top Right"), BOTTOM_RIGHT("Bottom Right"), BOTTOM_LEFT("Bottom Left") }

@Composable
private fun GeometryPanel(state: EditorUiState, viewModel: EditorViewModel, grid: GeometryGridMode, onGridChanged: (GeometryGridMode) -> Unit) {
    var selected by remember { mutableStateOf(GeometryControl.STRAIGHTEN) }
    var cropEdge by remember { mutableStateOf(CropEdge.LEFT) }
    var corner by remember { mutableStateOf(QuadCorner.TOP_LEFT) }
    var cornerAxis by remember { mutableStateOf("X") }
    PanelHeader("Geometry", actionLabel = "Reset", onResetAll = viewModel::resetGeometry)
    HorizontalTextSelector(GeometryGridMode.entries, grid, { it.label }, onGridChanged)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item { CompactToolButton("Rotate CW", AppIcon.Rotate, false, viewModel::rotateClockwise) }
        item { CompactToolButton("Rotate CCW", AppIcon.Rotate, false, viewModel::rotateCounterClockwise) }
        item { CompactToolButton("Rotate 180", AppIcon.Rotate, false, viewModel::rotate180) }
        item { CompactToolButton("Flip H", AppIcon.Compare, state.edits.flipHorizontal, viewModel::flipHorizontal) }
        item { CompactToolButton("Flip V", AppIcon.Compare, state.edits.flipVertical, viewModel::flipVertical) }
    }
    HorizontalTextSelector(GeometryControl.entries, selected, { it.label }) { selected = it }
    when (selected) {
        GeometryControl.STRAIGHTEN -> AdjustmentSlider(
            "Angle", state.edits.fineRotationDegrees, -45f..45f, 0f,
            onChange = viewModel::updateFineRotation,
            onCommit = {},
            onReset = { viewModel.updateFineRotation(0f) },
            valueFormatter = { "${String.format(Locale.ROOT, "%.1f", it)}°" },
        )
        GeometryControl.FREE_CROP -> {
            HorizontalTextSelector(CropEdge.entries, cropEdge, { it.label }) { cropEdge = it }
            val rect = state.edits.cropRect
            val value = when (cropEdge) { CropEdge.LEFT -> rect.left; CropEdge.TOP -> rect.top; CropEdge.RIGHT -> rect.right; CropEdge.BOTTOM -> rect.bottom }
            val range = when (cropEdge) {
                CropEdge.LEFT -> 0f..(rect.right - .02f).coerceAtLeast(0f)
                CropEdge.TOP -> 0f..(rect.bottom - .02f).coerceAtLeast(0f)
                CropEdge.RIGHT -> (rect.left + .02f).coerceAtMost(1f)..1f
                CropEdge.BOTTOM -> (rect.top + .02f).coerceAtMost(1f)..1f
            }
            fun update(v: Float) = viewModel.updateCropRect(when (cropEdge) {
                CropEdge.LEFT -> rect.copy(left = v); CropEdge.TOP -> rect.copy(top = v)
                CropEdge.RIGHT -> rect.copy(right = v); CropEdge.BOTTOM -> rect.copy(bottom = v)
            })
            AdjustmentSlider(cropEdge.label, value, range, if (cropEdge == CropEdge.LEFT || cropEdge == CropEdge.TOP) 0f else 1f, ::update, {}, { update(if (cropEdge == CropEdge.LEFT || cropEdge == CropEdge.TOP) 0f else 1f) })
        }
        GeometryControl.CUSTOM_RATIO -> {
            val ratio = state.edits.customCropRatio ?: 1f
            AdjustmentSlider("Ratio", ratio, .2f..5f, 1f, { viewModel.setCustomCropRatio(it) }, {}, { viewModel.setCustomCropRatio(1f) }, valueFormatter = { String.format(Locale.ROOT, "%.2f", it) })
            var pixelWidth by remember { mutableStateOf(state.edits.cropOutputWidth?.toString() ?: "") }
            var pixelHeight by remember { mutableStateOf(state.edits.cropOutputHeight?.toString() ?: "") }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(pixelWidth, { pixelWidth = it.filter(Char::isDigit).take(5) }, label = { Text("Width px") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(pixelHeight, { pixelHeight = it.filter(Char::isDigit).take(5) }, label = { Text("Height px") }, modifier = Modifier.weight(1f), singleLine = true)
                TextButton(enabled = (pixelWidth.toIntOrNull() ?: 0) > 0 && (pixelHeight.toIntOrNull() ?: 0) > 0, onClick = { viewModel.setCustomCropPixels(pixelWidth.toInt(), pixelHeight.toInt()) }) { Text("Set", color = RestoreXAccent) }
            }
        }
        GeometryControl.H_KEYSTONE, GeometryControl.V_KEYSTONE -> {
            val quad = state.edits.perspective
            val horizontal = selected == GeometryControl.H_KEYSTONE
            val current = if (horizontal) quad.topLeft.x else quad.topLeft.y
            fun transform(v: Float): PerspectiveQuad = if (horizontal) {
                quad.copy(topLeft = quad.topLeft.copy(x = v), topRight = quad.topRight.copy(x = 1f - v))
            } else quad.copy(topLeft = quad.topLeft.copy(y = v), bottomLeft = quad.bottomLeft.copy(y = 1f - v))
            AdjustmentSlider(selected.label, current, 0f..0.45f, 0f, { viewModel.updatePerspective(transform(it)) }, {}, { viewModel.updatePerspective(PerspectiveQuad()) })
        }
        GeometryControl.CORNERS -> {
            HorizontalTextSelector(QuadCorner.entries, corner, { it.label }) { corner = it }
            HorizontalTextSelector(listOf("X", "Y"), cornerAxis, { it }) { cornerAxis = it }
            val quad = state.edits.perspective
            val point = when (corner) { QuadCorner.TOP_LEFT -> quad.topLeft; QuadCorner.TOP_RIGHT -> quad.topRight; QuadCorner.BOTTOM_RIGHT -> quad.bottomRight; QuadCorner.BOTTOM_LEFT -> quad.bottomLeft }
            val value = if (cornerAxis == "X") point.x else point.y
            fun update(v: Float) {
                val changed = if (cornerAxis == "X") point.copy(x = v) else point.copy(y = v)
                viewModel.updatePerspective(when (corner) {
                    QuadCorner.TOP_LEFT -> quad.copy(topLeft = changed); QuadCorner.TOP_RIGHT -> quad.copy(topRight = changed)
                    QuadCorner.BOTTOM_RIGHT -> quad.copy(bottomRight = changed); QuadCorner.BOTTOM_LEFT -> quad.copy(bottomLeft = changed)
                })
            }
            AdjustmentSlider("${corner.label} $cornerAxis", value, 0f..1f, if (cornerAxis == "X" && (corner == QuadCorner.TOP_RIGHT || corner == QuadCorner.BOTTOM_RIGHT) || cornerAxis == "Y" && (corner == QuadCorner.BOTTOM_LEFT || corner == QuadCorner.BOTTOM_RIGHT)) 1f else 0f, ::update, {}, { viewModel.updatePerspective(PerspectiveQuad()) })
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = viewModel::cancelGeometryDraft) { Text("Cancel", color = RestoreXMuted) }
        TextButton(onClick = viewModel::commitGeometryDraft) { Text("Apply", color = RestoreXAccent) }
    }
}

@Composable
private fun RetouchPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val retouch = state.retouch
    PanelHeader("Retouch", actionLabel = "Reset retouch", onResetAll = viewModel::resetLocalEdits)
    HorizontalTextSelector(RetouchTool.entries, retouch.tool, { it.label }, viewModel::setRetouchTool)
    when (retouch.tool) {
        RetouchTool.NONE -> Text(
            "Navigate with one finger, or choose a retouch tool. While painting, use two fingers to pan and zoom.",
            color = RestoreXMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        RetouchTool.RADIAL, RetouchTool.LINEAR -> RetouchLightingControls(retouch, viewModel)
        else -> RetouchBrushControls(retouch, viewModel)
    }
    Row(
        Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = retouch.showMaskOverlay,
            onCheckedChange = { show -> viewModel.updateRetouch { it.copy(showMaskOverlay = show) } },
        )
        Text("Show mask overlay", color = RestoreXText, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text("${state.edits.localEdits.size} edits", color = RestoreXMuted, fontSize = 11.sp)
    }
}

@Composable
private fun LiquifyPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val liquify = state.liquify
    PanelHeader("Liquify", actionLabel = "Reset local edits", onResetAll = viewModel::resetLocalEdits)
    HorizontalTextSelector(LiquifyType.entries, liquify.type, { type ->
        when (type) {
            LiquifyType.TWIRL_CLOCKWISE -> "Twirl +"
            LiquifyType.TWIRL_COUNTERCLOCKWISE -> "Twirl -"
            else -> type.name.lowercase().replaceFirstChar { it.uppercase() }
        }
    }) { type -> viewModel.updateLiquify { it.copy(type = type) } }
    var control by remember { mutableStateOf("Size") }
    HorizontalTextSelector(listOf("Size", "Strength", "Feather"), control, { it }) { control = it }
    val value = when (control) {
        "Size" -> liquify.radius
        "Strength" -> liquify.strength
        else -> liquify.feather
    }
    val range = if (control == "Size") .01f..0.25f else .05f..1f
    val neutral = when (control) { "Size" -> .08f; "Strength" -> .35f; else -> .65f }
    fun update(value: Float) = viewModel.updateLiquify { current -> when (control) {
        "Size" -> current.copy(radius = value)
        "Strength" -> current.copy(strength = value)
        else -> current.copy(feather = value)
    } }
    AdjustmentSlider(control, value, range, neutral, ::update, {}, { update(neutral) })
    Text(
        "Drag with one finger to deform. Use two fingers to pan and zoom. Restore paints the source shape back locally.",
        color = RestoreXMuted,
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
    )
}

@Composable
private fun BackgroundPanel(state: EditorUiState, viewModel: EditorViewModel, onImportBackground: () -> Unit) {
    val background = state.background
    PanelHeader("Manual background", actionLabel = "Reset local edits", onResetAll = viewModel::resetLocalEdits)
    HorizontalTextSelector(BackgroundBrushMode.entries, background.brushMode, {
        if (it == BackgroundBrushMode.REMOVE) "Remove" else "Restore"
    }) { mode -> viewModel.updateBackground { it.copy(brushMode = mode) } }
    var brushControl by remember { mutableStateOf("Size") }
    HorizontalTextSelector(listOf("Size", "Hardness", "Opacity"), brushControl, { it }) { brushControl = it }
    val brushValue = when (brushControl) { "Size" -> background.radius; "Hardness" -> background.hardness; else -> background.opacity }
    val brushNeutral = when (brushControl) { "Size" -> .06f; "Hardness" -> .55f; else -> 1f }
    fun updateBrush(value: Float) = viewModel.updateBackground { current -> when (brushControl) {
        "Size" -> current.copy(radius = value); "Hardness" -> current.copy(hardness = value); else -> current.copy(opacity = value)
    } }
    AdjustmentSlider(brushControl, brushValue, if (brushControl == "Size") .005f..0.25f else 0f..1f, brushNeutral, ::updateBrush, {}, { updateBrush(brushNeutral) })
    HorizontalTextSelector(BackgroundFillMode.entries, background.fillMode, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) {
        mode -> viewModel.updateBackground { it.copy(fillMode = mode) }
    }
    if (background.fillMode == BackgroundFillMode.IMAGE) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onImportBackground) { Text(if (background.imageUri == null) "Choose image" else "Replace image", color = RestoreXAccent) }
            Text(if (background.imageUri == null) "No background selected" else "Background ready", color = RestoreXMuted, fontSize = 10.sp)
        }
        var imageControl by remember { mutableStateOf("Scale") }
        HorizontalTextSelector(listOf("Scale", "Horizontal", "Vertical", "Blur"), imageControl, { it }) { imageControl = it }
        val imageValue = when (imageControl) { "Scale" -> background.imageScale; "Horizontal" -> background.offsetX; "Vertical" -> background.offsetY; else -> background.blur }
        val imageRange = when (imageControl) { "Scale" -> .25f..4f; "Horizontal", "Vertical" -> -1f..1f; else -> 0f..1f }
        fun updateImage(v: Float) = viewModel.updateBackground { current -> when (imageControl) {
            "Scale" -> current.copy(imageScale = v); "Horizontal" -> current.copy(offsetX = v); "Vertical" -> current.copy(offsetY = v); else -> current.copy(blur = v)
        } }
        AdjustmentSlider(imageControl, imageValue, imageRange, if (imageControl == "Scale") 1f else 0f, ::updateImage, viewModel::commitBackgroundFill, { updateImage(if (imageControl == "Scale") 1f else 0f) })
    }
    if (background.fillMode != BackgroundFillMode.TRANSPARENT) {
        val colors = listOf("White" to 0xFFFFFFFF.toInt(), "Black" to 0xFF000000.toInt(), "Gray" to 0xFF777777.toInt(), "Amber" to 0xFFFFB323.toInt())
        val selected = colors.minBy { (_, value) -> kotlin.math.abs((value and 0xFFFFFF) - (background.startColor and 0xFFFFFF)) }
        HorizontalTextSelector(colors, selected, { it.first }) { choice -> viewModel.updateBackground { it.copy(startColor = choice.second) } }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = viewModel::commitBackgroundFill) { Text("Apply background", color = RestoreXAccent) }
    }
    Text("Paint with one finger; two fingers navigate. Transparent mode exports alpha with PNG.", color = RestoreXMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
}

@Composable
private fun UtilitiesPanel(state: EditorUiState, viewModel: EditorViewModel, onOpen: () -> Unit) {
    PanelHeader("Advanced utilities")
    Text("Create collages, merge bracketed HDR captures, stitch ordered panoramas, generate passport sheets, or clean documents and signatures.", color = RestoreXMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    Button(onClick = onOpen, modifier = Modifier.padding(horizontal = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = RestoreXAccent, contentColor = Color(0xFF251A00))) { Text("Choose utility") }
    var presetName by remember { mutableStateOf("") }
    var renamingPreset by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(presetName, { presetName = it.take(60) }, label = { Text("Preset name") }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(enabled = presetName.isNotBlank(), onClick = { viewModel.saveCurrentPreset(presetName); presetName = "" }) { Text("Save", color = RestoreXAccent) }
    }
    state.savedPresets.forEach { preset ->
        Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (renamingPreset == preset.id) OutlinedTextField(renameText, { renameText = it.take(60) }, singleLine = true, modifier = Modifier.weight(1f))
            else Text(preset.name, color = RestoreXText, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.applySavedPreset(preset.id) }) { Text("Apply", color = RestoreXAccent) }
            TextButton(onClick = {
                if (renamingPreset == preset.id) { viewModel.renameSavedPreset(preset.id, renameText); renamingPreset = null }
                else { renamingPreset = preset.id; renameText = preset.name }
            }) { Text(if (renamingPreset == preset.id) "Save" else "Rename", color = RestoreXMuted) }
            TextButton(onClick = { viewModel.deleteSavedPreset(preset.id) }) { Text("Delete", color = RestoreXMuted) }
        }
    }
}

@Composable
private fun UtilityChooserDialog(
    title: String,
    modes: List<UtilityMode>,
    onDismiss: () -> Unit,
    onSelected: (UtilityMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RestoreXPanelRaised,
        title = { Text(title, color = RestoreXText) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                modes.forEach { mode ->
                    TextButton(onClick = { onSelected(mode) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${mode.label}  •  ${mode.minimumImages}${if (mode.minimumImages == mode.maximumImages) "" else "-${mode.maximumImages}"} image(s)", color = RestoreXText, modifier = Modifier.fillMaxWidth())
                    }
                }
                Text("HDR requires genuine bracketed exposures. Panorama expects ordered overlapping captures. Passport presets remain manually editable and are not a compliance guarantee.", color = RestoreXMuted, fontSize = 10.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UtilityOptionsDialog(mode: UtilityMode, onDismiss: () -> Unit, onRun: (UtilityOptions) -> Unit) {
    val passport = mode == UtilityMode.PASSPORT_INDIA || mode == UtilityMode.PASSPORT_JAPAN
    var width by remember(mode) { mutableStateOf(if (passport) "1200" else "2400") }
    var height by remember(mode) { mutableStateOf(if (passport) "1800" else "1600") }
    var spacing by remember(mode) { mutableStateOf("20") }
    var dpi by remember(mode) { mutableStateOf("300") }
    var copies by remember(mode) { mutableStateOf("8") }
    var photoWidthMm by remember(mode) { mutableStateOf("35") }; var photoHeightMm by remember(mode) { mutableStateOf("45") }
    val parsedWidth = width.toIntOrNull(); val parsedHeight = height.toIntOrNull(); val parsedSpacing = spacing.toIntOrNull(); val parsedDpi = dpi.toIntOrNull(); val parsedCopies = copies.toIntOrNull()
    val photoWmm = photoWidthMm.toFloatOrNull(); val photoHmm = photoHeightMm.toFloatOrNull()
    val valid = parsedWidth in 64..12000 && parsedHeight in 64..12000 && parsedSpacing in 0..1000 && parsedDpi in 72..1200 && parsedCopies in 1..100 && (!passport || (photoWmm ?: 0f) in 10f..100f && (photoHmm ?: 0f) in 10f..100f)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = RestoreXPanelRaised,
        title = { Text(mode.label, color = RestoreXText) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (mode == UtilityMode.COLLAGE || passport) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(width, { width = it.filter(Char::isDigit).take(5) }, label = { Text("Output width") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(height, { height = it.filter(Char::isDigit).take(5) }, label = { Text("Output height") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(spacing, { spacing = it.filter(Char::isDigit).take(4) }, label = { Text("Spacing / margin px") }, singleLine = true)
                }
                if (passport) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(photoWidthMm, { photoWidthMm = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = { Text("Photo width mm") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(photoHeightMm, { photoHeightMm = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = { Text("Photo height mm") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(dpi, { dpi = it.filter(Char::isDigit).take(4) }, label = { Text("DPI") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(copies, { copies = it.filter(Char::isDigit).take(3) }, label = { Text("Copies") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Text("Default sheet is 4 × 6 inches at 300 DPI. India and Japan start at editable 35 × 45 mm photo dimensions.", color = RestoreXMuted, fontSize = 10.sp)
                }
                if (mode == UtilityMode.HDR_NATURAL || mode == UtilityMode.HDR_DRAMATIC) Text("Select exposures from darkest to brightest. This performs true multi-image exposure fusion.", color = RestoreXMuted, fontSize = 10.sp)
                if (mode == UtilityMode.PANORAMA) Text("Select images from left to right. Insufficient overlap is reported instead of exporting a distorted result.", color = RestoreXMuted, fontSize = 10.sp)
            }
        },
        confirmButton = { Button(enabled = valid, onClick = { onRun(UtilityOptions(requireNotNull(parsedWidth), requireNotNull(parsedHeight), requireNotNull(parsedSpacing), requireNotNull(parsedDpi), requireNotNull(parsedCopies), photoWmm ?: 35f, photoHmm ?: 45f)) }) { Text("Choose destination") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BatchOptionsDialog(onDismiss: () -> Unit, onRun: (BatchOptions) -> Unit) {
    var format by remember { mutableStateOf(BatchFormat.JPEG) }; var quality by remember { mutableFloatStateOf(90f) }
    var width by remember { mutableStateOf("") }; var height by remember { mutableStateOf("") }; var turns by remember { mutableStateOf(0) }
    var removeMetadata by remember { mutableStateOf(false) }; var prefix by remember { mutableStateOf("Restovue_") }
    var targetKb by remember { mutableStateOf("") }
    val w = width.toIntOrNull(); val h = height.toIntOrNull(); val target = targetKb.toIntOrNull()
    val valid = (width.isBlank() || (w ?: 0) > 0) && (height.isBlank() || (h ?: 0) > 0) && prefix.length <= 40 &&
        (targetKb.isBlank() || (target ?: 0) in 10..50_000)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = RestoreXPanelRaised, title = { Text("Batch editor", color = RestoreXText) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            HorizontalTextSelector(BatchFormat.entries, format, { it.name }) { format = it; if (it == BatchFormat.PNG) targetKb = "" }
            if (format != BatchFormat.PNG) AdjustmentSlider("Quality", quality, 20f..100f, 90f, { quality = it }, {}, { quality = 90f }, valueFormatter = { it.roundToInt().toString() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(width, { width = it.filter(Char::isDigit).take(5) }, label = { Text("Max width") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(height, { height = it.filter(Char::isDigit).take(5) }, label = { Text("Max height") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            if (format != BatchFormat.PNG) OutlinedTextField(
                targetKb,
                { targetKb = it.filter(Char::isDigit).take(5) },
                label = { Text("Optional target KB") },
                supportingText = { Text("Quality is searched first; dimensions reduce only if necessary.") },
                singleLine = true,
            )
            HorizontalTextSelector(listOf(0, 1, 2, 3), turns, { "${it * 90}°" }) { turns = it }
            OutlinedTextField(prefix, { prefix = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(40) }, label = { Text("Filename prefix") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(removeMetadata, { removeMetadata = it }); Text("Remove metadata", color = RestoreXText, fontSize = 12.sp) }
            Text("Images are processed sequentially. Current adjustments and filter are applied; local brushes and creative overlays are excluded to avoid coordinate mismatches.", color = RestoreXMuted, fontSize = 10.sp)
        } },
        confirmButton = { Button(enabled = valid, onClick = { onRun(BatchOptions(format, quality.roundToInt(), w, h, turns, removeMetadata, prefix, target)) }) { Text("Choose output folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreativePanel(state: EditorUiState, viewModel: EditorViewModel, onImportSticker: () -> Unit) {
    var section by remember { mutableStateOf("Text") }
    var text by remember { mutableStateOf("") }
    PanelHeader("Creative", actionLabel = "Reset creative", onResetAll = viewModel::resetCreative)
    HorizontalTextSelector(listOf("Text", "Shapes", "Stickers", "Frame"), section, { it }) { section = it }
    when (section) {
        "Text" -> {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it.take(500) }, label = { Text("Text or emoji") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(enabled = text.isNotBlank(), onClick = { viewModel.addTextLayer(text); text = "" }) { Text("Add", color = RestoreXAccent) }
            }
        }
        "Shapes" -> HorizontalTextSelector(ShapeType.entries, ShapeType.RECTANGLE, { it.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }) { viewModel.addShapeLayer(it) }
        "Stickers" -> Button(onClick = onImportSticker, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Import PNG sticker") }
        else -> FrameControls(state.edits.frame, viewModel)
    }
    if (section != "Frame" && state.edits.creativeLayers.isNotEmpty()) {
        val selected = state.edits.creativeLayers.firstOrNull { it.id == state.selectedCreativeLayerId } ?: state.edits.creativeLayers.last()
        LaunchedEffect(selected.id) { viewModel.selectCreativeLayer(selected.id) }
        HorizontalTextSelector(state.edits.creativeLayers, selected, { layer -> when (layer) {
            is TextLayer -> layer.text.take(12); is ShapeLayer -> layer.type.name.lowercase().replaceFirstChar { it.uppercase() }; is StickerLayer -> "Sticker"
        } }) { viewModel.selectCreativeLayer(it.id) }
        CreativeLayerControls(selected, viewModel)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { viewModel.moveSelectedCreativeLayer(-1) }) { Text("Back", color = RestoreXMuted) }
            TextButton(onClick = { viewModel.moveSelectedCreativeLayer(1) }) { Text("Front", color = RestoreXMuted) }
            TextButton(onClick = viewModel::deleteSelectedCreativeLayer) { Text("Delete", color = Color.Red) }
        }
    }
}

@Composable
private fun CreativeLayerControls(layer: CreativeLayer, viewModel: EditorViewModel) {
    var control by remember(layer.id) { mutableStateOf("X") }
    val options = if (layer is TextLayer) listOf("X", "Y", "Size", "Rotate", "Opacity", "Shadow", "Outline") else listOf("X", "Y", "Width", "Height", "Rotate", "Opacity")
    HorizontalTextSelector(options, control, { it }) { control = it }
    val value = when (control) {
        "X" -> layer.center.x; "Y" -> layer.center.y; "Rotate" -> layer.rotationDegrees; "Opacity" -> layer.opacity
        "Size" -> (layer as TextLayer).size; "Shadow" -> (layer as TextLayer).shadow; "Outline" -> (layer as TextLayer).outline
        "Width" -> if (layer is ShapeLayer) layer.width else (layer as StickerLayer).width
        else -> if (layer is ShapeLayer) layer.height else (layer as StickerLayer).height
    }
    val range = when (control) { "Rotate" -> -180f..180f; "Size" -> .01f..0.4f; "Width", "Height" -> .01f..1f; else -> 0f..1f }
    fun update(v: Float) = viewModel.updateCreativeLayer { current -> when (current) {
        is TextLayer -> when (control) {
            "X" -> current.copy(center = current.center.copy(x = v)); "Y" -> current.copy(center = current.center.copy(y = v)); "Size" -> current.copy(size = v)
            "Rotate" -> current.copy(rotationDegrees = v); "Opacity" -> current.copy(opacity = v); "Shadow" -> current.copy(shadow = v); else -> current.copy(outline = v)
        }
        is ShapeLayer -> when (control) {
            "X" -> current.copy(center = current.center.copy(x = v)); "Y" -> current.copy(center = current.center.copy(y = v)); "Width" -> current.copy(width = v)
            "Height" -> current.copy(height = v); "Rotate" -> current.copy(rotationDegrees = v); else -> current.copy(opacity = v)
        }
        is StickerLayer -> when (control) {
            "X" -> current.copy(center = current.center.copy(x = v)); "Y" -> current.copy(center = current.center.copy(y = v)); "Width" -> current.copy(width = v)
            "Height" -> current.copy(height = v); "Rotate" -> current.copy(rotationDegrees = v); else -> current.copy(opacity = v)
        }
    } }
    AdjustmentSlider(control, value, range, if (control == "Opacity") 1f else 0f, ::update, viewModel::commitCreativeLayer, { update(if (control == "Opacity") 1f else 0f) })
    if (layer is TextLayer) {
        HorizontalTextSelector(TextFont.entries, layer.font, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { font ->
            viewModel.updateCreativeLayer { (it as TextLayer).copy(font = font) }; viewModel.commitCreativeLayer()
        }
    }
}

@Composable
private fun FrameControls(frame: FrameSettings, viewModel: EditorViewModel) {
    HorizontalTextSelector(FrameStyle.entries, frame.style, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { style ->
        viewModel.updateFrame(frame.copy(style = style), commitNow = true)
    }
    var control by remember { mutableStateOf("Thickness") }
    HorizontalTextSelector(listOf("Thickness", "Corner", "Shadow"), control, { it }) { control = it }
    val value = when (control) { "Thickness" -> frame.thickness; "Corner" -> frame.cornerRadius; else -> frame.shadow }
    val range = when (control) { "Thickness" -> 0f..0.2f; "Corner" -> 0f..0.3f; else -> 0f..1f }
    fun update(v: Float) = viewModel.updateFrame(when (control) { "Thickness" -> frame.copy(thickness = v); "Corner" -> frame.copy(cornerRadius = v); else -> frame.copy(shadow = v) })
    AdjustmentSlider(control, value, range, 0f, ::update, { viewModel.updateFrame(frame, commitNow = true) }, { update(0f) })
}

@Composable
private fun RetouchBrushControls(retouch: RetouchUiState, viewModel: EditorViewModel) {
    if (retouch.tool == RetouchTool.CLONE || retouch.tool == RetouchTool.HEAL) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = viewModel::beginCloneSourceSelection) {
                Text(
                    if (retouch.selectingCloneSource) "Tap source on photo"
                    else if (retouch.tool == RetouchTool.HEAL) "Optional source" else "Set source",
                    color = RestoreXAccent,
                )
            }
            if (retouch.tool == RetouchTool.CLONE) {
                Checkbox(
                    checked = retouch.alignedSampling,
                    onCheckedChange = { aligned -> viewModel.updateRetouch { it.copy(alignedSampling = aligned) } },
                )
                Text("Aligned", color = RestoreXText, fontSize = 11.sp)
            }
            if (retouch.cloneSource != null) Text("Source ready", color = RestoreXAccent, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp))
            if (retouch.cloneSource != null) TextButton(onClick = viewModel::clearRetouchSource) { Text("Clear", color = RestoreXMuted, fontSize = 10.sp) }
        }
    }
    AdjustmentSlider(
        "Size", retouch.brush.radius, .002f..0.2f, .04f,
        onChange = { value -> viewModel.updateBrushSettings { it.copy(radius = value) } },
        onCommit = {},
        onReset = { viewModel.updateBrushSettings { it.copy(radius = .04f) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" },
    )
    val showsStrength = retouch.tool != RetouchTool.ERASER && retouch.tool != RetouchTool.BRUSH_LIGHT
    val controlOptions = if (showsStrength) listOf("Hardness", "Opacity", "Strength") else listOf("Hardness", "Opacity")
    var selectedControl by remember(retouch.tool) { mutableStateOf(controlOptions.first()) }
    if (selectedControl !in controlOptions) selectedControl = controlOptions.first()
    HorizontalTextSelector(controlOptions, selectedControl, { it }) { selectedControl = it }
    val controlValue = when (selectedControl) {
        "Hardness" -> retouch.brush.hardness
        "Opacity" -> retouch.brush.opacity
        else -> kotlin.math.abs(retouch.brush.strength)
    }
    val controlRange = if (selectedControl == "Opacity" || selectedControl == "Strength") .05f..1f else 0f..1f
    val controlNeutral = when (selectedControl) { "Hardness" -> .55f; "Opacity" -> 1f; else -> .35f }
    fun updateControl(value: Float) = viewModel.updateBrushSettings { current -> when (selectedControl) {
        "Hardness" -> current.copy(hardness = value)
        "Opacity" -> current.copy(opacity = value)
        else -> current.copy(strength = value)
    } }
    AdjustmentSlider(
        selectedControl, controlValue, controlRange, controlNeutral,
        onChange = ::updateControl,
        onCommit = {},
        onReset = { updateControl(controlNeutral) },
    )
    if (retouch.tool == RetouchTool.BRUSH_LIGHT) {
        var lightControl by remember { mutableStateOf("Exposure") }
        val lightOptions = listOf("Exposure", "Shadows", "Highlights", "Temperature", "Tint")
        HorizontalTextSelector(lightOptions, lightControl, { it }) { lightControl = it }
        val lightValue = when (lightControl) {
            "Exposure" -> retouch.lightExposure
            "Shadows" -> retouch.lightShadows
            "Highlights" -> retouch.lightHighlights
            "Temperature" -> retouch.lightTemperature
            else -> retouch.lightTint
        }
        val lightRange = if (lightControl == "Exposure") -2f..2f else -1f..1f
        fun updateLight(value: Float) = viewModel.updateRetouch { current -> when (lightControl) {
            "Exposure" -> current.copy(lightExposure = value)
            "Shadows" -> current.copy(lightShadows = value)
            "Highlights" -> current.copy(lightHighlights = value)
            "Temperature" -> current.copy(lightTemperature = value)
            else -> current.copy(lightTint = value)
        } }
        AdjustmentSlider(
            lightControl, lightValue, lightRange, 0f,
            onChange = ::updateLight,
            onCommit = {},
            onReset = { updateLight(0f) },
        )
    }
    Text(
        when {
            retouch.selectingCloneSource -> "Tap once to select the clone source."
            retouch.tool == RetouchTool.HEAL -> "Tap blemishes or paint over a small unwanted area."
            else -> "Paint with one finger. Use two fingers to navigate without painting."
        },
        color = RestoreXMuted,
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
    )
}

@Composable
private fun RetouchLightingControls(retouch: RetouchUiState, viewModel: EditorViewModel) {
    var selected by remember(retouch.tool) { mutableStateOf("Exposure") }
    val options = if (retouch.tool == RetouchTool.RADIAL) {
        listOf("Exposure", "Width", "Height", "Rotation", "Feather", "Highlights", "Shadows")
    } else listOf("Exposure", "Rotation", "Feather", "Temperature", "Tint")
    if (selected !in options) selected = options.first()
    HorizontalTextSelector(options, selected, { it }) { selected = it }
    val value = when (selected) {
        "Exposure" -> retouch.lightExposure
        "Width" -> retouch.radialWidth
        "Height" -> retouch.radialHeight
        "Rotation" -> retouch.rotation
        "Feather" -> retouch.feather
        "Highlights" -> retouch.lightHighlights
        "Shadows" -> retouch.lightShadows
        "Temperature" -> retouch.lightTemperature
        else -> retouch.lightTint
    }
    val range = when (selected) {
        "Exposure" -> -2f..2f
        "Width", "Height" -> .05f..1f
        "Rotation" -> -180f..180f
        "Feather" -> .01f..1f
        else -> -1f..1f
    }
    val neutral = when (selected) {
        "Exposure" -> .5f
        "Width" -> .35f
        "Height" -> .25f
        "Feather" -> .65f
        else -> 0f
    }
    fun update(newValue: Float) = viewModel.updateRetouch { current -> when (selected) {
        "Exposure" -> current.copy(lightExposure = newValue)
        "Width" -> current.copy(radialWidth = newValue)
        "Height" -> current.copy(radialHeight = newValue)
        "Rotation" -> current.copy(rotation = newValue)
        "Feather" -> current.copy(feather = newValue)
        "Highlights" -> current.copy(lightHighlights = newValue)
        "Shadows" -> current.copy(lightShadows = newValue)
        "Temperature" -> current.copy(lightTemperature = newValue)
        else -> current.copy(lightTint = newValue)
    } }
    AdjustmentSlider(
        selected, value, range, neutral,
        onChange = ::update,
        onCommit = {},
        onReset = { update(neutral) },
        valueFormatter = if (selected == "Rotation") ({ "${it.roundToInt()}°" }) else null,
    )
    Row(
        Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = retouch.invert,
            onCheckedChange = { invert -> viewModel.updateRetouch { it.copy(invert = invert) } },
        )
        Text("Invert mask", color = RestoreXText, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text("Tap photo to place", color = RestoreXAccent, fontSize = 11.sp)
    }
}

@Composable
private fun PanelHeader(title: String, actionLabel: String = "Reset all", onResetAll: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = RestoreXText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (onResetAll != null) {
            Text(
                actionLabel,
                color = RestoreXAccent,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onResetAll).padding(6.dp),
            )
        }
    }
}

@Composable
private fun CropPanel(state: EditorUiState, viewModel: EditorViewModel) {
    PanelHeader("Crop & rotate", actionLabel = "Reset", onResetAll = viewModel::resetGeometry)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CompactToolButton("Rotate", AppIcon.Rotate, false, viewModel::rotateClockwise)
        }
        items(CropAspect.entries) { aspect ->
            CompactToolButton(
                label = aspect.label,
                icon = AppIcon.Crop,
                selected = state.edits.cropAspect == aspect,
                onClick = { viewModel.setCrop(aspect) },
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = viewModel::cancelGeometryDraft) { Text("Cancel", color = RestoreXMuted) }
        TextButton(onClick = viewModel::commitGeometryDraft) { Text("Apply", color = RestoreXAccent) }
    }
}

@Composable
private fun EnhancePanel(state: EditorUiState, viewModel: EditorViewModel) {
    val adjustments = state.edits.adjustments
    PanelHeader("Enhance", actionLabel = "Reset", onResetAll = {
        viewModel.commitAdjustments { it.copy(autoMode = AutoEnhanceMode.NONE, enhanceStrength = 1f, vibrance = 0f, clarity = 0f) }
    })
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(AutoEnhanceMode.entries.filter { it != AutoEnhanceMode.NONE }) { mode ->
            CompactToolButton(
                mode.label,
                AppIcon.Enhance,
                adjustments.autoMode == mode,
            ) {
                viewModel.commitAdjustments {
                    it.copy(autoMode = if (it.autoMode == mode) AutoEnhanceMode.NONE else mode)
                }
            }
        }
    }
    if (adjustments.autoMode != AutoEnhanceMode.NONE) {
        AdjustmentSlider(
            "Strength", adjustments.enhanceStrength, 0f..1f, 1f,
            onChange = { value -> viewModel.updateAdjustments { it.copy(enhanceStrength = value) } },
            onCommit = viewModel::commitAdjustments,
            onReset = { viewModel.resetAdjustments { it.copy(enhanceStrength = 1f) } },
        )
    } else {
        AdjustmentSlider(
            "Vibrance", adjustments.vibrance, -1f..1f, 0f,
            onChange = { value -> viewModel.updateAdjustments { it.copy(vibrance = value) } },
            onCommit = viewModel::commitAdjustments,
            onReset = { viewModel.resetAdjustments { it.copy(vibrance = 0f) } },
        )
        AdjustmentSlider(
            "Clarity", adjustments.clarity, -1f..1f, 0f,
            onChange = { value -> viewModel.updateAdjustments { it.copy(clarity = value) } },
            onCommit = viewModel::commitAdjustments,
            onReset = { viewModel.resetAdjustments { it.copy(clarity = 0f) } },
        )
    }
}

private enum class LightControl(val label: String) {
    EXPOSURE("Exposure"), BRIGHTNESS("Brightness"), CONTRAST("Contrast"), HIGHLIGHTS("Highlights"),
    SHADOWS("Shadows"), WHITES("Whites"), BLACKS("Blacks"), GAMMA("Gamma"), CURVE("Tone Curve"), VIGNETTE("Vignette"),
}

@Composable
private fun LightPanel(state: EditorUiState, viewModel: EditorViewModel) {
    var selected by remember { mutableStateOf(LightControl.EXPOSURE) }
    val a = state.edits.adjustments
    PanelHeader("Light", actionLabel = "Reset", onResetAll = {
        viewModel.commitAdjustments { it.copy(
            exposure = 0f, brightness = 0f, contrast = 1f, highlights = 0f, shadows = 0f,
            whites = 0f, blacks = 0f, gamma = 1f, toneCurve = 0f, vignette = 0f,
        ) }
    })
    HorizontalTextSelector(LightControl.entries, selected, { it.label }) { selected = it }
    val value = when (selected) {
        LightControl.EXPOSURE -> a.exposure; LightControl.BRIGHTNESS -> a.brightness; LightControl.CONTRAST -> a.contrast
        LightControl.HIGHLIGHTS -> a.highlights; LightControl.SHADOWS -> a.shadows; LightControl.WHITES -> a.whites
        LightControl.BLACKS -> a.blacks; LightControl.GAMMA -> a.gamma; LightControl.CURVE -> a.toneCurve
        LightControl.VIGNETTE -> a.vignette
    }
    val range = when (selected) {
        LightControl.EXPOSURE -> -2f..2f; LightControl.CONTRAST -> 0.5f..1.5f; LightControl.GAMMA -> 0.4f..2.5f
        else -> -1f..1f
    }
    val neutral = when (selected) { LightControl.CONTRAST, LightControl.GAMMA -> 1f; else -> 0f }
    fun transform(value: Float) = when (selected) {
        LightControl.EXPOSURE -> a.copy(exposure = value); LightControl.BRIGHTNESS -> a.copy(brightness = value)
        LightControl.CONTRAST -> a.copy(contrast = value); LightControl.HIGHLIGHTS -> a.copy(highlights = value)
        LightControl.SHADOWS -> a.copy(shadows = value); LightControl.WHITES -> a.copy(whites = value)
        LightControl.BLACKS -> a.copy(blacks = value); LightControl.GAMMA -> a.copy(gamma = value)
        LightControl.CURVE -> a.copy(toneCurve = value); LightControl.VIGNETTE -> a.copy(vignette = value)
    }
    AdjustmentSlider(
        selected.label, value, range, neutral,
        onChange = { newValue -> viewModel.updateAdjustments { transform(newValue) } },
        onCommit = viewModel::commitAdjustments,
        onReset = { viewModel.resetAdjustments { transform(neutral) } },
    )
}

private enum class ColorControl(val label: String) {
    TEMPERATURE("Temperature"), TINT("Tint"), SATURATION("Saturation"), VIBRANCE("Vibrance"), HUE("Hue"),
    RGB("RGB Channels"), HSL("HSL Mix"), BALANCE("Color Balance"), SPLIT_TONE("Split Tone"),
}
private enum class RgbChannel(val label: String) { RED("Red"), GREEN("Green"), BLUE("Blue") }
private enum class BalanceBand(val label: String) { SHADOWS("Shadows"), MIDTONES("Midtones"), HIGHLIGHTS("Highlights") }
private enum class HslComponent(val label: String) { HUE("Hue"), SATURATION("Saturation"), LUMINANCE("Luminance") }
private enum class SplitToneControl(val label: String) {
    SHADOW_HUE("Shadow Hue"), SHADOW_SAT("Shadow Sat"), HIGHLIGHT_HUE("Highlight Hue"),
    HIGHLIGHT_SAT("Highlight Sat"), BALANCE("Balance"),
}

@Composable
private fun ColorPanel(state: EditorUiState, viewModel: EditorViewModel) {
    var selected by remember { mutableStateOf(ColorControl.TEMPERATURE) }
    var rgbChannel by remember { mutableStateOf(RgbChannel.RED) }
    var hslRange by remember { mutableStateOf(HslColorRange.RED) }
    var hslComponent by remember { mutableStateOf(HslComponent.HUE) }
    var balanceBand by remember { mutableStateOf(BalanceBand.MIDTONES) }
    var balanceChannel by remember { mutableStateOf(RgbChannel.RED) }
    var splitControl by remember { mutableStateOf(SplitToneControl.SHADOW_HUE) }
    val a = state.edits.adjustments
    PanelHeader("Color", actionLabel = "Reset", onResetAll = {
        viewModel.commitAdjustments { it.copy(
            temperature = 0f, tint = 0f, saturation = 1f, vibrance = 0f, hue = 0f,
            redChannel = 0f, greenChannel = 0f, blueChannel = 0f,
            hsl = com.restorex.app.domain.model.HslAdjustments(),
            colorBalance = com.restorex.app.domain.model.ColorBalance(),
            splitToning = com.restorex.app.domain.model.SplitToning(),
        ) }
    })
    HorizontalTextSelector(ColorControl.entries, selected, { it.label }) { selected = it }
    when (selected) {
        ColorControl.TEMPERATURE -> SimpleColorSlider("Temperature", a.temperature, -1f..1f, 0f, viewModel) { copy(temperature = it) }
        ColorControl.TINT -> SimpleColorSlider("Tint", a.tint, -1f..1f, 0f, viewModel) { copy(tint = it) }
        ColorControl.SATURATION -> SimpleColorSlider("Saturation", a.saturation, 0f..2f, 1f, viewModel) { copy(saturation = it) }
        ColorControl.VIBRANCE -> SimpleColorSlider("Vibrance", a.vibrance, -1f..1f, 0f, viewModel) { copy(vibrance = it) }
        ColorControl.HUE -> SimpleColorSlider("Hue", a.hue, -180f..180f, 0f, viewModel, valueFormatter = { "${it.roundToInt()}°" }) { copy(hue = it) }
        ColorControl.RGB -> {
            HorizontalTextSelector(RgbChannel.entries, rgbChannel, { it.label }) { rgbChannel = it }
            val value = when (rgbChannel) { RgbChannel.RED -> a.redChannel; RgbChannel.GREEN -> a.greenChannel; RgbChannel.BLUE -> a.blueChannel }
            SimpleColorSlider(rgbChannel.label, value, -1f..1f, 0f, viewModel) { v ->
                when (rgbChannel) { RgbChannel.RED -> copy(redChannel = v); RgbChannel.GREEN -> copy(greenChannel = v); RgbChannel.BLUE -> copy(blueChannel = v) }
            }
        }
        ColorControl.HSL -> {
            HorizontalTextSelector(HslColorRange.entries, hslRange, { it.label }) { hslRange = it }
            HorizontalTextSelector(HslComponent.entries, hslComponent, { it.label }) { hslComponent = it }
            val current = a.hsl[hslRange]
            val value = when (hslComponent) { HslComponent.HUE -> current.hue; HslComponent.SATURATION -> current.saturation; HslComponent.LUMINANCE -> current.luminance }
            val range = if (hslComponent == HslComponent.HUE) -60f..60f else -1f..1f
            SimpleColorSlider("${hslRange.label} ${hslComponent.label}", value, range, 0f, viewModel) { v ->
                copy(hsl = hsl.update(hslRange) { item ->
                    when (hslComponent) { HslComponent.HUE -> item.copy(hue = v); HslComponent.SATURATION -> item.copy(saturation = v); HslComponent.LUMINANCE -> item.copy(luminance = v) }
                })
            }
        }
        ColorControl.BALANCE -> {
            HorizontalTextSelector(BalanceBand.entries, balanceBand, { it.label }) { balanceBand = it }
            HorizontalTextSelector(RgbChannel.entries, balanceChannel, { it.label }) { balanceChannel = it }
            val band = when (balanceBand) { BalanceBand.SHADOWS -> a.colorBalance.shadows; BalanceBand.MIDTONES -> a.colorBalance.midtones; BalanceBand.HIGHLIGHTS -> a.colorBalance.highlights }
            val value = when (balanceChannel) { RgbChannel.RED -> band.red; RgbChannel.GREEN -> band.green; RgbChannel.BLUE -> band.blue }
            SimpleColorSlider("${balanceBand.label} ${balanceChannel.label}", value, -1f..1f, 0f, viewModel) { v ->
                val updated = when (balanceChannel) { RgbChannel.RED -> band.copy(red = v); RgbChannel.GREEN -> band.copy(green = v); RgbChannel.BLUE -> band.copy(blue = v) }
                copy(colorBalance = when (balanceBand) {
                    BalanceBand.SHADOWS -> colorBalance.copy(shadows = updated)
                    BalanceBand.MIDTONES -> colorBalance.copy(midtones = updated)
                    BalanceBand.HIGHLIGHTS -> colorBalance.copy(highlights = updated)
                })
            }
        }
        ColorControl.SPLIT_TONE -> {
            HorizontalTextSelector(SplitToneControl.entries, splitControl, { it.label }) { splitControl = it }
            val split = a.splitToning
            val value = when (splitControl) {
                SplitToneControl.SHADOW_HUE -> split.shadowHue; SplitToneControl.SHADOW_SAT -> split.shadowSaturation
                SplitToneControl.HIGHLIGHT_HUE -> split.highlightHue; SplitToneControl.HIGHLIGHT_SAT -> split.highlightSaturation
                SplitToneControl.BALANCE -> split.balance
            }
            val range = if (splitControl == SplitToneControl.SHADOW_HUE || splitControl == SplitToneControl.HIGHLIGHT_HUE) 0f..360f else if (splitControl == SplitToneControl.BALANCE) -1f..1f else 0f..1f
            val neutral = when (splitControl) { SplitToneControl.SHADOW_HUE -> 220f; SplitToneControl.HIGHLIGHT_HUE -> 40f; else -> 0f }
            SimpleColorSlider(splitControl.label, value, range, neutral, viewModel) { v ->
                copy(splitToning = when (splitControl) {
                    SplitToneControl.SHADOW_HUE -> splitToning.copy(shadowHue = v)
                    SplitToneControl.SHADOW_SAT -> splitToning.copy(shadowSaturation = v)
                    SplitToneControl.HIGHLIGHT_HUE -> splitToning.copy(highlightHue = v)
                    SplitToneControl.HIGHLIGHT_SAT -> splitToning.copy(highlightSaturation = v)
                    SplitToneControl.BALANCE -> splitToning.copy(balance = v)
                })
            }
        }
    }
}

@Composable
private fun SimpleColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    neutral: Float,
    viewModel: EditorViewModel,
    valueFormatter: ((Float) -> String)? = null,
    transform: com.restorex.app.domain.model.Adjustments.(Float) -> com.restorex.app.domain.model.Adjustments,
) {
    AdjustmentSlider(
        label, value, range, neutral,
        onChange = { v -> viewModel.updateAdjustments { it.transform(v) } },
        onCommit = viewModel::commitAdjustments,
        onReset = { viewModel.resetAdjustments { it.transform(neutral) } },
        valueFormatter = valueFormatter,
    )
}

private enum class DetailGroup(val label: String) {
    STRUCTURE("Structure"), SHARPENING("Sharpening"), NOISE("Noise Reduction"),
}

private enum class DetailControl(val label: String, val group: DetailGroup) {
    STRUCTURE("Structure", DetailGroup.STRUCTURE),
    CLARITY("Clarity", DetailGroup.STRUCTURE),
    TEXTURE("Texture", DetailGroup.STRUCTURE),
    DEHAZE("Dehaze", DetailGroup.STRUCTURE),
    SHARPEN("Amount", DetailGroup.SHARPENING),
    RADIUS("Radius", DetailGroup.SHARPENING),
    THRESHOLD("Threshold", DetailGroup.SHARPENING),
    MASKING("Masking", DetailGroup.SHARPENING),
    LUMA_NOISE("Luminance", DetailGroup.NOISE),
    COLOR_NOISE("Color", DetailGroup.NOISE),
}

private enum class FilmControl(val label: String) {
    GRAIN("Grain"), FADE("Fade"), VIGNETTE("Vignette"), TONE("Warm / Cool"), COLOR_FADE("Color Fade"),
    ROLLOFF("Highlight Roll-off"), SHADOW_HUE("Shadow Hue"), SHADOW_TINT("Shadow Tint"),
    LIGHT_LEAK("Light Leak"), ABERRATION("Chromatic"),
}

@Composable
private fun FilmPanel(state: EditorUiState, viewModel: EditorViewModel) {
    var selected by remember { mutableStateOf(FilmControl.GRAIN) }
    val a = state.edits.adjustments
    val film = a.film
    PanelHeader(
        "Film",
        actionLabel = "Reset film",
        onResetAll = { viewModel.commitAdjustments { it.copy(film = com.restorex.app.domain.model.FilmEffects(), vignette = 0f) } },
    )
    HorizontalTextSelector(FilmControl.entries, selected, { it.label }) { selected = it }
    val value = when (selected) {
        FilmControl.GRAIN -> film.grain; FilmControl.FADE -> film.fade; FilmControl.VIGNETTE -> a.vignette
        FilmControl.TONE -> film.tone; FilmControl.COLOR_FADE -> film.colorFade; FilmControl.ROLLOFF -> film.highlightRolloff
        FilmControl.SHADOW_HUE -> film.shadowTintHue; FilmControl.SHADOW_TINT -> film.shadowTint
        FilmControl.LIGHT_LEAK -> film.lightLeak; FilmControl.ABERRATION -> film.chromaticAberration
    }
    val range = when (selected) {
        FilmControl.VIGNETTE, FilmControl.TONE -> -1f..1f
        FilmControl.SHADOW_HUE -> 0f..360f
        else -> 0f..1f
    }
    val neutral = when (selected) { FilmControl.SHADOW_HUE -> 220f; else -> 0f }
    fun transform(v: Float) = when (selected) {
        FilmControl.VIGNETTE -> a.copy(vignette = v)
        FilmControl.GRAIN -> a.copy(film = film.copy(grain = v)); FilmControl.FADE -> a.copy(film = film.copy(fade = v))
        FilmControl.TONE -> a.copy(film = film.copy(tone = v)); FilmControl.COLOR_FADE -> a.copy(film = film.copy(colorFade = v))
        FilmControl.ROLLOFF -> a.copy(film = film.copy(highlightRolloff = v)); FilmControl.SHADOW_HUE -> a.copy(film = film.copy(shadowTintHue = v))
        FilmControl.SHADOW_TINT -> a.copy(film = film.copy(shadowTint = v)); FilmControl.LIGHT_LEAK -> a.copy(film = film.copy(lightLeak = v))
        FilmControl.ABERRATION -> a.copy(film = film.copy(chromaticAberration = v))
    }
    AdjustmentSlider(
        selected.label, value, range, neutral,
        onChange = { newValue -> viewModel.updateAdjustments { transform(newValue) } },
        onCommit = viewModel::commitAdjustments,
        onReset = { viewModel.resetAdjustments { transform(neutral) } },
        valueFormatter = if (selected == FilmControl.SHADOW_HUE) ({ "${it.roundToInt()}°" }) else null,
    )
}

@Composable
private fun DetailsPanel(state: EditorUiState, viewModel: EditorViewModel) {
    var group by remember { mutableStateOf(DetailGroup.STRUCTURE) }
    var selected by remember { mutableStateOf(DetailControl.STRUCTURE) }
    val a = state.edits.adjustments
    PanelHeader("Details", actionLabel = "Reset details", onResetAll = {
        viewModel.setSharpenMaskPreview(false)
        viewModel.commitAdjustments { it.copy(
            structure = 0f, clarity = 0f, texture = 0f, dehaze = 0f,
            sharpening = 0f, sharpeningRadius = 1f, sharpeningThreshold = 0.02f,
            sharpeningMasking = 0f, luminanceNoiseReduction = 0f, colorNoiseReduction = 0f,
        ) }
    })
    HorizontalTextSelector(DetailGroup.entries, group, { it.label }) { newGroup ->
        group = newGroup
        selected = DetailControl.entries.first { it.group == newGroup }
        if (newGroup != DetailGroup.SHARPENING) viewModel.setSharpenMaskPreview(false)
    }
    HorizontalTextSelector(DetailControl.entries.filter { it.group == group }, selected, { it.label }) { selected = it }
    val value = when (selected) {
        DetailControl.STRUCTURE -> a.structure; DetailControl.CLARITY -> a.clarity; DetailControl.TEXTURE -> a.texture
        DetailControl.DEHAZE -> a.dehaze; DetailControl.SHARPEN -> a.sharpening
        DetailControl.RADIUS -> a.sharpeningRadius; DetailControl.THRESHOLD -> a.sharpeningThreshold
        DetailControl.MASKING -> a.sharpeningMasking
        DetailControl.LUMA_NOISE -> a.luminanceNoiseReduction; DetailControl.COLOR_NOISE -> a.colorNoiseReduction
    }
    val range = when (selected) {
        DetailControl.CLARITY, DetailControl.TEXTURE -> -1f..1f
        DetailControl.SHARPEN -> 0f..2f
        DetailControl.RADIUS -> 0.3f..3f
        else -> 0f..1f
    }
    val neutral = when (selected) { DetailControl.RADIUS -> 1f; DetailControl.THRESHOLD -> 0.02f; else -> 0f }
    fun transform(v: Float) = when (selected) {
        DetailControl.STRUCTURE -> a.copy(structure = v); DetailControl.CLARITY -> a.copy(clarity = v)
        DetailControl.TEXTURE -> a.copy(texture = v); DetailControl.DEHAZE -> a.copy(dehaze = v)
        DetailControl.SHARPEN -> a.copy(sharpening = v); DetailControl.RADIUS -> a.copy(sharpeningRadius = v)
        DetailControl.THRESHOLD -> a.copy(sharpeningThreshold = v); DetailControl.MASKING -> a.copy(sharpeningMasking = v)
        DetailControl.LUMA_NOISE -> a.copy(luminanceNoiseReduction = v)
        DetailControl.COLOR_NOISE -> a.copy(colorNoiseReduction = v)
    }
    AdjustmentSlider(
        selected.label, value, range, neutral,
        onChange = { newValue -> viewModel.updateAdjustments { transform(newValue) } },
        onCommit = viewModel::commitAdjustments,
        onReset = { viewModel.resetAdjustments { transform(neutral) } },
        valueFormatter = when (selected) {
            DetailControl.RADIUS -> { v -> String.format(Locale.ROOT, "%.1f", v) }
            DetailControl.SHARPEN -> { v -> "${(v * 100).roundToInt()}%" }
            DetailControl.THRESHOLD, DetailControl.MASKING, DetailControl.STRUCTURE, DetailControl.DEHAZE,
            DetailControl.LUMA_NOISE, DetailControl.COLOR_NOISE -> { v -> "${(v * 100).roundToInt()}" }
            else -> null
        },
    )
    when (selected) {
        DetailControl.RADIUS -> Text(
            "Radius is measured in pixels at original image resolution.",
            color = RestoreXMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        DetailControl.THRESHOLD -> Text(
            "0–100 maps to 0–30.6 levels of 8-bit luminance detail suppression.",
            color = RestoreXMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        else -> Unit
    }
    if (group == DetailGroup.SHARPENING) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val maskAvailable = a.sharpening > 0f || a.sharpeningMasking > 0f
            Checkbox(
                checked = state.showSharpenMask,
                onCheckedChange = viewModel::setSharpenMaskPreview,
                enabled = maskAvailable,
            )
            Text(
                "Preview edge mask (preview only)",
                color = if (maskAvailable) RestoreXText else RestoreXDisabled,
                fontSize = 12.sp,
                modifier = Modifier.semantics { contentDescription = "Preview sharpening edge mask" },
            )
        }
    }
}

@Composable
private fun <T> HorizontalTextSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(options) { option ->
            val active = option == selected
            Text(
                label(option),
                color = if (active) Color(0xFF251A00) else RestoreXMuted,
                fontSize = 11.sp,
                modifier = Modifier.background(if (active) RestoreXAccent else RestoreXPanelRaised, RoundedCornerShape(10.dp))
                    .clickable { onSelected(option) }.padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun FilterPanel(state: EditorUiState, viewModel: EditorViewModel, onImportLut: () -> Unit) {
    var workspace by remember { mutableStateOf(FilterCategory.NATURAL.label) }
    val categoryLabels = FilterCategory.entries.map { it.label } + "LUTs"
    PanelHeader("Filters", actionLabel = "Reset filter", onResetAll = viewModel::resetFilterAndLut)
    HorizontalTextSelector(categoryLabels, workspace, { it }) { workspace = it }
    if (workspace == "LUTs") {
        LutPanelContent(state, viewModel, onImportLut)
        return
    }
    val category = FilterCategory.entries.first { it.label == workspace }
    val original = state.presets.first { it.id == com.restorex.app.domain.model.FilterPreset.ORIGINAL_ID }
    val visible = buildList {
        add(original)
        addAll(state.presets.filter { it.category == category && it.id != original.id })
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(visible, key = { it.id }) { preset ->
            FilterThumbnail(
                id = preset.id,
                label = preset.name,
                image = state.filterPreviews[preset.id],
                selected = state.edits.filter.presetId == preset.id,
                sourceIdentity = state.sourceIdentity,
                onRequest = viewModel::requestFilterThumbnail,
                onClick = { viewModel.applyPreset(preset.id) },
            )
        }
    }
    if (state.edits.filter.presetId != com.restorex.app.domain.model.FilterPreset.ORIGINAL_ID) {
        val preset = state.presets.firstOrNull { it.id == state.edits.filter.presetId }
        Text(preset?.name.orEmpty(), color = RestoreXText, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        AdjustmentSlider(
            "Intensity", state.edits.filter.intensity, 0f..1f, preset?.defaultIntensity ?: 1f,
            onChange = viewModel::updateFilterIntensity,
            onCommit = viewModel::commitFilterIntensity,
            onReset = { viewModel.updateFilterIntensity(preset?.defaultIntensity ?: 1f); viewModel.commitFilterIntensity() },
        )
    }
}

@Composable
private fun LutPanelContent(state: EditorUiState, viewModel: EditorViewModel, onImportLut: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { CompactToolButton("Import .cube", AppIcon.Enhance, false, onImportLut) }
        items(state.importedLuts, key = { it.id }) { lut ->
            val selected = state.edits.lut?.id == lut.id
            Column(
                Modifier.width(108.dp).background(if (selected) RestoreXPanelRaised else Color.Transparent, RoundedCornerShape(10.dp))
                    .border(if (selected) 2.dp else 0.dp, if (selected) RestoreXAccent else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { viewModel.selectLut(lut) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(lut.name, color = if (selected) RestoreXAccent else RestoreXText, fontSize = 11.sp, maxLines = 1)
                Text("Delete", color = RestoreXMuted, fontSize = 10.sp, modifier = Modifier.clickable { viewModel.deleteLut(lut) }.padding(5.dp))
            }
        }
    }
    state.edits.lut?.let { lut ->
        Text(lut.name, color = RestoreXText, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        AdjustmentSlider(
            "LUT intensity", lut.intensity, 0f..1f, 1f,
            onChange = viewModel::updateLutIntensity,
            onCommit = viewModel::commitLutIntensity,
            onReset = { viewModel.updateLutIntensity(1f); viewModel.commitLutIntensity() },
        )
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    neutral: Float,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    valueFormatter: ((Float) -> String)? = null,
) {
    val display = when {
        range.endInclusive <= 1f -> (value * 100).roundToInt()
        neutral == 1f -> ((value - 1f) * 100).roundToInt()
        else -> (value * 100).roundToInt()
    }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = RestoreXMuted, fontSize = 12.sp, modifier = Modifier.width(72.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onCommit,
            valueRange = range,
            modifier = Modifier.weight(1f)
                .pointerInput(label, onReset) { detectTapGestures(onDoubleTap = { onReset() }) }
                .semantics { contentDescription = "$label adjustment; double tap to reset" },
        )
        Text(
            text = valueFormatter?.invoke(value) ?: if (display > 0) "+$display" else display.toString(),
            color = if (value == neutral) RestoreXMuted else RestoreXAccent,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
        ToolIconButton(AppIcon.Reset, "Reset $label", enabled = value != neutral, onClick = onReset, size = 36)
    }
}

@Composable
private fun FilterThumbnail(
    id: String,
    label: String,
    image: RasterImage?,
    selected: Boolean,
    sourceIdentity: Long,
    onRequest: (String) -> Unit,
    onClick: () -> Unit,
) {
    LaunchedEffect(sourceIdentity, id) { onRequest(id) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp).clickable(onClick = onClick).semantics { contentDescription = "$label filter" },
    ) {
        Box(
            Modifier.width(80.dp).height(96.dp).background(RestoreXPanelRaised, RoundedCornerShape(11.dp))
                .then(if (selected) Modifier.border(2.dp, RestoreXAccent, RoundedCornerShape(11.dp)) else Modifier),
        ) {
            if (image != null) {
                val bitmap = rememberRasterImage(image)
                Canvas(Modifier.fillMaxSize().padding(if (selected) 3.dp else 1.dp)) {
                    val targetAspect = size.width / size.height
                    val sourceAspect = bitmap.width.toFloat() / bitmap.height
                    val cropWidth = if (sourceAspect > targetAspect) (bitmap.height * targetAspect).roundToInt() else bitmap.width
                    val cropHeight = if (sourceAspect > targetAspect) bitmap.height else (bitmap.width / targetAspect).roundToInt()
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset((bitmap.width - cropWidth) / 2, (bitmap.height - cropHeight) / 2),
                        srcSize = IntSize(cropWidth, cropHeight),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(18.dp), color = RestoreXAccent, strokeWidth = 2.dp)
            }
        }
        Text(label, color = if (selected) RestoreXAccent else RestoreXMuted, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CompactToolButton(label: String, icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clickable(onClick = onClick).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(42.dp).background(if (selected) RestoreXAccent else RestoreXPanelRaised, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { AppIconCanvas(icon, if (selected) Color(0xFF251A00) else RestoreXText, Modifier.size(21.dp)) }
        Text(label, color = if (selected) RestoreXAccent else RestoreXMuted, fontSize = 10.sp)
    }
}

@Composable
private fun ToolNavigation(selected: EditorTool, onSelected: (EditorTool) -> Unit) {
    var category by remember(selected.category) { mutableStateOf(selected.category) }
    HorizontalDivider(color = Color(0xFF2A2B2F))
    Column(Modifier.fillMaxWidth().background(RestoreXPanel).navigationBarsPadding()) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 3.dp)) {
        ToolCategory.entries.forEach { item ->
            val active = item == category
            Column(
                modifier = Modifier.width(72.dp).height(50.dp).clickable {
                    category = item; EditorTool.entries.firstOrNull { it.category == item && it.available }?.let(onSelected)
                }.semantics { contentDescription = "${item.label} tools" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                AppIconCanvas(item.icon, if (active) RestoreXAccent else RestoreXMuted, Modifier.size(20.dp))
                Text(item.label, color = if (active) RestoreXAccent else RestoreXMuted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
    val categoryTools = EditorTool.entries.filter { it.category == category }
    if (categoryTools.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp)) {
        categoryTools.forEach { tool ->
            val tint = when {
                !tool.available -> RestoreXDisabled
                tool == selected -> RestoreXAccent
                else -> RestoreXMuted
            }
            Column(
                modifier = Modifier.width(88.dp).height(34.dp)
                    .clickable(enabled = tool.available) { onSelected(tool) }
                    .semantics {
                        contentDescription = if (tool.available) tool.label else "${tool.label}, unavailable"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(tool.label, color = tint, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
    }
}

@Composable
private fun ToolIconButton(
    icon: AppIcon,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    size: Int = 48,
) {
    Box(
        Modifier.size(size.dp).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AppIconCanvas(icon, if (enabled) RestoreXText else RestoreXDisabled, Modifier.size(21.dp))
    }
}

private enum class AppIcon {
    Back, Undo, Redo, Compare, Fullscreen, More, Settings, Magic, Collage, Layers, MultipleImages, IdCard, Document, Swap,
    Crop, Enhance, Light, Color, Filters, Details, Retouch, Rotate, Reset, Image,
}

@Composable
private fun AppIconCanvas(icon: AppIcon, tint: Color, modifier: Modifier) {
    Canvas(modifier) { drawAppIcon(icon, tint) }
}

private fun DrawScope.drawAppIcon(icon: AppIcon, tint: Color) {
    val stroke = size.minDimension * 0.085f
    val outline = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2), stroke, StrokeCap.Round)

    when (icon) {
        AppIcon.Back -> { line(.72f, .18f, .32f, .5f); line(.32f, .5f, .72f, .82f) }
        AppIcon.Undo, AppIcon.Redo -> {
            val mirror = icon == AppIcon.Redo
            val path = Path().apply {
                val sx = if (mirror) .25f else .75f
                val ex = if (mirror) .82f else .18f
                moveTo(size.width * sx, size.height * .72f)
                cubicTo(size.width * sx, size.height * .32f, size.width * .5f, size.height * .25f, size.width * ex, size.height * .43f)
            }
            drawPath(path, tint, style = outline)
            if (mirror) { line(.82f, .43f, .66f, .2f); line(.82f, .43f, .57f, .5f) }
            else { line(.18f, .43f, .34f, .2f); line(.18f, .43f, .43f, .5f) }
        }
        AppIcon.Compare -> {
            drawRect(tint, Offset(size.width * .15f, size.height * .18f), Size(size.width * .7f, size.height * .64f), style = outline)
            line(.5f, .18f, .5f, .82f)
            drawRect(tint.copy(alpha = .35f), Offset(size.width * .18f, size.height * .21f), Size(size.width * .29f, size.height * .58f))
        }
        AppIcon.Fullscreen -> {
            line(.15f, .38f, .15f, .15f); line(.15f, .15f, .38f, .15f)
            line(.62f, .15f, .85f, .15f); line(.85f, .15f, .85f, .38f)
            line(.15f, .62f, .15f, .85f); line(.15f, .85f, .38f, .85f)
            line(.62f, .85f, .85f, .85f); line(.85f, .85f, .85f, .62f)
        }
        AppIcon.More -> {
            drawCircle(tint, size.width * .07f, Offset(size.width * .22f, center.y))
            drawCircle(tint, size.width * .07f, center)
            drawCircle(tint, size.width * .07f, Offset(size.width * .78f, center.y))
        }
        AppIcon.Settings -> {
            val gear = Path().apply {
                repeat(24) { index ->
                    val angle = -Math.PI / 2 + index * Math.PI * 2 / 24
                    val radius = if (index % 3 == 1) .32f else .43f
                    val point = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * size.width * radius,
                        center.y + kotlin.math.sin(angle).toFloat() * size.height * radius,
                    )
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
                close()
            }
            drawPath(gear, tint, style = outline)
            drawCircle(tint, size.width * .11f, center, style = outline)
        }
        AppIcon.Magic -> {
            line(.20f, .82f, .68f, .34f)
            line(.15f, .76f, .26f, .87f)
            line(.62f, .29f, .73f, .40f)
            line(.78f, .10f, .78f, .24f); line(.71f, .17f, .85f, .17f)
            line(.54f, .08f, .54f, .17f); line(.49f, .125f, .59f, .125f)
            line(.86f, .38f, .86f, .48f); line(.81f, .43f, .91f, .43f)
        }
        AppIcon.Collage -> {
            drawRect(tint, Offset(size.width * .12f, size.height * .12f), Size(size.width * .32f, size.height * .32f), style = outline)
            drawRect(tint, Offset(size.width * .56f, size.height * .12f), Size(size.width * .32f, size.height * .32f), style = outline)
            drawRect(tint, Offset(size.width * .12f, size.height * .56f), Size(size.width * .32f, size.height * .32f), style = outline)
            drawRect(tint, Offset(size.width * .56f, size.height * .56f), Size(size.width * .32f, size.height * .32f), style = outline)
        }
        AppIcon.Layers -> {
            val top = Path().apply { moveTo(size.width * .5f, size.height * .12f); lineTo(size.width * .86f, size.height * .34f); lineTo(size.width * .5f, size.height * .56f); lineTo(size.width * .14f, size.height * .34f); close() }
            drawPath(top, tint, style = outline)
            line(.18f, .51f, .5f, .71f); line(.5f, .71f, .82f, .51f)
            line(.18f, .68f, .5f, .88f); line(.5f, .88f, .82f, .68f)
        }
        AppIcon.MultipleImages -> {
            drawRect(tint, Offset(size.width * .1f, size.height * .25f), Size(size.width * .62f, size.height * .58f), style = outline)
            drawRect(tint, Offset(size.width * .28f, size.height * .12f), Size(size.width * .62f, size.height * .58f), style = outline)
            drawCircle(tint, size.width * .055f, Offset(size.width * .7f, size.height * .3f))
        }
        AppIcon.IdCard -> {
            drawRect(tint, Offset(size.width * .1f, size.height * .18f), Size(size.width * .8f, size.height * .64f), style = outline)
            drawCircle(tint, size.width * .1f, Offset(size.width * .36f, size.height * .4f), style = outline)
            val shoulders = Path().apply { moveTo(size.width * .2f, size.height * .68f); cubicTo(size.width * .22f, size.height * .52f, size.width * .5f, size.height * .52f, size.width * .52f, size.height * .68f) }
            drawPath(shoulders, tint, style = outline)
            line(.62f, .38f, .8f, .38f); line(.62f, .55f, .8f, .55f)
        }
        AppIcon.Document -> {
            val page = Path().apply { moveTo(size.width * .22f, size.height * .1f); lineTo(size.width * .62f, size.height * .1f); lineTo(size.width * .82f, size.height * .3f); lineTo(size.width * .82f, size.height * .9f); lineTo(size.width * .22f, size.height * .9f); close() }
            drawPath(page, tint, style = outline)
            line(.62f, .1f, .62f, .3f); line(.62f, .3f, .82f, .3f)
            line(.34f, .48f, .7f, .48f); line(.34f, .63f, .7f, .63f); line(.34f, .78f, .62f, .78f)
        }
        AppIcon.Swap -> {
            line(.14f, .34f, .82f, .34f); line(.82f, .34f, .66f, .18f); line(.82f, .34f, .66f, .5f)
            line(.86f, .68f, .18f, .68f); line(.18f, .68f, .34f, .52f); line(.18f, .68f, .34f, .84f)
        }
        AppIcon.Crop -> { line(.22f, .08f, .22f, .72f); line(.22f, .72f, .86f, .72f); line(.78f, .92f, .78f, .28f); line(.78f, .28f, .14f, .28f) }
        AppIcon.Enhance -> { line(.5f, .08f, .5f, .92f); line(.08f, .5f, .92f, .5f); line(.2f, .2f, .8f, .8f); line(.8f, .2f, .2f, .8f) }
        AppIcon.Light -> {
            drawCircle(tint, size.minDimension * .22f, center, style = outline)
            repeat(8) { index ->
                val a = index * Math.PI / 4
                val p1 = Offset(center.x + kotlin.math.cos(a).toFloat() * size.width * .34f, center.y + kotlin.math.sin(a).toFloat() * size.height * .34f)
                val p2 = Offset(center.x + kotlin.math.cos(a).toFloat() * size.width * .46f, center.y + kotlin.math.sin(a).toFloat() * size.height * .46f)
                drawLine(tint, p1, p2, stroke, StrokeCap.Round)
            }
        }
        AppIcon.Color -> {
            val path = Path().apply { moveTo(center.x, size.height * .08f); cubicTo(size.width * .82f, size.height * .45f, size.width * .82f, size.height * .78f, center.x, size.height * .9f); cubicTo(size.width * .18f, size.height * .78f, size.width * .18f, size.height * .45f, center.x, size.height * .08f) }
            drawPath(path, tint, style = outline)
        }
        AppIcon.Filters -> {
            drawCircle(tint, size.width * .26f, Offset(size.width * .38f, center.y), style = outline)
            drawCircle(tint, size.width * .26f, Offset(size.width * .62f, center.y), style = outline)
        }
        AppIcon.Details -> { line(.15f, .25f, .85f, .25f); line(.15f, .5f, .85f, .5f); line(.15f, .75f, .85f, .75f); drawCircle(tint, stroke, Offset(size.width * .35f, size.height * .25f)); drawCircle(tint, stroke, Offset(size.width * .65f, size.height * .5f)); drawCircle(tint, stroke, Offset(size.width * .45f, size.height * .75f)) }
        AppIcon.Retouch -> { line(.2f, .8f, .8f, .2f); line(.32f, .92f, .92f, .32f); line(.2f, .8f, .32f, .92f); line(.8f, .2f, .92f, .32f); line(.45f, .55f, .57f, .67f) }
        AppIcon.Rotate -> {
            val path = Path().apply { moveTo(size.width * .78f, size.height * .37f); cubicTo(size.width * .68f, size.height * .12f, size.width * .3f, size.height * .14f, size.width * .2f, size.height * .43f); cubicTo(size.width * .08f, size.height * .78f, size.width * .55f, size.height * .95f, size.width * .78f, size.height * .7f) }
            drawPath(path, tint, style = outline); line(.78f, .37f, .8f, .13f); line(.78f, .37f, .56f, .3f)
        }
        AppIcon.Reset -> { line(.28f, .35f, .28f, .8f); line(.72f, .35f, .72f, .8f); line(.18f, .27f, .82f, .27f); line(.4f, .17f, .6f, .17f); line(.2f, .27f, .32f, .15f) }
        AppIcon.Image -> {
            drawRect(tint, Offset(size.width * .12f, size.height * .18f), Size(size.width * .76f, size.height * .64f), style = outline)
            drawCircle(tint, size.width * .07f, Offset(size.width * .67f, size.height * .36f))
            val path = Path().apply { moveTo(size.width * .18f, size.height * .72f); lineTo(size.width * .4f, size.height * .48f); lineTo(size.width * .55f, size.height * .62f); lineTo(size.width * .66f, size.height * .51f); lineTo(size.width * .82f, size.height * .72f) }
            drawPath(path, tint, style = outline)
        }
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportOptions, Boolean) -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableFloatStateOf(95f) }
    var maxWidth by remember { mutableStateOf("") }
    var maxHeight by remember { mutableStateOf("") }
    var shareAfterExport by remember { mutableStateOf(false) }
    var preserveMetadata by remember { mutableStateOf(true) }
    var targetKb by remember { mutableStateOf("") }
    val widthValue = maxWidth.toIntOrNull()
    val heightValue = maxHeight.toIntOrNull()
    val targetValue = targetKb.toIntOrNull()
    val dimensionsValid = (maxWidth.isBlank() || (widthValue ?: 0) > 0) &&
        (maxHeight.isBlank() || (heightValue ?: 0) > 0) &&
        (targetKb.isBlank() || (targetValue ?: 0) in 10..50_000)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RestoreXPanelRaised,
        title = { Text("Export photo", color = RestoreXText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Export renders from the original photo. Blank dimensions preserve the edited resolution.", color = RestoreXMuted)
                HorizontalTextSelector(ExportFormat.entries, format, { it.name }) { format = it; if (it == ExportFormat.PNG) targetKb = "" }
                if (format != ExportFormat.PNG) {
                    AdjustmentSlider(
                        "Quality", quality, 50f..100f, 95f,
                        onChange = { quality = it }, onCommit = {}, onReset = { quality = 95f },
                        valueFormatter = { "${it.roundToInt()}" },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxWidth,
                        onValueChange = { maxWidth = it.filter(Char::isDigit).take(5) },
                        label = { Text("Max width") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxHeight,
                        onValueChange = { maxHeight = it.filter(Char::isDigit).take(5) },
                        label = { Text("Max height") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Custom dimensions fit within these bounds without distortion or upscaling.", color = RestoreXMuted, fontSize = 11.sp)
                if (format != ExportFormat.PNG) {
                    OutlinedTextField(
                        value = targetKb,
                        onValueChange = { targetKb = it.filter(Char::isDigit).take(5) },
                        label = { Text("Optional target KB") },
                        supportingText = { Text("Uses the highest fitting quality, then proportionally reduces dimensions if necessary.") },
                        singleLine = true,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = shareAfterExport, onCheckedChange = { shareAfterExport = it })
                    Text("Open Android share sheet after saving", color = RestoreXText, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = preserveMetadata, onCheckedChange = { preserveMetadata = it })
                    Text("Preserve available photo metadata", color = RestoreXText, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(enabled = dimensionsValid, onClick = {
                onExport(
                    ExportOptions(format, quality.roundToInt(), widthValue, heightValue, preserveMetadata, targetValue.takeIf { format != ExportFormat.PNG }),
                    shareAfterExport,
                )
            }) { Text("Save to device") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun RasterImage.toBitmap(): Bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)

@Composable
private fun rememberRasterPair(edited: RasterImage, original: RasterImage): Pair<ImageBitmap, ImageBitmap> {
    val bitmaps = remember(edited, original) {
        val editedBitmap = edited.toBitmap()
        val originalBitmap = if (edited === original) editedBitmap else original.toBitmap()
        editedBitmap to originalBitmap
    }
    DisposableEffect(bitmaps) {
        onDispose {
            if (!bitmaps.first.isRecycled) bitmaps.first.recycle()
            if (bitmaps.second !== bitmaps.first && !bitmaps.second.isRecycled) bitmaps.second.recycle()
        }
    }
    return remember(bitmaps) { bitmaps.first.asImageBitmap() to bitmaps.second.asImageBitmap() }
}

@Composable
private fun rememberRasterImage(raster: RasterImage): ImageBitmap {
    val bitmap = remember(raster) { raster.toBitmap() }
    DisposableEffect(bitmap) { onDispose { if (!bitmap.isRecycled) bitmap.recycle() } }
    return remember(bitmap) { bitmap.asImageBitmap() }
}

private fun exportName(extension: String): String {
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return "Restovue_$stamp.$extension"
}

private fun openDeveloperWebsite(context: Context, url: String) {
    if (url.isBlank()) {
        Toast.makeText(context, context.getString(R.string.website_not_configured), Toast.LENGTH_LONG).show()
        return
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        .onFailure { Toast.makeText(context, "Unable to open developer website", Toast.LENGTH_LONG).show() }
}
