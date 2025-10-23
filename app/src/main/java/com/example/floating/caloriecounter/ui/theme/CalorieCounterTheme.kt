package com.example.floating.caloriecounter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC), // Primary color for floating action buttons
    //secondary = Color(0xFF03DAC6), // Secondary color (unused but kept for balance)
    background = Color(0xFF121212), // App background
    surface = Color(0xFF1E1E1E), // Dialog background or card surfaces
    onPrimary = Color.Black, // Icon/text color on primary (e.g., floating action button)
    onSecondary = Color.Black, // Icon/text color on secondary (if used)
    onBackground = Color(0xFFE0E0E0), // Text color on background
    onSurface = Color(0xFFE0E0E0), // Text color on surfaces (e.g., dialogs)

    // Specific additions for inputs and dialog buttons
    primaryContainer = Color(0xFF2C2C2C), // Background for input fields
    onPrimaryContainer = Color(0xFFE0E0E0), // Text color in input fields
    secondaryContainer = Color(0xFF1E88E5), // Confirm button background in dialogs
    onSecondaryContainer = Color.White // Text color for dialog buttons
)

@Composable
fun CalorieCounterTheme(
    darkTheme: Boolean = true, // Force dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}