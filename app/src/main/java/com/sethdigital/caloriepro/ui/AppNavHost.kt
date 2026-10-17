package com.sethdigital.caloriepro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sethdigital.caloriepro.ui.day.DayScreen
import com.sethdigital.caloriepro.ui.day.DayViewModel
import com.sethdigital.caloriepro.ui.entry.EntryScreen
import com.sethdigital.caloriepro.ui.entry.EntryViewModel
import com.sethdigital.caloriepro.ui.history.HistoryScreen
import com.sethdigital.caloriepro.ui.settings.SettingsScreen
import java.time.LocalDate

private object Routes {
    const val DAY = "day"
    const val ENTRY =
        "entry?${EntryViewModel.ARG_ENTRY_ID}={${EntryViewModel.ARG_ENTRY_ID}}" +
            "&${EntryViewModel.ARG_DAY}={${EntryViewModel.ARG_DAY}}" +
            "&${EntryViewModel.ARG_SCAN}={${EntryViewModel.ARG_SCAN}}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun newEntry(day: LocalDate, scan: Boolean) =
        "entry?${EntryViewModel.ARG_DAY}=${day.toEpochDay()}&${EntryViewModel.ARG_SCAN}=$scan"

    fun editEntry(id: Long) = "entry?${EntryViewModel.ARG_ENTRY_ID}=$id"
}

/** Key the history screen uses to hand the picked day back to the day screen. */
private const val PICKED_DAY = "picked_day"

@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.DAY) {
        composable(Routes.DAY) { backStackEntry ->
            val viewModel: DayViewModel = viewModel(factory = AppViewModelProvider.Factory)

            val picked by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(PICKED_DAY, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(picked) {
                picked?.let {
                    viewModel.selectDate(LocalDate.ofEpochDay(it))
                    backStackEntry.savedStateHandle[PICKED_DAY] = null
                }
            }

            DayScreen(
                viewModel = viewModel,
                onAddFood = { day -> nav.navigate(Routes.newEntry(day, scan = false)) },
                onScanLabel = { day -> nav.navigate(Routes.newEntry(day, scan = true)) },
                onEditEntry = { id -> nav.navigate(Routes.editEntry(id)) },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.ENTRY,
            arguments = listOf(
                navArgument(EntryViewModel.ARG_ENTRY_ID) { type = NavType.LongType; defaultValue = -1L },
                navArgument(EntryViewModel.ARG_DAY) { type = NavType.LongType; defaultValue = -1L },
                navArgument(EntryViewModel.ARG_SCAN) { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            EntryScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onDone = { nav.popBackStack() },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onBack = { nav.popBackStack() },
                onOpenDay = { day ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(PICKED_DAY, day.toEpochDay())
                    nav.popBackStack()
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
