package com.fit2x.app.workout

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fit2x.app.appGraph
import com.fit2x.app.data.local.Fit2xDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manual-DI helpers for the workout feature. The app-level graph (or the UI
 * agent wiring MainActivity) can reuse [provideDatabase] for other features.
 */
object WorkoutGraph {
    @Volatile
    private var repository: WorkoutRepository? = null

    /**
     * The ONE Room instance for the whole app, owned by AppGraph.
     * Never build a second database on "fit2x-db" — Room's invalidation
     * tracker is per-instance, so a second instance breaks cross-feature
     * Flow observers (e.g. Stats going stale after a workout).
     */
    fun provideDatabase(context: Context): Fit2xDatabase =
        context.appGraph().database

    fun provideRepository(context: Context): WorkoutRepository =
        repository ?: synchronized(this) {
            repository ?: WorkoutRepository(
                context.applicationContext,
                provideDatabase(context).workoutSessionDao()
            ).also { repository = it }
        }

    fun provideViewModelFactory(context: Context): ViewModelProvider.Factory =
        WorkoutViewModelFactory(provideRepository(context))
}

class WorkoutViewModelFactory(
    private val repository: WorkoutRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WorkoutViewModel::class.java))
        return WorkoutViewModel(repository) as T
    }
}

class WorkoutViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    // ------------------------------------------------------------------
    // Plan & schedule state
    // ------------------------------------------------------------------

    val activeTemplate: StateFlow<String> =
        repository.observeActiveTemplate()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "STRENGTH")

    val weekSchedule: StateFlow<List<WorkoutDay>> =
        repository.observeWeekSchedule()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayWorkout: StateFlow<WorkoutDay?> =
        repository.observeTodayWorkout()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Consecutive fully-completed workout days. Motivational — surfaced prominently. */
    val streak: StateFlow<Int> =
        repository.getStreak()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setTemplate(templateId: String) {
        viewModelScope.launch { repository.setActiveTemplate(templateId) }
    }

    fun updateDay(dayOfWeek: String, workout: WorkoutDay) {
        viewModelScope.launch { repository.updateDaySchedule(dayOfWeek, workout) }
    }

    fun clearDayOverride(dayOfWeek: String) {
        viewModelScope.launch { repository.clearDayOverride(dayOfWeek) }
    }

    // ------------------------------------------------------------------
    // Live session state
    // ------------------------------------------------------------------

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    /**
     * Guards [startWorkout] against double-taps: set on the Main thread
     * before launching, so a second tap in the same frame is rejected.
     * Observed by UI to disable the start button while launching.
     */
    private val _starting = MutableStateFlow(false)
    val starting: StateFlow<Boolean> = _starting.asStateFlow()

    private val _completedExercises = MutableStateFlow<Set<String>>(emptySet())
    val completedExercises: StateFlow<Set<String>> = _completedExercises.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _workoutFinished = MutableStateFlow(false)
    val workoutFinished: StateFlow<Boolean> = _workoutFinished.asStateFlow()

    private var timerJob: Job? = null

    val isSessionActive: Boolean get() = _activeSessionId.value != null

    fun startWorkout() {
        if (_activeSessionId.value != null || _starting.value) return
        val workout = todayWorkout.value ?: return
        // Set synchronously on Main — a second tap before the coroutine
        // starts can no longer slip through the guard.
        _starting.value = true
        viewModelScope.launch {
            try {
                val id = repository.startSession(
                    date = todayDateString(),
                    title = workout.title,
                    totalMoves = workout.exercises.size
                )
                _activeSessionId.value = id
                _completedExercises.value = emptySet()
                _elapsedSeconds.value = 0L
                _workoutFinished.value = false
                startTimer()
            } finally {
                _starting.value = false
            }
        }
    }

    fun toggleExercise(exerciseName: String) {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            val currentlyDone = _completedExercises.value.contains(exerciseName)
            if (currentlyDone) {
                repository.uncompleteExercise(sessionId, exerciseName)
                _completedExercises.value -= exerciseName
            } else {
                repository.completeExercise(sessionId, exerciseName)
                _completedExercises.value += exerciseName
            }
        }
    }

    fun finishWorkout() {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            timerJob?.cancel()
            val minutes = (_elapsedSeconds.value / 60).toInt().coerceAtLeast(1)
            repository.setSessionDuration(sessionId, minutes)
            repository.completeWorkout(sessionId)
            _activeSessionId.value = null
            _workoutFinished.value = true
        }
    }

    /** Dismiss the completion celebration and reset session UI state. */
    fun dismissFinished() {
        _workoutFinished.value = false
        _completedExercises.value = emptySet()
        _elapsedSeconds.value = 0L
    }

    fun cancelSession() {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            timerJob?.cancel()
            repository.completeWorkout(sessionId)
            _activeSessionId.value = null
            _completedExercises.value = emptySet()
            _elapsedSeconds.value = 0L
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _elapsedSeconds.value = _elapsedSeconds.value + 1
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}

/** mm:ss (or h:mm:ss past an hour) for the session timer chip. */
fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
