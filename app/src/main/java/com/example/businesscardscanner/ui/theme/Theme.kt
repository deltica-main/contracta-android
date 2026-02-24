package com.example.businesscardscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

private val SlatePrimary = Color(0xFF2E3A59)
private val AppSurface = Color(0xFFF6F7F9)
private val AppSurfaceElevated = Color(0xFFFFFFFF)
private val AppOnSurface = Color(0xFF111418)
private val AppMutedText = Color(0xFF616A75)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFAFBDD8),
    onPrimary = Color(0xFF1A2438),
    primaryContainer = Color(0xFF3A4764),
    onPrimaryContainer = Color(0xFFE0E6F5),
    secondary = Color(0xFFB5C1D2),
    onSecondary = Color(0xFF1F2A3B),
    secondaryContainer = Color(0xFF313D4D),
    onSecondaryContainer = Color(0xFFD8E2F1),
    tertiary = Color(0xFF9EAAB9),
    onTertiary = Color(0xFF202A37),
    tertiaryContainer = Color(0xFF344050),
    onTertiaryContainer = Color(0xFFD8E2F0),
    background = Color(0xFF0F131A),
    onBackground = Color(0xFFE7EBF2),
    surface = Color(0xFF11161D),
    onSurface = Color(0xFFE7EBF2),
    surfaceVariant = Color(0xFF1C222C),
    onSurfaceVariant = Color(0xFFA3ACB8),
    outline = Color(0xFF4A5361),
    outlineVariant = Color(0xFF313A47),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = SlatePrimary,
    onPrimary = AppSurfaceElevated,
    primaryContainer = Color(0xFFE3E8F2),
    onPrimaryContainer = Color(0xFF1F2A40),
    secondary = Color(0xFF4E5A6E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7ECF3),
    onSecondaryContainer = Color(0xFF253042),
    tertiary = Color(0xFF6A7587),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9EDF3),
    onTertiaryContainer = Color(0xFF293346),
    background = AppSurface,
    onBackground = AppOnSurface,
    surface = AppSurface,
    onSurface = AppOnSurface,
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = AppMutedText,
    outline = Color(0xFFC8CED8),
    outlineVariant = Color(0xFFE0E4EB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DisplayFont = FontFamily.SansSerif
private val BodyFont = FontFamily.SansSerif

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Immutable
data class AppSpacing(
    val s4: Dp,
    val s8: Dp,
    val s12: Dp,
    val s16: Dp,
    val s24: Dp,
    val s32: Dp
)

@Immutable
data class AppCorners(
    val small: Dp,
    val medium: Dp
)

private val LocalAppSpacing = staticCompositionLocalOf {
    AppSpacing(
        s4 = 4.dp,
        s8 = 8.dp,
        s12 = 12.dp,
        s16 = 16.dp,
        s24 = 24.dp,
        s32 = 32.dp
    )
}

private val LocalAppCorners = staticCompositionLocalOf {
    AppCorners(
        small = 8.dp,
        medium = 12.dp
    )
}

object AppTheme {
    val spacing: AppSpacing
        @Composable get() = LocalAppSpacing.current
    val corners: AppCorners
        @Composable get() = LocalAppCorners.current
}

@Composable
fun ContactraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(
            s4 = 4.dp,
            s8 = 8.dp,
            s12 = 12.dp,
            s16 = 16.dp,
            s24 = 24.dp,
            s32 = 32.dp
        ),
        LocalAppCorners provides AppCorners(
            small = 8.dp,
            medium = 12.dp
        )
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
