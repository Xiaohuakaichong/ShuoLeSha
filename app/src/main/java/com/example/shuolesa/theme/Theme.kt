package com.example.shuolesa.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = PureBlack,
    primaryContainer = NeonGreenDim,
    onPrimaryContainer = PureBlack,
    secondary = NeonGreenDim,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkGray,
    onSurfaceVariant = TextSecondary,
    outline = BorderGray,
    outlineVariant = MediumGray,
    error = DangerRed,
    onError = PureBlack,
)

@Composable
fun ShuoLeSaTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
