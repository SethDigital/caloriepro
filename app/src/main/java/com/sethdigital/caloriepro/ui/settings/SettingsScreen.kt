package com.sethdigital.caloriepro.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Daily goals", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = viewModel.calorieGoal,
                onValueChange = viewModel::onCalorieGoalChange,
                label = { Text("Calorie goal") },
                suffix = { Text("kcal") },
                isError = "calories" in viewModel.invalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.caffeineLimit,
                onValueChange = viewModel::onCaffeineLimitChange,
                label = { Text("Caffeine limit") },
                suffix = { Text("mg") },
                supportingText = { Text("400 mg a day is the usual guideline for healthy adults.") },
                isError = "caffeine" in viewModel.invalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(if (viewModel.saved) "Saved" else "Save goals")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your data", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Everything you log is stored only in a database on this phone. CaloriePro has " +
                            "no internet access, label photos are read on the device and never saved, " +
                            "and the log is excluded from cloud backups.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Uninstalling the app deletes the log. Installing a newer build over this one keeps it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
