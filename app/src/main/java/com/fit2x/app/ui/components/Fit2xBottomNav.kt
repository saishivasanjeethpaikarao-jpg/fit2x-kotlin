package com.fit2x.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fit2x.app.ui.navigation.Screen

private val navIcons: Map<String, ImageVector> = mapOf(
    Screen.Today.route to Icons.Filled.Today,
    Screen.Steps.route to Icons.Filled.DirectionsWalk,
    Screen.Week.route to Icons.Filled.DateRange,
    Screen.Diet.route to Icons.Filled.Restaurant,
    Screen.Stats.route to Icons.Filled.BarChart,
    Screen.Settings.route to Icons.Filled.Settings
)

/**
 * Bottom navigation bar for the six main destinations.
 */
@Composable
fun Fit2xBottomNav(navController: NavController) {
    val scheme = MaterialTheme.colorScheme
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(
        containerColor = scheme.surface,
        contentColor = scheme.onSurface
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = navIcons[screen.route] ?: Icons.Filled.Today,
                        contentDescription = null
                    )
                },
                label = { Text(text = stringResource(screen.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = scheme.onPrimary,
                    selectedTextColor = scheme.primary,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                    indicatorColor = scheme.primary
                )
            )
        }
    }
}
