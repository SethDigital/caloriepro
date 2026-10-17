package com.sethdigital.caloriepro.scan.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class RowGrouperTest {

    private fun line(text: String, left: Float, top: Float, right: Float, bottom: Float) =
        OcrLine.fromBox(text, left, top, right, bottom)

    /** A label where the amounts were recognized as separate lines on the right. */
    private val label = listOf(
        line("Nutrition Facts", 10f, 0f, 300f, 40f),
        line("Amount per serving", 10f, 60f, 160f, 80f),
        line("Calories", 10f, 90f, 150f, 125f),
        line("230", 220f, 82f, 300f, 128f), // big, bold number
        line("Total Fat 8g", 10f, 140f, 120f, 158f),
        line("10%", 260f, 141f, 300f, 159f),
        line("Sodium 160mg", 10f, 166f, 130f, 184f),
        line("7%", 270f, 167f, 300f, 185f),
    )

    @Test
    fun `joins labels with the amounts beside them`() {
        assertEquals(
            listOf(
                "Nutrition Facts",
                "Amount per serving",
                "Calories 230",
                "Total Fat 8g 10%",
                "Sodium 160mg 7%",
            ),
            RowGrouper.group(label.shuffled(java.util.Random(7))),
        )
    }

    @Test
    fun `handles a tilted photo`() {
        val angle = Math.toRadians(6.0)
        val tilted = label.map { l ->
            l.copy(corners = l.corners.map { p ->
                Point(
                    (p.x * cos(angle) - p.y * sin(angle)).toFloat(),
                    (p.x * sin(angle) + p.y * cos(angle)).toFloat(),
                )
            })
        }
        assertEquals(
            listOf("Calories 230", "Total Fat 8g 10%", "Sodium 160mg 7%"),
            RowGrouper.group(tilted).drop(2),
        )
    }

    @Test
    fun `parses recognized lines end to end`() {
        val facts = NutritionLabelParser.parse(label)
        assertEquals(230.0, facts.calories)
        assertEquals(8.0, facts.fatG)
        assertEquals(160.0, facts.sodiumMg)
    }
}
