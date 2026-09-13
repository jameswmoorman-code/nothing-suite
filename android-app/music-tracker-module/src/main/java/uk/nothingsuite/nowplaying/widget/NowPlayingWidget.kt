package uk.nothingsuite.nowplaying.widget

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import uk.nothingsuite.nowplaying.MainActivity
import uk.nothingsuite.nowplaying.NowPlayingApp

/**
 * Nothing-styled widget: pure black card, hairline-free, dot-matrix text
 * rendered via [DotMatrixBitmap], a single red dot as the "live" indicator.
 */
class NowPlayingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val size = LocalSize.current
        val state = NowPlayingApp.instance.state
        val density = context.resources.displayMetrics.density
        val maxWidthPx = ((size.width.value - 40) * density).toInt().coerceAtLeast(50)

        val title = state.title.ifBlank { "NOTHING PLAYING" }
        val sub = if (state.status == "PLAYING") state.artist else state.status

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(androidx.compose.ui.graphics.Color.Black))
                .cornerRadius(24.dp)
                .padding(20.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(DotMatrixBitmap.render(context, if (state.status == "LISTENING") "●" else "○", 10f, Color.parseColor("#D71921"), 40)),
                    contentDescription = null,
                )
                Spacer(GlanceModifier.width(8.dp))
                Image(
                    provider = ImageProvider(DotMatrixBitmap.render(context, sub.ifBlank { "—" }, 11f, Color.parseColor("#7A7A7A"), maxWidthPx - 40)),
                    contentDescription = sub,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Image(
                provider = ImageProvider(DotMatrixBitmap.render(context, title, 20f, Color.WHITE, maxWidthPx)),
                contentDescription = title,
            )
        }
    }

    companion object {
        suspend fun updateAll(context: Context) = NowPlayingWidget().updateAll(context)
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
