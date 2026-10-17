package com.sethdigital.caloriepro.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.sethdigital.caloriepro.data.Goals
import com.sethdigital.caloriepro.data.Nutrient
import com.sethdigital.caloriepro.data.SettingsRepository

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    var calorieGoal by mutableStateOf(settings.goals.value.calories.toString())
        private set

    /** The limit fields as typed, by nutrient. */
    var limits by mutableStateOf(Nutrient.entries.associateWith { settings.goals.value.limit(it).toString() })
        private set

    /** Fields that failed validation: [CALORIES] or a [Nutrient] name. */
    var invalid by mutableStateOf(emptySet<String>())
        private set

    var saved by mutableStateOf(false)
        private set

    fun onCalorieGoalChange(value: String) {
        calorieGoal = value.filter(Char::isDigit).take(5)
        saved = false
    }

    fun onLimitChange(nutrient: Nutrient, value: String) {
        limits = limits + (nutrient to value.filter(Char::isDigit).take(5))
        saved = false
    }

    /** Fills every field with its recommended value; takes effect on [save]. */
    fun useRecommended() {
        calorieGoal = Goals.RECOMMENDED_CALORIES.toString()
        limits = Nutrient.entries.associateWith { it.recommended.toString() }
        invalid = emptySet()
        saved = false
    }

    fun save() {
        val calories = calorieGoal.toIntOrNull()?.takeIf { it > 0 }
        val parsed = Nutrient.entries.associateWith { limits[it]?.toIntOrNull()?.takeIf { v -> v > 0 } }
        invalid = buildSet {
            if (calories == null) add(CALORIES)
            parsed.forEach { (n, v) -> if (v == null) add(n.name) }
        }
        if (calories == null || invalid.isNotEmpty()) return
        settings.setGoals(Goals(calories = calories, limits = parsed.mapValues { it.value!! }))
        saved = true
    }

    companion object {
        const val CALORIES = "calories"
    }
}
