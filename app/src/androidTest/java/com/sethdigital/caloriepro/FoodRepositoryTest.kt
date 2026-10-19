package com.sethdigital.caloriepro

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sethdigital.caloriepro.data.AppDatabase
import com.sethdigital.caloriepro.data.FoodEntry
import com.sethdigital.caloriepro.data.FoodRepository
import com.sethdigital.caloriepro.data.Meal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FoodRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var foods: FoodRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        foods = FoodRepository(db.foodDao(), db.savedMealDao(), clock = { 1_000L })
    }

    @After
    fun tearDown() = db.close()

    private val day = LocalDate.of(2026, 3, 2)

    private fun food(
        name: String,
        meal: Meal = Meal.BREAKFAST,
        calories: Double = 100.0,
        createdAt: Long = 1,
        barcode: String? = null,
        caffeineMg: Double? = null,
    ) = FoodEntry(
        epochDay = day.toEpochDay(),
        name = name,
        meal = meal,
        servings = 1.5,
        servingSize = "1 cup (240 mL)",
        calories = calories,
        proteinG = 2.0,
        carbsG = null,
        fatG = null,
        saturatedFatG = null,
        sugarG = null,
        fiberG = null,
        sodiumMg = null,
        caffeineMg = caffeineMg,
        createdAt = createdAt,
        barcode = barcode,
        byWeight = true,
    )

    @Test
    fun copyLogsTheSameFoodsOnAnotherDay() = runBlocking {
        foods.save(food("Coffee", createdAt = 1, caffeineMg = 95.0))
        foods.save(food("Toast", createdAt = 2))
        val target = day.plusDays(3)

        assertEquals(2, foods.copy(foods.entriesOn(day), target))

        val copied = foods.entriesOn(target)
        assertEquals(listOf("Coffee", "Toast"), copied.map { it.name })
        assertTrue(copied.all { it.meal == Meal.BREAKFAST && it.servings == 1.5 && it.byWeight })
        assertEquals(95.0, copied[0].caffeineMg!!, 0.0)
        assertEquals(2, foods.entriesOn(day).size)
        assertTrue(copied.map { it.id }.intersect(foods.entriesOn(day).map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun copyCanMoveFoodsToAnotherMeal() = runBlocking {
        foods.save(food("Leftover pasta", meal = Meal.DINNER))

        foods.copy(foods.entriesOn(day), day.plusDays(1), meal = Meal.LUNCH)

        assertEquals(Meal.LUNCH, foods.entriesOn(day.plusDays(1)).single().meal)
    }

    @Test
    fun savedMealsLogInOrderUnderTheChosenMeal() = runBlocking {
        foods.save(food("Coffee", createdAt = 1, caffeineMg = 95.0, barcode = "0012345678905"))
        foods.save(food("Toast", createdAt = 2, calories = 80.0))
        foods.saveMeal("  Usual breakfast ", foods.entriesOn(day))

        val saved = foods.observeSavedMeals().first().single()
        assertEquals("Usual breakfast", saved.meal.name)
        assertEquals((100.0 + 80.0) * 1.5, saved.calories, 0.001)

        val target = day.plusDays(1)
        assertEquals(2, foods.logSavedMeal(saved, target, Meal.DINNER))

        val logged = foods.entriesOn(target)
        assertEquals(listOf("Coffee", "Toast"), logged.map { it.name })
        assertTrue(logged.all { it.meal == Meal.DINNER && it.servings == 1.5 && it.byWeight })
        assertEquals(95.0, logged[0].caffeineMg!!, 0.0)
        assertEquals("0012345678905", logged[0].barcode)
    }

    @Test
    fun deletingASavedMealKeepsTheFoodsAlreadyLogged() = runBlocking {
        foods.save(food("Coffee"))
        foods.saveMeal("Coffee break", foods.entriesOn(day))

        foods.deleteSavedMeal(foods.observeSavedMeals().first().single().meal)

        assertTrue(foods.observeSavedMeals().first().isEmpty())
        db.query("SELECT COUNT(*) FROM saved_meal_items", null).use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        assertEquals(1, foods.entriesOn(day).size)
    }

    @Test
    fun barcodeLookupFindsTheLatestFoodWithIt() = runBlocking {
        foods.save(food("Old granola", createdAt = 1, barcode = "0012345678905"))
        foods.save(food("Granola", createdAt = 2, barcode = "0012345678905"))
        foods.save(food("Pens", createdAt = 3, barcode = "4006381333931"))

        // The same product read as UPC-A rather than EAN-13.
        assertEquals("Granola", foods.findByBarcode("012345678905")?.name)
        assertEquals("Pens", foods.findByBarcode("4006381333931")?.name)
        assertNull(foods.findByBarcode("5000112637922"))
        assertNull(foods.findByBarcode("not a barcode"))
    }
}
