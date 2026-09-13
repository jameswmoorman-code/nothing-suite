package uk.nothingsuite.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two faces, exactly as Nothing OS does it:
 *   - Dot-matrix for display text (titles, numbers, labels) — ALL CAPS, wide tracking
 *   - A plain grotesk for body copy
 *
 * FONT LICENSING — read before shipping:
 *   Nothing's "NDot 57" / "NType 82" are proprietary. Do NOT commit them to a
 *   public repo. Options:
 *     a) ship an OFL dot-matrix face — "Doto" (Google Fonts, OFL-1.1) is a
 *        near-perfect stand-in — as res/font/ndot.ttf
 *     b) let users drop the real file in via a file picker at runtime
 *   The resource name `ndot` is a slot, not an assertion that it's NDot.
 */
private val DotMatrix = FontFamily(Font(R.font.ndot, FontWeight.Normal))
private val Grotesk = FontFamily.SansSerif // swap for Inter/Manrope if bundled

@Immutable
data class NothingTypography(
    val display: TextStyle,   // hero numbers, caller ID
    val title: TextStyle,     // screen titles
    val label: TextStyle,     // buttons, section headers
    val body: TextStyle,
    val caption: TextStyle,
)

val NothingTypographyDefault = NothingTypography(
    display = TextStyle(fontFamily = DotMatrix, fontSize = 40.sp, letterSpacing = 2.sp, lineHeight = 44.sp),
    title = TextStyle(fontFamily = DotMatrix, fontSize = 24.sp, letterSpacing = 1.5.sp, lineHeight = 30.sp),
    label = TextStyle(fontFamily = DotMatrix, fontSize = 13.sp, letterSpacing = 2.sp),
    body = TextStyle(fontFamily = Grotesk, fontSize = 16.sp, lineHeight = 24.sp),
    caption = TextStyle(fontFamily = Grotesk, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
