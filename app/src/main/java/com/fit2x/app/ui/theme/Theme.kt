package com.fit2x.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The four FIT2X themes. Dark-first — this is the primary use case.
 * Stored as [UserProfile.themeId]; resolved via [Fit2xTheme.fromId].
 */
sealed class Fit2xTheme(
    val id: String,
    val displayName: String,
    val accent: Color,
    val scheme: androidx.compose.material3.ColorScheme
) {
    data object Electric : Fit2xTheme(
        id = "ELECTRIC",
        displayName = "Electric",
        accent = Color(0xFF4D6BFF),
        scheme = darkColorScheme(
            primary = Color(0xFF4D6BFF),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF1B2A6B),
            onPrimaryContainer = Color(0xFFDCE3FF),
            secondary = Color(0xFF8FA2FF),
            onSecondary = Color(0xFF0A0E1A),
            background = Color(0xFF070B16),
            onBackground = Color(0xFFF2F4FF),
            surface = Color(0xFF0D1322),
            onSurface = Color(0xFFF2F4FF),
            surfaceVariant = Color(0xFF161E33),
            onSurfaceVariant = Color(0xFFA8B3D6),
            outline = Color(0xFF2A3557)
        )
    )

    data object Ember : Fit2xTheme(
        id = "EMBER",
        displayName = "Ember",
        accent = Color(0xFFFF6B35),
        scheme = darkColorScheme(
            primary = Color(0xFFFF6B35),
            onPrimary = Color(0xFF1A0D06),
            primaryContainer = Color(0xFF6B2A12),
            onPrimaryContainer = Color(0xFFFFE3D3),
            secondary = Color(0xFFFFB08A),
            onSecondary = Color(0xFF1A0D06),
            background = Color(0xFF140B07),
            onBackground = Color(0xFFFFF1E8),
            surface = Color(0xFF1E120B),
            onSurface = Color(0xFFFFF1E8),
            surfaceVariant = Color(0xFF2E1D12),
            onSurfaceVariant = Color(0xFFD6B3A0),
            outline = Color(0xFF57382A)
        )
    )

    data object Noir : Fit2xTheme(
        id = "NOIR",
        displayName = "Noir",
        accent = Color(0xFFF5F5F5),
        scheme = darkColorScheme(
            primary = Color(0xFFF5F5F5),
            onPrimary = Color(0xFF000000),
            primaryContainer = Color(0xFF3A3A3A),
            onPrimaryContainer = Color(0xFFFFFFFF),
            secondary = Color(0xFFBDBDBD),
            onSecondary = Color(0xFF000000),
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF0D0D0D),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF1C1C1C),
            onSurfaceVariant = Color(0xFF9E9E9E),
            outline = Color(0xFF333333)
        )
    )

    data object Earth : Fit2xTheme(
        id = "EARTH",
        displayName = "Earth",
        accent = Color(0xFF7CB342),
        scheme = darkColorScheme(
            primary = Color(0xFF7CB342),
            onPrimary = Color(0xFF0E1A08),
            primaryContainer = Color(0xFF2E4A1B),
            onPrimaryContainer = Color(0xFFE4F3D3),
            secondary = Color(0xFFAED581),
            onSecondary = Color(0xFF0E1A08),
            background = Color(0xFF0C1208),
            onBackground = Color(0xFFF1F6E9),
            surface = Color(0xFF141B0E),
            onSurface = Color(0xFFF1F6E9),
            surfaceVariant = Color(0xFF212B17),
            onSurfaceVariant = Color(0xFFB3C49E),
            outline = Color(0xFF3A4A2C)
        )
    )

    companion object {
        val all: List<Fit2xTheme> = listOf(Electric, Ember, Noir, Earth)

        /** Falls back to Electric (the default) for unknown ids. */
        fun fromId(id: String): Fit2xTheme =
            all.firstOrNull { it.id == id } ?: Electric
    }
}

/**
 * Applies the given [Fit2xTheme]. Screens read the active theme from
 * DataStore/UserProfile and pass it here — MainActivity wires that up
 * once the settings agent lands the theme repository.
 */
@Composable
fun Fit2xTheme(
    theme: Fit2xTheme = Fit2xTheme.Electric,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = theme.scheme,
        content = content
    )
}
