package com.example.businesscardscanner.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class DesignColorTokens(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val primarySoft: Color,
    val surfaceMuted: Color,
    val surfaceStrong: Color,
    val scrim: Color
)

@Immutable
data class DesignTypographyTokens(
    val display: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val button: TextStyle
)

@Immutable
data class DesignSpacingTokens(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp
)

@Immutable
data class DesignRadiusTokens(
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val full: Dp
)

@Immutable
data class DesignElevationTokens(
    val subtle: Dp,
    val medium: Dp,
    val floating: Dp
)

private val DisplayFont = FontFamily.SansSerif
private val BodyFont = FontFamily.SansSerif

internal val LightDesignColors = DesignColorTokens(
    primary = Color(0xFF0E5CC7),
    secondary = Color(0xFF2F7FE6),
    accent = Color(0xFF0A4B9F),
    background = Color(0xFFF4F8FF),
    surface = Color(0xFFFFFFFF),
    border = Color(0xFFC5D3E7),
    textPrimary = Color(0xFF0B1730),
    textSecondary = Color(0xFF51627D),
    success = Color(0xFF14804A),
    warning = Color(0xFFC98112),
    error = Color(0xFFBA1A1A),
    primarySoft = Color(0xFFEAF4FF),
    surfaceMuted = Color(0xFFF7FAFF),
    surfaceStrong = Color(0xFFFFFFFF),
    scrim = Color(0x660B1730)
)

internal val DarkDesignColors = DesignColorTokens(
    primary = Color(0xFF8FBDFE),
    secondary = Color(0xFFABD1FF),
    accent = Color(0xFF6DA4F6),
    background = Color(0xFF08111F),
    surface = Color(0xFF111E30),
    border = Color(0xFF314763),
    textPrimary = Color(0xFFE7F0FF),
    textSecondary = Color(0xFFA8BAD4),
    success = Color(0xFF54C98A),
    warning = Color(0xFFF2B44F),
    error = Color(0xFFFF8C84),
    primarySoft = Color(0xFF18304F),
    surfaceMuted = Color(0xFF16253A),
    surfaceStrong = Color(0xFF17263B),
    scrim = Color(0x99040A14)
)

internal val DefaultTypographyTokens = DesignTypographyTokens(
    display = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    title = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    ),
    subtitle = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    body = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    caption = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    button = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
)

internal val DefaultSpacingTokens = DesignSpacingTokens(
    xs = 4.dp,
    sm = 8.dp,
    md = 12.dp,
    lg = 16.dp,
    xl = 24.dp
)

internal val DefaultRadiusTokens = DesignRadiusTokens(
    small = 14.dp,
    medium = 20.dp,
    large = 28.dp,
    full = 999.dp
)

internal val DefaultElevationTokens = DesignElevationTokens(
    subtle = 2.dp,
    medium = 8.dp,
    floating = 14.dp
)
