package com.fit2x.app.notifications

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row in the Settings reminders section. */
data class ReminderUiState(
    val type: ReminderType,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
) {
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}

/**
 * Settings-screen hook for reminders. The UI agent binds toggles and time
 * pickers to [reminders], [setEnabled] and [setTime]; each change is persisted
 * to DataStore and the WorkManager schedule is updated immediately.
 */
class ReminderSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val settings = ReminderSettings(context)

    val reminders: StateFlow<List<ReminderUiState>> =
        combine(ReminderType.entries.map { settings.prefsFlow(it) }) { prefsArray ->
            prefsArray.map { prefs ->
                ReminderUiState(
                    type = prefs.type,
                    enabled = prefs.enabled,
                    hour = prefs.hour,
                    minute = prefs.minute
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReminderType.entries.map {
                ReminderUiState(it, true, it.defaultHour, it.defaultMinute)
            }
        )

    fun setEnabled(type: ReminderType, enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(type, enabled)
            NotificationHelper.reschedule(context, type)
        }
    }

    fun setTime(type: ReminderType, hour: Int, minute: Int) {
        viewModelScope.launch {
            settings.setTime(type, hour, minute)
            NotificationHelper.reschedule(context, type)
        }
    }

    /** Call on first launch (MainActivity) so reminders can actually fire on Android 13+. */
    fun hasNotificationPermission(): Boolean =
        NotificationHelper.hasNotificationPermission(context)
}

/**
 * Launcher the UI agent can drop into a first-launch / Settings flow to request
 * POST_NOTIFICATIONS on Android 13+. Returns the launcher and the current grant state.
 *
 * Usage:
 * ```
 * val (launcher, granted) = rememberNotificationPermissionRequest { /* on result */ }
 * LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
 * ```
 */
@Composable
fun rememberNotificationPermissionRequest(
    onResult: (granted: Boolean) -> Unit = {}
): Pair<androidx.activity.result.ActivityResultLauncher<String>, Boolean> {
    var granted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
            else ContextCompat.checkSelfPermission(
                androidx.compose.ui.platform.LocalContext.current,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        onResult(isGranted)
    }
    return launcher to granted
}
