package com.sethdigital.caloriepro.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

enum class Meal(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        /** A sensible default meal for food logged at [time]. */
        fun forTime(time: LocalTime): Meal = when (time.hour) {
            in 4..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACK
        }
    }
}

/**
 * One food logged on one day. Nutrient values are per serving; the amount
 * eaten is [servings] × each value. Null nutrients were not entered.
 */
@Entity(tableName = "food_entries", indices = [Index("epochDay")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The day this was eaten, as [LocalDate.toEpochDay]. */
    val epochDay: Long,
    val name: String,
    val meal: Meal,
    val servings: Double,
    val servingSize: String?,
    val calories: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val fiberG: Double?,
    val sodiumMg: Double?,
    val caffeineMg: Double?,
    val createdAt: Long,
)

// Derived values live outside the entity so Room only sees stored columns.

val FoodEntry.date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

/** What was actually eaten: per-serving values times servings. */
val FoodEntry.totals: NutrientTotals
    get() = NutrientTotals(
        calories = calories * servings,
        proteinG = (proteinG ?: 0.0) * servings,
        carbsG = (carbsG ?: 0.0) * servings,
        fatG = (fatG ?: 0.0) * servings,
        saturatedFatG = (saturatedFatG ?: 0.0) * servings,
        sugarG = (sugarG ?: 0.0) * servings,
        fiberG = (fiberG ?: 0.0) * servings,
        sodiumMg = (sodiumMg ?: 0.0) * servings,
        caffeineMg = (caffeineMg ?: 0.0) * servings,
    )

fun List<FoodEntry>.sumTotals(): NutrientTotals = fold(NutrientTotals()) { acc, e -> acc + e.totals }

/** Totals for one day, computed by the database for the history screen. */
data class DailySummary(
    val epochDay: Long,
    val entryCount: Int,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val sugarG: Double,
    val caffeineMg: Double,
)

val DailySummary.date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
