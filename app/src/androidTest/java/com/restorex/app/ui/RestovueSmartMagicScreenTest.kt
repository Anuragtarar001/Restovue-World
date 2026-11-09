package com.restorex.app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.EnhancementLevel
import com.restorex.app.domain.smartmagic.SmartMagicAnalysis
import com.restorex.app.domain.smartmagic.SmartMagicColorStatistics
import com.restorex.app.domain.smartmagic.SmartMagicDetailStatistics
import com.restorex.app.domain.smartmagic.SmartMagicLuminanceStatistics
import com.restorex.app.domain.smartmagic.SmartMagicSceneScores
import com.restorex.app.presentation.SmartMagicPhase
import com.restorex.app.presentation.SmartMagicUiState
import com.restorex.app.ui.editor.RestovueSmartMagicScreen
import com.restorex.app.ui.theme.RestoreXTheme
import org.junit.Rule
import org.junit.Test

class RestovueSmartMagicScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun approvedResultScreenUsesLiveComparisonAndActions() {
        val original = image(0xff202020.toInt())
        val enhanced = image(0xffd0d0d0.toInt())
        composeRule.setContent {
            RestoreXTheme {
                RestovueSmartMagicScreen(
                    state = SmartMagicUiState(
                        phase = SmartMagicPhase.READY,
                        original = original,
                        enhanced = enhanced,
                        selectedLevel = EnhancementLevel.ENHANCED,
                        completedLevel = EnhancementLevel.ENHANCED,
                        completedRenderGeneration = 1,
                        modeSelectionEnabled = true,
                    ),
                    onBack = {}, onChooseAnother = {}, onSave = {}, onCancel = {},
                    onOpen = { _, _ -> }, onShare = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Smart Magic").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Before and after divider").assertExists()
        composeRule.onNodeWithContentDescription("Fit photo to comparison canvas").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Fill photo to comparison canvas").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Natural enhancement").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Enhanced enhancement").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Extreme enhancement").assertHasClickAction()
        composeRule.onNodeWithText("What changed").assertExists()
        composeRule.onNodeWithText("Save Photo", substring = true).assertExists()
    }

    @Test fun outdoorBacklitPortraitNeverClaimsIndoor() {
        val photo = image(0xff808080.toInt())
        composeRule.setContent {
            RestoreXTheme {
                RestovueSmartMagicScreen(
                    state = SmartMagicUiState(
                        phase = SmartMagicPhase.READY,
                        original = photo,
                        enhanced = photo,
                        analysis = analysis(portrait = .86f, landscape = .61f, backlit = .72f),
                        selectedLevel = EnhancementLevel.ENHANCED,
                        completedLevel = EnhancementLevel.ENHANCED,
                        completedRenderGeneration = 2,
                        modeSelectionEnabled = true,
                    ),
                    onBack = {}, onChooseAnother = {}, onSave = {}, onCancel = {},
                    onOpen = { _, _ -> }, onShare = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Outdoor Portrait • Backlit").assertExists()
        composeRule.onNodeWithText("Indoor Portrait").assertDoesNotExist()
        composeRule.onNodeWithText("Face light balanced · Highlights protected").assertExists()
        composeRule.onNodeWithText("Group Face Light").assertHasClickAction()
        composeRule.onNodeWithText("Group Face Light").performClick()
        composeRule.onNodeWithContentDescription("Apply face light unavailable").assertIsDisplayed()
    }

    @Test fun comparisonShowsOriginalOnLeftAndEnhancedOnRight() {
        val original = image(0xffff0000.toInt())
        val enhanced = image(0xff0000ff.toInt())
        composeRule.setContent {
            RestoreXTheme {
                RestovueSmartMagicScreen(
                    state = SmartMagicUiState(
                        phase = SmartMagicPhase.READY,
                        original = original,
                        enhanced = enhanced,
                        selectedLevel = EnhancementLevel.ENHANCED,
                        completedLevel = EnhancementLevel.ENHANCED,
                        completedRenderGeneration = 1,
                        modeSelectionEnabled = true,
                    ),
                    onBack = {}, onChooseAnother = {}, onSave = {}, onCancel = {},
                    onOpen = { _, _ -> }, onShare = { _, _ -> },
                )
            }
        }
        val pixels = composeRule.onNodeWithTag("smart_magic_comparison").captureToImage().toPixelMap()
        val y = pixels.height / 2
        val left = pixels[pixels.width / 4, y]
        val right = pixels[pixels.width * 3 / 4, y]
        assert(left.red > .8f && left.blue < .2f) { "Left side must show the original image" }
        assert(right.blue > .8f && right.red < .2f) { "Right side must show the enhanced image" }

        composeRule.onNodeWithContentDescription("Before and after divider")
            .performTouchInput {
                // Drag the actual shared handle, as a user does on both editors.
                swipe(center, Offset(-500f, center.y), 500L)
            }
        composeRule.waitForIdle()
        val dragged = composeRule.onNodeWithTag("smart_magic_comparison").captureToImage().toPixelMap()
        val afterDrag = dragged[dragged.width * 3 / 5, dragged.height / 2]
        val dividerProgress = composeRule.onNodeWithContentDescription("Before and after divider")
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current
        assert(dividerProgress < .05f && afterDrag.blue > .8f && afterDrag.red < .2f) {
            "Dragging to the extreme left must move the divider to the edge and reveal the enhanced image; divider=$dividerProgress"
        }
    }

    private fun image(color: Int) = RasterImage(4, 4, IntArray(16) { color })

    private fun analysis(portrait: Float, landscape: Float, backlit: Float) = SmartMagicAnalysis(
        width = 4, height = 4, sampledPixels = 16,
        luminance = SmartMagicLuminanceStatistics(
            mean = .4f, median = .4f, p01 = .05f, p05 = .1f, p25 = .25f, p50 = .4f,
            p75 = .6f, p95 = .85f, p99 = .95f, shadowClippingFraction = 0f,
            highlightClippingFraction = 0f, effectiveRange = .8f, globalContrast = .4f,
            localContrast = .3f,
        ),
        color = SmartMagicColorStatistics(
            meanRed = .4f, meanGreen = .4f, meanBlue = .4f, colorfulness = .2f,
            meanChroma = .1f, meanSaturation = .2f, highlySaturatedFraction = .05f,
            neutralFraction = .2f, neutralRed = .4f, neutralGreen = .4f, neutralBlue = .4f,
            whiteBalanceConfidence = .8f, skinLikeFraction = .1f, centralSkinLikeFraction = .2f,
            warmLightConfidence = .2f,
        ),
        detail = SmartMagicDetailStatistics(
            edgeDensity = .2f, localVariance = .1f, fineDetail = .2f,
            estimatedNoise = .01f, highFrequencyEnergy = .1f, haloRisk = 0f,
        ),
        scenes = SmartMagicSceneScores(
            general = .2f, portrait = portrait, landscape = landscape, lowLight = .1f,
            backlit = backlit, document = 0f, alreadyBalanced = .2f, highKey = .1f, lowKey = .1f,
        ),
    )
}
