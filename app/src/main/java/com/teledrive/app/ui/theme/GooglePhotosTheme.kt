package com.teledrive.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Brand palette (Google Photos inspired) ────────────────────────────
val GoogleDarkBackground = Color(0xFF0F0F14)
val GoogleDarkSurface = Color(0xFF1A1A22)
val GoogleDarkCard = Color(0xFF25252F)
val GoogleDarkCardElevated = Color(0xFF2D2D38)
val GooglePillSurface = Color(0xFF2A2A36)
val GooglePillSelected = Color(0xFFE8EAED)
val GooglePrimaryAccent = Color(0xFFA8C7FA)        // Photos light blue
val GoogleSecondaryAccent = Color(0xFFD0BCFF)
val GoogleTertiaryAccent = Color(0xFFFFD8A8)        // warm orange for highlights
val GoogleDanger = Color(0xFFFF7A7A)
val GoogleOnDarkText = Color(0xFFE3E2E6)
val GoogleOnDarkTextMuted = Color(0xFFB6B5BD)
val GoogleOnDarkTextSubtle = Color(0xFF8A8993)

// Light palette
val GoogleLightBackground = Color(0xFFF7F8FB)
val GoogleLightSurface = Color(0xFFFFFFFF)
val GoogleLightCard = Color(0xFFF1F2F6)
val GoogleOnLightText = Color(0xFF1B1B1F)

private val DarkColorScheme = darkColorScheme(
    primary = GooglePrimaryAccent,
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF1F3A66),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = GoogleSecondaryAccent,
    onSecondary = Color(0xFF371E73),
    secondaryContainer = Color(0xFF4F378B),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = GoogleTertiaryAccent,
    background = GoogleDarkBackground,
    onBackground = GoogleOnDarkText,
    surface = GoogleDarkSurface,
    onSurface = GoogleOnDarkText,
    surfaceVariant = GoogleDarkCard,
    onSurfaceVariant = GoogleOnDarkTextMuted,
    surfaceTint = GooglePrimaryAccent,
    outline = Color(0xFF3A3A45),
    outlineVariant = Color(0xFF2A2A36),
    error = GoogleDanger,
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF6750A4),
    onSecondary = Color.White,
    background = GoogleLightBackground,
    onBackground = GoogleOnLightText,
    surface = GoogleLightSurface,
    onSurface = GoogleOnLightText,
    surfaceVariant = GoogleLightCard,
    onSurfaceVariant = Color(0xFF46464F),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun GooglePhotosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
