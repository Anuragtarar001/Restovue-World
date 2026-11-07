package com.restorex.app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.presentation.SmartMagicPhase
import com.restorex.app.presentation.SmartMagicUiState
import com.restorex.app.ui.editor.SmartMagicScreen
import com.restorex.app.ui.theme.RestoreXTheme
import org.junit.Rule
import org.junit.Test

class SmartMagicScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun readyWorkflowExposesComparisonSaveAndChooseAnother() {
        val original = RasterImage(2, 2, intArrayOf(-1, -1, -1, -1))
        val enhanced = RasterImage(2, 2, intArrayOf(0xFFEEEEEE.toInt(), 0xFFEEEEEE.toInt(), 0xFFEEEEEE.toInt(), 0xFFEEEEEE.toInt()))
        composeRule.setContent {
            RestoreXTheme {
                SmartMagicScreen(
                    SmartMagicUiState(phase = SmartMagicPhase.READY, original = original, enhanced = enhanced),
                    {}, {}, {}, {}, { _, _ -> }, { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Smart Magic").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Enhanced photo. Press and hold to view original").assertIsDisplayed()
        composeRule.onNodeWithText("Save Photo").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Choose Another Photo").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Back").assertHasClickAction()
    }

    @Test fun processingWorkflowShowsRealStageAndCancellation() {
        composeRule.setContent {
            RestoreXTheme {
                SmartMagicScreen(
                    SmartMagicUiState(phase = SmartMagicPhase.PROCESSING, stage = "Analyzing light, color, and detail"),
                    {}, {}, {}, {}, { _, _ -> }, { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Analyzing light, color, and detail").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed().assertHasClickAction()
    }
}
