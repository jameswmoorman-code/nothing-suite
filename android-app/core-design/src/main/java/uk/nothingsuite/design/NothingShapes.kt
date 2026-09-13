package uk.nothingsuite.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Nothing's geometry: hairline borders, big pill buttons, cards with
 * generous radius, and perfect circles for icon wells. Nothing is "soft".
 */
@Immutable
data class NothingShapes(
    val card: Shape = RoundedCornerShape(24.dp),
    val button: Shape = CircleShape,              // pill
    val chip: Shape = RoundedCornerShape(8.dp),
    val circle: Shape = CircleShape,
    val hairline: Dp = 1.dp,
    val outlineHeavy: Dp = 2.dp,
)

val NothingShapesDefault = NothingShapes()
