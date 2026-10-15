package com.fit2x.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fit2x.app.R
import com.fit2x.app.appGraph
import com.fit2x.app.ui.components.StatCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

// ---------------------------------------------------------------------------
// State + ViewModel — real data from Room (sessions + step entries).
// ---------------------------------------------------------------------------

data class StatsUiState(
    val totalWorkouts: Int = 0,
    val totalSteps: Long = 0,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    /** Last 14 days of step counts, oldest → newest. */
    val stepsHistory: List<Int> = List(14) { 0 },
    val records: List<Pair<String, String>> = listOf(
        "Most steps in a day" to "—",
        "Longest streak" to "—",
        "Most protein in a day" to "—"
    )
)

class StatsViewModel(
    private val database: com.fit2x.app.data.local.Fit2xDatabase
) : ViewModel() {
    val uiState: StateFlow<StatsUiState> =
        kotlinx.coroutines.flow.combine(
            database.workoutSessionDao().observeRecent(400),
            database.stepEntryDao().observeRecent(30),
            database.foodEntryDao().let { dao ->
                // Last 30 days of food entries for the protein record.
                // FoodEntryDao has no observeRecent; approximate via today flow.
                kotlinx.coroutines.flow.flowOf(emptyList<com.fit2x.app.data.local.FoodEntry>())
            }
        ) { sessions, stepEntries, _ ->
            val totalWorkouts = sessions.size
            val totalSteps = stepEntries.sumOf { it.steps.toLong() }

            // Current streak: consecutive completed days ending today/yesterday.
            val completedDates = sessions
                .filter { it.totalMoves > 0 && it.completedMoves >= it.totalMoves }
                .map { it.date }
                .toSet()
            var currentStreak = 0
            var day = LocalDate.now()
            if (!completedDates.contains(day.toString())) {
                day = day.minusDays(1)
            }
            while (completedDates.contains(day.toString())) {
                currentStreak++
                day = day.minusDays(1)
            }
            // Best streak: longest run anywhere in history.
            var bestStreak = 0
            var run = 0
            var cursor = completedDates.minOrNull()?.let { LocalDate.parse(it) }
            val end = LocalDate.now()
            while (cursor != null && !cursor.isAfter(end)) {
                if (completedDates.contains(cursor.toString())) {
                    run++
                    bestStreak = maxOf(bestStreak, run)
                } else {
                    run = 0
                }
                cursor = cursor.plusDays(1)
            }

            // 14-day steps history, oldest → newest.
            val stepsByDate = stepEntries.associate { it.date to it.steps }
            val stepsHistory = (13 downTo 0).map { offset ->
                val d = LocalDate.now().minusDays(offset.toLong()).toString()
                stepsByDate[d] ?: 0
            }

            val bestDay = stepEntries.maxByOrNull { it.steps }

            StatsUiState(
                totalWorkouts = totalWorkouts,
                totalSteps = totalSteps,
                bestStreak = bestStreak,
                currentStreak = currentStreak,
                stepsHistory = stepsHistory,
                records = listOf(
                    "Most steps in a day" to (bestDay?.let { "%,d".format(it.steps) } ?: "—"),
                    "Longest streak" to if (bestStreak > 0) "$bestStreak days" else "—",
                    "Workouts logged" to "$totalWorkouts"
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState()
        )
}

class StatsViewModelFactory(
    private val database: com.fit2x.app.data.local.Fit2xDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StatsViewModel::class.java))
        return StatsViewModel(database) as T
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun StatsScreen(
    healthViewModel: com.fit2x.app.health.HealthViewModel = viewModel(),
    workoutViewModel: com.fit2x.app.workout.WorkoutViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val statsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModelFactory(context.appGraph().database)
    )
    val state by statsViewModel.uiState.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val hasData = state.stepsHistory.any { it > 0 } || state.totalWorkouts > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_title).uppercase(),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            ),
            color = scheme.onBackground
        )

        // Top stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                value = state.totalWorkouts.toString(),
                label = stringResource(R.string.stats_workouts).uppercase(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = formatCompact(state.totalSteps),
                label = stringResource(R.string.stats_total_steps).uppercase(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                value = state.bestStreak.toString(),
                label = stringResource(R.string.stats_best_streak).uppercase(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = state.currentStreak.toString(),
                label = stringResource(R.string.stat_streak),
                modifier = Modifier.weight(1f)
            )
        }

        // 14-day steps line chart
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.steps_14_days).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    ),
                    color = scheme.onSurfaceVariant
                )
                if (hasData) {
                    StepsLineChart(
                        values = state.stepsHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.no_data_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        }

        // Personal records
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.personal_records).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    ),
                    color = scheme.onSurfaceVariant
                )
                state.records.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                tint = scheme.primary
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface
                            )
                        }
                        Text(
                            text = value,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = scheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun formatCompact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun StepsLineChart(values: List<Int>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val lineColor = scheme.primary
    val fillColor = scheme.primary.copy(alpha = 0.15f)
    val dotColor = scheme.primary

    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val n = values.size
        val stepX = size.width / (n - 1)

        val points = values.mapIndexed { i, v ->
            Offset(
                x = i * stepX,
                y = size.height - (v / max) * (size.height - 16.dp.toPx()) - 8.dp.toPx()
            )
        }

        // Fill under curve
        val fillPath = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(fillPath, fillColor)

        // Line
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }
        drawPath(linePath, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

        // Last-point dot
        drawCircle(dotColor, radius = 5.dp.toPx(), center = points.last())
        drawCircle(
            scheme.background,
            radius = 2.5.dp.toPx(),
            center = points.last()
        )
    }
}
