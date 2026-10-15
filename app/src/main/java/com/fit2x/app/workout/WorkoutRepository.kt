package com.fit2x.app.workout

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fit2x.app.data.local.WorkoutSession
import com.fit2x.app.data.local.WorkoutSessionDao
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private val Context.workoutDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "workout_prefs")

private val KEY_ACTIVE_TEMPLATE = stringPreferencesKey("active_workout_template")
private val KEY_DAY_OVERRIDES = stringPreferencesKey("workout_day_overrides_json")

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun todayDateString(): String = LocalDate.now().format(DATE_FMT)

/**
 * Single source of truth for workout plans, schedule edits and session tracking.
 *
 * Schedule = active template from [WORKOUT_TEMPLATES] + per-day overrides stored
 * as JSON in DataStore, so the Week screen can edit any day without forking
 * the whole template.
 */
class WorkoutRepository(
    private val context: Context,
    private val sessionDao: WorkoutSessionDao
) {
    private val dataStore: DataStore<Preferences> = context.workoutDataStore

    /** In-memory checkbox state for live sessions: sessionId -> completed exercise names. */
    private val sessionProgress = mutableMapOf<Long, MutableSet<String>>()
    private val progressMutex = Mutex()

    // ------------------------------------------------------------------
    // Templates & schedule
    // ------------------------------------------------------------------

    fun observeActiveTemplate(): Flow<String> =
        dataStore.data.map { it[KEY_ACTIVE_TEMPLATE] ?: "STRENGTH" }

    suspend fun setActiveTemplate(templateId: String) {
        require(WORKOUT_TEMPLATES.any { it.id == templateId }) { "Unknown template $templateId" }
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_TEMPLATE] = templateId
            // Switching template clears day overrides so the new plan is clean.
            prefs.remove(KEY_DAY_OVERRIDES)
        }
    }

    fun observeWeekSchedule(): Flow<List<WorkoutDay>> =
        dataStore.data.map { prefs ->
            val templateId = prefs[KEY_ACTIVE_TEMPLATE] ?: "STRENGTH"
            val overrides = parseOverrides(prefs[KEY_DAY_OVERRIDES])
            val template = templateById(templateId)
            WEEK_DAYS.map { dayName ->
                overrides[dayName]
                    ?: template.days.first { it.dayOfWeek == dayName }
            }
        }

    /** Suspend variant for one-shot reads (e.g. building today's session). */
    suspend fun getWeekSchedule(): List<WorkoutDay> = observeWeekSchedule().first()

    suspend fun getTodayWorkout(): WorkoutDay {
        val todayName = DayOfWeek.from(LocalDate.now()).name
        return getWeekSchedule().first { it.dayOfWeek == todayName }
    }

    fun observeTodayWorkout(): Flow<WorkoutDay> =
        observeWeekSchedule().map { week ->
            val todayName = DayOfWeek.from(LocalDate.now()).name
            week.first { it.dayOfWeek == todayName }
        }

    /**
     * Replace one day of the schedule (used by the Week editor). The override
     * is stored keyed by day name and wins over the template.
     */
    suspend fun updateDaySchedule(dayOfWeek: String, workout: WorkoutDay) {
        require(dayOfWeek in WEEK_DAYS) { "Invalid day $dayOfWeek" }
        val fixed = workout.copy(dayOfWeek = dayOfWeek)
        dataStore.edit { prefs ->
            val overrides = parseOverrides(prefs[KEY_DAY_OVERRIDES]).toMutableMap()
            overrides[dayOfWeek] = fixed
            prefs[KEY_DAY_OVERRIDES] = overridesToJson(overrides)
        }
    }

    /** Remove a day override, falling back to the template's day. */
    suspend fun clearDayOverride(dayOfWeek: String) {
        dataStore.edit { prefs ->
            val overrides = parseOverrides(prefs[KEY_DAY_OVERRIDES]).toMutableMap()
            overrides.remove(dayOfWeek)
            if (overrides.isEmpty()) prefs.remove(KEY_DAY_OVERRIDES)
            else prefs[KEY_DAY_OVERRIDES] = overridesToJson(overrides)
        }
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    /** Starts a session row and returns its id. */
    suspend fun startSession(date: String, title: String, totalMoves: Int): Long {
        val id = sessionDao.insert(
            WorkoutSession(
                date = date,
                title = title,
                completedMoves = 0,
                totalMoves = totalMoves
            )
        )
        progressMutex.withLock { sessionProgress[id] = mutableSetOf() }
        return id
    }

    suspend fun completeExercise(sessionId: Long, exerciseName: String) {
        progressMutex.withLock {
            val done = sessionProgress.getOrPut(sessionId) { mutableSetOf() }
            if (done.add(exerciseName)) {
                persistProgress(sessionId, done.size)
            }
        }
    }

    suspend fun uncompleteExercise(sessionId: Long, exerciseName: String) {
        progressMutex.withLock {
            val done = sessionProgress[sessionId] ?: return
            if (done.remove(exerciseName)) {
                persistProgress(sessionId, done.size)
            }
        }
    }

    fun observeCompletedExercises(sessionId: Long): Flow<Set<String>> =
        // DB updates (via persistProgress) drive re-emission; the in-memory set
        // is the live truth while the session is active.
        sessionDao.observeSession(sessionId).map {
            progressMutex.withLock { sessionProgress[sessionId] }?.toSet() ?: emptySet()
        }

    /** Marks the session finished; call [setSessionDuration] first if timed. */
    suspend fun completeWorkout(sessionId: Long) {
        progressMutex.withLock { sessionProgress.remove(sessionId) }
    }

    suspend fun setSessionDuration(sessionId: Long, minutes: Int) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(durationMinutes = minutes.coerceAtLeast(0)))
    }

    fun observeSession(sessionId: Long): Flow<WorkoutSession?> =
        sessionDao.observeSession(sessionId)

    fun observeTodaySessions(date: String = todayDateString()): Flow<List<WorkoutSession>> =
        sessionDao.observeDay(date)

    // ------------------------------------------------------------------
    // Streak — consecutive fully-completed workout days.
    // ------------------------------------------------------------------

    /**
     * Counts back from today: a day counts when at least one session was fully
     * completed (completedMoves >= totalMoves). Today may be missing — the
     * streak stays alive until the day ends.
     */
    fun getStreak(): Flow<Int> =
        sessionDao.observeRecent(400).map { sessions ->
            val completedDates = sessions
                .filter { it.totalMoves > 0 && it.completedMoves >= it.totalMoves }
                .map { it.date }
                .toSet()
            var streak = 0
            var day = LocalDate.now()
            if (!completedDates.contains(day.format(DATE_FMT))) {
                day = day.minusDays(1)
            }
            while (completedDates.contains(day.format(DATE_FMT))) {
                streak++
                day = day.minusDays(1)
            }
            streak
        }

    // ------------------------------------------------------------------
    // JSON (org.json — no extra dependency)
    // ------------------------------------------------------------------

    private suspend fun persistProgress(sessionId: Long, completedCount: Int) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(completedMoves = completedCount))
    }

    private fun exerciseToJson(e: Exercise): JSONObject = JSONObject()
        .put("name", e.name)
        .put("sets", e.sets)
        .put("reps", e.reps)
        .put("muscleGroup", e.muscleGroup)
        .put("instructions", e.instructions)
        .put("videoUrl", e.videoUrl ?: JSONObject.NULL)

    private fun exerciseFromJson(o: JSONObject): Exercise = Exercise(
        name = o.optString("name"),
        sets = o.optInt("sets", 3),
        reps = o.optString("reps", "10"),
        muscleGroup = o.optString("muscleGroup", ""),
        instructions = o.optString("instructions", ""),
        videoUrl = o.optString("videoUrl", null).takeUnless { it.isNullOrEmpty() }
    )

    private fun dayToJson(d: WorkoutDay): JSONObject = JSONObject()
        .put("dayOfWeek", d.dayOfWeek)
        .put("title", d.title)
        .put("subtitle", d.subtitle)
        .put("isRest", d.isRest)
        .put("estimatedMinutes", d.estimatedMinutes)
        .put("exercises", JSONArray(d.exercises.map { exerciseToJson(it) }))

    private fun dayFromJson(o: JSONObject): WorkoutDay {
        val arr = o.optJSONArray("exercises") ?: JSONArray()
        return WorkoutDay(
            dayOfWeek = o.optString("dayOfWeek", "MONDAY"),
            title = o.optString("title", ""),
            subtitle = o.optString("subtitle", ""),
            exercises = List(arr.length()) { i -> exerciseFromJson(arr.getJSONObject(i)) },
            isRest = o.optBoolean("isRest", false),
            estimatedMinutes = o.optInt("estimatedMinutes", 0)
        )
    }

    private fun parseOverrides(json: String?): Map<String, WorkoutDay> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(json)
            root.keys().asSequence()
                .associateWith { dayFromJson(root.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun overridesToJson(overrides: Map<String, WorkoutDay>): String {
        val root = JSONObject()
        overrides.forEach { (day, workout) -> root.put(day, dayToJson(workout)) }
        return root.toString()
    }
}
