package com.sethdigital.caloriepro

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sethdigital.caloriepro.data.AppDatabase
import com.sethdigital.caloriepro.data.Meal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Updating the app must keep every food already logged. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun foodLoggedInVersion1SurvivesTheUpgrade() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO food_entries (epochDay, name, meal, servings, servingSize, calories, proteinG, " +
                    "carbsG, fatG, saturatedFatG, sugarG, fiberG, sodiumMg, caffeineMg, createdAt) " +
                    "VALUES (19000, 'Coffee', 'BREAKFAST', 2.0, '1 cup (240 mL)', 5.0, NULL, NULL, NULL, NULL, " +
                    "NULL, NULL, NULL, 95.0, 1)"
            )
            close()
        }

        helper.runMigrationsAndValidate(DB, 2, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, DB).build()
        try {
            val entries = runBlocking { db.foodDao().day(19000) }
            assertEquals(1, entries.size)
            val coffee = entries.single()
            assertEquals("Coffee", coffee.name)
            assertEquals(Meal.BREAKFAST, coffee.meal)
            assertEquals(2.0, coffee.servings, 0.0)
            assertEquals(95.0, coffee.caffeineMg!!, 0.0)
            assertNull(coffee.barcode)
            assertFalse(coffee.byWeight)
            assertTrue(runBlocking { db.savedMealDao().observeAll().first() }.isEmpty())
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
