package uk.nothingsuite.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import uk.nothingsuite.design.NothingTheme

/** Flat surface with a hairline outline. No elevation, no shadow — ever. */
@Composable
fun NothingCard(
    modifier: Modifier = Modifier,
    outlined: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = NothingTheme.colors
    val s = NothingTheme.shapes
    Box(
        modifier
            .clip(s.card)
            .background(c.surface)
            .then(if (outlined) Modifier.border(s.hairline, c.outline, s.card) else Modifier),
    ) { content() }
}
