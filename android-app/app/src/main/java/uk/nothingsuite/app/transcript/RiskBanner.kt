package uk.nothingsuite.app.transcript

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.shapes
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText

val CautionAmber = Color(0xFFF2A900)

fun Risk.label() = when (this) { Risk.SCAM -> "SCAM LIKELY"; Risk.CAUTION -> "CAUTION"; Risk.NONE -> "" }

@Composable
fun Risk.tint(): Color = when (this) { Risk.SCAM -> colors.accent; Risk.CAUTION -> CautionAmber; Risk.NONE -> colors.onBackgroundMuted }

/** The scam-shield strip: red for SCAM LIKELY, amber for CAUTION, with the reasons. */
@Composable
fun RiskBanner(risk: Risk, reasons: List<String>, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (risk == Risk.NONE) return
    val tint = risk.tint()
    Column(
        modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f), shapes.card)
            .border(1.dp, tint, shapes.card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        DotMatrixText(if (risk == Risk.SCAM) "SCAM LIKELY" else "CAUTION", size = 14, color = tint)
        if (!compact && reasons.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(reasons.joinToString("  \u00B7  ") { it.replaceFirstChar(Char::uppercase) }, style = typography.caption, color = colors.onBackground)
        }
    }
}
