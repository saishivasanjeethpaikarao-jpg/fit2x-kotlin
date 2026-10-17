package com.fit2x.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fit2x.app.data.auth.AuthState
import com.fit2x.app.data.auth.AuthViewModel
import com.fit2x.app.data.auth.AuthViewModelFactory
import com.fit2x.app.diet.DietViewModel
import com.fit2x.app.diet.DietViewModelFactory
import com.fit2x.app.health.HealthViewModel
import com.fit2x.app.notifications.ReminderSettingsViewModel
import com.fit2x.app.notifications.NotificationHelper
import com.fit2x.app.ui.components.Fit2xBottomNav
import com.fit2x.app.ui.navigation.Screen
import com.fit2x.app.ui.screens.DietScreen
import com.fit2x.app.ui.screens.LoginScreen
import com.fit2x.app.ui.screens.SettingsScreen
import com.fit2x.app.ui.screens.StatsScreen
import com.fit2x.app.ui.screens.StepsScreen
import com.fit2x.app.ui.screens.TodayScreen
import com.fit2x.app.ui.screens.WeekScreen
import com.fit2x.app.ui.screens.WorkoutDetailScreen
import com.fit2x.app.ui.theme.Fit2xTheme
import com.fit2x.app.workout.WorkoutGraph
import com.fit2x.app.workout.WorkoutViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var authViewModel: AuthViewModel

    private lateinit var signInLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>

    private val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (::authViewModel.isInitialized) {
            authViewModel.handleSignInResult(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DIAGNOSTIC BUILD ONLY: if a previous launch crashed, show the saved
        // report on screen instead of dying silently.
        val crashPrefs = getSharedPreferences(
            Fit2xApplication.CRASH_PREFS, MODE_PRIVATE
        )
        val lastCrash = crashPrefs.getString(Fit2xApplication.KEY_LAST_CRASH, null)
        if (lastCrash != null) {
            setContent {
                CrashReportScreen(
                    report = lastCrash,
                    onClear = {
                        crashPrefs.edit()
                            .remove(Fit2xApplication.KEY_LAST_CRASH).apply()
                        recreate()
                    }
                )
            }
            return
        }

        try {
            onCreateInner(savedInstanceState)
        } catch (t: Throwable) {
            crashPrefs.edit().putString(
                Fit2xApplication.KEY_LAST_CRASH,
                "${t::class.java.name}: ${t.message}\n" +
                    t.stackTraceToString().take(3500)
            ).apply()
            setContent {
                CrashReportScreen(
                    report = crashPrefs.getString(
                        Fit2xApplication.KEY_LAST_CRASH, ""
                    ) ?: "",
                    onClear = {
                        crashPrefs.edit()
                            .remove(Fit2xApplication.KEY_LAST_CRASH).apply()
                        recreate()
                    }
                )
            }
        }
    }

    private fun onCreateInner(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        // Make the launcher accessible to composables via the property.
        signInLauncher = googleSignInLauncher

        // Ensure the notification channel exists before any reminder can fire.
        NotificationHelper.createChannel(this)

        setContent {
            val graph = LocalContext.current.appGraph()

            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModelFactory(graph.authRepository)
            )
            this@MainActivity.authViewModel = authViewModel
            val authState by authViewModel.authState.collectAsState()
            // Capture launcher for nested composables (label doesn't resolve at depth).
            val launcher = this@MainActivity.signInLauncher

            // Theme follows the persisted profile; falls back to Electric.
            // Remember the flow so recompositions don't restart Room collection.
            val profile by remember {
                graph.database.userProfileDao()
                    .observeProfile()
                    .map { it?.themeId ?: Fit2xTheme.Electric.id }
            }.collectAsState(initial = Fit2xTheme.Electric.id)
            val theme = Fit2xTheme.fromId(profile)

            Fit2xTheme(theme = theme) {
                when (val s = authState) {
                    is AuthState.Checking -> SplashScreen()
                    is AuthState.SignedOut,
                    is AuthState.SigningIn -> LoginScreen(
                        authState = s,
                        errors = authViewModel.errors,
                        onGoogleSignIn = { authViewModel.signIn(launcher) },
                        onContinueOffline = { authViewModel.continueOffline() }
                    )
                    is AuthState.SignedIn,
                    is AuthState.Offline -> Fit2xApp(
                        authViewModel = authViewModel,
                        currentTheme = theme,
                        onThemeSelected = { selected ->
                            // Persist theme choice to the profile.
                            // (Fire-and-forget is fine — UI already reflects it.)
                            persistTheme(selected.id)
                        },
                        signInLauncher = launcher
                    )
                }
            }
        }
    }

    private val activityScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private fun persistTheme(themeId: String) {
        val graph = appGraph()
        activityScope.launch {
            try {
                val dao = graph.database.userProfileDao()
                val row = dao.observeProfile().firstOrNull()
                if (row != null) {
                    dao.upsert(row.copy(themeId = themeId))
                } else {
                    dao.upsert(
                        com.fit2x.app.data.local.UserProfile(themeId = themeId)
                    )
                }
            } catch (_: Exception) {
                // Theme persistence is best-effort.
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * DIAGNOSTIC BUILD ONLY: shows the captured launch crash so it can be
 * screenshotted and fixed — instead of the app dying silently.
 */
@Composable
private fun CrashReportScreen(report: String, onClear: () -> Unit) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        androidx.compose.material3.Text(
            text = "FIT2X crashed on launch. Screenshot this:",
            color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
            style = MaterialTheme.typography.titleMedium
        )
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.padding(8.dp)
        )
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF0D0D0D))
                .padding(12.dp)
        ) {
            androidx.compose.material3.Text(
                text = report,
                color = androidx.compose.ui.graphics.Color(0xFF00FF88),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(scroll)
            )
        }
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.padding(8.dp)
        )
        androidx.compose.material3.Button(onClick = onClear) {
            androidx.compose.material3.Text("Clear & retry launch")
        }
    }
}

