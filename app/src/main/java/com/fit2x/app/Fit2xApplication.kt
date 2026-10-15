package com.fit2x.app

import android.app.Application
import androidx.room.Room
import com.fit2x.app.data.auth.AuthRepository
import com.fit2x.app.data.local.Fit2xDatabase
import com.fit2x.app.diet.FoodRepository
import com.fit2x.app.workout.WorkoutRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Application class — owns the single [Fit2xDatabase] instance and all
 * repositories. Every ViewModel gets its dependencies from [AppGraph],
 * so there is exactly ONE database file ("fit2x-db") for the whole app.
 *
 * Registered in AndroidManifest as android:name=".Fit2xApplication".
 */
class Fit2xApplication : Application() {

    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph(this)
    }
}

/**
 * Manual dependency graph (no Hilt — keeps the build simple).
 * All singletons are thread-safe via lazy initialization.
 */
class AppGraph(private val app: Application) {

    private val context get() = app.applicationContext

    // ------------------------------------------------------------------
    // Database (single instance — "fit2x-db")
    // ------------------------------------------------------------------

    val database: Fit2xDatabase by lazy {
        Room.databaseBuilder(context, Fit2xDatabase::class.java, DB_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    // ------------------------------------------------------------------
    // Repositories
    // ------------------------------------------------------------------

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            appContext = context,
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance(),
            profileDao = database.userProfileDao()
        )
    }

    val foodRepository: FoodRepository by lazy {
        FoodRepository(context, database)
    }

    val workoutRepository: WorkoutRepository by lazy {
        WorkoutRepository(context, database.workoutSessionDao())
    }

    companion object {
        /** The ONE database file name used across the entire app. */
        const val DB_NAME = "fit2x-db"
    }
}

/** Convenience accessor from any Context. */
fun android.content.Context.appGraph(): AppGraph =
    (applicationContext as Fit2xApplication).appGraph
