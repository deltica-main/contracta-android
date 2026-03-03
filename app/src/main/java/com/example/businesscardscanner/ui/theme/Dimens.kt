package com.example.businesscardscanner.ui.theme

import androidx.compose.ui.unit.dp

object AppDimens {
    val space4 = DefaultSpacingTokens.xs
    val space8 = DefaultSpacingTokens.sm
    val space12 = DefaultSpacingTokens.md
    val space16 = DefaultSpacingTokens.lg
    val space24 = DefaultSpacingTokens.xl
    val space32 = DefaultSpacingTokens.xl + DefaultSpacingTokens.sm

    val xs = space4
    val sm = space8
    val md = space12
    val lg = space16
    val xl = space24
    val xxl = space32

    val radiusSm = DefaultRadiusTokens.small
    val radiusMd = DefaultRadiusTokens.medium
    val radiusLg = DefaultRadiusTokens.large
    val cardRadius = radiusLg
    val fieldRadius = radiusMd
    val buttonRadius = DefaultRadiusTokens.full
    val chipRadius = DefaultRadiusTokens.full
    val pillRadius = DefaultRadiusTokens.full

    val divider = 1.dp
    val outline = 2.dp
    val touchTargetMin = 52.dp
    val touchTargetCompact = 48.dp
    val iconButtonSize = 48.dp
    val iconSizeSm = 18.dp
    val iconSizeMd = 20.dp
    val buttonMinWidth = 112.dp
    val buttonHeight = 52.dp
    val avatarSize = 56.dp
    val cardElevation = DefaultElevationTokens.medium
    val floatingElevation = DefaultElevationTokens.floating
    val subtleElevation = DefaultElevationTokens.subtle
    const val businessCardAspectRatio = 1.75f
}
