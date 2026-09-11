package com.example.shuolesa.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryDim: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryGlow: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryDim: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val secondaryGlow: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryDim: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceBright: Color,
    val surfaceDim: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceGlass: Color,
    val outline: Color,
    val outlineVariant: Color,
    val outlineHover: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerDim: Color,
    val dangerContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val scrim: Color,
    val shadowTint: Color,
    val accentShadow: Color,
)

val LightPalette = AppPalette(
    primary = Color(0xFF5B7F74),
    onPrimary = Color(0xFFFFFFFF),
    primaryDim = Color(0xFF48665D),
    primaryContainer = Color(0xFFE4EFEA),
    onPrimaryContainer = Color(0xFF243832),
    primaryGlow = Color(0x225B7F74),
    secondary = Color(0xFF5E7FA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryDim = Color(0xFF4A6586),
    secondaryContainer = Color(0xFFE6EEF6),
    onSecondaryContainer = Color(0xFF24344A),
    secondaryGlow = Color(0x1A5E7FA8),
    tertiary = Color(0xFF7A6E96),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryDim = Color(0xFF61587A),
    tertiaryContainer = Color(0xFFECE8F3),
    onTertiaryContainer = Color(0xFF2E2A3C),
    background = Color(0xFFFDFBF7),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFFFFDFB),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFF1EEE8),
    onSurfaceVariant = Color(0xFF57534E),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEBE7E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF8F4),
    surfaceContainer = Color(0xFFFFFDFB),
    surfaceContainerHigh = Color(0xFFF1EEE8),
    surfaceContainerHighest = Color(0xFFE6E2DB),
    surfaceGlass = Color(0xF5FFFDFB),
    outline = Color(0x1A1C1917),
    outlineVariant = Color(0xFFE8E4DC),
    outlineHover = Color(0xFF5B7F74),
    success = Color(0xFF5B7F74),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFE4EFEA),
    warning = Color(0xFFB08948),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFF6EBD4),
    danger = Color(0xFFB56A64),
    onDanger = Color(0xFFFFFFFF),
    dangerDim = Color(0xFF8F504B),
    dangerContainer = Color(0xFFF4E3E1),
    textPrimary = Color(0xFF1C1917),
    textSecondary = Color(0xFF57534E),
    textMuted = Color(0xFF8A847C),
    scrim = Color(0x331C1917),
    shadowTint = Color(0x140C0A08),
    accentShadow = Color(0x1A5B7F74),
)

val DarkPalette = AppPalette(
    primary = Color(0xFFA8C4BB),
    onPrimary = Color(0xFF1C1917),
    primaryDim = Color(0xFF7F9E94),
    primaryContainer = Color(0xFF2A3834),
    onPrimaryContainer = Color(0xFFD7E8E2),
    primaryGlow = Color(0x33A8C4BB),
    secondary = Color(0xFF9BB3CC),
    onSecondary = Color(0xFF161411),
    secondaryDim = Color(0xFF7A92AB),
    secondaryContainer = Color(0xFF24344A),
    onSecondaryContainer = Color(0xFFD6E4F2),
    secondaryGlow = Color(0x229BB3CC),
    tertiary = Color(0xFFB7AEC8),
    onTertiary = Color(0xFF161411),
    tertiaryDim = Color(0xFF948BA8),
    tertiaryContainer = Color(0xFF2E2A3C),
    onTertiaryContainer = Color(0xFFE6E0F0),
    background = Color(0xFF161411),
    onBackground = Color(0xFFF5F2EC),
    surface = Color(0xFF1F1C18),
    onSurface = Color(0xFFF5F2EC),
    surfaceVariant = Color(0xFF2A2622),
    onSurfaceVariant = Color(0xFFB8B2A8),
    surfaceBright = Color(0xFF35302B),
    surfaceDim = Color(0xFF12100E),
    surfaceContainerLowest = Color(0xFF0E0C0A),
    surfaceContainerLow = Color(0xFF1A1815),
    surfaceContainer = Color(0xFF1F1C18),
    surfaceContainerHigh = Color(0xFF2A2622),
    surfaceContainerHighest = Color(0xFF35302B),
    surfaceGlass = Color(0xE61F1C18),
    outline = Color(0x33F5F2EC),
    outlineVariant = Color(0xFF35302B),
    outlineHover = Color(0xFFA8C4BB),
    success = Color(0xFFA8C4BB),
    onSuccess = Color(0xFF1C1917),
    successContainer = Color(0xFF2A3834),
    warning = Color(0xFFD4B07A),
    onWarning = Color(0xFF1C1917),
    warningContainer = Color(0xFF3A3018),
    danger = Color(0xFFD4A09C),
    onDanger = Color(0xFF1C1917),
    dangerDim = Color(0xFFB56A64),
    dangerContainer = Color(0xFF3A2020),
    textPrimary = Color(0xFFF5F2EC),
    textSecondary = Color(0xFFB8B2A8),
    textMuted = Color(0xFF8A847C),
    scrim = Color(0x99000000),
    shadowTint = Color(0x66000000),
    accentShadow = Color(0x33A8C4BB),
)

