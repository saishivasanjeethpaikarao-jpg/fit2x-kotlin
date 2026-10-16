package com.fit2x.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fit2x.app.R
import com.fit2x.app.appGraph
import com.fit2x.app.health.HealthConnectManager
import com.fit2x.app.health.HealthViewModel
import com.fit2x.app.ui.components.HealthPermissionCard
import com.fit2x.app.ui.components.HealthPermissionState
import com.fit2x.app.ui.components.ProgressRing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Step tracking screen — backed by [HealthViewModel] (Health Connect).
 *
 * - Permission card when Health Connect isn't connected (with Play Store fallback).
 * - Live today count, 7-day bar chart, editable goal, manual entry fallback.
 */
@Composable
fun StepsScreen(
    healthViewModel: HealthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // Structured scope for one-shot writes (step-goal stepper).
    val scope = rememberCoroutineScope()

    val todaySteps by healthViewModel.todaySteps.collectAsState()
    val weeklySteps by healthViewModel.weeklySteps.collectAsState()
    val permissionGranted by healthViewModel.permissionGranted.collectAsState()
    val hcAvailable by healthViewModel.healthConnectAvailable.collectAsState()
    val hcInstalled by healthViewModel.healthConnectInstalled.collectAsState()

    // Step goal lives on the profile so Today + Steps stay consistent.
    // Remember the flow so recompositions don't restart Room collection.
    val stepGoal by remember {
        context.appGraph().database.userProfileDao()
            .observeProfile()
            .map { it?.stepGoal ?: 10_000 }
    }.collectAsState(initial = 10_000)

    var showManualDialog by remember { mutableStateOf(false) }

    // Permission launcher for the Health Connect consent screen.
    val permissionLauncher = rememberLauncherForActivityResult(
        HealthConnectManager.permissionContract()
    ) {
        // Result set is ignored — re-check grants directly.
        healthViewModel.refreshAvailability()
    }

    // Re-check on every entry (covers the user granting in Settings).
    LaunchedEffect(Unit) { healthViewModel.refreshAvailability() }

    val today = LocalDate.now()
    val weekDates = (6 downTo 0).map { today.minusDays(it.toLong()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero: today's count
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.steps_today_title).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        ),
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "%,d".format(todaySteps),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        ),
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.steps_goal_title) + ": %,d".format(stepGoal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                val progress = if (stepGoal > 0) todaySteps.toFloat() / stepGoal else 0f
                ProgressRing(
                    progress = progress,
                    centerText = "${(progress * 100).toInt()}%",
                    size = 104.dp
                )
            }
        }

        // Health Connect permission / install card
        if (!permissionGranted) {
            val cardState = if (!hcInstalled) HealthPermissionState.NotInstalled
            else HealthPermissionState.Denied
            HealthPermissionCard(
                state = cardState,
                onGrantAccess = {
                    if (hcAvailable) {
                        HealthConnectManager.requestPermissions(permissionLauncher)
                    } else {
                        healthViewModel.refreshAvailability()
                    }
                },
                onInstall = { healthViewModel.installHealthConnect() },
                onEnterManually = { showManualDialog = true }
            )
        }

        // Weekly bar chart
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
                    text = stringResource(R.string.steps_this_week).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    ),
                    color = scheme.onSurfaceVariant
                )
                WeeklyBarChart(
                    days = weekDates,
                    steps = weeklySteps.map { it?.toInt() ?: 0 },
                    goal = stepGoal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }

        // Goal stepper
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.steps_goal_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = scheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { updateStepGoal(scope, context, stepGoal - 1_000) }) {
                        Icon(Icons.Filled.Remove, contentDescription = null, tint = scheme.primary)
                    }
                    Text(
                        text = "%,d".format(stepGoal),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = scheme.onSurface,
                        modifier = Modifier.width(90.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { updateStepGoal(scope, context, stepGoal + 1_000) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = scheme.primary)
                    }
                }
            }
        }

        // Manual entry
        OutlinedButton(
            onClick = { showManualDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.log_steps_manually),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showManualDialog) {
        var input by remember {
            mutableStateOf(todaySteps.takeIf { it > 0 }?.toString() ?: "")
        }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text(text = stringResource(R.string.log_steps_manually)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.all(Char::isDigit)) input = it },
                    label = { Text(stringResource(R.string.enter_steps)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    input.toIntOrNull()?.let { healthViewModel.setManualSteps(it) }
                    showManualDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Persists the step goal on the profile (Today + Steps read the same value).
 * Single atomic UPDATE — no read-modify-write, so rapid stepper taps can't
 * lose increments. Launched in the composable's structured scope.
 */
private fun updateStepGoal(
    scope: CoroutineScope,
    context: android.content.Context,
    goal: Int
) {
    val clamped = goal.coerceIn(1_000, 50_000)
    val dao = context.appGraph().database.userProfileDao()
    scope.launch {
        try {
            dao.updateStepGoal(clamped)
        } catch (_: Exception) {
            // Best-effort; the UI already reflects the tapped value.
        }
    }
}

@Composable
private fun WeeklyBarChart(
    days: List<LocalDate>,
    steps: List<Int>,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val maxSteps = (steps.maxOrNull() ?: 0).coerceAtLeast(goal).toFloat()
    val barColor = scheme.primary
    val trackColor = scheme.surfaceVariant
    val labelColor = scheme.onSurfaceVariant

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (days.isEmpty()) return@Canvas
            val barCount = days.size
            val gap = size.width / (barCount * 3f)
            val barWidth = (size.width - gap * (barCount + 1)) / barCount
            days.forEachIndexed { i, day ->
                val daySteps = steps.getOrNull(i) ?: 0
                val x = gap + i * (barWidth + gap)
                val frac = (daySteps.toFloat() / maxSteps).coerceIn(0.02f, 1f)
                val barHeight = size.height * frac
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
                drawRoundRect(
                    color = if (day == today) barColor else barColor.copy(alpha = 0.55f),
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                val isToday = day == today
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                            .uppercase().take(2),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isToday) scheme.primary else labelColor
                    )
                }
            }
        }
    }
}
