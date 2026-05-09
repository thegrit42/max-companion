package com.max.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Max Theme - Dark, trustworthy, personal.
 * Color scheme designed to feel calm, direct, and steady.
 * Not flashy - Max is a companion, not a billboard.
 */

// Dark theme colors - deep, calm, trustworthy
val MaxBackground = Color(0xFF0A0A0B)         // Near black, warm undertone
val MaxSurface = Color(0xFF141416)            // Slightly lighter surface
val MaxSurfaceVariant = Color(0xFF1E1E22)     // Cards and elevated surfaces
val MaxPrimary = Color(0xFF6B8AFE)            // Soft blue - trustworthy
val MaxPrimaryContainer = Color(0xFF1A2544)   // Deep blue container
val MaxSecondary = Color(0xFF4ECDC4)          // Teal accent - calm
val MaxSecondaryContainer = Color(0xFF143D3A) // Deep teal container
val MaxTertiary = Color(0xFFFF6B6B)            // Soft red - warnings/blocks
val MaxTertiaryContainer = Color(0xFF442222)  // Deep red container
val MaxError = Color(0xFFFF5252)
val MaxErrorContainer = Color(0xFF3B1111)

// Text colors
val MaxOnBackground = Color(0xFFE8E8EC)
val MaxOnSurface = Color(0xFFF0F0F4)
val MaxOnSurfaceVariant = Color(0xFFB0B0B8)
val MaxOnPrimary = Color(0xFFFFFFFF)
val MaxOnPrimaryContainer = Color(0xFFB8C4FF)
val MaxOnSecondary = Color(0xFF003D38)
val MaxOnTertiary = Color(0xFF5E1010)

// Light theme (for users who prefer it)
val MaxLightBackground = Color(0xFFFAFAFC)
val MaxLightSurface = Color(0xFFFFFFFF)
val MaxLightSurfaceVariant = Color(0xFFF0F0F4)
val MaxLightPrimary = Color(0xFF4F6ACF)
val MaxLightPrimaryContainer = Color(0xFFDDE0FF)
val MaxLightSecondary = Color(0xFF3AA89F)
val MaxLightSecondaryContainer = Color(0xFFC8F0EC)
val MaxLightTertiary = Color(0xFFE05050)
val MaxLightTertiaryContainer = Color(0xFFFFDAD6)
val MaxLightError = Color(0xFFBA1A1A)
val MaxLightErrorContainer = Color(0xFFFFDAD6)
val MaxLightOnBackground = Color(0xFF1A1A1E)
val MaxLightOnSurface = Color(0xFF0A0A0C)

private val MaxDarkColorScheme = darkColorScheme(
    primary = MaxPrimary,
    primaryContainer = MaxPrimaryContainer,
    secondary = MaxSecondary,
    secondaryContainer = MaxSecondaryContainer,
    tertiary = MaxTertiary,
    tertiaryContainer = MaxTertiaryContainer,
    background = MaxBackground,
    surface = MaxSurface,
    surfaceVariant = MaxSurfaceVariant,
    error = MaxError,
    errorContainer = MaxErrorContainer,
    onBackground = MaxOnBackground,
    onSurface = MaxOnSurface,
    onSurfaceVariant = MaxOnSurfaceVariant,
    onPrimary = MaxOnPrimary,
    onPrimaryContainer = MaxOnPrimaryContainer,
    onSecondary = MaxOnSecondary,
    onTertiary = MaxOnTertiary
)

private val MaxLightColorScheme = lightColorScheme(
    primary = MaxLightPrimary,
    primaryContainer = MaxLightPrimaryContainer,
    secondary = MaxLightSecondary,
    secondaryContainer = MaxLightSecondaryContainer,
    tertiary = MaxLightTertiary,
    tertiaryContainer = MaxLightTertiaryContainer,
    background = MaxLightBackground,
    surface = MaxLightSurface,
    surfaceVariant = MaxLightSurfaceVariant,
    error = MaxLightError,
    errorContainer = MaxLightErrorContainer,
    onBackground = MaxLightOnBackground,
    onSurface = MaxLightOnSurface
)

@Composable
fun MaxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled to keep Max's identity consistent
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MaxDarkColorScheme else MaxLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaxTypography,
        content = content
    )
}

/**
 * Typography for Max.
 * Clean, readable, not flashy.
 */
val MaxTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 57.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 45.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    displaySmall = androidx.compose.ui.text.TextStyle(
        fontSize = 36.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    headlineLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 32.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 28.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    headlineSmall = androidx.compose.ui.text.TextStyle(
        fontSize = 24.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 22.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = 0.15.sp
    ),
    titleSmall = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        letterSpacing = 0.25.sp
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        letterSpacing = 0.4.sp
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        letterSpacing = 0.5.sp
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontSize = 11.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
)

// Helper for sp extension
private val Int.sp: androidx.compose.ui.unit.TextUnit
    get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

private val Float.sp: androidx.compose.ui.unit.TextUnit
    get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
