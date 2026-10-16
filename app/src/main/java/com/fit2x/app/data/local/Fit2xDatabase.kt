package com.fit2x.app.data.local

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------

/** Single-row profile for the signed-in user (id is always "me"). */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: String = "me",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    /** Matches Fit2xTheme id: ELECTRIC, EMBER, NOIR, EARTH */
    val themeId: String = "ELECTRIC",
    val stepGoal: Int = 10_000,
    val proteinGoalGrams: Int = 70,
    /** Daily calorie target (was hardcoded 2200 before v2). */
    @ColumnInfo(defaultValue = "2200")
    val calorieGoal: Int = 2_200,
    val avatarUri: String? = null,
    val streakDays: Int = 0,
    val lastActiveDate: String? = null // yyyy-MM-dd
)

/** One row per calendar day — synced from Health Connect, editable offline. */
@Entity(tableName = "step_entries")
data class StepEntry(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val steps: Int,
    val goal: Int,
    val manualOverride: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/** A single logged food item. */
@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val name: String,
    val proteinGrams: Double,
    val calories: Int,
    val quantityLabel: String = "",
    val mealType: String = "SNACK", // BREAKFAST, LUNCH, SNACK, DINNER
    val createdAt: Long = System.currentTimeMillis()
)

/** A completed (or partial) workout session. */
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val title: String,
    val completedMoves: Int,
    val totalMoves: Int,
    val durationMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------------------
// DAOs
// ---------------------------------------------------------------------------

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 'me'")
    fun observeProfile(): Flow<UserProfile?>

    @Upsert
    suspend fun upsert(profile: UserProfile)

    /**
     * Atomic step-goal write — no read-modify-write, so rapid stepper taps
     * can't lose increments.
     */
    @Query("UPDATE user_profile SET stepGoal = :goal WHERE id = 'me'")
    suspend fun updateStepGoal(goal: Int)

    /** Atomic calorie-goal write. */
    @Query("UPDATE user_profile SET calorieGoal = :goal WHERE id = 'me'")
    suspend fun updateCalorieGoal(goal: Int)
}

@Dao
interface StepEntryDao {
    @Query("SELECT * FROM step_entries WHERE date = :date")
    fun observeDay(date: String): Flow<StepEntry?>

    @Query("SELECT * FROM step_entries ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<StepEntry>>

    @Upsert
    suspend fun upsert(entry: StepEntry)
}

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY createdAt DESC")
    fun observeDay(date: String): Flow<List<FoodEntry>>

    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Delete
    suspend fun delete(entry: FoodEntry)

    /** Total protein per day, highest first — drives the "Most protein in a day" record. */
    @Query(
        "SELECT date, SUM(proteinGrams) AS totalProtein FROM food_entries " +
            "GROUP BY date ORDER BY totalProtein DESC LIMIT 60"
    )
    fun observeProteinByDay(): Flow<List<DayProteinTotal>>
}

/** Result of [FoodEntryDao.observeProteinByDay]. */
data class DayProteinTotal(
    val date: String,
    val totalProtein: Double
)

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE date = :date ORDER BY createdAt DESC")
    fun observeDay(date: String): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSession?

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)
}

// ---------------------------------------------------------------------------
// Database
// ---------------------------------------------------------------------------

/** v1 -> v2: adds UserProfile.calorieGoal (was a hardcoded 2200). */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE user_profile ADD COLUMN calorieGoal INTEGER NOT NULL DEFAULT 2200"
        )
    }
}

@Database(
    entities = [
        UserProfile::class,
        StepEntry::class,
        FoodEntry::class,
        WorkoutSession::class
    ],
    version = 2,
    exportSchema = false
)
abstract class Fit2xDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun stepEntryDao(): StepEntryDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
}
