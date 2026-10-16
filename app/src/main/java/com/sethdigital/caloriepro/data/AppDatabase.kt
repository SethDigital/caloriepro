package com.sethdigital.caloriepro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FoodEntry::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao

    companion object {
        // Future schema changes must ship a Migration: there is deliberately
        // no destructive fallback, so an update can never wipe the food log.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "caloriepro.db").build()
    }
}
