package com.sethdigital.caloriepro.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethdigital.caloriepro.R
import com.sethdigital.caloriepro.data.FoodEntry
import com.sethdigital.caloriepro.data.Meal
import com.sethdigital.caloriepro.data.Nutrient
import com.sethdigital.caloriepro.data.NutrientTotals
import com.sethdigital.caloriepro.data.SavedMealWithItems
import com.sethdigital.caloriepro.data.ServingWeight
import com.sethdigital.caloriepro.scan.ScannerScreen
import com.sethdigital.caloriepro.ui.formatAmount
import com.sethdigital.caloriepro.ui.formatWhole
import com.sethdigital.caloriepro.ui.friendlyName
import com.sethdigital.caloriepro.ui.toInputText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(viewModel: EntryViewModel, onDone: () -> Unit) {
    LaunchedEffect(viewModel.finished) {
        if (viewModel.finished) onDone()
    }

    if (viewModel.showScanner) {
        BackHandler(onBack = viewModel::closeScanner)
        ScannerScreen(
            onResult = viewModel::applyScan,
            onBarcode = viewModel::onBarcode,
            barcodeNote = viewModel.barcodeNote,
            onClose = viewModel::closeScanner,
        )
        return
    }

    val form = viewModel.form
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val savedMeals by viewModel.savedMeals.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deletingMeal by remember { mutableStateOf<SavedMealWithItems?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit food" else "Add food") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (viewModel.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = !viewModel.loading) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (viewModel.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Logging for ${viewModel.date.friendlyName()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            FilledTonalButton(onClick = viewModel::openScanner, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_camera), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan barcode or label")
            }

            viewModel.scanNotice?.let { ScanNoticeCard(it, onDismiss = viewModel::dismissScanNotice) }

            // The meal comes first so a saved meal can be logged under it in one tap.
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (meal in Meal.entries) {
                    FilterChip(
                        selected = form.meal == meal,
                        onClick = { viewModel.update { copy(meal = meal) } },
                        label = { Text(meal.label) },
                    )
                }
            }

            if (!viewModel.isEditing && savedMeals.isNotEmpty()) {
                SavedMeals(
                    meals = savedMeals,
                    meal = form.meal,
                    onLog = viewModel::logSavedMeal,
                    onDelete = { deletingMeal = it },
                )
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { copy(name = v) } },
                label = { Text("Food name") },
                placeholder = { Text("e.g. Greek yogurt") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_name"),
            )

            form.barcode?.let { code ->
                InputChip(
                    selected = false,
                    onClick = viewModel::removeBarcode,
                    label = { Text("Barcode $code") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_barcode), contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Forget barcode") },
                    modifier = Modifier.testTag("barcode_chip"),
                )
            }

            val matches = suggestions.filterNot { it.name.equals(form.name.trim(), ignoreCase = true) }
            if (!viewModel.isEditing && matches.isNotEmpty()) {
                Suggestions(
                    title = if (form.name.isBlank()) "Recent foods" else "Logged before",
                    foods = matches,
                    onPick = viewModel::fillFrom,
                )
            }

            val weight = form.servingWeight
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = form.amount,
                    onValueChange = { v -> viewModel.update { copy(amount = v) } },
                    label = if (form.byWeight) "Amount eaten" else "Servings",
                    suffix = if (form.byWeight) weight?.unit else null,
                    isError = "servings" in viewModel.invalidFields,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("field_servings"),
                )
                OutlinedTextField(
                    value = form.servingSize,
                    onValueChange = { v -> viewModel.update { copy(servingSize = v) } },
                    label = { Text("Serving size") },
                    placeholder = { Text("e.g. 1 cup (240ml)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.6f)
                        .testTag("field_serving_size"),
                )
            }

            if (weight != null) {
                AmountUnit(form = form, weight = weight, onByWeight = viewModel::setByWeight)
            }

            viewModel.servingsPerContainer?.let { perPackage ->
                AssistChip(
                    onClick = { viewModel.update { withByWeight(false).copy(amount = perPackage.toInputText()) } },
                    label = { Text("Ate the whole package (${perPackage.formatAmount()} servings)") },
                )
            }

            NumberField(
                value = form.calories,
                onValueChange = { v -> viewModel.update { copy(calories = v) } },
                label = "Calories per serving",
                suffix = "kcal",
                isError = "calories" in viewModel.invalidFields,
                errorText = "Enter the calories (0 is fine)",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_calories"),
            )

            Text(
                "Per serving (optional)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            for (pair in Nutrient.entries.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (n in pair) {
                        NumberField(
                            value = form.nutrient(n),
                            onValueChange = { v -> viewModel.updateNutrient(n, v) },
                            label = n.label,
                            suffix = n.unit,
                            isError = n.name in viewModel.invalidFields,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("field_${n.name.lowercase()}"),
                        )
                    }
                }
            }

            form.previewTotals()?.let { TotalsPreview(it) }

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("save_entry"),
            ) { Text(if (viewModel.isEditing) "Save changes" else "Add to log") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this food?") },
            text = { Text("It will be removed from ${viewModel.date.friendlyName()}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    deletingMeal?.let { saved ->
        AlertDialog(
            onDismissRequest = { deletingMeal = null },
            title = { Text("Delete \"${saved.meal.name}\"?") },
            text = { Text("The saved meal is removed. Foods already logged from it stay in your log.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingMeal = null
                    viewModel.deleteSavedMeal(saved)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMeal = null }) { Text("Cancel") }
            },
        )
    }
}

