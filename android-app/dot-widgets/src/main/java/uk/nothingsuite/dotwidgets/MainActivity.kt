package uk.nothingsuite.dotwidgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.design.components.NothingCard
import uk.nothingsuite.dotwidgets.widgets.BatteryWidget
import uk.nothingsuite.dotwidgets.widgets.ClockWidget
import uk.nothingsuite.dotwidgets.widgets.CountdownWidget
import uk.nothingsuite.dotwidgets.widgets.DateWidget
import uk.nothingsuite.dotwidgets.widgets.DotWidget
import uk.nothingsuite.dotwidgets.widgets.LabelWidget
import uk.nothingsuite.dotwidgets.widgets.ProgressWidget
import uk.nothingsuite.dotwidgets.widgets.StreakWidget
import uk.nothingsuite.dotwidgets.widgets.StepsWidget
import uk.nothingsuite.dotwidgets.widgets.NextUpWidget
import uk.nothingsuite.dotwidgets.widgets.WeatherWidget
import uk.nothingsuite.dotwidgets.widgets.QuoteWidget
import uk.nothingsuite.dotwidgets.widgets.StorageWidget

/**
 * The gallery: every widget, a one-line description, "Add" where the
 * launcher supports it (Android 8+ pin request), and the single £2.99 unlock.
 */
class MainActivity : ComponentActivity() {

    private data class Entry(val name: Int, val desc: Int, val cls: Class<out DotWidget>, val premium: Boolean)

    private val entries = listOf(
        Entry(R.string.w_clock, R.string.d_clock, ClockWidget::class.java, false),
        Entry(R.string.w_date, R.string.d_date, DateWidget::class.java, false),
        Entry(R.string.w_battery, R.string.d_battery, BatteryWidget::class.java, false),
        Entry(R.string.w_progress, R.string.d_progress, ProgressWidget::class.java, true),
        Entry(R.string.w_countdown, R.string.d_countdown, CountdownWidget::class.java, true),
        Entry(R.string.w_streak, R.string.d_streak, StreakWidget::class.java, true),
        Entry(R.string.w_label, R.string.d_label, LabelWidget::class.java, true),
        Entry(R.string.w_steps, R.string.d_steps, StepsWidget::class.java, true),
        Entry(R.string.w_nextup, R.string.d_nextup, NextUpWidget::class.java, true),
        Entry(R.string.w_weather, R.string.d_weather, WeatherWidget::class.java, true),
        Entry(R.string.w_quote, R.string.d_quote, QuoteWidget::class.java, true),
        Entry(R.string.w_storage, R.string.d_storage, StorageWidget::class.java, true),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = DotWidgetsApp.instance

        setContent {
            NothingTheme {
                val tier by app.license.tier.collectAsState()
                val premium = tier.isPremium || DotWidget.isDebugBuild(this)

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    DotMatrixText("DOT WIDGETS", size = 28)
                    Text(
                        if (premium) "ALL WIDGETS UNLOCKED" else "3 FREE · UNLOCK 9 MORE FOR ${app.license.catalogue.plus?.price}",
                        style = typography.caption, color = colors.accent,
                    )
                    Spacer(Modifier.height(20.dp))

                    var style by remember { mutableStateOf(app.style) }
                    DotMatrixText("STYLE", size = 14)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DotWidgetsApp.Style.entries.forEach { s ->
                            NothingButton(
                                s.label,
                                if (style == s) NothingButtonStyle.Solid else NothingButtonStyle.Outline,
                                Modifier.weight(1f),
                            ) {
                                style = s; app.style = s; DotWidget.refreshEverything(this@MainActivity)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Applies to every widget. Circle looks best at 2 × 2 — long-press a widget to resize it.",
                        style = typography.caption, color = colors.onBackgroundMuted,
                    )
                    Spacer(Modifier.height(20.dp))

                    entries.forEach { e ->
                        WidgetRow(
                            name = getString(e.name),
                            desc = getString(e.desc),
                            locked = e.premium && !premium,
                            onAdd = { requestPin(e.cls) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    Spacer(Modifier.height(10.dp))
                    if (!premium) {
                        NothingButton("UNLOCK ALL · ${app.license.catalogue.plus?.price}", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                            app.license.play.buy(this@MainActivity, uk.nothingsuite.billing.Sku.PLUS)
                        }
                        Spacer(Modifier.height(8.dp))
                        NothingButton("RESTORE PURCHASE", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                            app.license.play.restore(); app.license.refresh(); DotWidget.refreshEverything(this@MainActivity)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(
                        "Can't see an Add button? Long-press your home screen → Widgets → Dot Widgets.",
                        style = typography.caption, color = colors.onBackgroundMuted,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DotWidgetsApp.instance.license.refresh()
        DotWidget.refreshEverything(this)
    }

    /** Android 8+: ask the launcher to place the widget directly. */
    private fun requestPin(cls: Class<out DotWidget>) {
        val manager = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, cls), null, null)
        }
    }
}

@Composable
private fun WidgetRow(name: String, desc: String, locked: Boolean, onAdd: () -> Unit) {
    NothingCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                DotMatrixText(name, size = 14, color = if (locked) NothingTheme.colors.onBackgroundMuted else NothingTheme.colors.onBackground)
                Spacer(Modifier.height(4.dp))
                Text(desc, style = NothingTheme.typography.caption, color = NothingTheme.colors.onBackgroundMuted)
            }
            Spacer(Modifier.width(12.dp))
            NothingButton(if (locked) "LOCKED" else "ADD", if (locked) NothingButtonStyle.Outline else NothingButtonStyle.Solid, Modifier.width(96.dp), enabled = !locked, onClick = onAdd)
        }
    }
}
