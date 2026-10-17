package com.sethdigital.caloriepro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _goals = MutableStateFlow(read())
    val goals: StateFlow<Goals> = _goals.asStateFlow()

    /**
     * Saves [goals]. Values equal to the recommendation are not stored, so
     * they keep following the recommendation instead of becoming custom.
     */
    fun setGoals(goals: Goals) {
        val editor = prefs.edit()
        if (goals.calories == Goals.RECOMMENDED_CALORIES) {
            editor.remove(KEY_CALORIES)
        } else {
            editor.putInt(KEY_CALORIES, goals.calories)
        }
        for (n in Nutrient.entries) {
            val limit = goals.limit(n)
            if (limit == n.recommended) editor.remove(keyFor(n)) else editor.putInt(keyFor(n), limit)
        }
        editor.apply()
        _goals.value = read()
    }

    private fun read() = Goals(
        calories = prefs.getInt(KEY_CALORIES, Goals.RECOMMENDED_CALORIES),
        limits = Nutrient.entries
            .filter { prefs.contains(keyFor(it)) }
            .associateWith { prefs.getInt(keyFor(it), it.recommended) },
    )

    private companion object {
        const val KEY_CALORIES = "calorie_goal"

        // Caffeine keeps the key it had before the other limits existed, so an
        // existing custom caffeine limit carries over.
        fun keyFor(n: Nutrient) = if (n == Nutrient.CAFFEINE) "caffeine_limit_mg" else "limit_${n.name.lowercase()}"
    }
}
