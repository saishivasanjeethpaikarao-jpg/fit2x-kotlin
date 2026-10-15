package com.fit2x.app.notifications

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
 * Creates the channel, shows notifications, and owns the WorkManager schedule.
 *
 * Scheduling model: one unique 24-hour PeriodicWorkRequest per reminder type,
 * with an initial delay computed to the next occurrence of the reminder's time.
 * WorkManager survives app kills; [BootReceiver] re-schedules after reboot.
 * Times are user-editable — changing a time re-enqueues that reminder's work.
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
                enqueueReminder(context, type, prefs.hour, prefs.minute)
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
            enqueueReminder(context, type, prefs.hour, prefs.minute)
        } else {
            cancelReminder(context, type)
        }
    }

    /** Cancel every reminder. */
    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        ReminderType.entries.forEach { wm.cancelUniqueWork(uniqueWorkName(it)) }
    }

    private fun enqueueReminder(context: Context, type: ReminderType, hour: Int, minute: Int) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext(hour, minute), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_TYPE to type.key))
            .addTag("fit2x_reminder")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueWorkName(type),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelReminder(context: Context, type: ReminderType) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(type))
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
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
        return next.timeInMillis - now.timeInMillis
    }
}
