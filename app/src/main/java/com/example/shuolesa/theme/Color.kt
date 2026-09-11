package com.example.shuolesa.theme

import androidx.compose.ui.graphics.Color

/**
 * Light paper palette. Off-white, not pure #FFFFFF.
 * Brand mint is the only chrome accent; meeting/digest stay as role colors.
 * Keep hex values in sync with `res/values/colors.xml`.
 */
object AppColor {
    val primary = Color(0xFF5B7F74)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryDim = Color(0xFF48665D)
    val primaryContainer = Color(0xFFE4EFEA)
    val onPrimaryContainer = Color(0xFF243832)
    val primaryGlow = Color(0x225B7F74)

    val secondary = Color(0xFF5E7FA8)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryDim = Color(0xFF4A6586)
    val secondaryContainer = Color(0xFFE6EEF6)
    val onSecondaryContainer = Color(0xFF24344A)
    val secondaryGlow = Color(0x1A5E7FA8)

    val tertiary = Color(0xFF7A6E96)
    val onTertiary = Color(0xFFFFFFFF)
    val tertiaryDim = Color(0xFF61587A)
    val tertiaryContainer = Color(0xFFECE8F3)
    val onTertiaryContainer = Color(0xFF2E2A3C)

    val background = Color(0xFFFDFBF7)
    val onBackground = Color(0xFF1C1917)

    val surface = Color(0xFFFFFDFB)
    val onSurface = Color(0xFF1C1917)
    val surfaceVariant = Color(0xFFF1EEE8)
    val onSurfaceVariant = Color(0xFF57534E)
    val surfaceBright = Color(0xFFFFFFFF)
    val surfaceDim = Color(0xFFEBE7E0)
    val surfaceContainerLowest = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFFAF8F4)
    val surfaceContainer = Color(0xFFFFFDFB)
    val surfaceContainerHigh = Color(0xFFF1EEE8)
    val surfaceContainerHighest = Color(0xFFE6E2DB)
    val surfaceGlass = Color(0xF5FFFDFB)

    val outline = Color(0x1A1C1917)
    val outlineVariant = Color(0xFFE8E4DC)
    val outlineHover = Color(0xFF5B7F74)

    val success = Color(0xFF5B7F74)
    val onSuccess = Color(0xFFFFFFFF)
    val successContainer = Color(0xFFE4EFEA)

    val warning = Color(0xFFB08948)
    val onWarning = Color(0xFFFFFFFF)
    val warningContainer = Color(0xFFF6EBD4)

    val danger = Color(0xFFB56A64)
    val onDanger = Color(0xFFFFFFFF)
    val dangerDim = Color(0xFF8F504B)
    val dangerContainer = Color(0xFFF4E3E1)

    val textPrimary = Color(0xFF1C1917)
    val textSecondary = Color(0xFF57534E)
    val textMuted = Color(0xFF8A847C)

    val scrim = Color(0x331C1917)

    val shadowTint = Color(0x140C0A08)
    val accentShadow = Color(0x1A5B7F74)
}

/** Brand accent (muted mint). Prefer AppColor.primary in new code. */
val Accent = AppColor.primary
val AccentOn = AppColor.onPrimary
val AccentDim = AppColor.primaryDim
val AccentGlow = AppColor.primaryGlow

/** Recording roles: casual / meeting / daily digest. */
val ModeCasual = AppColor.primary
val ModeMeeting = AppColor.secondary
val ModeDigest = AppColor.tertiary

fun modeColor(isMeeting: Boolean): Color =
    if (isMeeting) ModeMeeting else ModeCasual

fun recordRoleColor(isDigest: Boolean, isMeeting: Boolean): Color = when {
    isDigest -> ModeDigest
    isMeeting -> ModeMeeting
    else -> ModeCasual
}

// ---------------------------------------------------------------------------
// Legacy names still imported by screens. Values now resolve to AppColor.
// Do not add new call sites; migrate to AppColor / MaterialTheme.colorScheme.
// ---------------------------------------------------------------------------

val BgDark = AppColor.background
val CardDark = AppColor.surface
val CardElevated = AppColor.surfaceVariant
val CardGlass = AppColor.surfaceGlass
val SurfaceBorder = AppColor.outline
val SurfaceBorderHover = AppColor.outlineHover

val MintCyan = AppColor.primary
val MintCyanDim = AppColor.primaryDim
val MintCyanGlow = AppColor.primaryGlow
val ElectricBlue = AppColor.secondary
val ElectricBlueDim = AppColor.secondaryDim
val ElectricBlueGlow = AppColor.secondaryGlow
val PurpleAccent = AppColor.tertiary

val TextPrimary = AppColor.textPrimary
val TextSecondary = AppColor.textSecondary
val TextMuted = AppColor.textMuted

val StatusUploaded = AppColor.success
val StatusPending = AppColor.warning
val StatusFailed = AppColor.danger
val DangerRed = AppColor.danger
val DangerRedDim = AppColor.dangerDim
val WarningAmber = AppColor.warning

val NeonGreen = AppColor.primary
val NeonGreenDim = AppColor.primaryDim
val NeonGreenGlow = AppColor.primaryGlow
val PureBlack = AppColor.background
val DarkSurface = AppColor.surface
val DarkCard = AppColor.surface
val DarkGray = AppColor.surfaceVariant
val MediumGray = AppColor.outline
val BorderGray = AppColor.outline
