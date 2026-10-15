package com.fit2x.app.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fit2x.app.R
import com.fit2x.app.appGraph
import com.fit2x.app.data.auth.AuthState
import com.fit2x.app.data.auth.AuthViewModel
import com.fit2x.app.notifications.ReminderSettingsViewModel
import com.fit2x.app.notifications.ReminderType
import com.fit2x.app.ui.components.ThemeCard
import com.fit2x.app.ui.theme.Fit2xTheme
import kotlinx.coroutines.flow.map

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
    val profile by context.appGraph().database.userProfileDao()
        .observeProfile()
        .map { it }
        .collectAsState(initial = null)

    val isSignedIn = authState is AuthState.SignedIn
    val userName = (authState as? AuthState.SignedIn)?.user?.name
        ?: profile?.name.orEmpty()
    val userEmail = (authState as? AuthState.SignedIn)?.user?.email
        ?: profile?.email.orEmpty()
    val userInitial = userName.firstOrNull()?.uppercase() ?: "?"

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

        // Reminders — real DataStore-backed toggles
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
                                color = scheme.onSurfaceVariant
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
private fun GoalRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
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
