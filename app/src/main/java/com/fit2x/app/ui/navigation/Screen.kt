package com.fit2x.app.ui.navigation

import androidx.annotation.StringRes
import com.fit2x.app.R

/**
 * Bottom-nav destinations. Specialist agents implement each screen's
 * composable and register it in MainActivity's NavHost.
 */
sealed class Screen(val route: String, @StringRes val labelRes: Int) {
    data object Today : Screen("today", R.string.nav_today)
    data object Steps : Screen("steps", R.string.nav_steps)
    data object Week : Screen("week", R.string.nav_week)
    data object Diet : Screen("diet", R.string.nav_diet)
    data object Stats : Screen("stats", R.string.nav_stats)
    data object Settings : Screen("settings", R.string.nav_settings)

    /** Detail destination (not in bottom nav) for an in-progress workout. */
    data object WorkoutDetail : Screen("workout_detail", R.string.workout_detail_title)

    companion object {
        /** Ordered for the bottom navigation bar. */
        val bottomNavItems: List<Screen> =
            listOf(Today, Steps, Week, Diet, Stats, Settings)
    }
}
