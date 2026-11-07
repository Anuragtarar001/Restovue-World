package com.restorex.app.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.presentation.SmartMagicPhase
import com.restorex.app.presentation.SmartMagicUiState
import com.restorex.app.ui.theme.RestoreXAccent
import com.restorex.app.ui.theme.RestoreXBackground
import com.restorex.app.ui.theme.RestoreXMuted
import com.restorex.app.ui.theme.RestoreXPanel
import com.restorex.app.ui.theme.RestoreXText

@Composable
fun SmartMagicScreen(
    state: SmartMagicUiState,
    onBack: () -> Unit,
    onChooseAnother: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String, String?) -> Unit,
    onShare: (String, String?) -> Unit,
) {
    BackHandler(onBack = onBack)
    var showOriginal by remember { mutableStateOf(false) }
    val visible = if (showOriginal) state.original else state.enhanced ?: state.original
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF121318), RestoreXBackground, Color(0xFF090A0D))),
        ).statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "Back" }) {
                Text("‹", color = RestoreXText, fontSize = 36.sp, lineHeight = 36.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("Smart Magic", color = RestoreXText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Professional enhancement in one tap", color = RestoreXMuted, fontSize = 11.sp)
            }
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)
                .background(Color(0xFF08090C), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFF36383E), RoundedCornerShape(18.dp))
                .pointerInput(state.canCompare) {
                    if (state.canCompare) detectTapGestures(onPress = {
                        showOriginal = true
                        try { tryAwaitRelease() } finally { showOriginal = false }
                    })
                }.semantics { contentDescription = "Enhanced photo. Press and hold to view original" },
            contentAlignment = Alignment.Center,
        ) {
            visible?.let { Image(rememberRasterImage(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            if (state.isWorking) {
                Column(
                    Modifier.background(Color(0xDD1C1D20), RoundedCornerShape(14.dp)).padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = RestoreXAccent, modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(state.stage ?: "Processing", color = RestoreXText, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            if (showOriginal) {
                Text(
                    "Original", color = RestoreXText, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                        .background(Color(0xBB111216), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.phase) {
                SmartMagicPhase.READY, SmartMagicPhase.SAVED -> {
                    Text("Press and hold the photo to see the original", color = RestoreXMuted, fontSize = 12.sp)
                    Button(
                        onClick = onSave,
                        enabled = state.phase != SmartMagicPhase.SAVED,
                        colors = ButtonDefaults.buttonColors(containerColor = RestoreXAccent, contentColor = Color(0xFF171300)),
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) { Text(if (state.phase == SmartMagicPhase.SAVED) "Saved to Gallery" else "Save Photo", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                    if (state.phase == SmartMagicPhase.SAVED && state.savedUri != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpen(state.savedUri, state.savedMimeType) }) { Text("Open", color = RestoreXAccent) }
                            TextButton(onClick = { onShare(state.savedUri, state.savedMimeType) }) { Text("Share", color = RestoreXAccent) }
                        }
                    }
                    TextButton(onClick = onChooseAnother) { Text("Choose Another Photo", color = RestoreXText) }
                }
                SmartMagicPhase.PROCESSING, SmartMagicPhase.SAVING -> TextButton(onClick = onCancel) { Text("Cancel", color = RestoreXAccent) }
                SmartMagicPhase.ERROR -> {
                    Text(state.message ?: "This photo could not be processed", color = Color(0xFFFF8A80), textAlign = TextAlign.Center)
                    Button(onClick = onChooseAnother, colors = ButtonDefaults.buttonColors(containerColor = RestoreXAccent)) { Text("Choose Another Photo") }
                }
                SmartMagicPhase.IDLE -> Text("Choose a photo to begin", color = RestoreXMuted)
            }
            state.message?.takeIf { state.phase != SmartMagicPhase.ERROR }?.let {
                Text(it, color = RestoreXMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun rememberRasterImage(raster: RasterImage): ImageBitmap {
    val bitmap = remember(raster) { Bitmap.createBitmap(raster.argb, raster.width, raster.height, Bitmap.Config.ARGB_8888) }
    DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
    return remember(bitmap) { bitmap.asImageBitmap() }
}
