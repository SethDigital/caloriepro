package com.sethdigital.caloriepro.scan.parser

/**
 * Values read from a nutrition label, per serving. A null field means the
 * value was not found on the label (not that it is zero).
 */
data class NutritionFacts(
    val calories: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val saturatedFatG: Double? = null,
    val sugarG: Double? = null,
    val fiberG: Double? = null,
    val sodiumMg: Double? = null,
    val caffeineMg: Double? = null,
    val servingSize: String? = null,
    val servingsPerContainer: Double? = null,
) {
    /** How many nutrient values were found (serving info not counted). */
    val nutrientCount: Int
        get() = listOf(
            calories, proteinG, carbsG, fatG, saturatedFatG,
            sugarG, fiberG, sodiumMg, caffeineMg,
        ).count { it != null }
}
