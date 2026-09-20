package uk.nothingsuite.dotwidgets.config

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.dotwidgets.DotWidgetsApp
import uk.nothingsuite.dotwidgets.widgets.NextUpWidget
import uk.nothingsuite.dotwidgets.widgets.QuoteWidget
import uk.nothingsuite.dotwidgets.widgets.StepsWidget
import uk.nothingsuite.dotwidgets.widgets.WeatherWidget

/** Setup screen that needs one runtime permission before it's useful. */
abstract class PermissionConfigActivity : WidgetConfigActivity() {
    abstract val permission: String
    private var granted by mutableStateOf(false)
    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    override fun onResume() {
        super.onResume()
        granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    @Composable
    protected fun PermissionRow(why: String) {
        if (granted) return
        Text(why, style = typography.caption, color = colors.onBackgroundMuted)
        Spacer(Modifier.height(8.dp))
        NothingButton("ALLOW", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) { ask.launch(permission) }
        Spacer(Modifier.height(16.dp))
    }
}

class StepsConfigActivity : PermissionConfigActivity() {
    override val permission = Manifest.permission.ACTIVITY_RECOGNITION

    @Composable
    override fun Content() {
        var goal by remember { mutableStateOf(app.getWidgetLong(widgetId, StepsWidget.KEY_GOAL).takeIf { it > 0 }?.toString() ?: "8000") }
        var shape by remember { mutableStateOf(app.getWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE) ?: "") }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            DotMatrixText("STEPS", size = 28)
            Text("Counted by the phone itself. Nothing leaves the phone.", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            PermissionRow("Android asks once for permission to read the step counter.")
            OutlinedTextField(goal, { goal = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("Daily goal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(12.dp))
            ShapeRow(shape) { shape = it }
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                app.putWidgetLong(widgetId, StepsWidget.KEY_GOAL, goal.toLongOrNull() ?: 8000L)
                app.putWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE, shape)
                finishWith(StepsWidget())
            }
        }
    }
}

class NextUpConfigActivity : PermissionConfigActivity() {
    override val permission = Manifest.permission.READ_CALENDAR

    @Composable
    override fun Content() {
        var shape by remember { mutableStateOf(app.getWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE) ?: "") }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            DotMatrixText("NEXT UP", size = 28)
            Text("Your next calendar event in the coming 24 hours, and how long until it.", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            PermissionRow("Needs to read your calendar. It only ever looks one day ahead.")
            ShapeRow(shape) { shape = it }
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                app.putWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE, shape)
                finishWith(NextUpWidget())
            }
        }
    }
}

class WeatherConfigActivity : WidgetConfigActivity() {
    @Composable
    override fun Content() {
        var town by remember { mutableStateOf(app.prefs.getString("weather:town", null) ?: "") }
        var shape by remember { mutableStateOf(app.getWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE) ?: "") }
        var status by remember { mutableStateOf("") }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            DotMatrixText("WEATHER", size = 28)
            Text("Type your town. Weather comes from Open-Meteo, free, no account, and your location is never read.", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(town, { town = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Town") }, placeholder = { Text("Manchester") })
            if (status.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(status, style = typography.caption, color = colors.accent) }
            Spacer(Modifier.height(12.dp))
            ShapeRow(shape) { shape = it }
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth(), enabled = town.isNotBlank()) {
                status = "Looking up $town…"
                Thread {
                    val hit = runCatching { WeatherWidget.geocode(town.trim()) }.getOrNull()
                    runOnUiThread {
                        if (hit == null) { status = "Couldn't find that town — check the spelling or your connection."; return@runOnUiThread }
                        app.prefs.edit().putString("weather:lat", hit.first).putString("weather:lon", hit.second)
                            .putString("weather:town", hit.third.uppercase()).remove("weather:at").apply()
                        app.putWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE, shape)
                        finishWith(WeatherWidget())
                    }
                }.start()
            }
        }
    }
}

class QuoteConfigActivity : WidgetConfigActivity() {
    @Composable
    override fun Content() {
        var lines by remember { mutableStateOf(app.getWidgetString(widgetId, QuoteWidget.KEY_LINES) ?: "") }
        var shape by remember { mutableStateOf(app.getWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE) ?: "") }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            DotMatrixText("QUOTE", size = 28)
            Text("One line a day. Leave this empty for our list, or type your own — one per line, add “— Name” for the author.", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(lines, { lines = it.take(2000) }, Modifier.fillMaxWidth().height(180.dp), placeholder = { Text("Less, but better — Dieter Rams\nOne day at a time") })
            Spacer(Modifier.height(12.dp))
            ShapeRow(shape) { shape = it }
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                app.putWidgetString(widgetId, QuoteWidget.KEY_LINES, lines)
                app.putWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE, shape)
                finishWith(QuoteWidget())
            }
        }
    }
}
