package uk.nothingsuite.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import uk.nothingsuite.design.LocalNothingHaptics
import uk.nothingsuite.design.NothingTheme

enum class NothingButtonStyle { Solid, Outline, Accent }

/**
 * Pill button, dot-matrix label, hairline or filled, with the Nothing "click"
 * haptic fired on every tap. Ripple deliberately disabled — Nothing OS
 * buttons don't ripple, they snap.
 */
@Composable
fun NothingButton(
    text: String,
    style: NothingButtonStyle = NothingButtonStyle.Solid,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = NothingTheme.colors
    val shapes = NothingTheme.shapes
    val haptics = LocalNothingHaptics.current

    val (bg, fg, border) = when (style) {
        NothingButtonStyle.Solid -> Triple(c.onBackground, c.background, Color.Transparent)
        NothingButtonStyle.Outline -> Triple(Color.Transparent, c.onBackground, c.onBackground)
        NothingButtonStyle.Accent -> Triple(c.accent, c.onAccent, Color.Transparent)
    }
    val alpha = if (enabled) 1f else 0.35f

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shapes.button)
            .background(bg.copy(alpha = bg.alpha * alpha))
            .border(shapes.hairline, border.copy(alpha = border.alpha * alpha), shapes.button)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.click()
                onClick()
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = NothingTheme.typography.label,
            color = fg.copy(alpha = alpha),
            maxLines = 1,
        )
    }
}
