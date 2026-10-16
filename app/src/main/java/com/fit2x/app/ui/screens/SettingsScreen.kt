package com.fit2x.app.ui.screens

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fit2x.app.R
import com.fit2x.app.appGraph
import com.fit2x.app.data.auth.AuthState
import com.fit2x.app.data.auth.AuthViewModel
import com.fit2x.app.notifications.ReminderSettingsViewModel
import com.fit2x.app.notifications.ReminderType
import com.fit2x.app.notifications.rememberNotificationPermissionRequest
import com.fit2x.app.ui.components.ThemeCard
import com.fit2x.app.ui.theme.Fit2xTheme
import kotlinx.coroutines.launch

/**
 * Settings — backed by real ViewModels.
 *
 * - Profile card: [AuthViewModel] (sign in/out, user identity)
 * - Theme picker: persisted to the profile via [onThemeSelected]
 * - Reminders: [ReminderSettingsViewModel] (DataStore + WorkManager)
 * - Goals: read from the profile
 */
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel = viewModel(),
    reminderViewModel: ReminderSettingsViewModel = viewModel(),
    currentTheme: Fit2xTheme = Fit2xTheme.Electric,
    onThemeSelected: (Fit2xTheme) -> Unit = {},
    onSignIn: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    val authState by authViewModel.authState.collectAsState()
    val reminders by reminderViewModel.reminders.collectAsState()
    // Remember the flow so recompositions don't restart Room collection.
    val profile by remember {
        context.appGraph().database.userProfileDao().observeProfile()
    }.collectAsState(initial = null)

    val isSignedIn = authState is AuthState.SignedIn
    val userName = (authState as? AuthState.SignedIn)?.user?.name
        ?: profile?.name.orEmpty()
    val userEmail = (authState as? AuthState.SignedIn)?.user?.email
        ?: profile?.email.orEmpty()
    val userInitial = userName.firstOrNull()?.uppercase() ?: "?"

    // C1: POST_NOTIFICATIONS is denied by default on Android 13+. Without it,
    // reminders silently never fire — so ask once when Settings is opened.
    val (notifLauncher, notifGranted) = rememberNotificationPermissionRequest()
    var notifAutoPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!notifGranted && !notifAutoPrompted) {
            notifAutoPrompted = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // M4: calorie goal is editable via a dialog on the Goals card.
    var showCalorieDialog by remember { mutableStateOf(false) }
    val settingsScope = rememberCoroutineScope()

    // H1: which reminder's time is being edited (null = no picker open).
    var timePickerFor by remember { mutableStateOf<ReminderType?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_settings).uppercase(),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            ),
            color = scheme.onBackground
        )

        // Profile / auth card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
        ) {
            if (isSignedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInitial,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = scheme.onPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName.ifBlank { "FIT2X User" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = scheme.onSurface
                        )
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { authViewModel.signOut() }) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_profile),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = scheme.onSurface
                    )
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.sign_in_google),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // Theme picker
        Text(
            text = stringResource(R.string.theme_title).uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            ),
            color = scheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeCard(
                theme = Fit2xTheme.Electric,
                selected = currentTheme.id == Fit2xTheme.Electric.id,
                onClick = { onThemeSelected(Fit2xTheme.Electric) },
                modifier = Modifier.weight(1f)
            )
            ThemeCard(
                theme = Fit2xTheme.Ember,
                selected = currentTheme.id == Fit2xTheme.Ember.id,
                onClick = { onThemeSelected(Fit2xTheme.Ember) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeCard(
                theme = Fit2xTheme.Noir,
                selected = currentTheme.id == Fit2xTheme.Noir.id,
                onClick = { onThemeSelected(Fit2xTheme.Noir) },
                modifier = Modifier.weight(1f)
            )
            ThemeCard(
                theme = Fit2xTheme.Earth,
                selected = currentTheme.id == Fit2xTheme.Earth.id,
                onClick = { onThemeSelected(Fit2xTheme.Earth) },
                modifier = Modifier.weight(1f)
            )
        }

        // Reminders — real DataStore-backed toggles.
        // C1: persistent card while the notification permission is denied.
        if (!notifGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.errorContainer),
                border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        tint = scheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notifications are off",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = scheme.onErrorContainer
                        )
                        Text(
                            text = "Reminders can't fire until you allow notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onErrorContainer
                        )
                    }
                    Button(
                        onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.error,
                            contentColor = scheme.onError
                        )
                    ) {
                        Text("Enable")
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.reminders_title).uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            ),
            color = scheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                reminders.forEach { reminder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                reminderViewModel.setEnabled(
                                    reminder.type,
                                    !reminder.enabled
                                )
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = if (reminder.enabled) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reminderLabel(reminder.type),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = scheme.onSurface
                            )
                            Text(
                                text = formatTimeLabel(reminder.hour, reminder.minute),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.primary,
                                modifier = Modifier.clickable { timePickerFor = reminder.type }
                            )
                        }
                        Switch(
                            checked = reminder.enabled,
                            onCheckedChange = {
                                reminderViewModel.setEnabled(reminder.type, it)
                            }
                        )
                    }
                }
            }
        }

        // Goals
        Text(
            text = "GOALS",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            ),
            color = scheme.onSurfaceVariant
        )
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
                GoalRow(
                    label = stringResource(R.string.step_goal_title),
                    value = "%,d".format(profile?.stepGoal ?: 10_000)
                )
                GoalRow(
                    label = stringResource(R.string.protein_goal_title),
                    value = "${profile?.proteinGoalGrams ?: 70}g"
                )
                GoalRow(
                    label = stringResource(R.string.calorie_goal_title),
                    value = "%,d".format(profile?.calorieGoal ?: 2_200),
                    onClick = { showCalorieDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.app_version),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // H1: Material3 time picker for editing a reminder's time.
        timePickerFor?.let { type ->
            val current = reminders.firstOrNull { it.type == type }
            if (current != null) {
                ReminderTimePickerDialog(
                    initialHour = current.hour,
                    initialMinute = current.minute,
                    onDismiss = { timePickerFor = null },
                    onConfirm = { h, m ->
                        reminderViewModel.setTime(type, h, m)
                        timePickerFor = null
                    }
                )
            }
        }

        // M4: calorie-goal editor.
        if (showCalorieDialog) {
            var input by remember { mutableStateOf((profile?.calorieGoal ?: 2_200).toString()) }
            AlertDialog(
                onDismissRequest = { showCalorieDialog = false },
                title = { Text(text = stringResource(R.string.calorie_goal_title)) },
                text = {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.all(Char::isDigit)) input = it },
                        label = { Text(text = "kcal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        input.toIntOrNull()?.let { kcal ->
                            val clamped = kcal.coerceIn(1_000, 6_000)
                            settingsScope.launch {
                                try {
                                    context.appGraph().database.userProfileDao()
                                        .updateCalorieGoal(clamped)
                                } catch (_: Exception) {
                                    // Best-effort.
                                }
                            }
                        }
                        showCalorieDialog = false
                    }) { Text(text = stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCalorieDialog = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun reminderLabel(type: ReminderType): String = stringResource(
    when (type) {
        ReminderType.GYM -> R.string.reminder_gym
        ReminderType.LUNCH -> R.string.reminder_lunch
        ReminderType.SNACK -> R.string.reminder_snack
        ReminderType.DINNER -> R.string.reminder_dinner
    }
)

@Composable
private fun GoalRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = scheme.primary
        )
    }
}

/** 7, 30 -> "7:30 AM" */
private fun formatTimeLabel(hour: Int, minute: Int): String {
    val m = "%02d".format(minute)
    return when {
        hour == 0 -> "12:$m AM"
        hour < 12 -> "$hour:$m AM"
        hour == 12 -> "12:$m PM"
        else -> "${hour - 12}:$m PM"
    }
}

/** H1: Material3 time picker wrapped in an AlertDialog, for editing reminder times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("Set time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = state)
        }
    )
}
