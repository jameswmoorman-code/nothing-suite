package uk.nothingsuite.design

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

val LocalNothingColors = staticCompositionLocalOf { NothingDarkColors }
val LocalNothingTypography = staticCompositionLocalOf { NothingTypographyDefault }
val LocalNothingShapes = staticCompositionLocalOf { NothingShapesDefault }

/**
 * Entry point for every screen in the suite.
 *
 * Also configures a minimal Material3 scheme underneath so stock M3 widgets
 * (text fields, switches) that we haven't re-skinned yet still fall in line:
 * monochrome, red primary, no tonal elevation tints.
 */
@Composable
fun NothingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) NothingDarkColors else NothingLightColors
    val context = LocalContext.current
    val haptics = remember(context) { NothingHaptics(context) }

    val m3 = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.onBackground,
            surface = colors.background, onSurface = colors.onBackground,
            surfaceVariant = colors.surface, onSurfaceVariant = colors.onBackgroundMuted,
            outline = colors.outline, surfaceTint = colors.background,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.onBackground,
            surface = colors.background, onSurface = colors.onBackground,
            surfaceVariant = colors.surface, onSurfaceVariant = colors.onBackgroundMuted,
            outline = colors.outline, surfaceTint = colors.background,
        )
    }

    CompositionLocalProvider(
        LocalNothingColors provides colors,
        LocalNothingTypography provides NothingTypographyDefault,
        LocalNothingShapes provides NothingShapesDefault,
        LocalNothingHaptics provides haptics,
    ) {
        MaterialTheme(colorScheme = m3) {
            Box(Modifier.fillMaxSize().background(colors.background)) { content() }
        }
    }
}

/** `NothingTheme.colors.accent` etc. — the only way screens should read tokens. */
object NothingTheme {
    val colors: NothingColors
        @Composable @ReadOnlyComposable get() = LocalNothingColors.current
    val typography: NothingTypography
        @Composable @ReadOnlyComposable get() = LocalNothingTypography.current
    val shapes: NothingShapes
        @Composable @ReadOnlyComposable get() = LocalNothingShapes.current
}
