package uk.nothingsuite.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import uk.nothingsuite.design.NothingTheme
import kotlin.math.floor

/**
 * Dot-matrix loading state: a 4-wide row of dots that lights up in a
 * scanning pattern. Same visual grammar as the Glyph interface and the
 * NDot font, so it reads as "native" rather than a Material spinner.
 */
@Composable
fun NothingLoader(
    modifier: Modifier = Modifier,
    dots: Int = 4,
    color: Color = NothingTheme.colors.onBackground,
    dim: Color = NothingTheme.colors.outline,
) {
    val transition = rememberInfiniteTransition(label = "loader")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = dots.toFloat(),
        animationSpec = infiniteRepeatable(tween(dots * 180, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(modifier.size(width = (dots * 16).dp, height = 16.dp)) {
        val r = 4.dp.toPx()
        val step = size.width / dots
        val lit = floor(phase).toInt() % dots
        repeat(dots) { i ->
            drawCircle(
                color = if (i == lit) color else dim,
                radius = r,
                center = Offset(step * i + step / 2, size.height / 2),
            )
        }
    }
}
