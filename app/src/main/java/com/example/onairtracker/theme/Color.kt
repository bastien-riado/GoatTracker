package com.example.onairtracker.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Surface tokens
val Bg = Color(0xFF050505)
val Surface = Color(0xFF121212)
val SurfaceWarm = Color(0xFF181818)
val SurfaceElevated = Color(0xFF1E1E1E)

// Foreground tokens
val Fg = Color(0xFFFFFFFF)
val Fg2 = Color(0xFFE0E0E0)
val Muted = Color(0xFFA0A0A0)
val Meta = Color(0xFF606060)

// Accent tokens
val Accent = Color(0xFFFF3366)
val AccentHover = Color(0xFFFF1A53)
val AccentActive = Color(0xFFE60039)
val AccentSecondary = Color(0xFFFF9933)

// Semantic tokens
val Success = Color(0xFF00FFAA)
val Warn = Color(0xFFFFCC00)
val Danger = Color(0xFFFF3333)
val Info = Color(0xFF00EEFF)

// Border tokens
val Border = Color(0xFF2A2A2A)
val BorderSoft = Color(0xFF1F1F1F)

// Brush Gradients
val PremiumGradient = Brush.linearGradient(
    colors = listOf(Accent, AccentSecondary)
)

