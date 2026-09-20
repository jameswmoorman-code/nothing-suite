package uk.nothingsuite.dotwidgets.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.dotwidgets.DotWidgetsApp
import uk.nothingsuite.dotwidgets.widgets.CountdownWidget
import uk.nothingsuite.dotwidgets.widgets.DotWidget
import uk.nothingsuite.dotwidgets.widgets.LabelWidget
import uk.nothingsuite.dotwidgets.widgets.StreakWidget
import java.time.Instant
import java.time.ZoneOffset

/**
 * Shared scaffolding for the "widget needs setup" screens Android shows when
 * a configurable widget is dropped on the home screen. Must call setResult
 * with the widget id or the launcher deletes the widget.
 */
abstract class WidgetConfigActivity : ComponentActivity() {

    protected var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    protected val app get() = DotWidgetsApp.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED) // until saved
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContent { NothingTheme { Content() } }
    }

    @Composable
    protected abstract fun Content()

    /** Shape chooser shared by every setup screen. "DEFAULT" follows the app-wide STYLE row. */
    @Composable
    protected fun ShapeRow(shape: String, onChange: (String) -> Unit) {
        Text("Shape (AUTO follows the STYLE row in the app)", style = typography.caption, color = colors.onBackgroundMuted)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("" to "AUTO") + DotWidgetsApp.Style.entries.map { it.name to it.label }).forEach { (key, label) ->
                NothingButton(label, if (shape == key) NothingButtonStyle.Solid else NothingButtonStyle.Outline, Modifier.weight(1f)) { onChange(key) }
            }
        }
    }

    protected fun finishWith(widget: DotWidget) {
        widget.update(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

class CountdownConfigActivity : WidgetConfigActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        var label by remember { mutableStateOf(app.getWidgetString(widgetId, CountdownWidget.KEY_LABEL) ?: "") }
        var shape by remember { mutableStateOf(app.getWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE) ?: "") }
        var repeat by remember { mutableStateOf(app.getWidgetString(widgetId, CountdownWidget.KEY_REPEAT)) }
        var payday by remember { mutableStateOf(repeat?.takeIf { it.startsWith("monthly:") }?.substringAfter(":") ?: "25") }
        val saved = app.getWidgetLong(widgetId, CountdownWidget.KEY_EPOCH_DAY)
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = (if (saved == 0L) java.time.LocalDate.now().toEpochDay() else saved) * 86_400_000L
        )

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            DotMatrixText("COUNTDOWN", size = 28)
            Text("What are you counting down to? (For days since something, use Dot Streak.)", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(label, { label = it.take(18) }, Modifier.fillMaxWidth(), placeholder = { Text("HOLIDAY") })
            Spacer(Modifier.height(12.dp))
            Text("Quick picks — these repeat by themselves", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("WEEKEND" to "weekly:6", "PAYDAY" to "monthly:$payday", "XMAS" to "yearly:12-25", "NEW YEAR" to "yearly:1-1").forEach { (name, rule) ->
                    val on = repeat != null && repeat!!.substringBefore(":") == rule.substringBefore(":") && (rule.substringBefore(":") != "yearly" || repeat == rule)
                    NothingButton(name, if (on) NothingButtonStyle.Solid else NothingButtonStyle.Outline, Modifier.weight(1f)) {
                        repeat = if (on) null else rule
                        if (label.isBlank() || listOf("WEEKEND", "PAYDAY", "XMAS", "NEW YEAR").contains(label)) label = if (on) "" else name
                    }
                }
            }
            if (repeat?.startsWith("monthly:") == true) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(payday, { v -> payday = v.filter(Char::isDigit).take(2); payday.toIntOrNull()?.let { if (it in 1..31) repeat = "monthly:$it" } }, Modifier.fillMaxWidth(), label = { Text("Payday — day of the month") })
            }
            Spacer(Modifier.height(12.dp))
            ShapeRow(shape) { shape = it }
            Spacer(Modifier.height(12.dp))
            if (repeat == null) DateButton(dateState) else Text("Date is worked out automatically.", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                val millis = dateState.selectedDateMillis ?: return@NothingButton
                val epochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                app.putWidgetString(widgetId, CountdownWidget.KEY_LABEL, label.ifBlank { "COUNTDOWN" })
                app.putWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE, shape)
                app.putWidgetLong(widgetId, CountdownWidget.KEY_EPOCH_DAY, epochDay)
                val r = repeat
                if (r != null) app.putWidgetString(widgetId, CountdownWidget.KEY_REPEAT, r) else app.removeWidgetKey(widgetId, CountdownWidget.KEY_REPEAT)
                finishWith(CountdownWidget())
            }
        }
    }
}

