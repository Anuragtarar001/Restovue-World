package com.restorex.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.restorex.app.MainActivity
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun approvedHomeHierarchyAndUtilityActionsAreAccessible() {
        composeRule.onNodeWithContentDescription("Restovue Photo Editor logo").assertIsDisplayed()
        composeRule.onNodeWithText("Choose Photo").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Smart Magic. Professional photo enhancement in one tap")
            .assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open settings").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open Restovue menu").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Privacy first. Works offline").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open manual editor").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Start Smart Magic in enhanced mode").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Crop & Resize utility").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Retouch & Heal utility").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Background utility").assertHasClickAction()

        composeRule.onNodeWithText("Recent Projects").assertIsDisplayed()
        listOf("Collage", "Batch Edit").forEach { label ->
            composeRule.onNodeWithContentDescription("$label utility").assertHasClickAction()
        }
        composeRule.onNodeWithText("All Tools").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Undo").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Export photo").assertDoesNotExist()
    }
}
