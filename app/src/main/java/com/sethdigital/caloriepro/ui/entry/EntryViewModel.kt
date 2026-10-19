package com.sethdigital.caloriepro.ui.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethdigital.caloriepro.data.Barcodes
import com.sethdigital.caloriepro.data.FoodEntry
import com.sethdigital.caloriepro.data.FoodRepository
import com.sethdigital.caloriepro.data.Meal
import com.sethdigital.caloriepro.data.Nutrient
import com.sethdigital.caloriepro.data.NutrientTotals
import com.sethdigital.caloriepro.data.SavedMealWithItems
import com.sethdigital.caloriepro.data.ServingWeight
import com.sethdigital.caloriepro.data.date
import com.sethdigital.caloriepro.data.servingWeight
import com.sethdigital.caloriepro.scan.ScanResult
import com.sethdigital.caloriepro.ui.parseAmount
import com.sethdigital.caloriepro.ui.toInputText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** The form exactly as typed, so partial input like "1." survives. */
data class EntryForm(
    val name: String = "",
    val meal: Meal = Meal.SNACK,
    /** How much was eaten: a number of servings, or a weight when [byWeight]. */
    val amount: String = "1",
    val byWeight: Boolean = false,
    val servingSize: String = "",
    val calories: String = "",
    val nutrients: Map<Nutrient, String> = emptyMap(),
    /** The product barcode this food will be remembered under. */
    val barcode: String? = null,
) {
    fun nutrient(n: Nutrient): String = nutrients[n].orEmpty()

    /** The weight of one serving, when the serving size includes one ("2/3 cup (55g)"). */
    val servingWeight: ServingWeight? get() = ServingWeight.parse(servingSize)

    /** Servings eaten, or null while the amount is incomplete. */
    fun servings(): Double? {
        val value = amount.parseAmount()?.takeIf { it > 0 } ?: return null
        return if (byWeight) servingWeight?.servingsIn(value) else value
    }

    /** The same amount expressed by weight ([on]) or in servings. */
    fun withByWeight(on: Boolean): EntryForm {
        if (on == byWeight) return this
        val weight = servingWeight ?: return copy(byWeight = false)
        val converted = servings()?.let { if (on) weight.weightOf(it) else it }?.toInputText() ?: amount
        return copy(byWeight = on, amount = converted)
    }

    /** What saving this form would log, or null while the numbers are incomplete. */
    fun previewTotals(): NutrientTotals? {
        val servings = servings() ?: return null
        val calories = calories.parseAmount() ?: return null
        fun total(n: Nutrient) = (nutrient(n).parseAmount() ?: 0.0) * servings
        return NutrientTotals(
            calories = calories * servings,
            proteinG = total(Nutrient.PROTEIN),
            carbsG = total(Nutrient.CARBS),
            fatG = total(Nutrient.FAT),
            saturatedFatG = total(Nutrient.SATURATED_FAT),
            sugarG = total(Nutrient.SUGAR),
            fiberG = total(Nutrient.FIBER),
            sodiumMg = total(Nutrient.SODIUM),
            caffeineMg = total(Nutrient.CAFFEINE),
        )
    }

    companion object {
        fun from(entry: FoodEntry): EntryForm {
            val weight = entry.servingWeight
            val byWeight = entry.byWeight && weight != null
            val amount = if (byWeight && weight != null) weight.weightOf(entry.servings) else entry.servings
            return EntryForm(
                name = entry.name,
                meal = entry.meal,
                amount = amount.toInputText(),
                byWeight = byWeight,
                servingSize = entry.servingSize.orEmpty(),
                calories = entry.calories.toInputText(),
                nutrients = nutrientText(
                    Nutrient.PROTEIN to entry.proteinG,
                    Nutrient.CARBS to entry.carbsG,
                    Nutrient.FAT to entry.fatG,
                    Nutrient.SATURATED_FAT to entry.saturatedFatG,
                    Nutrient.SUGAR to entry.sugarG,
                    Nutrient.FIBER to entry.fiberG,
                    Nutrient.SODIUM to entry.sodiumMg,
                    Nutrient.CAFFEINE to entry.caffeineMg,
                ),
                barcode = entry.barcode,
            )
        }
    }
}

