package com.sethdigital.caloriepro

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sethdigital.caloriepro.data.Goals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val settings get() = ApplicationProvider.getApplicationContext<CalorieApp>().container.settingsRepository

    @Before
    fun setUp() {
        settings.setGoals(Goals())
        compose.keepScreenOn()
    }

    @After
    fun useRecommendedGoals() = settings.setGoals(Goals())

    @Test
    fun ownLimitsReplaceTheRecommendedOnes() {
        compose.waitFor(hasTestTag("meter_protein") and hasText("/ 50 g", substring = true))

        compose.onNodeWithContentDescription("Settings").performClick()
        setField("limit_protein", "120")
        setField("limit_sodium", "900")
        compose.onNodeWithTag("save_settings").performScrollTo().performClick()
        compose.onNodeWithTag("save_settings").assert(hasText("Saved"))
        compose.onNodeWithContentDescription("Back").performClick()

        compose.waitFor(hasTestTag("meter_protein") and hasText("/ 120 g", substring = true))
        compose.onNodeWithTag("meter_sodium").assert(hasText("/ 900 mg", substring = true))
    }

    private fun setField(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(value)
    }
}
