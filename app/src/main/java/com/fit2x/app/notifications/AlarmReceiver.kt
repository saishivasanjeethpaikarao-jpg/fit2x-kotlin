package com.fit2x.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when an exact reminder alarm goes off.
 *
 * - Re-checks the DataStore toggle, so a reminder disabled after scheduling never fires.
 * - Shows the notification.
 * - Re-arms tomorrow's alarm for the same reminder type (exact alarms are one-shot).
 *
 * Declared in the manifest with `exported=false`. AlarmManager delivers via an
 * explicit PendingIntent, which is exempt from the Android 8+ implicit-broadcast ban.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMINDER = "com.fit2x.app.notifications.REMINDER_ALARM"
        const val EXTRA_TYPE = "reminder_type"
        private const val REQUEST_CODE_BASE = 7000

        /** Explicit PendingIntent for one reminder type; stable request code per type. */
        fun pendingIntent(context: Context, type: ReminderType): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_REMINDER
                putExtra(EXTRA_TYPE, type.key)
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BASE + type.ordinal,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun cancel(context: Context, type: ReminderType) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(context, type))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val type = intent.getStringExtra(EXTRA_TYPE)?.let(ReminderType::fromKey) ?: return
        val pending = goAsync()
        val job = SupervisorJob()
        CoroutineScope(job + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val prefs = ReminderSettings(appContext).prefsFlow(type).first()
                if (prefs.enabled) {
                    NotificationHelper.showNotification(appContext, type)
                }
                // Re-arm tomorrow — exact alarms fire once.
                NotificationHelper.scheduleExactAlarm(appContext, type, prefs.hour, prefs.minute)
            } catch (_: Exception) {
                // Best-effort: the next app launch re-syncs the whole schedule.
            } finally {
                pending.finish()
                job.cancel()
            }
        }
    }
}
