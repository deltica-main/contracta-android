package ca.deltica.contactra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LocalAppColors = staticCompositionLocalOf { LightDesignColors }
private val LocalAppTypography = staticCompositionLocalOf { DefaultTypographyTokens }
private val LocalAppSpacing = staticCompositionLocalOf { DefaultSpacingTokens }
private val LocalAppRadii = staticCompositionLocalOf { DefaultRadiusTokens }
private val LocalAppElevation = staticCompositionLocalOf { DefaultElevationTokens }

object AppTheme {
    val colors: DesignColorTokens
        @Composable get() = LocalAppColors.current

    val typography: DesignTypographyTokens
        @Composable get() = LocalAppTypography.current

    val spacing: DesignSpacingTokens
        @Composable get() = LocalAppSpacing.current

    val radii: DesignRadiusTokens
        @Composable get() = LocalAppRadii.current

    val elevation: DesignElevationTokens
        @Composable get() = LocalAppElevation.current
}

private fun materialTypography(tokens: DesignTypographyTokens): Typography {
    return Typography(
        displayLarge = tokens.display,
        displayMedium = tokens.display.copy(fontSize = 30.sp, lineHeight = 36.sp),
        headlineLarge = tokens.title,
        headlineMedium = tokens.subtitle.copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleLarge = tokens.title.copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = tokens.subtitle,
        titleSmall = tokens.subtitle.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = tokens.body,
        bodyMedium = tokens.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = tokens.caption.copy(fontWeight = FontWeight.Medium),
        labelLarge = tokens.button,
        labelMedium = tokens.caption.copy(fontWeight = FontWeight.Bold),
        labelSmall = tokens.caption.copy(fontSize = 11.sp, lineHeight = 14.sp)
    )
}

private fun materialShapes(radii: DesignRadiusTokens): Shapes {
    return Shapes(
        extraSmall = RoundedCornerShape(radii.small),
        small = RoundedCornerShape(radii.small),
        medium = RoundedCornerShape(radii.medium),
        large = RoundedCornerShape(radii.large),
        extraLarge = RoundedCornerShape(radii.full)
    )
}

private fun lightScheme(colors: DesignColorTokens) = lightColorScheme(
    primary = colors.primary,
    onPrimary = Color.White,
    primaryContainer = colors.primarySoft,
    onPrimaryContainer = colors.accent,
    secondary = colors.secondary,
    onSecondary = Color.White,
    secondaryContainer = colors.primarySoft.copy(alpha = 0.9f),
    onSecondaryContainer = colors.textPrimary,
    tertiary = colors.accent,
    onTertiary = Color.White,
    tertiaryContainer = colors.surfaceMuted,
    onTertiaryContainer = colors.textPrimary,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceMuted,
    onSurfaceVariant = colors.textSecondary,
    outline = colors.border,
    outlineVariant = colors.border.copy(alpha = 0.72f),
    error = colors.error,
    onError = Color.White,
    errorContainer = colors.error.copy(alpha = 0.14f),
    onErrorContainer = colors.error
)

private fun darkScheme(colors: DesignColorTokens) = darkColorScheme(
    primary = colors.primary,
    onPrimary = Color(0xFF062349),
    primaryContainer = colors.primarySoft,
    onPrimaryContainer = colors.textPrimary,
    secondary = colors.secondary,
    onSecondary = Color(0xFF0A2340),
    secondaryContainer = colors.primarySoft.copy(alpha = 0.9f),
    onSecondaryContainer = colors.textPrimary,
    tertiary = colors.accent,
    onTertiary = Color(0xFF08111F),
    tertiaryContainer = colors.surfaceMuted,
    onTertiaryContainer = colors.textPrimary,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceMuted,
    onSurfaceVariant = colors.textSecondary,
    outline = colors.border,
    outlineVariant = colors.border.copy(alpha = 0.72f),
    error = colors.error,
    onError = Color(0xFF3E0A0A),
    errorContainer = colors.error.copy(alpha = 0.18f),
    onErrorContainer = colors.error
)

@Composable
fun ContactraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkDesignColors else LightDesignColors
    val typography = DefaultTypographyTokens
    val spacing = DefaultSpacingTokens
    val radii = DefaultRadiusTokens
    val elevation = DefaultElevationTokens

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography,
        LocalAppSpacing provides spacing,
        LocalAppRadii provides radii,
        LocalAppElevation provides elevation
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(colors) else lightScheme(colors),
            typography = materialTypography(typography),
            shapes = materialShapes(radii),
            content = content
        )
    }
}
