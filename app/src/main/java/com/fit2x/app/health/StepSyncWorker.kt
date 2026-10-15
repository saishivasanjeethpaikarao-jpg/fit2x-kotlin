package com.fit2x.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fit2x.app.appGraph
import com.fit2x.app.data.local.StepEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Background worker that syncs today's step count from Health Connect
 * into the local Room database every 15 minutes.
 *
 * Skips silently when Health Connect is unavailable or permission
 * has not been granted — never crashes, never nags.
 */
class StepSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        // Bail out quietly if Health Connect isn't usable.
        if (!HealthConnectManager.isAvailable(context)) return Result.success()

        val client = HealthConnectClient.getOrCreate(context)
        if (!HealthConnectManager.hasPermissions(client)) return Result.success()

        val steps = HealthConnectManager.getTodaySteps(client) ?: return Result.success()

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        // Single shared database via AppGraph — never build a second instance.
        val stepDao = context.appGraph().database.stepEntryDao()

        val existing = stepDao.observeDay(today).first()
        // Preserve manual overrides and the user's goal.
        val manual = existing?.manualOverride == true
        val goal = existing?.goal ?: 10_000
        if (!manual) {
            stepDao.upsert(
                StepEntry(
                    date = today,
                    steps = steps.toInt(),
                    goal = goal,
                    manualOverride = false
                )
            )
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "fit2x-step-sync"

        /** Schedules (or keeps) the 15-minute periodic sync. Idempotent. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepSyncWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancels the periodic sync (e.g. on sign-out). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
