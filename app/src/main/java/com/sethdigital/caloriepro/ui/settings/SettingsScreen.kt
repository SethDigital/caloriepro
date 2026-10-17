package com.sethdigital.caloriepro.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sethdigital.caloriepro.data.Goals
import com.sethdigital.caloriepro.data.LimitKind
import com.sethdigital.caloriepro.data.Nutrient
import com.sethdigital.caloriepro.ui.formatWhole

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                actions = {
                    TextButton(onClick = viewModel::save) { Text(if (viewModel.saved) "Saved" else "Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Daily goals and limits", style = MaterialTheme.typography.titleMedium)
            Text(
                "Recommended values are filled in to start. Change any of them to set your own.",
                style = MaterialTheme.typography.bodyMedium,
            )

            LimitField(
                value = viewModel.calorieGoal,
                onValueChange = viewModel::onCalorieGoalChange,
                label = "Calories",
                unit = "kcal",
                hint = "Recommended: ${Goals.RECOMMENDED_CALORIES.formatWhole()} kcal as a general guide. " +
                    "Needs vary with age, size and activity.",
                isError = SettingsViewModel.CALORIES in viewModel.invalid,
                testTag = "goal_calories",
            )
            for (n in Nutrient.entries) {
                LimitField(
                    value = viewModel.limits[n].orEmpty(),
                    onValueChange = { viewModel.onLimitChange(n, it) },
                    label = n.label,
                    unit = n.unit,
                    hint = n.recommendationText(),
                    isError = n.name in viewModel.invalid,
                    testTag = "limit_${n.name.lowercase()}",
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = viewModel::useRecommended, modifier = Modifier.weight(1f)) {
                    Text("Use recommended")
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_settings"),
                ) {
                    Text(if (viewModel.saved) "Saved" else "Save")
                }
            }
            Text(
                "Recommendations are the U.S. FDA Daily Values used on nutrition labels, based on a " +
                    "2,000-calorie diet, and the FDA's guidance of up to 400 mg of caffeine a day for " +
                    "healthy adults. Check with a doctor or dietitian for advice that fits you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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

@Composable
private fun LimitField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unit: String,
    hint: String,
    isError: Boolean,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(unit) },
        supportingText = { Text(if (isError) "Enter a number above 0" else hint) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

/** "Recommended: up to 2,300 mg" or "Recommended: at least 28 g", plus any note. */
private fun Nutrient.recommendationText(): String {
    val bound = if (kind == LimitKind.MINIMUM) "at least" else "up to"
    val base = "Recommended: $bound ${recommended.formatWhole()} $unit"
    return if (note != null) "$base ($note)" else base
}
