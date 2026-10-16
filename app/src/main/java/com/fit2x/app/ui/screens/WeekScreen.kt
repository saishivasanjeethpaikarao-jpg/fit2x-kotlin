package com.fit2x.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fit2x.app.R
import com.fit2x.app.workout.WORKOUT_TEMPLATES
import com.fit2x.app.workout.WorkoutDay
import com.fit2x.app.workout.WorkoutViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Weekly schedule — backed by the real [WorkoutViewModel].
 *
 * - Template picker (Strength / Hypertrophy / Home / Recovery)
 * - 7-day list from the active template + per-day overrides
 * - Tap a day to swap its workout or set it to Rest
 */
@Composable
fun WeekScreen(
    workoutViewModel: WorkoutViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val weekSchedule by workoutViewModel.weekSchedule.collectAsState()
    val activeTemplate by workoutViewModel.activeTemplate.collectAsState()
    val today = LocalDate.now().dayOfWeek
    var editingDay by remember { mutableStateOf<WorkoutDay?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.week_title).uppercase(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = scheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.week_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }

        // Template picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WORKOUT_TEMPLATES.forEach { template ->
                val selected = template.id == activeTemplate
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { workoutViewModel.setTemplate(template.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) scheme.primary else scheme.surfaceVariant
                    ),
                    border = if (selected) null
                    else BorderStroke(1.dp, scheme.outline.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = template.name,
                        modifier = Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(weekSchedule, key = { it.dayOfWeek }) { workoutDay ->
                val day = runCatching { DayOfWeek.valueOf(workoutDay.dayOfWeek) }
                    .getOrDefault(DayOfWeek.MONDAY)
                val isToday = day == today
                val isRest = workoutDay.isRest
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingDay = workoutDay },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isToday) scheme.primaryContainer else scheme.surface
                    ),
                    border = BorderStroke(
                        width = if (isToday) 2.dp else 1.dp,
                        color = if (isToday) scheme.primary else scheme.outline.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isToday) scheme.primary else scheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                                    .uppercase().take(1),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isToday) scheme.onPrimary else scheme.onSurfaceVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isToday) scheme.onPrimaryContainer else scheme.onSurface
                            )
                            if (isToday) {
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = scheme.primary
                                )
                            } else if (workoutDay.subtitle.isNotBlank()) {
                                Text(
                                    text = workoutDay.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isRest -> scheme.surfaceVariant
                                    isToday -> scheme.primary
                                    else -> scheme.primaryContainer
                                }
                            )
                        ) {
                            Text(
                                text = workoutDay.title,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = when {
                                    isRest -> scheme.onSurfaceVariant
                                    isToday -> scheme.onPrimary
                                    else -> scheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit dialog: swap the tapped day's workout
    editingDay?.let { day ->
        // All unique workout options across templates + Rest.
        // Keyed on dayOfWeek so switching days rebuilds the list.
        val options = remember(day.dayOfWeek) {
            val seen = mutableSetOf<String>()
            buildList {
                add(
                    WorkoutDay(
                        dayOfWeek = day.dayOfWeek,
                        title = "Rest",
                        subtitle = "Recovery day. Light walk if you feel like it.",
                        isRest = true
                    )
                )
                WORKOUT_TEMPLATES.forEach { t ->
                    t.days.forEach { d ->
                        if (!d.isRest && seen.add(d.title)) {
                            add(d.copy(dayOfWeek = day.dayOfWeek))
                        }
                    }
                }
            }
        }
        AlertDialog(
            onDismissRequest = { editingDay = null },
            title = {
                Text(
                    text = runCatching { DayOfWeek.valueOf(day.dayOfWeek) }
                        .getOrDefault(DayOfWeek.MONDAY)
                        .getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " — pick workout"
                )
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(options, key = { it.title }) { option ->
                        val selected = option.title == day.title && option.isRest == day.isRest
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    workoutViewModel.updateDay(day.dayOfWeek, option)
                                    editingDay = null
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected)
                                    scheme.primaryContainer
                                else
                                    scheme.surfaceVariant
                            ),
                            border = if (selected) BorderStroke(
                                2.dp,
                                scheme.primary
                            ) else null
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = scheme.onSurface
                                )
                                if (option.subtitle.isNotBlank()) {
                                    Text(
                                        text = option.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingDay = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            dismissButton = {
                // Reset this day to the template default (clears any override).
                TextButton(
                    onClick = {
                        workoutViewModel.clearDayOverride(day.dayOfWeek)
                        editingDay = null
                    }
                ) {
                    Text(
                        text = "Reset day",
                        color = scheme.error
                    )
                }
            }
        )
    }
}
