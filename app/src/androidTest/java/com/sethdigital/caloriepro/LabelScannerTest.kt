package com.sethdigital.caloriepro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.common.InputImage
import com.sethdigital.caloriepro.scan.LabelScanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real on-device text recognizer over a rendered nutrition label,
 * checking recognition, row grouping and parsing together.
 */
@RunWith(AndroidJUnit4::class)
class LabelScannerTest {

    @Test
    fun readsANutritionFactsPanel() = runBlocking {
        val result = LabelScanner().use { it.scan(InputImage.fromBitmap(renderLabel(), 0)) }
        val facts = result.facts
        val read = "Read rows: ${result.rows}"

        assertEquals(read, 230.0, facts.calories)
        assertEquals(read, 8.0, facts.fatG)
        assertEquals(read, 1.0, facts.saturatedFatG)
        assertEquals(read, 160.0, facts.sodiumMg)
        assertEquals(read, 37.0, facts.carbsG)
        assertEquals(read, 4.0, facts.fiberG)
        assertEquals(read, 12.0, facts.sugarG)
        assertEquals(read, 3.0, facts.proteinG)
        assertEquals(read, 95.0, facts.caffeineMg)
        assertEquals(read, 8.0, facts.servingsPerContainer)
        assertTrue(read, facts.servingSize.orEmpty().contains("2/3 cup"))
    }

    /** Draws a US-style label, with amounts right-aligned like the real thing. */
    private fun renderLabel(): Bitmap {
        val width = 1000
        val bitmap = Bitmap.createBitmap(width, 1500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        var y = 110f

        fun row(left: String, right: String? = null, size: Float = 44f, bold: Boolean = false, rightSize: Float = size) {
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.textSize = size
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(left, 60f, y, paint)
            if (right != null) {
                paint.textSize = rightSize
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(right, width - 60f, y, paint)
            }
            y += maxOf(size, rightSize) * 1.55f
        }

        row("Nutrition Facts", size = 84f, bold = true)
        row("8 servings per container")
        row("Serving size", "2/3 cup (55g)", bold = true)
        row("Amount per serving", size = 34f, bold = true)
        row("Calories", "230", size = 66f, bold = true, rightSize = 96f)
        row("", "% Daily Value*", size = 34f, bold = true)
        row("Total Fat 8g", "10%")
        row("Saturated Fat 1g", "5%")
        row("Trans Fat 0g")
        row("Sodium 160mg", "7%")
        row("Total Carbohydrate 37g", "13%")
        row("Dietary Fiber 4g", "14%")
        row("Total Sugars 12g")
        row("Protein 3g")
        row("Caffeine 95mg")
        return bitmap
    }
}
