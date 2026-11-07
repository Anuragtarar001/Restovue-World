package com.restorex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.restorex.app.presentation.EditorViewModel
import com.restorex.app.presentation.SmartMagicViewModel
import com.restorex.app.ui.editor.RestoreXEditorScreen
import com.restorex.app.ui.theme.RestoreXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as RestoreXApplication).container
        setContent {
            RestoreXTheme {
                val editor: EditorViewModel = viewModel(factory = EditorViewModel.Factory(container))
                val smartMagic: SmartMagicViewModel = viewModel(factory = SmartMagicViewModel.Factory(container))
                RestoreXEditorScreen(viewModel = editor, smartMagicViewModel = smartMagic, onExit = ::finish)
            }
        }
    }
}
