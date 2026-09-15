package uk.nothingsuite.dotwidgets.widgets

import android.content.Context
import android.os.BatteryManager
import android.widget.RemoteViews
import uk.nothingsuite.dotwidgets.DotWidgetsApp
import uk.nothingsuite.dotwidgets.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// ---------------------------------------------------------------- FREE ----

/** TextClock does all the work; nothing to render. */
class ClockWidget : DotWidget() {
    override val layout = R.layout.widget_clock
    override val premium = false
    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) = Unit
}

class DateWidget : DotWidget() {
    override val layout = R.layout.widget_date
    override val premium = false
    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) = Unit
}

class BatteryWidget : DotWidget() {
    override val layout = R.layout.widget_battery
    override val premium = false

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val charging = bm.isCharging
        views.setTextViewText(R.id.level, "$level%")
        views.setTextViewText(R.id.state, if (charging) "CHARGING" else if (level <= 20) "LOW" else "")
        views.setTextViewText(R.id.dots, dots(level / 100.0, 10))
        views.setTextColor(R.id.dots, context.getColor(if (level <= 20 && !charging) R.color.dw_red else R.color.dw_white))
    }
}

// ------------------------------------------------------------- PREMIUM ----

class ProgressWidget : DotWidget() {
    override val layout = R.layout.widget_progress
    override val premium = true

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val now = LocalDateTime.now()
        val day = (now.hour * 60 + now.minute) / 1440.0
        val ym = YearMonth.from(now)
        val month = (now.dayOfMonth - 1 + day) / ym.lengthOfMonth()
        val year = (now.dayOfYear - 1 + day) / now.toLocalDate().lengthOfYear()

        fun pct(f: Double) = "${(f * 100).toInt()}%"
        views.setTextViewText(R.id.day_label, "DAY ${pct(day)}")
        views.setTextViewText(R.id.day_dots, dots(day))
        views.setTextViewText(R.id.month_label, "MONTH ${pct(month)}")
        views.setTextViewText(R.id.month_dots, dots(month))
        views.setTextViewText(R.id.year_label, "YEAR ${pct(year)}")
        views.setTextViewText(R.id.year_dots, dots(year))
    }
}

class CountdownWidget : DotWidget() {
    override val layout = R.layout.widget_countdown
    override val premium = true

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val app = DotWidgetsApp.instance
        val label = app.getWidgetString(appWidgetId, KEY_LABEL) ?: "COUNTDOWN"
        val epochDay = app.getWidgetLong(appWidgetId, KEY_EPOCH_DAY)
        views.setTextViewText(R.id.label, label)

        if (epochDay == 0L) {
            views.setTextViewText(R.id.days, "—")
            views.setTextViewText(R.id.unit, "TAP TO SET")
            return
        }
        val target = LocalDate.ofEpochDay(epochDay)
        val days = ChronoUnit.DAYS.between(LocalDate.now(), target)
        views.setTextViewText(R.id.days, abs(days).toString())
        views.setTextViewText(
            R.id.unit,
            when {
                days > 1 -> "DAYS"
                days == 1L -> "DAY"
                days == 0L -> "TODAY"
                days == -1L -> "DAY AGO"
                else -> "DAYS AGO"
            },
        )
    }

    companion object {
        const val KEY_LABEL = "label"
        const val KEY_EPOCH_DAY = "epochDay"
    }
}

class LabelWidget : DotWidget() {
    override val layout = R.layout.widget_label
    override val premium = true

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val text = DotWidgetsApp.instance.getWidgetString(appWidgetId, KEY_TEXT) ?: "TAP TO EDIT"
        views.setTextViewText(R.id.text, text)
    }

    companion object {
        const val KEY_TEXT = "text"
    }
}