class StreakConfigActivity : WidgetConfigActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        var label by remember { mutableStateOf(app.getWidgetString(widgetId, StreakWidget.KEY_LABEL) ?: "") }
        var note by remember { mutableStateOf(app.getWidgetString(widgetId, StreakWidget.KEY_NOTE) ?: "") }
        var icon by remember { mutableStateOf(app.getWidgetString(widgetId, StreakWidget.KEY_ICON) ?: "cigarette") }
        var shape by remember { mutableStateOf(app.getWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE) ?: "") }
        val saved = app.getWidgetLong(widgetId, StreakWidget.KEY_EPOCH_DAY)
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = (if (saved == 0L) java.time.LocalDate.now().toEpochDay() else saved) * 86_400_000L
        )

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            DotMatrixText("STREAK", size = 28)
            Text(
                "Counts up from the day you stopped — or started. Quit smoking, stopped drinking, first run, day one at a new job.",
                style = typography.caption, color = colors.onBackgroundMuted,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(label, { label = it.take(18) }, Modifier.fillMaxWidth(), placeholder = { Text("SMOKE FREE") }, label = { Text("Title") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(note, { note = it.take(28) }, Modifier.fillMaxWidth(), placeholder = { Text("ONE DAY AT A TIME") }, label = { Text("Note (optional)") })
            Spacer(Modifier.height(12.dp))
            Text("Icon", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreakWidget.ICONS.forEach { (key, res) ->
                    val selected = icon == key
                    Box(
                        Modifier
                            .size(44.dp)
                            .border(1.dp, if (selected) colors.accent else colors.outline, CircleShape)
                            .clickable { icon = key },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (res != null) Icon(painterResource(res), contentDescription = key, tint = colors.onBackground, modifier = Modifier.size(26.dp))
                        else Text("—", color = colors.onBackgroundMuted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ShapeRow(shape) { shape = it }
            Spacer(Modifier.height(12.dp))
            Text("When did it start?", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(6.dp))
            DateButton(dateState)
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                val millis = dateState.selectedDateMillis ?: return@NothingButton
                val epochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                app.putWidgetString(widgetId, StreakWidget.KEY_LABEL, label.ifBlank { "STREAK" })
                app.putWidgetString(widgetId, StreakWidget.KEY_NOTE, note.trim())
                app.putWidgetString(widgetId, StreakWidget.KEY_ICON, icon)
                app.putWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE, shape)
                app.putWidgetLong(widgetId, StreakWidget.KEY_EPOCH_DAY, epochDay)
                finishWith(StreakWidget())
            }
        }
    }
}

/**
 * Shape screen for widgets without other settings (Clock, Date, Battery,
 * Progress). Reached by tapping the widget, or as an optional config.
 */
class ShapeConfigActivity : WidgetConfigActivity() {
    @Composable
    override fun Content() {
        var shape by remember { mutableStateOf(app.getWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE) ?: "") }
        val cls = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className
        val widget = DotWidget.ALL.firstOrNull { it.name == cls }?.getDeclaredConstructor()?.newInstance()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            DotMatrixText("THIS WIDGET", size = 28)
            Text("Just this one. The STYLE row in the app sets the default for the rest.", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            ShapeRow(shape) { shape = it }
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                app.putWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE, shape)
                if (widget != null) finishWith(widget) else finish()
            }
            Spacer(Modifier.height(8.dp))
            NothingButton("OPEN DOT WIDGETS", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                startActivity(Intent(this@ShapeConfigActivity, uk.nothingsuite.dotwidgets.MainActivity::class.java)); finish()
            }
        }
    }
}

class LabelConfigActivity : WidgetConfigActivity() {
    @Composable
    override fun Content() {
        var text by remember { mutableStateOf(app.getWidgetString(widgetId, LabelWidget.KEY_TEXT) ?: "") }
        var shape by remember { mutableStateOf(app.getWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE) ?: "") }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            DotMatrixText("LABEL", size = 28)
            Text("Short and loud works best. Up to 24 characters.", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(text, { text = it.take(24) }, Modifier.fillMaxWidth(), placeholder = { Text("STAY CURIOUS") })
            Spacer(Modifier.height(12.dp))
            ShapeRow(shape) { shape = it }
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth(), enabled = text.isNotBlank()) {
                app.putWidgetString(widgetId, LabelWidget.KEY_TEXT, text.trim())
                app.putWidgetString(widgetId, DotWidgetsApp.KEY_SHAPE, shape)
                finishWith(LabelWidget())
            }
        }
    }
}

/** A button showing the chosen date; tapping opens the calendar in a dialog so the screen stays short. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(state: androidx.compose.material3.DatePickerState) {
    var open by remember { mutableStateOf(false) }
    val label = state.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    } ?: "PICK A DATE"
    NothingButton(label.uppercase(), NothingButtonStyle.Outline, Modifier.fillMaxWidth()) { open = true }
    if (open) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("DONE") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}
