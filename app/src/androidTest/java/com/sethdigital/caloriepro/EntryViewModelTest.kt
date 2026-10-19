package com.sethdigital.caloriepro

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sethdigital.caloriepro.data.AppDatabase
import com.sethdigital.caloriepro.data.FoodEntry
import com.sethdigital.caloriepro.data.FoodRepository
import com.sethdigital.caloriepro.data.Meal
import com.sethdigital.caloriepro.data.Nutrient
import com.sethdigital.caloriepro.scan.ScanResult
import com.sethdigital.caloriepro.scan.parser.NutritionFacts
import com.sethdigital.caloriepro.ui.entry.EntryForm
import com.sethdigital.caloriepro.ui.entry.EntryViewModel
import com.sethdigital.caloriepro.ui.entry.ScanNotice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class EntryViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var foods: FoodRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        foods = FoodRepository(db.foodDao(), db.savedMealDao())
    }

    @After
    fun tearDown() = db.close()

    private val today = LocalDate.now()

    /** View models run their work on the main thread, as they do in the app. */
    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!onMain(condition)) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting until $what" }
            Thread.sleep(20)
        }
    }

    private fun newViewModel() = onMain {
        EntryViewModel(SavedStateHandle(mapOf(EntryViewModel.ARG_DAY to today.toEpochDay())), foods)
    }

    @Test
    fun aKnownBarcodeFillsInTheFoodLoggedWithIt() {
        runBlocking {
            foods.save(
                FoodEntry(
                    epochDay = today.minusDays(2).toEpochDay(),
                    name = "Granola",
                    meal = Meal.SNACK,
                    servings = 2.0,
                    servingSize = "2/3 cup (55g)",
                    calories = 240.0,
                    proteinG = 6.0,
                    carbsG = null,
                    fatG = null,
                    saturatedFatG = null,
                    sugarG = 12.0,
                    fiberG = null,
                    sodiumMg = null,
                    caffeineMg = null,
                    createdAt = 1,
                    barcode = "0012345678905",
                    byWeight = true,
                )
            )
        }
        val vm = newViewModel()

        onMain {
            vm.update { copy(meal = Meal.BREAKFAST) }
            vm.openScanner()
            vm.onBarcode("012345678905") // read as UPC-A this time
        }
        waitUntil("the barcode is looked up") { vm.scanNotice != null }

        onMain {
            assertEquals(ScanNotice.FromBarcode("Granola"), vm.scanNotice)
            assertFalse(vm.showScanner)
            val form = vm.form
            assertEquals("Granola", form.name)
            assertEquals(Meal.BREAKFAST, form.meal)
            assertEquals("240", form.calories)
            assertEquals("12", form.nutrient(Nutrient.SUGAR))
            assertTrue(form.byWeight)
            assertEquals("110", form.amount)
            assertEquals("0012345678905", form.barcode)
        }
    }

    @Test
    fun aNewBarcodeIsRememberedWithTheFoodSavedNext() {
        val vm = newViewModel()

        onMain {
            vm.openScanner()
            vm.onBarcode("4006381333931")
        }
        waitUntil("the new barcode is noted") { vm.barcodeNote != null }
        onMain {
            assertTrue(vm.showScanner)
            assertEquals("4006381333931", vm.form.barcode)
            vm.applyScan(ScanResult(NutritionFacts(calories = 90.0, caffeineMg = 80.0), rows = emptyList()))
            assertEquals(ScanNotice.FromLabel(2, caloriesFound = true, rows = emptyList(), barcodeSaved = true), vm.scanNotice)
            vm.update { copy(name = "Energy drink") }
            vm.save()
        }
        waitUntil("the food is saved") { vm.finished }

        val saved = runBlocking { foods.findByBarcode("4006381333931") }
        assertEquals("Energy drink", saved?.name)
        assertEquals(80.0, saved?.caffeineMg!!, 0.0)
    }

    @Test
    fun aWeightIsSavedAsServingsAndShownAgainAsAWeight() {
        val vm = newViewModel()

        onMain {
            vm.update { copy(name = "Rice", servingSize = "1 cup (180 g)", calories = "200") }
            vm.setByWeight(true)
            assertEquals("180", vm.form.amount)
            vm.update { copy(amount = "90") }
            assertEquals(100.0, vm.form.previewTotals()!!.calories, 0.001)
            vm.save()
        }
        waitUntil("the food is saved") { vm.finished }

        val rice = runBlocking { foods.entriesOn(today) }.single()
        assertEquals(0.5, rice.servings, 0.0001)
        assertTrue(rice.byWeight)
        val reopened = EntryForm.from(rice)
        assertTrue(reopened.byWeight)
        assertEquals("90", reopened.amount)
    }
}
