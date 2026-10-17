package com.sethdigital.caloriepro.data

import kotlin.math.roundToLong

/** Whether a daily amount is a ceiling to stay under or a target to reach. */
enum class LimitKind { MAXIMUM, MINIMUM }

/**
 * The nutrients tracked besides calories, in display order, each with the
 * recommended daily amount for adults: the U.S. FDA Daily Values used on
 * nutrition labels (based on a 2,000-calorie diet), and the FDA's guidance of
 * up to 400 mg of caffeine a day.
 */
enum class Nutrient(
    val label: String,
    val unit: String,
    val recommended: Int,
    val kind: LimitKind,
    /** Context for the recommendation, if it needs any. */
    val note: String? = null,
) {
    PROTEIN("Protein", "g", 50, LimitKind.MINIMUM),
    CARBS("Carbs", "g", 275, LimitKind.MAXIMUM),
    FAT("Fat", "g", 78, LimitKind.MAXIMUM),
    SATURATED_FAT("Saturated fat", "g", 20, LimitKind.MAXIMUM),
    SUGAR("Sugar", "g", 50, LimitKind.MAXIMUM, note = "the guideline is for added sugar"),
    FIBER("Fiber", "g", 28, LimitKind.MINIMUM),
    SODIUM("Sodium", "mg", 2300, LimitKind.MAXIMUM),
    CAFFEINE("Caffeine", "mg", 400, LimitKind.MAXIMUM, note = "for healthy adults");

    fun amountIn(totals: NutrientTotals): Double = when (this) {
        PROTEIN -> totals.proteinG
        CARBS -> totals.carbsG
        FAT -> totals.fatG
        SATURATED_FAT -> totals.saturatedFatG
        SUGAR -> totals.sugarG
        FIBER -> totals.fiberG
        SODIUM -> totals.sodiumMg
        CAFFEINE -> totals.caffeineMg
    }

    /** [amount] rounded the way it is shown: whole milligrams, tenths of a gram. */
    fun rounded(amount: Double): Double =
        if (unit == "mg") amount.roundToLong().toDouble() else (amount * 10).roundToLong() / 10.0

    /** Above a maximum, as shown on screen. A minimum is never "over". */
    fun isOver(amount: Double, limit: Int): Boolean = kind == LimitKind.MAXIMUM && rounded(amount) > limit

    /** A minimum that has been reached. */
    fun isMet(amount: Double, limit: Int): Boolean = kind == LimitKind.MINIMUM && rounded(amount) >= limit
}

data class NutrientTotals(
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val saturatedFatG: Double = 0.0,
    val sugarG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val caffeineMg: Double = 0.0,
) {
    operator fun plus(o: NutrientTotals) = NutrientTotals(
        calories + o.calories,
        proteinG + o.proteinG,
        carbsG + o.carbsG,
        fatG + o.fatG,
        saturatedFatG + o.saturatedFatG,
        sugarG + o.sugarG,
        fiberG + o.fiberG,
        sodiumMg + o.sodiumMg,
        caffeineMg + o.caffeineMg,
    )
}

/**
 * The daily calorie goal and nutrient limits. [limits] holds only the values
 * the user has changed; every other nutrient uses its recommended amount.
 */
data class Goals(
    val calories: Int = RECOMMENDED_CALORIES,
    val limits: Map<Nutrient, Int> = emptyMap(),
) {
    fun limit(nutrient: Nutrient): Int = limits[nutrient] ?: nutrient.recommended

    /** Nutrients whose daily maximum [totals] goes over, in display order. */
    fun overLimits(totals: NutrientTotals): List<Nutrient> =
        Nutrient.entries.filter { it.isOver(it.amountIn(totals), limit(it)) }

    companion object {
        /** The general reference intake used on nutrition labels. */
        const val RECOMMENDED_CALORIES = 2000
    }
}
