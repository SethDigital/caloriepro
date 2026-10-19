package com.sethdigital.caloriepro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("SELECT * FROM food_entries WHERE epochDay = :epochDay ORDER BY createdAt")
    fun observeDay(epochDay: Long): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries WHERE epochDay = :epochDay ORDER BY createdAt")
    suspend fun day(epochDay: Long): List<FoodEntry>

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun get(id: Long): FoodEntry?

    /** The most recently logged food with this (normalized) barcode. */
    @Query("SELECT * FROM food_entries WHERE barcode = :barcode ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByBarcode(barcode: String): FoodEntry?

    @Query(
        """
        SELECT epochDay,
               COUNT(*) AS entryCount,
               SUM(calories * servings) AS calories,
               SUM(COALESCE(proteinG, 0) * servings) AS proteinG,
               SUM(COALESCE(carbsG, 0) * servings) AS carbsG,
               SUM(COALESCE(fatG, 0) * servings) AS fatG,
               SUM(COALESCE(sugarG, 0) * servings) AS sugarG,
               SUM(COALESCE(caffeineMg, 0) * servings) AS caffeineMg
        FROM food_entries
        GROUP BY epochDay
        ORDER BY epochDay DESC
        """
    )
    fun observeDailySummaries(): Flow<List<DailySummary>>

    /**
     * The most recent entry for each distinct food name matching [pattern]
     * (a SQL LIKE pattern), newest first. Used to re-log foods quickly.
     */
    @Query(
        """
        SELECT * FROM food_entries
        WHERE id IN (
            SELECT MAX(id) FROM food_entries
            WHERE name LIKE :pattern
            GROUP BY name COLLATE NOCASE
        )
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentFoods(pattern: String, limit: Int): Flow<List<FoodEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FoodEntry): Long

    @Insert
    suspend fun insertAll(entries: List<FoodEntry>)

    @Delete
    suspend fun delete(entry: FoodEntry)
}
