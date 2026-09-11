package com.example.shuolesa.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun colorSchemeOf(p: AppPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryContainer,
        onPrimaryContainer = p.onPrimaryContainer,
        secondary = p.secondary,
        onSecondary = p.onSecondary,
        secondaryContainer = p.secondaryContainer,
        onSecondaryContainer = p.onSecondaryContainer,
        tertiary = p.tertiary,
        onTertiary = p.onTertiary,
        tertiaryContainer = p.tertiaryContainer,
        onTertiaryContainer = p.onTertiaryContainer,
        background = p.background,
        onBackground = p.onBackground,
        surface = p.surface,
        onSurface = p.onSurface,
        surfaceVariant = p.surfaceVariant,
        onSurfaceVariant = p.onSurfaceVariant,
        surfaceTint = p.primary,
        surfaceBright = p.surfaceBright,
        surfaceDim = p.surfaceDim,
        surfaceContainerLowest = p.surfaceContainerLowest,
        surfaceContainerLow = p.surfaceContainerLow,
        surfaceContainer = p.surfaceContainer,
        surfaceContainerHigh = p.surfaceContainerHigh,
        surfaceContainerHighest = p.surfaceContainerHighest,
        inverseSurface = p.textPrimary,
        inverseOnSurface = p.surface,
        inversePrimary = p.primaryDim,
        outline = p.outline,
        outlineVariant = p.outlineVariant,
        error = p.danger,
        onError = p.onDanger,
        errorContainer = p.dangerContainer,
        onErrorContainer = p.textPrimary,
        scrim = p.scrim,
    )
} else {
    lightColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryContainer,
        onPrimaryContainer = p.onPrimaryContainer,
        secondary = p.secondary,
        onSecondary = p.onSecondary,
        secondaryContainer = p.secondaryContainer,
        onSecondaryContainer = p.onSecondaryContainer,
        tertiary = p.tertiary,
        onTertiary = p.onTertiary,
        tertiaryContainer = p.tertiaryContainer,
        onTertiaryContainer = p.onTertiaryContainer,
        background = p.background,
        onBackground = p.onBackground,
        surface = p.surface,
        onSurface = p.onSurface,
        surfaceVariant = p.surfaceVariant,
        onSurfaceVariant = p.onSurfaceVariant,
        surfaceTint = p.primary,
        surfaceBright = p.surfaceBright,
        surfaceDim = p.surfaceDim,
        surfaceContainerLowest = p.surfaceContainerLowest,
        surfaceContainerLow = p.surfaceContainerLow,
        surfaceContainer = p.surfaceContainer,
        surfaceContainerHigh = p.surfaceContainerHigh,
        surfaceContainerHighest = p.surfaceContainerHighest,
        inverseSurface = p.textPrimary,
        inverseOnSurface = p.surface,
        inversePrimary = p.primaryDim,
        outline = p.outline,
        outlineVariant = p.outlineVariant,
        error = p.danger,
        onError = p.onDanger,
        errorContainer = p.dangerContainer,
        onErrorContainer = p.textPrimary,
        scrim = p.scrim,
    )
}

@Composable
fun ShuoLeSaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorSchemeOf(palette, darkTheme),
            typography = Typography,
            content = content,
        )
    }
}

@Composable
fun InkAtmosphere(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColor.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppColor.primary.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-80).dp)
                .size(260.dp)
                .clip(CircleShape)
                .background(AppColor.primary.copy(alpha = 0.05f)),
        )
    }
}
