package com.sethdigital.caloriepro

import android.app.Application
import android.content.Context
import com.sethdigital.caloriepro.data.AppDatabase
import com.sethdigital.caloriepro.data.FoodRepository
import com.sethdigital.caloriepro.data.SettingsRepository

/** Holds the app's single instances of its data sources. */
class AppContainer(context: Context) {
    private val database by lazy { AppDatabase.create(context) }
    val foodRepository by lazy { FoodRepository(database.foodDao(), database.savedMealDao()) }
    val settingsRepository by lazy { SettingsRepository(context) }
}

class CalorieApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
