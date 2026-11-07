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
        composeRule.onNodeWithText("Edit beautifully. Stay private.").assertIsDisplayed()
        composeRule.onNodeWithText("Professional photo editing, completely on your device.").assertIsDisplayed()
        composeRule.onNodeWithText("Choose Photo").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Smart Magic. Professional photo enhancement in one tap")
            .assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open settings").assertIsDisplayed().assertHasClickAction()

        listOf(
            "Collage", "HDR", "Panorama", "Batch Edit",
            "Passport Photo", "Document Cleanup", "Compress & Convert", "More Tools",
        ).forEach { label ->
            composeRule.onNodeWithContentDescription("$label utility").assertHasClickAction()
        }

        composeRule.onNodeWithContentDescription("Undo").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Export photo").assertDoesNotExist()
    }
}
