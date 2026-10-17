package com.sethdigital.caloriepro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sethdigital.caloriepro.data.Goals
import com.sethdigital.caloriepro.data.Nutrient
import com.sethdigital.caloriepro.data.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Before
    @After
    fun clearSettings() {
        prefs.edit().clear().commit()
        (context as CalorieApp).container.settingsRepository.setGoals(Goals())
    }

    @Test
    fun startsWithRecommendedValues() {
        val goals = SettingsRepository(context).goals.value
        assertEquals(Goals(), goals)
        assertEquals(50, goals.limit(Nutrient.PROTEIN))
        assertEquals(2300, goals.limit(Nutrient.SODIUM))
    }

    @Test
    fun customValuesSurviveARestart() {
        SettingsRepository(context).setGoals(
            Goals(calories = 1800, limits = mapOf(Nutrient.PROTEIN to 120, Nutrient.CAFFEINE to 200))
        )

        val reloaded = SettingsRepository(context).goals.value
        assertEquals(1800, reloaded.calories)
        assertEquals(120, reloaded.limit(Nutrient.PROTEIN))
        assertEquals(200, reloaded.limit(Nutrient.CAFFEINE))
        assertEquals(2300, reloaded.limit(Nutrient.SODIUM))
    }

    @Test
    fun recommendedValuesAreNotSavedAsCustom() {
        val repo = SettingsRepository(context)
        repo.setGoals(Goals(limits = mapOf(Nutrient.PROTEIN to 120)))
        repo.setGoals(Goals(limits = Nutrient.entries.associateWith { it.recommended }))

        assertEquals(emptyMap<Nutrient, Int>(), repo.goals.value.limits)
    }

    @Test
    fun keepsGoalsSavedByTheFirstVersion() {
        // The first release stored only these two keys.
        prefs.edit().putInt("calorie_goal", 2200).putInt("caffeine_limit_mg", 300).commit()

        val goals = SettingsRepository(context).goals.value
        assertEquals(2200, goals.calories)
        assertEquals(300, goals.limit(Nutrient.CAFFEINE))
        assertEquals(50, goals.limit(Nutrient.PROTEIN))
    }
}
