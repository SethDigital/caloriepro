package com.sethdigital.caloriepro.ui.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethdigital.caloriepro.data.FoodEntry
import com.sethdigital.caloriepro.data.FoodRepository
import com.sethdigital.caloriepro.data.Goals
import com.sethdigital.caloriepro.data.Meal
import com.sethdigital.caloriepro.data.NutrientTotals
import com.sethdigital.caloriepro.data.SettingsRepository
import com.sethdigital.caloriepro.data.sumTotals
import com.sethdigital.caloriepro.ui.friendlyName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayUiState(
    val date: LocalDate,
    val entries: List<FoodEntry> = emptyList(),
    /** Foods logged the day before [date], offered for copying onto an empty day. */
    val previousDayCount: Int = 0,
    val totals: NutrientTotals = NutrientTotals(),
    val goals: Goals = Goals(),
    val loaded: Boolean = false,
)

/** A snackbar message; [goTo] offers to show that day. */
data class DayMessage(val text: String, val goTo: LocalDate? = null)

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
            combine(foods.observeDay(date), foods.observeDay(date.minusDays(1))) { entries, previous ->
                Triple(date, entries, previous.size)
            }
        }
        .combine(settings.goals) { (date, entries, previousCount), goals ->
            DayUiState(
                date = date,
                entries = entries,
                previousDayCount = previousCount,
                totals = entries.sumTotals(),
                goals = goals,
                loaded = true,
            )
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

    private val _messages = Channel<DayMessage>(Channel.BUFFERED)

    /** One-off confirmations for the snackbar. */
    val messages: Flow<DayMessage> = _messages.receiveAsFlow()

    /** Logs the selected day's [meal] again on [to], as the same meal. */
    fun copyMeal(meal: Meal, to: LocalDate) {
        val entries = uiState.value.entries.filter { it.meal == meal }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val count = foods.copy(entries, to)
            _messages.send(DayMessage("Copied ${count.foods()} to ${to.friendlyName()}", goTo = to))
        }
    }

    /** Logs everything from the day before the selected day on the selected day. */
    fun copyPreviousDay() {
        val date = uiState.value.date
        viewModelScope.launch {
            val count = foods.copy(foods.entriesOn(date.minusDays(1)), date)
            _messages.send(DayMessage("Copied ${count.foods()} from ${date.minusDays(1).friendlyName()}"))
        }
    }

    /** Saves the selected day's [meal] as a named meal for logging in one tap. */
    fun saveMeal(meal: Meal, name: String) {
        val entries = uiState.value.entries.filter { it.meal == meal }
        if (entries.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            foods.saveMeal(name, entries)
            _messages.send(DayMessage("Saved \"${name.trim()}\". Log it from Add food."))
        }
    }

    private fun Int.foods() = if (this == 1) "1 food" else "$this foods"

    private companion object {
        const val KEY_DAY = "selected_day"
    }
}
