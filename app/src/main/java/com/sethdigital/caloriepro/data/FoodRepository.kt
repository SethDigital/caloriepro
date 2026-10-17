package com.sethdigital.caloriepro.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class FoodRepository(private val dao: FoodDao) {

    fun observeDay(date: LocalDate): Flow<List<FoodEntry>> = dao.observeDay(date.toEpochDay())

    fun observeDailySummaries(): Flow<List<DailySummary>> = dao.observeDailySummaries()

    fun observeRecentFoods(query: String, limit: Int = 8): Flow<List<FoodEntry>> {
        val escaped = query.trim().replace("%", "").replace("_", "")
        return dao.observeRecentFoods("%$escaped%", limit)
    }

    suspend fun get(id: Long): FoodEntry? = dao.get(id)

    /** Inserts a new entry, or replaces the one with the same id. */
    suspend fun save(entry: FoodEntry): Long = dao.upsert(entry)

    suspend fun delete(entry: FoodEntry) = dao.delete(entry)
}
