package com.sethdigital.caloriepro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddFoodFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun manuallyAddedFoodShowsUpOnToday() {
        val name = "Test banana ${System.nanoTime() % 100_000}"

        compose.onNodeWithText("Add food").performClick()
        compose.onNodeWithTag("field_name").performTextInput(name)
        compose.onNodeWithTag("field_calories").performTextInput("105")
        compose.onNodeWithTag("field_protein").performTextInput("1.3")
        compose.onNodeWithText("Add to log").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(name).assertIsDisplayed()
        compose.onNodeWithText("Scan label").assertIsDisplayed()
    }
}
