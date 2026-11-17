package com.example.androidwebsiteblocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Periwinkle,
    onPrimary = NightBackground,
    primaryContainer = Color(0xFF2E3870),
    onPrimaryContainer = NightOnSurface,
    secondary = Teal,
    onSecondary = NightBackground,
    secondaryContainer = Color(0xFF1F3C42),
    onSecondaryContainer = NightOnSurface,
    tertiary = Amber,
    onTertiary = NightBackground,
    tertiaryContainer = Color(0xFF3D3020),
    onTertiaryContainer = Color(0xFFFFE2C2),
    error = Coral,
    errorContainer = Color(0xFF452128),
    onErrorContainer = Color(0xFFFFD9DC),
    background = NightBackground,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightContainer,
    onSurfaceVariant = NightOnSurfaceVariant,
    surfaceContainerLowest = NightBackground,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightContainer,
    surfaceContainerHigh = NightContainerHigh,
    surfaceContainerHighest = Color(0xFF2B3250),
    outline = NightOutline,
    outlineVariant = Color(0xFF2A3050),
)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF0E1A66),
    secondary = DeepTeal,
    secondaryContainer = Color(0xFFCDEDE8),
    tertiary = DeepAmber,
    tertiaryContainer = Color(0xFFFFE2C2),
    onTertiaryContainer = Color(0xFF3A2200),
    background = DayBackground,
    surface = DayBackground,
    surfaceVariant = DayContainer,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = DayContainer,
    surfaceContainerHigh = DayContainerHigh,
    surfaceContainerHighest = Color(0xFFD8DCEE),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Fixed palette (no wallpaper-based dynamic color) so the app looks the same everywhere. */
@Composable
fun AndroidWebsiteBlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
