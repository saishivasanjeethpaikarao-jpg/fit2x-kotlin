package com.fit2x.app.health

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fit2x.app.appGraph
import com.fit2x.app.data.local.Fit2xDatabase
import com.fit2x.app.data.local.StepEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI state for step tracking.
 *
 * - [todaySteps]    — live count (Health Connect → Room fallback)
 * - [weeklySteps]   — last 7 days, most-recent last (null = no data)
 * - [permissionGranted] — Health Connect READ_STEPS status
 * - [healthConnectAvailable] — installed on device
 * - [healthConnectInstalled] — false means "send user to Play Store"
 */
class HealthViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    // Single shared database — never create a second instance.
    private val db: Fit2xDatabase = context.appGraph().database
    private val stepDao = db.stepEntryDao()

    private val _todaySteps = MutableStateFlow(0L)
    val todaySteps: StateFlow<Long> = _todaySteps.asStateFlow()

    private val _weeklySteps = MutableStateFlow<List<Long?>>(List(7) { null })
    val weeklySteps: StateFlow<List<Long?>> = _weeklySteps.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _healthConnectAvailable = MutableStateFlow(false)
    val healthConnectAvailable: StateFlow<Boolean> = _healthConnectAvailable.asStateFlow()

    private val _healthConnectInstalled = MutableStateFlow(true)
    val healthConnectInstalled: StateFlow<Boolean> = _healthConnectInstalled.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    init {
        viewModelScope.launch { refreshAvailability() }
        viewModelScope.launch { observeLocalSteps() }
        // Kick off background sync; harmless if permission is missing.
        StepSyncWorker.schedule(context)
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Re-checks availability + permission (call onResume, and after the permission screen). */
    fun refreshAvailability() {
        viewModelScope.launch {
            _healthConnectAvailable.value = HealthConnectManager.isAvailable(context)
            _healthConnectInstalled.value = !HealthConnectManager.isNotInstalled(context)
            if (_healthConnectAvailable.value) {
                val client = HealthConnectClient.getOrCreate(context)
                _permissionGranted.value = HealthConnectManager.hasPermissions(client)
                if (_permissionGranted.value) refreshSteps()
            } else {
                _permissionGranted.value = false
            }
        }
    }

    /** Pulls fresh steps from Health Connect into Room + UI state. */
    fun refreshSteps() {
        viewModelScope.launch {
            if (!_healthConnectAvailable.value || !_permissionGranted.value) return@launch
            _syncing.value = true
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val today = HealthConnectManager.getTodaySteps(client)
                if (today != null) {
                    persistToday(today)
                    _todaySteps.value = today
                }
                _weeklySteps.value = HealthConnectManager.getWeeklySteps(client)
            } catch (_: Exception) {
                // Health Connect read failed — keep last known values.
            } finally {
                _syncing.value = false
            }
        }
    }

    /** Manual step entry (fallback when Health Connect is unavailable). */
    fun setManualSteps(steps: Int) {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val existing = stepDao.observeDay(today).first()
            stepDao.upsert(
                StepEntry(
                    date = today,
                    steps = steps,
                    goal = existing?.goal ?: 10_000,
                    manualOverride = true
                )
            )
            _todaySteps.value = steps.toLong()
        }
    }

    /** Opens the Play Store page for Health Connect. */
    fun installHealthConnect() = HealthConnectManager.openPlayStore(context)

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    /**
     * Observes Room so steps survive restarts and update from the worker.
     * The date is recomputed on every emission (not pinned at init), so an
     * app left open past midnight picks up the new day's row as soon as
     * anything writes to the step table.
     */
    private suspend fun observeLocalSteps() {
        stepDao.observeRecent(3).collect { entries ->
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val entry = entries.firstOrNull { it.date == today }
            // Only overwrite live Health Connect values with stored ones
            // when we don't have permission to read fresh data.
            if (!_permissionGranted.value && entry != null) {
                _todaySteps.value = entry.steps.toLong()
            } else if (entry == null && !_permissionGranted.value) {
                _todaySteps.value = 0L
            }
        }
    }

    private suspend fun persistToday(steps: Long) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val existing = stepDao.observeDay(today).first()
        // Never clobber a manual override with auto-read data.
        if (existing?.manualOverride == true) return
        stepDao.upsert(
            StepEntry(
                date = today,
                steps = steps.toInt(),
                goal = existing?.goal ?: 10_000,
                manualOverride = false
            )
        )
    }

    override fun onCleared() {
        // Database is owned by AppGraph — do NOT close it here.
        super.onCleared()
    }
}
