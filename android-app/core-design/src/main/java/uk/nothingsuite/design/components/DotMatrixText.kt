package uk.nothingsuite.design.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import uk.nothingsuite.design.NothingTheme

/**
 * Display text in the dot-matrix face. Always upper-case, tracking scales
 * with size the way Nothing's own widgets do (bigger = wider).
 */
@Composable
fun DotMatrixText(
    text: String,
    size: Int = 24,
    modifier: Modifier = Modifier,
    color: Color = NothingTheme.colors.onBackground,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        style = NothingTheme.typography.title.copy(
            fontSize = size.sp,
            lineHeight = (size * 1.2f).sp,
            letterSpacing = (size * 0.06f).sp,
        ),
    )
}
