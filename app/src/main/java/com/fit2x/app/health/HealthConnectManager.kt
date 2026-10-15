package com.fit2x.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Central manager for all Health Connect interactions.
 * Handles availability, permissions, and step aggregation.
 */
object HealthConnectManager {

    /** The set of permissions FIT2X needs. */
    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    /** Play Store deep-link for installing Health Connect. */
    private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

    // -----------------------------------------------------------------------
    // Availability
    // -----------------------------------------------------------------------

    /** True when Health Connect is installed and the SDK can be used. */
    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** True when Health Connect is not installed at all (user needs Play Store). */
    fun isNotInstalled(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE

    /** Opens the Play Store page for Health Connect. */
    fun openPlayStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            // Fallback: browser link
            val web = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(web) }
        }
    }

    // -----------------------------------------------------------------------
    // Permissions
    // -----------------------------------------------------------------------

    /** True when READ_STEPS has been granted. */
    suspend fun hasPermissions(client: HealthConnectClient): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(PERMISSIONS)
    }

    /**
     * The ActivityResultContract for the Health Connect permission screen.
     * Use with rememberLauncherForActivityResult in Compose:
     *
     *   val launcher = rememberLauncherForActivityResult(
     *       HealthConnectManager.permissionContract()
     *   ) { granted -> viewModel.refreshAvailability() }
     *   ...
     *   HealthConnectManager.requestPermissions(launcher)
     */
    fun permissionContract(): androidx.activity.result.contract.ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** Launches the Health Connect permission screen. */
    fun requestPermissions(launcher: ActivityResultLauncher<Set<String>>) {
        launcher.launch(PERMISSIONS)
    }

    // -----------------------------------------------------------------------
    // Step aggregation
    // -----------------------------------------------------------------------

    /**
     * Today's steps from local midnight to now.
     * Returns null if Health Connect is unavailable or permission is missing.
     */
    suspend fun getTodaySteps(client: HealthConnectClient): Long? {
        if (!hasPermissions(client)) return null
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone)
        val now = ZonedDateTime.now(zone)
        return aggregateSteps(client, startOfDay, now)
    }

    /**
     * Steps for a specific [date] (local timezone).
     * Returns null if unavailable or permission missing.
     */
    suspend fun getStepsForDate(client: HealthConnectClient, date: LocalDate): Long? {
        if (!hasPermissions(client)) return null
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone)
        val end = date.plusDays(1).atStartOfDay(zone).minusNanos(1)
        return aggregateSteps(client, start, end)
    }

    /**
     * Last [days] days of steps, most-recent last.
     * Missing days (no permission / no data) are null.
     */
    suspend fun getWeeklySteps(
        client: HealthConnectClient,
        days: Int = 7
    ): List<Long?> {
        val today = LocalDate.now(ZoneId.systemDefault())
        return (days - 1 downTo 0).map { offset ->
            getStepsForDate(client, today.minusDays(offset.toLong()))
        }
    }

    private suspend fun aggregateSteps(
        client: HealthConnectClient,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): Long {
        val request = AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start.toInstant(), end.toInstant())
        )
        val result: AggregationResult = client.aggregate(request)
        return result[StepsRecord.COUNT_TOTAL] ?: 0L
    }
}
