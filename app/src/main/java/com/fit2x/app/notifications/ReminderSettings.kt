package com.fit2x.app.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_prefs")

/** Snapshot of one reminder's user-editable settings. */
data class ReminderPrefs(
    val type: ReminderType,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
) {
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}

/**
 * DataStore-backed settings for the four daily reminders.
 * Each reminder has an on/off toggle and an editable time.
 */
class ReminderSettings(private val context: Context) {

    private fun enabledKey(type: ReminderType) = booleanPreferencesKey("reminder_${type.key}_enabled")
    private fun hourKey(type: ReminderType) = intPreferencesKey("reminder_${type.key}_hour")
    private fun minuteKey(type: ReminderType) = intPreferencesKey("reminder_${type.key}_minute")

    fun prefsFlow(type: ReminderType): Flow<ReminderPrefs> =
        context.reminderDataStore.data.map { prefs ->
            ReminderPrefs(
                type = type,
                enabled = prefs[enabledKey(type)] ?: true,
                hour = prefs[hourKey(type)] ?: type.defaultHour,
                minute = prefs[minuteKey(type)] ?: type.defaultMinute
            )
        }

    suspend fun setEnabled(type: ReminderType, enabled: Boolean) {
        context.reminderDataStore.edit { it[enabledKey(type)] = enabled }
    }

    suspend fun setTime(type: ReminderType, hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59) { "Invalid time $hour:$minute" }
        context.reminderDataStore.edit {
            it[hourKey(type)] = hour
            it[minuteKey(type)] = minute
        }
    }
}
