package com.sethdigital.caloriepro.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethdigital.caloriepro.R
import com.sethdigital.caloriepro.data.FoodEntry
import com.sethdigital.caloriepro.data.Goals
import com.sethdigital.caloriepro.data.LimitKind
import com.sethdigital.caloriepro.data.Meal
import com.sethdigital.caloriepro.data.Nutrient
import com.sethdigital.caloriepro.data.NutrientTotals
import com.sethdigital.caloriepro.data.sumTotals
import com.sethdigital.caloriepro.data.totals
import com.sethdigital.caloriepro.ui.formatAmount
import com.sethdigital.caloriepro.ui.formatWhole
import com.sethdigital.caloriepro.ui.friendlyName
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    viewModel: DayViewModel,
    onAddFood: (LocalDate) -> Unit,
    onScanLabel: (LocalDate) -> Unit,
    onEditEntry: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    val deleteWithUndo: (FoodEntry) -> Unit = { entry ->
        viewModel.delete(entry)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "Deleted ${entry.name}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CaloriePro") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = "History")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            // The content-slot ExtendedFloatingActionButton keeps the label in the
            // accessibility tree; the text/icon overload clears the text's semantics.
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { onScanLabel(state.date) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(painterResource(R.drawable.ic_camera), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Scan label")
                }
                ExtendedFloatingActionButton(onClick = { onAddFood(state.date) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Add food")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 176.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "date") {
                DateSwitcher(
                    date = state.date,
                    today = today,
                    onChange = viewModel::selectDate,
                    onPick = { showDatePicker = true },
                )
            }
            item(key = "summary") {
                SummaryCard(totals = state.totals, goals = state.goals)
            }
            if (state.loaded && state.entries.isEmpty()) {
                item(key = "empty") { EmptyDay() }
            }
            for (meal in Meal.entries) {
                val mealEntries = state.entries.filter { it.meal == meal }
                if (mealEntries.isEmpty()) continue
                item(key = "meal-${meal.name}") {
                    MealHeader(meal = meal, calories = mealEntries.sumTotals().calories)
                }
                items(mealEntries, key = { it.id }) { entry ->
                    SwipeToDelete(onDelete = { deleteWithUndo(entry) }) {
                        EntryRow(entry = entry, onClick = { onEditEntry(entry.id) })
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.date.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.selectDate(it.utcMillisToDate()) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateSwitcher(
    date: LocalDate,
    today: LocalDate,
    onChange: (LocalDate) -> Unit,
    onPick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { onChange(date.minusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
            }
            TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(date.friendlyName(today), style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { onChange(date.plusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
            }
        }
        if (date != today) {
            TextButton(onClick = { onChange(today) }) { Text("Back to today") }
        }
    }
}

@Composable
private fun SummaryCard(totals: NutrientTotals, goals: Goals) {
    val eaten = totals.calories
    val remaining = goals.calories - eaten
    val over = remaining < 0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    eaten.formatWhole(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    " / ${goals.calories.formatWhole()} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                if (over) "${(-remaining).formatWhole()} kcal over your goal" else "${remaining.formatWhole()} kcal left",
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearProgressIndicator(
                progress = { if (goals.calories > 0) (eaten / goals.calories).toFloat().coerceIn(0f, 1f) else 0f },
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
            val overLimits = goals.overLimits(totals)
            if (overLimits.isNotEmpty()) {
                Text(
                    "Over your daily limit for ${overLimits.joinLabels()}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            for (pair in Nutrient.entries.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (n in pair) {
                        NutrientMeter(n, amount = n.amountIn(totals), limit = goals.limit(n), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One nutrient's total against its daily limit (or target, for minimums). */
@Composable
private fun NutrientMeter(nutrient: Nutrient, amount: Double, limit: Int, modifier: Modifier = Modifier) {
    val over = nutrient.isOver(amount, limit)
    val met = nutrient.isMet(amount, limit)
    val shown = nutrient.rounded(amount).let { if (nutrient.unit == "mg") it.formatWhole() else it.formatAmount() }
    Column(
        modifier
            .semantics(mergeDescendants = true) {}
            .testTag("meter_${nutrient.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(nutrient.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "$shown / ${limit.formatWhole()} ${nutrient.unit}${if (met) " ✓" else ""}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
        )
        LinearProgressIndicator(
            progress = { if (limit > 0) (amount / limit).toFloat().coerceIn(0f, 1f) else 0f },
            color = when {
                over -> MaterialTheme.colorScheme.error
                nutrient.kind == LimitKind.MINIMUM && !met -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surface,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
    }
}

/** "sodium", "fat and sodium", "fat, sugar and sodium". */
private fun List<Nutrient>.joinLabels(): String {
    val names = map { it.label.lowercase() }
    return if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()
}

@Composable
private fun EmptyDay() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Nothing logged yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap Scan label to read the nutrition facts off a package with your camera, " +
                    "or Add food to type them in.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MealHeader(meal: Meal, calories: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(meal.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("${calories.formatWhole()} kcal", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit) {
    val totals = entry.totals
    val details = buildList {
        val servings = if (entry.servings == 1.0) "1 serving" else "${entry.servings.formatAmount()} servings"
        add(entry.servingSize?.let { "$servings of $it" } ?: servings)
        if (entry.proteinG != null) add("${totals.proteinG.formatAmount()} g protein")
        if (entry.caffeineMg != null && entry.caffeineMg > 0) add("${totals.caffeineMg.formatWhole()} mg caffeine")
    }.joinToString(" · ")

    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Text(
                "${totals.calories.formatWhole()} kcal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}

/** Material date pickers work in UTC midnight milliseconds. */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
