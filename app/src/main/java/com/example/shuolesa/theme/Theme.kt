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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = AppColor.primary,
    onPrimary = AppColor.onPrimary,
    primaryContainer = AppColor.primaryContainer,
    onPrimaryContainer = AppColor.onPrimaryContainer,
    secondary = AppColor.secondary,
    onSecondary = AppColor.onSecondary,
    secondaryContainer = AppColor.secondaryContainer,
    onSecondaryContainer = AppColor.onSecondaryContainer,
    tertiary = AppColor.tertiary,
    onTertiary = AppColor.onTertiary,
    tertiaryContainer = AppColor.tertiaryContainer,
    onTertiaryContainer = AppColor.onTertiaryContainer,
    background = AppColor.background,
    onBackground = AppColor.onBackground,
    surface = AppColor.surface,
    onSurface = AppColor.onSurface,
    surfaceVariant = AppColor.surfaceVariant,
    onSurfaceVariant = AppColor.onSurfaceVariant,
    surfaceTint = AppColor.primary,
    surfaceBright = AppColor.surfaceBright,
    surfaceDim = AppColor.surfaceDim,
    surfaceContainerLowest = AppColor.surfaceContainerLowest,
    surfaceContainerLow = AppColor.surfaceContainerLow,
    surfaceContainer = AppColor.surfaceContainer,
    surfaceContainerHigh = AppColor.surfaceContainerHigh,
    surfaceContainerHighest = AppColor.surfaceContainerHighest,
    inverseSurface = AppColor.textPrimary,
    inverseOnSurface = AppColor.surface,
    inversePrimary = AppColor.primaryDim,
    outline = AppColor.outline,
    outlineVariant = AppColor.outlineVariant,
    error = AppColor.danger,
    onError = AppColor.onDanger,
    errorContainer = AppColor.dangerContainer,
    onErrorContainer = AppColor.textPrimary,
    scrim = AppColor.scrim,
)

@Composable
fun ShuoLeSaTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content,
    )
}

/** Soft mint wash on paper. Tab screens stay transparent so this shows through. */
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
