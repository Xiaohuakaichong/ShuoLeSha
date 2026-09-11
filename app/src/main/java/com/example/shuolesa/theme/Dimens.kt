package com.example.shuolesa.theme

import androidx.compose.ui.unit.dp

/**
 * Shared spacing, radius, and size tokens.
 * Screens should consume these instead of inventing padding or icon sizes.
 */
object Dimens {
    // Spacing scale
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 16.dp
    val spaceLg = 24.dp
    val spaceXl = 32.dp
    val spaceXxl = 40.dp

    val gapXs = spaceXs
    val gapSm = spaceSm
    val gapMd = spaceMd
    val gapLg = spaceLg
    val gapXl = spaceXl

    // Page layout
    val pagePaddingH = 24.dp
    val pagePaddingV = 24.dp
    val pageBottomNavClearance = 96.dp

    // Radius
    val radiusXs = 6.dp
    val radiusSm = 8.dp
    val radiusMd = 12.dp
    val radiusLg = 16.dp
    val radiusXl = 20.dp
    val radius2xl = 28.dp
    val radiusFull = 999.dp
    val cardRadius = radius2xl
    val fieldRadius = radiusMd
    val chipRadius = radiusFull
    val sheetRadius = radiusXl
    val pillRadius = radiusFull

    val cardPadding = 20.dp
    val cardPaddingVCompact = 14.dp

    // Icons
    val iconXs = 12.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val iconXl = 28.dp
    val iconXxl = 32.dp

    // Bottom nav / FAB / record button
    val bottomNavHeight = 72.dp
    val bottomBarHeight = bottomNavHeight
    val fabSize = 64.dp
    val fabIconSize = 30.dp
    val fabElevation = 10.dp
    val recordButton = fabSize

    // Controls
    val buttonHeight = 48.dp
    val buttonHeightSm = 40.dp
    val iconButton = 40.dp
    val iconButtonLg = 72.dp
    val chipPaddingH = 8.dp
    val chipPaddingV = 4.dp
    val listGap = 20.dp
    val statusDot = 8.dp
    val statusDotLg = 10.dp

    // Borders
    val borderThin = 1.dp
    val borderThick = 2.dp
}
