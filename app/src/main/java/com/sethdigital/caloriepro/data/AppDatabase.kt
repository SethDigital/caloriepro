package com.sethdigital.caloriepro.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FoodEntry::class, SavedMeal::class, SavedMealItem::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 2: barcode and byWeight on food entries, saved meals.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun savedMealDao(): SavedMealDao

    companion object {
        // Future schema changes must ship a Migration: there is deliberately
        // no destructive fallback, so an update can never wipe the food log.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "caloriepro.db").build()
    }
}
