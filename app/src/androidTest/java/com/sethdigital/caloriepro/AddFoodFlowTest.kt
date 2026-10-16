package com.sethdigital.caloriepro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
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

        waitForText("Add food")
        compose.onNodeWithText("Add food").performClick()
        compose.onNodeWithTag("field_name").performTextInput(name)
        compose.onNodeWithTag("field_calories").performTextInput("105")
        compose.onNodeWithTag("field_protein").performTextInput("1.3")
        compose.onNodeWithText("Add to log").performScrollTo().performClick()

        waitForText(name)
        compose.onNodeWithText(name).assertIsDisplayed()
        compose.onNodeWithText("Scan label").assertIsDisplayed()
    }

    /** Waits for [text] to be on screen, failing with a dump of what is shown instead. */
    private fun waitForText(text: String, timeoutMillis: Long = 15_000) {
        try {
            compose.waitUntil(timeoutMillis) {
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Exception) {
            val screen = compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString()
            throw AssertionError("\"$text\" never appeared. On screen:\n$screen", e)
        }
    }
}
