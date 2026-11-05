package com.restorex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.restorex.app.presentation.EditorViewModel
import com.restorex.app.ui.editor.RestoreXEditorScreen
import com.restorex.app.ui.theme.RestoreXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RestoreXApplication).container
        setContent {
            RestoreXTheme {
                val editor: EditorViewModel = viewModel(factory = EditorViewModel.Factory(container))
                RestoreXEditorScreen(viewModel = editor, onExit = ::finish)
            }
        }
    }
}
