package com.sethdigital.caloriepro.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.sethdigital.caloriepro.data.Goals
import com.sethdigital.caloriepro.data.SettingsRepository

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    var calorieGoal by mutableStateOf(settings.goals.value.calories.toString())
        private set
    var caffeineLimit by mutableStateOf(settings.goals.value.caffeineMg.toString())
        private set
    var invalid by mutableStateOf(emptySet<String>())
        private set
    var saved by mutableStateOf(false)
        private set

    fun onCalorieGoalChange(value: String) {
        calorieGoal = value.filter(Char::isDigit).take(5)
        saved = false
    }

    fun onCaffeineLimitChange(value: String) {
        caffeineLimit = value.filter(Char::isDigit).take(4)
        saved = false
    }

    fun save() {
        val calories = calorieGoal.toIntOrNull()?.takeIf { it > 0 }
        val caffeine = caffeineLimit.toIntOrNull()?.takeIf { it > 0 }
        invalid = buildSet {
            if (calories == null) add("calories")
            if (caffeine == null) add("caffeine")
        }
        if (calories == null || caffeine == null) return
        settings.setGoals(Goals(calories = calories, caffeineMg = caffeine))
        saved = true
    }
}
