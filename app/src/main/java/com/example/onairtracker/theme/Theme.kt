package com.example.onairtracker.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Fg,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = Fg2,
    secondary = AccentSecondary,
    onSecondary = Fg,
    background = Bg,
    onBackground = Fg,
    surface = Surface,
    onSurface = Fg,
    surfaceVariant = SurfaceWarm,
    onSurfaceVariant = Fg2,
    outline = Border,
    outlineVariant = BorderSoft,
    error = Danger
)

@Composable
fun OnAirTrackerTheme(
    darkTheme: Boolean = true, // Force Dark mode by default for neon branding
    dynamicColor: Boolean = false, // Force custom theme by default for unified Canva/OnAir aesthetics
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

