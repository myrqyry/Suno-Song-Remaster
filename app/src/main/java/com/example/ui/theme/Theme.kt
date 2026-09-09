package com.example.ui.theme

import android.app.Activity
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

val StudioPurple = Color(0xFFA855F7)
val StudioPurpleLight = Color(0xFFC084FC)
val StudioPurpleDark = Color(0xFF7E22CE)
val StudioCyan = Color(0xFF06B6D4)
val StudioGreen = Color(0xFF10B981)
val StudioYellow = Color(0xFFF59E0B)
val StudioRed = Color(0xFFEF4444)

val DarkBackground = Color(0xFF0D0B14)
val DarkSurface = Color(0xFF161224)
val DarkSurfaceVariant = Color(0xFF221C38)
val DarkCardBorder = Color(0xFF322A4E)

private val DarkColorScheme = darkColorScheme(
    primary = StudioPurple,
    onPrimary = Color.White,
    primaryContainer = StudioPurpleDark,
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = StudioCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = StudioGreen,
    background = DarkBackground,
    onBackground = Color(0xFFF3F4F6),
    surface = DarkSurface,
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD1D5DB),
    outline = DarkCardBorder,
    error = StudioRed
)

private val LightColorScheme = lightColorScheme(
    primary = StudioPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E8FF),
    onPrimaryContainer = StudioPurpleDark,
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFF059669),
    background = Color(0xFFF9FAFB),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFE5E7EB),
    error = StudioRed
)

@Composable
fun AudioRemasterTheme(
    darkTheme: Boolean = true, // Default to sleek studio dark console aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
