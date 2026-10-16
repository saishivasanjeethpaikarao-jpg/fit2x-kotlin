package com.fit2x.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fit2x.app.MainActivity
import com.fit2x.app.R
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Creates the channel, shows notifications, and owns the reminder schedule.
 *
 * Scheduling model: one exact [AlarmManager] alarm per reminder type, fired by
 * [AlarmReceiver], which re-arms the next day's alarm after each firing. Exact
 * alarms survive Doze and fire at the chosen wall-clock time. When exact alarms
 * are unavailable (API 31+ without the user grant in system Settings), the
 * schedule falls back to one 24-hour [androidx.work.PeriodicWorkRequest] per
 * reminder via [ReminderWorker].
 *
 * [BootReceiver] re-registers the schedule after reboot (alarms don't survive it).
 * Times are user-editable — changing a time re-schedules that reminder immediately.
 */
object NotificationHelper {

    const val CHANNEL_ID = "fit2x_reminders"
    private const val CHANNEL_NAME = "FIT2X reminders"

    private fun uniqueWorkName(type: ReminderType) = "fit2x_reminder_${type.key}"

    // ---------- Channel & permission ----------

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Gym, meal and snack reminders"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ---------- Showing ----------

    fun showNotification(context: Context, type: ReminderType) {
        showNotification(context, type.title, type.body, notificationId = 1001 + type.ordinal)
    }

    fun showNotification(context: Context, title: String, body: String, notificationId: Int = 1000) {
        createChannel(context)
        if (!hasNotificationPermission(context)) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    // ---------- Scheduling ----------

    /** Schedule (or re-schedule) all enabled reminders. Reads times from DataStore. */
    suspend fun scheduleAll(context: Context) {
        val settings = ReminderSettings(context)
        ReminderType.entries.forEach { type ->
            val prefs = settings.prefsFlow(type).first()
            if (prefs.enabled) {
                scheduleReminder(context, type, prefs.hour, prefs.minute)
            } else {
                cancelReminder(context, type)
            }
        }
    }

    /** Re-schedule a single reminder (call after the user edits its time or toggles it). */
    suspend fun reschedule(context: Context, type: ReminderType) {
        val settings = ReminderSettings(context)
        val prefs = settings.prefsFlow(type).first()
        if (prefs.enabled) {
            scheduleReminder(context, type, prefs.hour, prefs.minute)
        } else {
            cancelReminder(context, type)
        }
    }

    /** Cancel every reminder — exact alarms and any WorkManager fallback work. */
    fun cancelAll(context: Context) {
        ReminderType.entries.forEach { cancelReminder(context, it) }
    }

    /**
     * Primary path: exact alarm so the reminder fires at the chosen wall-clock
     * time even under Doze. Falls back to WorkManager when exact alarms can't
     * be scheduled (API 31+ where the user hasn't granted them in system Settings).
     */
    fun scheduleReminder(context: Context, type: ReminderType, hour: Int, minute: Int) {
        if (canUseExactAlarms(context)) {
            scheduleExactAlarm(context, type, hour, minute)
        } else {
            enqueueReminderWork(context, type, hour, minute)
        }
    }

    /** True when [AlarmManager.setExactAndAllowWhileIdle] may be used. */
    fun canUseExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /**
     * One-shot exact alarm for the next occurrence of (hour, minute).
     * [AlarmReceiver] re-arms the following day after firing.
     */
    fun scheduleExactAlarm(context: Context, type: ReminderType, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            enqueueReminderWork(context, type, hour, minute)
            return
        }
        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerMillis(hour, minute),
                AlarmReceiver.pendingIntent(context, type)
            )
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call — fall back.
            enqueueReminderWork(context, type, hour, minute)
        }
    }

    private fun cancelReminder(context: Context, type: ReminderType) {
        // Cancel both paths: the exact alarm and the WorkManager fallback.
        AlarmReceiver.cancel(context, type)
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(type))
    }

    /**
     * Fallback path when exact alarms are unavailable: one 24h periodic worker.
     * Uses CANCEL_AND_REENQUEUE so a changed time actually re-anchors the schedule.
     */
    private fun enqueueReminderWork(context: Context, type: ReminderType, hour: Int, minute: Int) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext(hour, minute), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_TYPE to type.key))
            .addTag("fit2x_reminder")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueWorkName(type),
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long =
        nextTriggerMillis(hour, minute) - System.currentTimeMillis()

    /** Absolute wall-clock millis of the next occurrence of (hour, minute). */
    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }
}