val LocalAppPalette = staticCompositionLocalOf { LightPalette }

val AppColor: AppPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalAppPalette.current

val Accent: Color @Composable @ReadOnlyComposable get() = AppColor.primary
val AccentOn: Color @Composable @ReadOnlyComposable get() = AppColor.onPrimary
val AccentDim: Color @Composable @ReadOnlyComposable get() = AppColor.primaryDim
val AccentGlow: Color @Composable @ReadOnlyComposable get() = AppColor.primaryGlow

val ModeCasual: Color @Composable @ReadOnlyComposable get() = AppColor.primary
val ModeMeeting: Color @Composable @ReadOnlyComposable get() = AppColor.secondary
val ModeDigest: Color @Composable @ReadOnlyComposable get() = AppColor.tertiary

@Composable
@ReadOnlyComposable
fun modeColor(isMeeting: Boolean): Color =
    if (isMeeting) ModeMeeting else ModeCasual

@Composable
@ReadOnlyComposable
fun recordRoleColor(isDigest: Boolean, isMeeting: Boolean): Color = when {
    isDigest -> ModeDigest
    isMeeting -> ModeMeeting
    else -> ModeCasual
}

val BgDark: Color @Composable @ReadOnlyComposable get() = AppColor.background
val CardDark: Color @Composable @ReadOnlyComposable get() = AppColor.surface
val CardElevated: Color @Composable @ReadOnlyComposable get() = AppColor.surfaceVariant
val CardGlass: Color @Composable @ReadOnlyComposable get() = AppColor.surfaceGlass
val SurfaceBorder: Color @Composable @ReadOnlyComposable get() = AppColor.outline
val SurfaceBorderHover: Color @Composable @ReadOnlyComposable get() = AppColor.outlineHover

val MintCyan: Color @Composable @ReadOnlyComposable get() = AppColor.primary
val MintCyanDim: Color @Composable @ReadOnlyComposable get() = AppColor.primaryDim
val MintCyanGlow: Color @Composable @ReadOnlyComposable get() = AppColor.primaryGlow
val ElectricBlue: Color @Composable @ReadOnlyComposable get() = AppColor.secondary
val ElectricBlueDim: Color @Composable @ReadOnlyComposable get() = AppColor.secondaryDim
val ElectricBlueGlow: Color @Composable @ReadOnlyComposable get() = AppColor.secondaryGlow
val PurpleAccent: Color @Composable @ReadOnlyComposable get() = AppColor.tertiary

val TextPrimary: Color @Composable @ReadOnlyComposable get() = AppColor.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = AppColor.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = AppColor.textMuted

val StatusUploaded: Color @Composable @ReadOnlyComposable get() = AppColor.success
val StatusPending: Color @Composable @ReadOnlyComposable get() = AppColor.warning
val StatusFailed: Color @Composable @ReadOnlyComposable get() = AppColor.danger
val DangerRed: Color @Composable @ReadOnlyComposable get() = AppColor.danger
val DangerRedDim: Color @Composable @ReadOnlyComposable get() = AppColor.dangerDim
val WarningAmber: Color @Composable @ReadOnlyComposable get() = AppColor.warning

val NeonGreen: Color @Composable @ReadOnlyComposable get() = AppColor.primary
val NeonGreenDim: Color @Composable @ReadOnlyComposable get() = AppColor.primaryDim
val NeonGreenGlow: Color @Composable @ReadOnlyComposable get() = AppColor.primaryGlow
val PureBlack: Color @Composable @ReadOnlyComposable get() = AppColor.background
val DarkSurface: Color @Composable @ReadOnlyComposable get() = AppColor.surface
val DarkCard: Color @Composable @ReadOnlyComposable get() = AppColor.surface
val DarkGray: Color @Composable @ReadOnlyComposable get() = AppColor.surfaceVariant
val MediumGray: Color @Composable @ReadOnlyComposable get() = AppColor.outline
val BorderGray: Color @Composable @ReadOnlyComposable get() = AppColor.outline
