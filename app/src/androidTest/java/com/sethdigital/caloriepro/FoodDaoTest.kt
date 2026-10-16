package com.sethdigital.caloriepro

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sethdigital.caloriepro.data.AppDatabase
import com.sethdigital.caloriepro.data.FoodDao
import com.sethdigital.caloriepro.data.FoodEntry
import com.sethdigital.caloriepro.data.Meal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: FoodDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.foodDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entry(
        day: Long,
        name: String,
        calories: Double,
        createdAt: Long,
        servings: Double = 1.0,
        caffeineMg: Double? = null,
        proteinG: Double? = null,
    ) = FoodEntry(
        epochDay = day,
        name = name,
        meal = Meal.LUNCH,
        servings = servings,
        servingSize = null,
        calories = calories,
        proteinG = proteinG,
        carbsG = null,
        fatG = null,
        saturatedFatG = null,
        sugarG = null,
        fiberG = null,
        sodiumMg = null,
        caffeineMg = caffeineMg,
        createdAt = createdAt,
    )

    @Test
    fun entriesAreKeptPerDay() = runBlocking {
        dao.upsert(entry(100, "Oatmeal", 150.0, createdAt = 1))
        dao.upsert(entry(100, "Coffee", 5.0, createdAt = 2, caffeineMg = 95.0))
        dao.upsert(entry(101, "Pizza", 285.0, createdAt = 3, servings = 2.0))

        assertEquals(listOf("Oatmeal", "Coffee"), dao.observeDay(100).first().map { it.name })
        assertEquals(listOf("Pizza"), dao.observeDay(101).first().map { it.name })
    }

    @Test
    fun dailySummariesMultiplyByServings() = runBlocking {
        dao.upsert(entry(100, "Oatmeal", 150.0, createdAt = 1, proteinG = 5.0))
        dao.upsert(entry(100, "Coffee", 5.0, createdAt = 2, caffeineMg = 95.0))
        dao.upsert(entry(101, "Pizza", 285.0, createdAt = 3, servings = 2.0, proteinG = 12.0))

        val summaries = dao.observeDailySummaries().first()
        assertEquals(listOf(101L, 100L), summaries.map { it.epochDay })
        assertEquals(570.0, summaries[0].calories, 0.001)
        assertEquals(24.0, summaries[0].proteinG, 0.001)
        assertEquals(155.0, summaries[1].calories, 0.001)
        assertEquals(95.0, summaries[1].caffeineMg, 0.001)
        assertEquals(2, summaries[1].entryCount)
    }

    @Test
    fun recentFoodsAreDistinctByName() = runBlocking {
        dao.upsert(entry(100, "Coffee", 5.0, createdAt = 1))
        dao.upsert(entry(101, "coffee", 10.0, createdAt = 2))
        dao.upsert(entry(101, "Banana", 105.0, createdAt = 3))

        assertEquals(listOf("Banana", "coffee"), dao.observeRecentFoods("%", 10).first().map { it.name })
        assertEquals(listOf("coffee"), dao.observeRecentFoods("%cof%", 10).first().map { it.name })
    }

    @Test
    fun upsertReplacesAnExistingEntry() = runBlocking {
        val id = dao.upsert(entry(100, "Toast", 80.0, createdAt = 1))
        dao.upsert(entry(100, "Toast with butter", 180.0, createdAt = 1).copy(id = id))

        val day = dao.observeDay(100).first()
        assertEquals(1, day.size)
        assertEquals("Toast with butter", day[0].name)
        assertEquals(180.0, day[0].calories, 0.001)
    }
}
