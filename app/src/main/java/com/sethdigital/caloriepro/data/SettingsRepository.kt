package com.sethdigital.caloriepro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Goals(
    val calories: Int = DEFAULT_CALORIES,
    /** 400 mg is the commonly cited safe daily limit for healthy adults. */
    val caffeineMg: Int = DEFAULT_CAFFEINE_MG,
) {
    companion object {
        const val DEFAULT_CALORIES = 2000
        const val DEFAULT_CAFFEINE_MG = 400
    }
}

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _goals = MutableStateFlow(
        Goals(
            calories = prefs.getInt(KEY_CALORIES, Goals.DEFAULT_CALORIES),
            caffeineMg = prefs.getInt(KEY_CAFFEINE, Goals.DEFAULT_CAFFEINE_MG),
        )
    )
    val goals: StateFlow<Goals> = _goals.asStateFlow()

    fun setGoals(goals: Goals) {
        prefs.edit()
            .putInt(KEY_CALORIES, goals.calories)
            .putInt(KEY_CAFFEINE, goals.caffeineMg)
            .apply()
        _goals.value = goals
    }

    private companion object {
        const val KEY_CALORIES = "calorie_goal"
        const val KEY_CAFFEINE = "caffeine_limit_mg"
    }
}
