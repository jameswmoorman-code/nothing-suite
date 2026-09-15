package uk.nothingsuite.dotwidgets.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
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
        val saved = app.getWidgetLong(widgetId, CountdownWidget.KEY_EPOCH_DAY)
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = if (saved == 0L) null else saved * 86_400_000L
        )

        Column(Modifier.fillMaxSize().padding(20.dp)) {
            DotMatrixText("COUNTDOWN", size = 28)
            Text("What are you counting down to?", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(label, { label = it.take(18) }, Modifier.fillMaxWidth(), placeholder = { Text("HOLIDAY") })
            Spacer(Modifier.height(8.dp))
            DatePicker(state = dateState, showModeToggle = false, title = null, headline = null)
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth(), enabled = dateState.selectedDateMillis != null) {
                val millis = dateState.selectedDateMillis ?: return@NothingButton
                val epochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                app.putWidgetString(widgetId, CountdownWidget.KEY_LABEL, label.ifBlank { "COUNTDOWN" })
                app.putWidgetLong(widgetId, CountdownWidget.KEY_EPOCH_DAY, epochDay)
                finishWith(CountdownWidget())
            }
        }
    }
}

class LabelConfigActivity : WidgetConfigActivity() {
    @Composable
    override fun Content() {
        var text by remember { mutableStateOf(app.getWidgetString(widgetId, LabelWidget.KEY_TEXT) ?: "") }

        Column(Modifier.fillMaxSize().padding(20.dp)) {
            DotMatrixText("LABEL", size = 28)
            Text("Short and loud works best. Up to 24 characters.", style = typography.caption, color = colors.onBackgroundMuted)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(text, { text = it.take(24) }, Modifier.fillMaxWidth(), placeholder = { Text("STAY CURIOUS") })
            Spacer(Modifier.height(16.dp))
            NothingButton("SAVE", NothingButtonStyle.Accent, Modifier.fillMaxWidth(), enabled = text.isNotBlank()) {
                app.putWidgetString(widgetId, LabelWidget.KEY_TEXT, text.trim())
                finishWith(LabelWidget())
            }
        }
    }
}
