package com.sethdigital.caloriepro.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethdigital.caloriepro.data.DailySummary
import com.sethdigital.caloriepro.data.FoodRepository
import com.sethdigital.caloriepro.data.Goals
import com.sethdigital.caloriepro.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DayBar(val date: LocalDate, val calories: Double)

data class HistoryUiState(
    val days: List<DailySummary> = emptyList(),
    /** The last 7 days ending today, including days with nothing logged. */
    val week: List<DayBar> = emptyList(),
    /** Average over the days in [week] that have something logged. */
    val weekAverage: Double? = null,
    val goals: Goals = Goals(),
    val loaded: Boolean = false,
)

class HistoryViewModel(foods: FoodRepository, settings: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = foods.observeDailySummaries()
        .combine(settings.goals) { days, goals ->
            val today = LocalDate.now()
            val byDay = days.associateBy { it.epochDay }
            val week = (6 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                DayBar(date, byDay[date.toEpochDay()]?.calories ?: 0.0)
            }
            val logged = week.filter { byDay.containsKey(it.date.toEpochDay()) }
            HistoryUiState(
                days = days,
                week = week,
                weekAverage = logged.takeIf { it.isNotEmpty() }?.map { it.calories }?.average(),
                goals = goals,
                loaded = true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
