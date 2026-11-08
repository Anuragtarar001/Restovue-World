package com.restorex.app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.presentation.SmartMagicPhase
import com.restorex.app.presentation.SmartMagicUiState
import com.restorex.app.presentation.SmartMagicProgressEvent
import com.restorex.app.domain.photointelligence.SmartMagicWorkStage
import com.restorex.app.domain.photointelligence.SmartMagicWorkState
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticEntry
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticId
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticReport
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticStatus
import com.restorex.app.domain.photointelligence.AdjustmentDiagnosticValue
import androidx.compose.runtime.mutableStateOf
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
                    SmartMagicUiState(phase = SmartMagicPhase.READY, original = original, enhanced = enhanced,
                        completedLevel = EnhancementLevel.ENHANCED, completedRenderGeneration = 1,
                        modeSelectionEnabled = true),
                    {}, {}, {}, {}, { _, _ -> }, { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Smart Magic").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Enhanced processed photo").assertIsDisplayed()
        composeRule.onNodeWithText("Save Photo").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Choose Another Photo").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Back").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Natural enhancement level").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Enhanced enhancement level").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Extreme enhancement level").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun modeButtonsDispatchExactRequestedLevel() {
        val selected = mutableListOf<EnhancementLevel>()
        val image = RasterImage(2, 2, intArrayOf(-1, -1, -1, -1))
        composeRule.setContent { RestoreXTheme { SmartMagicScreen(
            SmartMagicUiState(phase = SmartMagicPhase.READY, original = image, enhanced = image,
                completedLevel = EnhancementLevel.ENHANCED, completedRenderGeneration = 4,
                modeSelectionEnabled = true), {}, {}, {}, {}, { _, _ -> }, { _, _ -> },
            onSelectLevel = { selected += it },
        ) } }
        composeRule.onNodeWithContentDescription("Natural enhancement level").performClick()
        composeRule.onNodeWithContentDescription("Extreme enhancement level").performClick()
        composeRule.onNodeWithContentDescription("Enhanced enhancement level").performClick()
        assert(selected == listOf(EnhancementLevel.NATURAL, EnhancementLevel.EXTREME, EnhancementLevel.ENHANCED))
    }

    @Test fun comparisonDividerAndDoubleTapZoomAreInteractive() {
        val events = mutableListOf<Boolean>()
        val original = RasterImage(4, 4, IntArray(16) { 0xff101010.toInt() })
        val enhanced = RasterImage(4, 4, IntArray(16) { 0xffe0e0e0.toInt() })
        composeRule.setContent { RestoreXTheme { SmartMagicScreen(
            SmartMagicUiState(phase = SmartMagicPhase.READY, original = original, enhanced = enhanced,
                completedLevel = EnhancementLevel.ENHANCED, completedRenderGeneration = 2,
                modeSelectionEnabled = true), {}, {}, {}, {}, { _, _ -> }, { _, _ -> },
            onComparisonChanged = { events += it },
        ) } }
        val canvas = composeRule.onNodeWithContentDescription("Enhanced processed photo")
        canvas.performTouchInput { doubleClick() }
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 2.0 times"))
        composeRule.onNodeWithContentDescription("Compare before and after").performClick()
        assert(events.last() == true)
        composeRule.onNodeWithContentDescription("Before and after comparison canvas").assertIsDisplayed()
        val divider = composeRule.onNodeWithContentDescription("Before and after divider")
        divider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "50 percent before"))
        divider.performTouchInput { down(center); moveBy(Offset(2_000f, 0f)); up() }
        divider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100 percent before"))
        divider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        divider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 percent before"))
    }

    @Test fun processingWorkflowShowsRealStageAndCancellation() {
        composeRule.setContent {
            RestoreXTheme {
                SmartMagicScreen(
                    SmartMagicUiState(phase = SmartMagicPhase.PROCESSING, stage = "Analyzing lighting",
                        progress = SmartMagicProgressEvent(1, null, null, SmartMagicWorkStage.ANALYZING_LIGHTING,
                            "Analyzing lighting", "Measuring exposure, color and detail", SmartMagicWorkState.STARTED)),
                    {}, {}, {}, {}, { _, _ -> }, { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Analyzing lighting").assertIsDisplayed()
        composeRule.onNodeWithText("Measuring exposure, color and detail").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun adjustmentDetailsShowCompleteInventoryAndSwitchModesWithoutStaleRows() {
        val level = mutableStateOf(EnhancementLevel.NATURAL)
        val image = RasterImage(2, 2, intArrayOf(-1, -1, -1, -1))
        composeRule.setContent {
            val selected = level.value
            RestoreXTheme { SmartMagicScreen(
                SmartMagicUiState(phase = SmartMagicPhase.READY, original = image, enhanced = image,
                    selectedLevel = selected, completedLevel = selected, sourceGeneration = 5,
                    activeRenderGeneration = 9, completedRenderGeneration = 9,
                    modeSelectionEnabled = true, adjustmentReport = diagnosticReport(selected, 5, 9)),
                {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, onSelectLevel = { level.value = it },
            ) }
        }
        composeRule.onNodeWithContentDescription("Adjustment Details").performClick()
        composeRule.onNodeWithText("Natural adjustment diagnostics").assertExists()
        AdjustmentDiagnosticId.entries.forEach { id ->
            composeRule.onNodeWithText(id.label, useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithContentDescription("Exposure: Applied").assertExists()
        composeRule.onNodeWithText("+0.180 EV").assertExists()
        composeRule.onNodeWithContentDescription("Brightness: Unsupported").assertExists()
        composeRule.onNodeWithContentDescription("Background exposure: Not evaluated").assertExists()

        composeRule.onNodeWithContentDescription("Extreme enhancement level").performClick()
        composeRule.onNodeWithText("Extreme adjustment diagnostics").assertExists()
        composeRule.onNodeWithText("Natural adjustment diagnostics").assertDoesNotExist()
    }

    @Test fun staleGenerationAdjustmentReportIsNotDisplayed() {
        val image = RasterImage(2, 2, intArrayOf(-1, -1, -1, -1))
        composeRule.setContent { RestoreXTheme { SmartMagicScreen(
            SmartMagicUiState(phase = SmartMagicPhase.READY, original = image, enhanced = image,
                selectedLevel = EnhancementLevel.ENHANCED, completedLevel = EnhancementLevel.ENHANCED,
                sourceGeneration = 8, completedRenderGeneration = 12, modeSelectionEnabled = true,
                adjustmentReport = diagnosticReport(EnhancementLevel.NATURAL, 7, 11)),
            {}, {}, {}, {}, { _, _ -> }, { _, _ -> },
        ) } }
        composeRule.onNodeWithContentDescription("Adjustment Details").assertDoesNotExist()
    }

    private fun diagnosticReport(level: EnhancementLevel, sourceGeneration: Long, renderGeneration: Long) =
        AdjustmentDiagnosticReport(sourceGeneration, renderGeneration, level, "ui-test", AdjustmentDiagnosticId.entries.map { id ->
            when (id) {
                AdjustmentDiagnosticId.EXPOSURE -> AdjustmentDiagnosticEntry(id, AdjustmentDiagnosticStatus.APPLIED,
                    listOf(AdjustmentDiagnosticValue("exposure", 0f, .18f, .18f, "EV")), "Global", "Accepted test plan")
                AdjustmentDiagnosticId.BRIGHTNESS -> AdjustmentDiagnosticEntry(id, AdjustmentDiagnosticStatus.UNSUPPORTED,
                    emptyList(), "Not applicable", "No brightness operator")
                AdjustmentDiagnosticId.BACKGROUND_EXPOSURE -> AdjustmentDiagnosticEntry(id, AdjustmentDiagnosticStatus.NOT_EVALUATED,
                    emptyList(), "Background", "No background exposure rule")
                else -> AdjustmentDiagnosticEntry(id, AdjustmentDiagnosticStatus.NOT_REQUIRED,
                    emptyList(), "Global", "Evaluated neutral test value")
            }
        })
}
