package com.sethdigital.caloriepro.scan.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionLabelParserTest {

    private fun parse(vararg rows: String) = NutritionLabelParser.parseRows(rows.toList())

    @Test
    fun `reads a standard US nutrition facts panel`() {
        val facts = parse(
            "Nutrition Facts",
            "8 servings per container",
            "Serving size 2/3 cup (55g)",
            "Amount per serving",
            "Calories 230",
            "% Daily Value*",
            "Total Fat 8g 10%",
            "Saturated Fat 1g 5%",
            "Trans Fat 0g",
            "Cholesterol 0mg 0%",
            "Sodium 160mg 7%",
            "Total Carbohydrate 37g 13%",
            "Dietary Fiber 4g 14%",
            "Total Sugars 12g",
            "Includes 10g Added Sugars 20%",
            "Protein 3g",
            "Vitamin D 2mcg 10%",
            "Calcium 260mg 20%",
            "Iron 8mg 45%",
            "Potassium 240mg 6%",
            "* The % Daily Value (DV) tells you how much a nutrient in a serving of food contributes",
            "to a daily diet. 2,000 calories a day is used for general nutrition advice.",
        )
        assertEquals(230.0, facts.calories)
        assertEquals(8.0, facts.fatG)
        assertEquals(1.0, facts.saturatedFatG)
        assertEquals(160.0, facts.sodiumMg)
        assertEquals(37.0, facts.carbsG)
        assertEquals(4.0, facts.fiberG)
        assertEquals(12.0, facts.sugarG)
        assertEquals(3.0, facts.proteinG)
        assertNull(facts.caffeineMg)
        assertEquals("2/3 cup (55g)", facts.servingSize)
        assertEquals(8.0, facts.servingsPerContainer)
        assertEquals(8, facts.nutrientCount)
    }

    @Test
    fun `tolerates common OCR mistakes`() {
        val facts = parse(
            "Calorles 23O",
            "Total Fat 8 g",
            "Saturated Fat O.5g",
            "Trans Fat Og",
            "Sodium 1,2OOmg",
            "Total Carb. 3l g",
            "Total Sugars Og",
            "Proteln 4g",
        )
        assertEquals(230.0, facts.calories)
        assertEquals(8.0, facts.fatG)
        assertEquals(0.5, facts.saturatedFatG)
        assertEquals(1200.0, facts.sodiumMg)
        assertEquals(31.0, facts.carbsG)
        assertEquals(0.0, facts.sugarG)
        assertEquals(4.0, facts.proteinG)
    }

    @Test
    fun `reads caffeine from an energy drink label`() {
        val facts = parse(
            "Nutrition Facts",
            "Serving size 1 Can (16 fl oz)",
            "Calories 210",
            "Total Fat 0g 0%",
            "Sodium 370mg 16%",
            "Total Carbohydrate 54g 20%",
            "Total Sugars 54g",
            "Protein 0g",
            "Caffeine Content: 160mg/16oz",
        )
        assertEquals(210.0, facts.calories)
        assertEquals(0.0, facts.fatG)
        assertEquals(54.0, facts.sugarG)
        assertEquals(160.0, facts.caffeineMg)
        assertEquals("1 can (16 fl oz)", facts.servingSize)
    }

    @Test
    fun `reads caffeine written as a sentence`() {
        assertEquals(95.0, parse("Contains 95 mg of caffeine per 12 fl oz").caffeineMg)
        assertEquals(200.0, parse("Caffeine (as caffeine anhydrous) 200 mg").caffeineMg)
    }

    @Test
    fun `does not take another nutrient's amount for caffeine`() {
        val facts = parse("Ingredients: water, sugar, citric acid, caffeine", "Sodium 160mg")
        assertNull(facts.caffeineMg)
        assertEquals(160.0, facts.sodiumMg)
    }

    @Test
    fun `scales caffeine given per 100ml to the serving`() {
        val facts = parse("Serving size 250 ml", "Caffeine 32 mg/100 ml")
        assertEquals(80.0, facts.caffeineMg)
    }

    @Test
    fun `uses the per-serving column of an EU table`() {
        val facts = parse(
            "Nutrition Information",
            "Typical values Per 100g Per 30g serving",
            "Energy 1569kJ / 372kcal 471kJ / 112kcal",
            "Fat 2.0g 0.6g",
            "of which saturates 0.4g 0.1g",
            "Carbohydrate 76g 23g",
            "of which sugars 22g 6.6g",
            "Fibre 5.0g 1.5g",
            "Protein 8.0g 2.4g",
            "Salt 0.65g 0.20g",
        )
        assertEquals(112.0, facts.calories)
        assertEquals(0.6, facts.fatG)
        assertEquals(0.1, facts.saturatedFatG)
        assertEquals(23.0, facts.carbsG)
        assertEquals(6.6, facts.sugarG)
        assertEquals(1.5, facts.fiberG)
        assertEquals(2.4, facts.proteinG)
        assertEquals(80.0, facts.sodiumMg)
        assertEquals("30 g", facts.servingSize)
    }

    @Test
    fun `marks per-100ml-only labels as a 100ml serving`() {
        val facts = parse(
            "Nutrition information per 100ml",
            "Energy 192kJ/45kcal",
            "Fat 0g",
            "Carbohydrate 11g",
            "of which sugars 11g",
            "Protein 0g",
            "Salt 0.1g",
            "Caffeine 32mg/100ml",
        )
        assertEquals("100 ml", facts.servingSize)
        assertEquals(45.0, facts.calories)
        assertEquals(11.0, facts.sugarG)
        assertEquals(32.0, facts.caffeineMg)
        assertEquals(40.0, facts.sodiumMg)
    }

    @Test
    fun `converts kilojoules when kcal is missing`() {
        assertEquals(100.0, parse("Energy 418kJ").calories)
    }

    @Test
    fun `finds an amount printed on the row below its label`() {
        val facts = parse("Amount per serving", "Calories", "230", "Total Fat 8g")
        assertEquals(230.0, facts.calories)
        assertEquals(8.0, facts.fatG)
    }

    @Test
    fun `takes the first column of a dual-column US label`() {
        val facts = parse(
            "Per serving Per container",
            "Calories 140 280",
            "Total Fat 8g 10% 16g 20%",
        )
        assertEquals(140.0, facts.calories)
        assertEquals(8.0, facts.fatG)
    }

    @Test
    fun `ignores footnotes and old-style calories from fat`() {
        val facts = parse(
            "Calories 250 Calories from Fat 110",
            "Calories per gram: Fat 9 Carbohydrate 4 Protein 4",
        )
        assertEquals(250.0, facts.calories)
        assertNull(facts.fatG)
        assertNull(facts.carbsG)
        assertNull(facts.proteinG)

        assertNull(parse("2,000 calories a day is used for general nutrition advice.").calories)
    }

    @Test
    fun `reads a bilingual Canadian label`() {
        val facts = parse(
            "Nutrition Facts Valeur nutritive",
            "Per 1 cup (250 mL) / par 1 tasse (250 mL)",
            "Calories 110 % Daily Value* / % valeur quotidienne*",
            "Fat / Lipides 2 g 3 %",
            "Saturated / saturés 0 g + Trans / trans 0 g 0 %",
            "Carbohydrate / Glucides 22 g",
            "Fibre / Fibres 3 g 11 %",
            "Sugars / Sucres 1 g 1 %",
            "Protein / Protéines 3 g",
            "Sodium 0 mg 0 %",
        )
        assertEquals(110.0, facts.calories)
        assertEquals(2.0, facts.fatG)
        assertEquals(0.0, facts.saturatedFatG)
        assertEquals(22.0, facts.carbsG)
        assertEquals(3.0, facts.fiberG)
        assertEquals(1.0, facts.sugarG)
        assertEquals(3.0, facts.proteinG)
        assertEquals(0.0, facts.sodiumMg)
    }

    @Test
    fun `returns nothing for text that is not a label`() {
        val facts = parse("Best before 12/2026", "Made in Canada")
        assertEquals(0, facts.nutrientCount)
    }
}