/** Text-field values for the nutrients that have one. */
private fun nutrientText(vararg values: Pair<Nutrient, Double?>): Map<Nutrient, String> =
    values.mapNotNull { (n, v) -> v?.let { n to it.toInputText() } }.toMap()

/** Shown after a scan fills in the form. */
sealed interface ScanNotice {
    /** Values read from a photo of the label; [barcodeSaved] if a barcode will remember them. */
    data class FromLabel(
        val filledCount: Int,
        val caloriesFound: Boolean,
        val rows: List<String>,
        val barcodeSaved: Boolean,
    ) : ScanNotice

    /** A barcode that was saved before, filled in from the food last logged with it. */
    data class FromBarcode(val foodName: String) : ScanNotice
}

class EntryViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
) : ViewModel() {

    private val entryId: Long = savedStateHandle[ARG_ENTRY_ID] ?: -1L
    val isEditing: Boolean = entryId > 0
    private var existing: FoodEntry? = null

    var date: LocalDate by mutableStateOf(
        savedStateHandle.get<Long>(ARG_DAY)?.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
    )
        private set

    var form by mutableStateOf(EntryForm(meal = Meal.forTime(LocalTime.now())))
        private set

    var loading by mutableStateOf(isEditing)
        private set

    /** Keys of fields that failed validation: "calories", "servings" or a [Nutrient] name. */
    var invalidFields by mutableStateOf(emptySet<String>())
        private set

    var showScanner by mutableStateOf(false)
        private set

    var scanNotice by mutableStateOf<ScanNotice?>(null)
        private set

    /** Shown in the camera view, e.g. when a scanned barcode is new. */
    var barcodeNote by mutableStateOf<String?>(null)
        private set

    var servingsPerContainer by mutableStateOf<Double?>(null)
        private set

    /** Set once the entry was saved or deleted and the screen should close. */
    var finished by mutableStateOf(false)
        private set

    /** Past foods matching the typed name, for one-tap re-logging. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<FoodEntry>> = snapshotFlow { form.name }
        .debounce(150)
        .flatMapLatest { foods.observeRecentFoods(it, limit = 6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedMeals: StateFlow<List<SavedMealWithItems>> = foods.observeSavedMeals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (isEditing) {
            viewModelScope.launch {
                existing = foods.get(entryId)
                existing?.let {
                    form = EntryForm.from(it)
                    date = it.date
                }
                loading = false
            }
        }
        // Open the camera straight away when coming from "Scan", but only
        // once, not again after rotation or process recreation.
        if (savedStateHandle.get<Boolean>(ARG_SCAN) == true && savedStateHandle.get<Boolean>(KEY_SCAN_SHOWN) != true) {
            savedStateHandle[KEY_SCAN_SHOWN] = true
            showScanner = true
        }
    }

    fun update(transform: EntryForm.() -> EntryForm) {
        val before = form
        var after = before.transform()
        // When the serving size stops saying what a serving weighs, the amount
        // can no longer be a weight: switch back to servings.
        if (after.byWeight && after.servingWeight == null) {
            after = after.copy(byWeight = false, amount = before.servings()?.toInputText() ?: after.amount)
        }
        form = after
    }

    fun updateNutrient(n: Nutrient, value: String) {
        form = form.copy(nutrients = form.nutrients + (n to value))
    }

    fun setByWeight(on: Boolean) {
        form = form.withByWeight(on)
    }

    fun removeBarcode() {
        form = form.copy(barcode = null)
    }

    fun openScanner() {
        barcodeNote = null
        showScanner = true
    }

    fun closeScanner() {
        showScanner = false
    }

    /**
     * A barcode was seen by the camera. A known barcode fills in the food
     * logged with it last time; a new one is kept so the food saved now is
     * remembered under it.
     */
    fun onBarcode(code: String) {
        val barcode = Barcodes.normalize(code) ?: return
        viewModelScope.launch {
            val known = foods.findByBarcode(barcode)
            if (known != null) {
                form = EntryForm.from(known).copy(meal = form.meal, barcode = barcode)
                servingsPerContainer = null
                scanNotice = ScanNotice.FromBarcode(known.name)
                invalidFields = emptySet()
                barcodeNote = null
                showScanner = false
            } else {
                form = form.copy(barcode = barcode)
                barcodeNote = "New barcode. Take a photo of the nutrition label, and next time scanning " +
                    "this barcode will fill everything in."
            }
        }
    }

    fun applyScan(result: ScanResult) {
        val facts = result.facts
        val scanned = nutrientText(
            Nutrient.PROTEIN to facts.proteinG,
            Nutrient.CARBS to facts.carbsG,
            Nutrient.FAT to facts.fatG,
            Nutrient.SATURATED_FAT to facts.saturatedFatG,
            Nutrient.SUGAR to facts.sugarG,
            Nutrient.FIBER to facts.fiberG,
            Nutrient.SODIUM to facts.sodiumMg,
            Nutrient.CAFFEINE to facts.caffeineMg,
        )
        val updated = form.copy(
            calories = facts.calories?.toInputText() ?: form.calories,
            servingSize = facts.servingSize ?: form.servingSize,
            nutrients = form.nutrients + scanned,
            barcode = result.barcode ?: form.barcode,
        )
        form = if (updated.byWeight && updated.servingWeight == null) updated.copy(byWeight = false) else updated
        servingsPerContainer = facts.servingsPerContainer
        scanNotice = ScanNotice.FromLabel(
            filledCount = facts.nutrientCount,
            caloriesFound = facts.calories != null,
            rows = result.rows,
            barcodeSaved = form.barcode != null,
        )
        invalidFields = emptySet()
        barcodeNote = null
        showScanner = false
    }

    fun dismissScanNotice() {
        scanNotice = null
    }

    /** Copies a previously logged food into the form, keeping the chosen meal. */
    fun fillFrom(entry: FoodEntry) {
        form = EntryForm.from(entry).copy(meal = form.meal)
        invalidFields = emptySet()
    }

    /** Logs every food in [saved] on this day, under the chosen meal, and closes. */
    fun logSavedMeal(saved: SavedMealWithItems) {
        viewModelScope.launch {
            foods.logSavedMeal(saved, date, form.meal)
            finished = true
        }
    }

    fun deleteSavedMeal(saved: SavedMealWithItems) {
        viewModelScope.launch { foods.deleteSavedMeal(saved.meal) }
    }

    fun save() {
        val current = form
        val calories = current.calories.parseAmount()
        val servings = current.servings()
        val invalid = buildSet {
            if (calories == null || calories < 0) add("calories")
            if (servings == null) add("servings")
            for (n in Nutrient.entries) {
                val text = current.nutrient(n)
                if (text.isNotBlank() && (text.parseAmount() ?: -1.0) < 0) add(n.name)
            }
        }
        invalidFields = invalid
        if (invalid.isNotEmpty() || calories == null || servings == null) return

        fun value(n: Nutrient) = current.nutrient(n).parseAmount()
        val entry = FoodEntry(
            id = existing?.id ?: 0,
            epochDay = date.toEpochDay(),
            name = current.name.trim().ifEmpty { "Quick add" },
            meal = current.meal,
            servings = servings,
            servingSize = current.servingSize.trim().ifEmpty { null },
            calories = calories,
            proteinG = value(Nutrient.PROTEIN),
            carbsG = value(Nutrient.CARBS),
            fatG = value(Nutrient.FAT),
            saturatedFatG = value(Nutrient.SATURATED_FAT),
            sugarG = value(Nutrient.SUGAR),
            fiberG = value(Nutrient.FIBER),
            sodiumMg = value(Nutrient.SODIUM),
            caffeineMg = value(Nutrient.CAFFEINE),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            barcode = current.barcode,
            byWeight = current.byWeight,
        )
        viewModelScope.launch {
            foods.save(entry)
            finished = true
        }
    }

    fun delete() {
        val entry = existing ?: return
        viewModelScope.launch {
            foods.delete(entry)
            finished = true
        }
    }

    companion object {
        const val ARG_ENTRY_ID = "entryId"
        const val ARG_DAY = "day"
        const val ARG_SCAN = "scan"
        private const val KEY_SCAN_SHOWN = "scan_shown"
    }
}
