package com.sethdigital.caloriepro.ui.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethdigital.caloriepro.data.FoodEntry
import com.sethdigital.caloriepro.data.FoodRepository
import com.sethdigital.caloriepro.data.Goals
import com.sethdigital.caloriepro.data.NutrientTotals
import com.sethdigital.caloriepro.data.SettingsRepository
import com.sethdigital.caloriepro.data.sumTotals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayUiState(
    val date: LocalDate,
    val entries: List<FoodEntry> = emptyList(),
    val totals: NutrientTotals = NutrientTotals(),
    val goals: Goals = Goals(),
    val loaded: Boolean = false,
)

class DayViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val selectedDay = savedStateHandle.getStateFlow(KEY_DAY, LocalDate.now().toEpochDay())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DayUiState> = selectedDay
        .flatMapLatest { day ->
            val date = LocalDate.ofEpochDay(day)
            foods.observeDay(date).map { date to it }
        }
        .combine(settings.goals) { (date, entries), goals ->
            DayUiState(date = date, entries = entries, totals = entries.sumTotals(), goals = goals, loaded = true)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DayUiState(date = LocalDate.ofEpochDay(selectedDay.value), goals = settings.goals.value),
        )

    fun selectDate(date: LocalDate) {
        savedStateHandle[KEY_DAY] = date.toEpochDay()
    }

    fun delete(entry: FoodEntry) {
        viewModelScope.launch { foods.delete(entry) }
    }

    /** Puts back an entry removed with [delete] (for undo). */
    fun restore(entry: FoodEntry) {
        viewModelScope.launch { foods.save(entry) }
    }

    private companion object {
        const val KEY_DAY = "selected_day"
    }
}
