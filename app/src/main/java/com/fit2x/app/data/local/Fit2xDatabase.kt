package com.fit2x.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
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
}

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

@Database(
    entities = [
        UserProfile::class,
        StepEntry::class,
        FoodEntry::class,
        WorkoutSession::class
    ],
    version = 1,
    exportSchema = false
)
abstract class Fit2xDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun stepEntryDao(): StepEntryDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
}
