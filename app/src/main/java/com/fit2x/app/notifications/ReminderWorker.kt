package com.fit2x.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

/**
 * Fires one reminder notification. The reminder type comes in via input data
 * ([KEY_TYPE]); the worker re-checks the DataStore toggle so a reminder that
 * was disabled after scheduling never fires.
 *
 * Runs on WorkManager, so it executes even when the app is closed.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TYPE = "reminder_type"
    }

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_TYPE) ?: return Result.failure()
        val type = ReminderType.fromKey(key) ?: return Result.failure()

        val settings = ReminderSettings(applicationContext)
        val prefs = settings.prefsFlow(type).first()
        if (!prefs.enabled) {
            // Disabled after scheduling — skip silently and keep the schedule.
            return Result.success()
        }

        NotificationHelper.showNotification(applicationContext, type)
        return Result.success()
    }
}
