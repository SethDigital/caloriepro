package com.sethdigital.caloriepro

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sethdigital.caloriepro.data.Goals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddFoodFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<CalorieApp>().container.settingsRepository.setGoals(Goals())
        compose.keepScreenOn()
    }

    @Test
    fun manuallyAddedFoodShowsUpOnToday() {
        val name = "Test banana ${System.nanoTime() % 100_000}"

        compose.waitFor(hasText("Add food"))
        compose.onNodeWithText("Add food").performClick()
        compose.onNodeWithTag("field_name").performTextInput(name)
        compose.onNodeWithTag("field_calories").performTextInput("105")
        compose.onNodeWithTag("field_protein").performTextInput("1.3")
        compose.onNodeWithText("Add to log").performScrollTo().performClick()

        compose.waitFor(hasText(name))
        compose.onNodeWithText(name).assertIsDisplayed()
        compose.onNodeWithText("Scan label").assertIsDisplayed()
        // Protein is measured against the recommended 50 g a day.
        compose.onNodeWithTag("meter_protein").assert(hasText("/ 50 g", substring = true))
    }
}
