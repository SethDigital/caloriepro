package com.sethdigital.caloriepro.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sethdigital.caloriepro.CalorieApp
import com.sethdigital.caloriepro.ui.day.DayViewModel
import com.sethdigital.caloriepro.ui.entry.EntryViewModel
import com.sethdigital.caloriepro.ui.history.HistoryViewModel
import com.sethdigital.caloriepro.ui.settings.SettingsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            DayViewModel(createSavedStateHandle(), container().foodRepository, container().settingsRepository)
        }
        initializer {
            EntryViewModel(createSavedStateHandle(), container().foodRepository)
        }
        initializer {
            HistoryViewModel(container().foodRepository, container().settingsRepository)
        }
        initializer {
            SettingsViewModel(container().settingsRepository)
        }
    }

    private fun CreationExtras.container() =
        (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CalorieApp).container
}
