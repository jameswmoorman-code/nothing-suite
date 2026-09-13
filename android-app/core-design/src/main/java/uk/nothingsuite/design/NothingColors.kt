package uk.nothingsuite.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Nothing OS is essentially two inks (black/white) with one red accent.
 * No blues, no gradients, no tinted surfaces. Greys are pure neutral.
 */
@Immutable
data class NothingColors(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onBackgroundMuted: Color,
    val outline: Color,
    val accent: Color,          // Nothing red
    val onAccent: Color,
    val isDark: Boolean,
)

object NothingPalette {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Grey900 = Color(0xFF111111)
    val Grey700 = Color(0xFF3A3A3A)
    val Grey500 = Color(0xFF7A7A7A)
    val Grey300 = Color(0xFFBDBDBD)
    val Grey100 = Color(0xFFEDEDED)
    val Red = Color(0xFFD71921)
}

val NothingDarkColors = NothingColors(
    background = NothingPalette.Black,
    surface = NothingPalette.Grey900,
    onBackground = NothingPalette.White,
    onBackgroundMuted = NothingPalette.Grey500,
    outline = NothingPalette.Grey700,
    accent = NothingPalette.Red,
    onAccent = NothingPalette.White,
    isDark = true,
)

val NothingLightColors = NothingColors(
    background = NothingPalette.White,
    surface = NothingPalette.Grey100,
    onBackground = NothingPalette.Black,
    onBackgroundMuted = NothingPalette.Grey500,
    outline = NothingPalette.Grey300,
    accent = NothingPalette.Red,
    onAccent = NothingPalette.White,
    isDark = false,
)
