package com.sethdigital.caloriepro.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class FoodRepository(
    private val dao: FoodDao,
    private val savedMeals: SavedMealDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeDay(date: LocalDate): Flow<List<FoodEntry>> = dao.observeDay(date.toEpochDay())

    suspend fun entriesOn(date: LocalDate): List<FoodEntry> = dao.day(date.toEpochDay())

    fun observeDailySummaries(): Flow<List<DailySummary>> = dao.observeDailySummaries()

    fun observeRecentFoods(query: String, limit: Int = 8): Flow<List<FoodEntry>> {
        val escaped = query.trim().replace("%", "").replace("_", "")
        return dao.observeRecentFoods("%$escaped%", limit)
    }

    suspend fun get(id: Long): FoodEntry? = dao.get(id)

    /** The food last logged with [barcode], if this barcode has been scanned before. */
    suspend fun findByBarcode(barcode: String): FoodEntry? =
        Barcodes.normalize(barcode)?.let { dao.findByBarcode(it) }

    /** Inserts a new entry, or replaces the one with the same id. */
    suspend fun save(entry: FoodEntry): Long = dao.upsert(entry)

    suspend fun delete(entry: FoodEntry) = dao.delete(entry)

    /**
     * Logs copies of [entries] on [date], keeping their order. Each copy keeps
     * its meal unless [meal] is given. Returns how many were copied.
     */
    suspend fun copy(entries: List<FoodEntry>, date: LocalDate, meal: Meal? = null): Int {
        val now = clock()
        dao.insertAll(
            entries.mapIndexed { i, e ->
                e.copy(id = 0, epochDay = date.toEpochDay(), meal = meal ?: e.meal, createdAt = now + i)
            }
        )
        return entries.size
    }

    fun observeSavedMeals(): Flow<List<SavedMealWithItems>> = savedMeals.observeAll()

    suspend fun saveMeal(name: String, entries: List<FoodEntry>): Long = savedMeals.insert(
        SavedMeal(name = name.trim(), createdAt = clock()),
        entries.mapIndexed { i, e -> SavedMealItem.from(e, position = i) },
    )

    /** Logs every food in [saved] on [date] as [meal]. Returns how many were logged. */
    suspend fun logSavedMeal(saved: SavedMealWithItems, date: LocalDate, meal: Meal): Int {
        val now = clock()
        dao.insertAll(
            saved.orderedItems.mapIndexed { i, item -> item.toEntry(date.toEpochDay(), meal, createdAt = now + i) }
        )
        return saved.items.size
    }

    suspend fun deleteSavedMeal(meal: SavedMeal) = savedMeals.delete(meal)
}
