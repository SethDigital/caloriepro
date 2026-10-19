package com.sethdigital.caloriepro

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sethdigital.caloriepro.data.Goals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AddFoodFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<CalorieApp>().container

    private val suffix = System.nanoTime() % 100_000

    @Before
    fun setUp() {
        container.settingsRepository.setGoals(Goals())
        compose.keepScreenOn()
    }

    private fun openAddFood() {
        compose.waitFor(hasText("Add food"))
        compose.onNodeWithText("Add food").performClick()
        compose.waitFor(hasTestTag("field_name"))
    }

    @Test
    fun manuallyAddedFoodShowsUpOnToday() {
        val name = "Test banana $suffix"

        openAddFood()
        compose.onNodeWithTag("field_name").performTextInput(name)
        compose.onNodeWithTag("field_calories").performTextInput("105")
        compose.onNodeWithTag("field_protein").performTextInput("1.3")
        compose.onNodeWithText("Add to log").performScrollTo().performClick()

        // Protein is measured against the recommended 50 g a day.
        compose.waitFor(hasTestTag("meter_protein"))
        compose.onNodeWithTag("meter_protein").assert(hasText("/ 50 g", substring = true))
        compose.onNodeWithText("Scan").assertIsDisplayed()
        compose.scrollDayTo(hasText(name))
        compose.onNodeWithText(name).assertIsDisplayed()
    }

    @Test
    fun foodCanBeLoggedByWeight() {
        val name = "Test rice $suffix"

        openAddFood()
        compose.onNodeWithTag("field_name").performTextInput(name)
        compose.onNodeWithTag("field_serving_size").performTextInput("1 cup (180 g)")
        compose.onNodeWithTag("field_calories").performTextInput("200")
        compose.onNodeWithTag("unit_weight").performScrollTo().performClick()
        // One serving, now shown as its weight.
        compose.onNodeWithTag("field_servings").assert(hasText("180"))
        compose.onNodeWithTag("field_servings").performTextClearance()
        compose.onNodeWithTag("field_servings").performTextInput("90")
        compose.onNodeWithTag("amount_hint").assert(hasText("0.5 servings of 180 g"))
        compose.onNodeWithText("Add to log").performScrollTo().performClick()

        val row = hasText(name) and hasText("90 g") and hasText("100 kcal")
        compose.scrollDayTo(row)
        compose.onNode(row).assertIsDisplayed()
    }

    @Test
    fun aMealCanBeSavedAndLoggedAgainInOneTap() {
        val food = "Test oats $suffix"
        val mealName = "Test dinner $suffix"

        openAddFood()
        compose.onNodeWithTag("field_name").performTextInput(food)
        compose.onNodeWithText("Dinner").performClick()
        compose.onNodeWithTag("field_calories").performTextInput("150")
        compose.onNodeWithText("Add to log").performScrollTo().performClick()

        compose.scrollDayTo(hasTestTag("meal_menu_dinner"))
        // The floating buttons may cover the end of the list, so click through semantics.
        compose.onNodeWithTag("meal_menu_dinner").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitFor(hasText("Save as a meal…"))
        compose.onNodeWithText("Save as a meal…").performClick()
        compose.waitFor(hasTestTag("saved_meal_name"))
        compose.onNodeWithTag("saved_meal_name").performTextInput(mealName)
        compose.onNodeWithText("Save").performClick()

        openAddFood()
        compose.waitFor(hasTestTag("saved_meal_$mealName"))
        compose.onNodeWithTag("saved_meal_$mealName").performClick()

        compose.waitUntil(15_000) {
            runBlocking { container.foodRepository.entriesOn(LocalDate.now()) }.count { it.name == food } == 2
        }
    }
}