@Composable
fun Fit2xApp(
    authViewModel: AuthViewModel,
    currentTheme: Fit2xTheme,
    onThemeSelected: (Fit2xTheme) -> Unit,
    signInLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    val context = LocalContext.current
    val graph = context.appGraph()
    val navController = rememberNavController()

    // Real ViewModels — one per feature, all backed by the shared AppGraph.
    val healthViewModel: HealthViewModel = viewModel()
    val workoutViewModel: WorkoutViewModel = viewModel(
        factory = WorkoutGraph.provideViewModelFactory(context)
    )
    val dietViewModel: DietViewModel = viewModel(
        factory = DietViewModelFactory(graph.foodRepository)
    )
    val reminderViewModel: ReminderSettingsViewModel = viewModel()

    // Schedule reminders on first launch (idempotent).
    LaunchedEffect(Unit) {
        try {
            NotificationHelper.scheduleAll(context)
        } catch (_: Exception) {
            // Best-effort.
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { Fit2xBottomNav(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Today.route) {
                TodayScreen(
                    healthViewModel = healthViewModel,
                    dietViewModel = dietViewModel,
                    workoutViewModel = workoutViewModel,
                    authViewModel = authViewModel,
                    onStartWorkout = {
                        workoutViewModel.startWorkout()
                        navController.navigate(Screen.WorkoutDetail.route)
                    }
                )
            }
            composable(Screen.Steps.route) {
                StepsScreen(healthViewModel = healthViewModel)
            }
            composable(Screen.Week.route) {
                WeekScreen(workoutViewModel = workoutViewModel)
            }
            composable(Screen.Diet.route) {
                DietScreen(dietViewModel = dietViewModel)
            }
            composable(Screen.Stats.route) {
                StatsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    authViewModel = authViewModel,
                    reminderViewModel = reminderViewModel,
                    currentTheme = currentTheme,
                    onThemeSelected = onThemeSelected,
                    onSignIn = { authViewModel.signIn(signInLauncher) }
                )
            }
            composable(Screen.WorkoutDetail.route) {
                WorkoutDetailScreen(
                    viewModel = workoutViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
