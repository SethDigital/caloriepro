package com.sethdigital.caloriepro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {

    @Test
    fun `recommended amounts are the FDA daily values`() {
        val expected = mapOf(
            Nutrient.PROTEIN to 50,
            Nutrient.CARBS to 275,
            Nutrient.FAT to 78,
            Nutrient.SATURATED_FAT to 20,
            Nutrient.SUGAR to 50,
            Nutrient.FIBER to 28,
            Nutrient.SODIUM to 2300,
            Nutrient.CAFFEINE to 400,
        )
        assertEquals(expected, Nutrient.entries.associateWith { it.recommended })
        assertEquals(2000, Goals().calories)
    }

    @Test
    fun `protein and fiber are targets to reach, the rest are limits`() {
        val minimums = Nutrient.entries.filter { it.kind == LimitKind.MINIMUM }
        assertEquals(listOf(Nutrient.PROTEIN, Nutrient.FIBER), minimums)
    }

    @Test
    fun `custom limits override the recommendation`() {
        val goals = Goals(calories = 1800, limits = mapOf(Nutrient.SODIUM to 1500))
        assertEquals(1500, goals.limit(Nutrient.SODIUM))
        assertEquals(400, goals.limit(Nutrient.CAFFEINE))
    }

    @Test
    fun `a maximum is over only once the shown amount passes it`() {
        assertFalse(Nutrient.SODIUM.isOver(2300.0, 2300))
        assertFalse(Nutrient.SODIUM.isOver(2300.4, 2300)) // shown as 2,300
        assertTrue(Nutrient.SODIUM.isOver(2300.6, 2300)) // shown as 2,301
        assertFalse(Nutrient.SUGAR.isOver(50.04, 50)) // shown as 50 g
        assertTrue(Nutrient.SUGAR.isOver(50.06, 50)) // shown as 50.1 g
    }

    @Test
    fun `a minimum is never over and is met once reached`() {
        assertFalse(Nutrient.PROTEIN.isOver(500.0, 50))
        assertFalse(Nutrient.PROTEIN.isMet(49.9, 50))
        assertTrue(Nutrient.PROTEIN.isMet(50.0, 50))
        assertFalse(Nutrient.SODIUM.isMet(5000.0, 2300))
    }

    @Test
    fun `over limits lists the nutrients past their maximum in display order`() {
        val totals = NutrientTotals(
            proteinG = 200.0, // a minimum, never "over"
            fatG = 90.0,
            sugarG = 20.0,
            sodiumMg = 3000.0,
            caffeineMg = 450.0,
        )
        val goals = Goals(limits = mapOf(Nutrient.CAFFEINE to 500))
        assertEquals(listOf(Nutrient.FAT, Nutrient.SODIUM), goals.overLimits(totals))
    }
}
