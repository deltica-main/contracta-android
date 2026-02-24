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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SlatePrimary = Color(0xFF0A54B5)
private val AppSurface = Color(0xFFF4F8FF)
private val AppSurfaceElevated = Color(0xFFFFFFFF)
private val AppOnSurface = Color(0xFF0A1730)
private val AppMutedText = Color(0xFF4B5F7D)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9C9FF),
    onPrimary = Color(0xFF03224F),
    primaryContainer = Color(0xFF1A4E91),
    onPrimaryContainer = Color(0xFFD9E8FF),
    secondary = Color(0xFF98C4FF),
    onSecondary = Color(0xFF052956),
    secondaryContainer = Color(0xFF1A3D73),
    onSecondaryContainer = Color(0xFFD6E7FF),
    tertiary = Color(0xFFB1C4E5),
    onTertiary = Color(0xFF0B274B),
    tertiaryContainer = Color(0xFF27466E),
    onTertiaryContainer = Color(0xFFD8E8FF),
    background = Color(0xFF070F1D),
    onBackground = Color(0xFFE5EEFF),
    surface = Color(0xFF0C1526),
    onSurface = Color(0xFFE5EEFF),
    surfaceVariant = Color(0xFF18253A),
    onSurfaceVariant = Color(0xFFA5B6D1),
    outline = Color(0xFF3E5372),
    outlineVariant = Color(0xFF2B3E5A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = SlatePrimary,
    onPrimary = AppSurfaceElevated,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF0C2E62),
    secondary = Color(0xFF1A73DA),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6F0FF),
    onSecondaryContainer = Color(0xFF0A2A57),
    tertiary = Color(0xFF2E5FA6),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEAF2FF),
    onTertiaryContainer = Color(0xFF0B2E5F),
    background = AppSurface,
    onBackground = AppOnSurface,
    surface = AppSurface,
    onSurface = AppOnSurface,
    surfaceVariant = Color(0xFFE8F0FF),
    onSurfaceVariant = AppMutedText,
    outline = Color(0xFFC9D8EE),
    outlineVariant = Color(0xFFDBE6F8),
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