/** Switches the amount between servings and the weight (or volume) eaten. */
@Composable
private fun AmountUnit(form: EntryForm, weight: ServingWeight, onByWeight: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Amount in", style = MaterialTheme.typography.bodyMedium)
        FilterChip(
            selected = !form.byWeight,
            onClick = { onByWeight(false) },
            label = { Text("Servings") },
            modifier = Modifier.testTag("unit_servings"),
        )
        FilterChip(
            selected = form.byWeight,
            onClick = { onByWeight(true) },
            label = { Text(if (weight.unit == "ml") "Millilitres" else "Grams") },
            modifier = Modifier.testTag("unit_weight"),
        )
    }
    val servings = form.servings() ?: return
    Text(
        if (form.byWeight) {
            "${servings.formatAmount()} servings of ${weight.amount.formatAmount()} ${weight.unit}"
        } else {
            "${weight.weightOf(servings).formatAmount()} ${weight.unit} in total"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("amount_hint"),
    )
}

@Composable
private fun SavedMeals(
    meals: List<SavedMealWithItems>,
    meal: Meal,
    onLog: (SavedMealWithItems) -> Unit,
    onDelete: (SavedMealWithItems) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Saved meals · tap to log as ${meal.label.lowercase()}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        )
        meals.forEachIndexed { i, saved ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Log") { onLog(saved) }
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                    .testTag("saved_meal_${saved.meal.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(saved.meal.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${saved.items.size} food${if (saved.items.size == 1) "" else "s"} · " +
                            "${saved.calories.formatWhole()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDelete(saved) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${saved.meal.name}")
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else {
            null
        },
        isError = isError,
        supportingText = if (isError && errorText != null) {
            { Text(errorText) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

@Composable
private fun ScanNoticeCard(notice: ScanNotice, onDismiss: () -> Unit) {
    var showText by rememberSaveable { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scan_notice"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (notice) {
                is ScanNotice.FromLabel -> {
                    Text(
                        "Filled in ${notice.filledCount} value${if (notice.filledCount == 1) "" else "s"} from the label",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        buildString {
                            append("Check them against the package before saving.")
                            if (!notice.caloriesFound) append(" Calories weren't found, so enter them yourself.")
                            if (notice.barcodeSaved) append(" Next time, scanning the barcode fills all of this in.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        TextButton(onClick = { showText = !showText }) {
                            Text(if (showText) "Hide scanned text" else "Show scanned text")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                    if (showText) {
                        Text(
                            notice.rows.joinToString("\n").ifEmpty { "(no text)" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                is ScanNotice.FromBarcode -> {
                    Text("Found ${notice.foodName}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Filled in from the last time you scanned this barcode. Set how much you had, then save.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Suggestions(title: String, foods: List<FoodEntry>, onPick: (FoodEntry) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        )
        foods.forEachIndexed { i, food ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(food) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    food.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${food.calories.formatWhole()} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TotalsPreview(totals: NutrientTotals) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("You're logging", style = MaterialTheme.typography.labelLarge)
            Text(
                "${totals.calories.formatWhole()} kcal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            val parts = buildList {
                if (totals.proteinG > 0) add("${totals.proteinG.formatAmount()} g protein")
                if (totals.carbsG > 0) add("${totals.carbsG.formatAmount()} g carbs")
                if (totals.fatG > 0) add("${totals.fatG.formatAmount()} g fat")
                if (totals.caffeineMg > 0) add("${totals.caffeineMg.formatWhole()} mg caffeine")
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
