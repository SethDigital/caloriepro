package com.sethdigital.caloriepro.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethdigital.caloriepro.data.DailySummary
import com.sethdigital.caloriepro.data.date
import com.sethdigital.caloriepro.ui.formatAmount
import com.sethdigital.caloriepro.ui.formatWhole
import com.sethdigital.caloriepro.ui.friendlyName
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "week") {
                WeekCard(state)
            }
            if (state.loaded && state.days.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Days you log food on will show up here.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            items(state.days, key = { it.epochDay }) { day ->
                DayRow(day, today, goal = state.goals.calories, onClick = { onOpenDay(day.date) })
            }
        }
    }
}

@Composable
private fun WeekCard(state: HistoryUiState) {
    val goal = state.goals.calories.toDouble()
    val barColor = MaterialTheme.colorScheme.primary
    val overColor = MaterialTheme.colorScheme.error
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            Text(
                state.weekAverage?.let { "Average ${it.formatWhole()} kcal per logged day" }
                    ?: "Nothing logged this week yet",
                style = MaterialTheme.typography.bodyMedium,
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val top = max(goal, state.week.maxOfOrNull { it.calories } ?: 0.0).coerceAtLeast(1.0) * 1.15
                val slot = size.width / state.week.size.coerceAtLeast(1)
                val barWidth = slot * 0.55f
                state.week.forEachIndexed { i, bar ->
                    val h = (bar.calories / top * size.height).toFloat()
                    if (h > 0f) {
                        drawRoundRect(
                            color = if (bar.calories > goal) overColor else barColor,
                            topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                        )
                    }
                }
                val goalY = (size.height - goal / top * size.height).toFloat()
                drawLine(
                    color = goalColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                for (bar in state.week) {
                    Text(
                        bar.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Dashed line: your ${state.goals.calories.formatWhole()} kcal goal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayRow(day: DailySummary, today: LocalDate, goal: Int, onClick: () -> Unit) {
    val details = buildList {
        add(if (day.entryCount == 1) "1 item" else "${day.entryCount} items")
        add("${day.proteinG.formatAmount()} g protein")
        if (day.caffeineMg > 0) add("${day.caffeineMg.formatWhole()} mg caffeine")
    }.joinToString(" · ")

    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(day.date.friendlyName(today)) },
            supportingContent = { Text(details) },
            trailingContent = {
                Text(
                    "${day.calories.formatWhole()} kcal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (day.calories > goal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}
