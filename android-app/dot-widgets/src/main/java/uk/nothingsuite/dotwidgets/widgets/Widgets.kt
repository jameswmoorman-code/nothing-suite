package uk.nothingsuite.dotwidgets.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.widget.RemoteViews
import uk.nothingsuite.dotwidgets.DotWidgetsApp
import uk.nothingsuite.dotwidgets.R
import uk.nothingsuite.dotwidgets.config.CountdownConfigActivity
import uk.nothingsuite.dotwidgets.config.LabelConfigActivity
import uk.nothingsuite.dotwidgets.config.StreakConfigActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// ---------------------------------------------------------------- FREE ----

/**
 * Drawn as pictures, so it can't tick by itself: we redraw every minute with
 * an alarm (exact when the user allows it in the widget's settings, otherwise
 * Android decides the timing and it may lag a little while the phone dozes).
 */
class ClockWidget : DotWidget() {
    override val layout = R.layout.widget_clock
    override val premium = false

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val now = java.time.LocalDateTime.now()
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        views.ink(context, R.id.time, now.format(java.time.format.DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")), DotInk.Kind.HERO)
        views.inkLabel(context, R.id.date, now.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        scheduleNextMinute(context)
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(tick(context))
    }

    private fun tick(context: Context) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ClockWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ClockWidget::class.java))),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleNextMinute(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 500
        val pi = tick(context)
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
    }
}

class DateWidget : DotWidget() {
    override val layout = R.layout.widget_date
    override val premium = false

    /** Plain TextViews (TextClock ignores custom fonts in widgets); refreshed on DATE_CHANGED. */
    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val today = LocalDate.now()
        views.inkLabel(context, R.id.day, today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE")))
        views.ink(context, R.id.date, today.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM")), DotInk.Kind.TITLE)
    }
}

class BatteryWidget : DotWidget() {
    override val layout = R.layout.widget_battery
    override val premium = false

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val charging = bm.isCharging
        views.ink(context, R.id.level, "$level%", DotInk.Kind.HERO)
        views.inkLabel(context, R.id.state, if (charging) "CHARGING" else if (level <= 20) "LOW" else " ", R.color.dw_red)
        views.inkDots(context, R.id.dots, level / 100.0, 10, if (level <= 20 && !charging) R.color.dw_red else R.color.dw_white)
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
        views.inkLabel(context, R.id.day_label, "DAY ${pct(day)}")
        views.inkDots(context, R.id.day_dots, day, 20)
        views.inkLabel(context, R.id.month_label, "MONTH ${pct(month)}")
        views.inkDots(context, R.id.month_dots, month, 20)
        views.inkLabel(context, R.id.year_label, "YEAR ${pct(year)}")
        views.inkDots(context, R.id.year_dots, year, 20, R.color.dw_red)
    }
}

class CountdownWidget : DotWidget() {
    override val layout = R.layout.widget_countdown
    override val premium = true
    override fun tapIntent(context: Context, appWidgetId: Int) = Intent(context, CountdownConfigActivity::class.java)

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val app = DotWidgetsApp.instance
        val label = app.getWidgetString(appWidgetId, KEY_LABEL) ?: "COUNTDOWN"
        val repeat = app.getWidgetString(appWidgetId, KEY_REPEAT)
        val epochDay = if (repeat != null) nextOccurrence(repeat).toEpochDay() else app.getWidgetLong(appWidgetId, KEY_EPOCH_DAY)
        views.inkLabel(context, R.id.label, label)

        if (epochDay == 0L) {
            views.ink(context, R.id.days, "—", DotInk.Kind.HERO)
            views.inkLabel(context, R.id.unit, "TAP TO SET", R.color.dw_red)
            return
        }
        val target = LocalDate.ofEpochDay(epochDay)
        val days = ChronoUnit.DAYS.between(LocalDate.now(), target)
        views.ink(context, R.id.days, abs(days).toString(), DotInk.Kind.HERO)
        views.inkLabel(
            context, R.id.unit,
            when {
                days > 1 -> "DAYS"
                days == 1L -> "DAY"
                days == 0L -> "TODAY"
                days == -1L -> "DAY SINCE"
                else -> "DAYS SINCE"   // count-up: sober days, smoke-free, since you met
            },
            R.color.dw_red,
        )
    }

    companion object {
        const val KEY_LABEL = "label"
        const val KEY_EPOCH_DAY = "epochDay"
        /** "weekly:6" (Saturday), "monthly:25" (payday), "yearly:12-25" (Christmas). Recomputed every render. */
        const val KEY_REPEAT = "repeat"

        fun nextOccurrence(rule: String, today: LocalDate = LocalDate.now()): LocalDate {
            val (kind, arg) = rule.split(":")
            return when (kind) {
                "weekly" -> { val dow = arg.toInt(); var d = today; while (d.dayOfWeek.value != dow) d = d.plusDays(1); d }
                "monthly" -> {
                    val dom = arg.toInt()
                    fun on(m: YearMonth) = m.atDay(minOf(dom, m.lengthOfMonth()))
                    if (on(YearMonth.from(today)) >= today) on(YearMonth.from(today)) else on(YearMonth.from(today).plusMonths(1))
                }
                "yearly" -> {
                    val (mm, dd) = arg.split("-").map { it.toInt() }
                    val thisYear = LocalDate.of(today.year, mm, dd)
                    if (thisYear >= today) thisYear else thisYear.plusYears(1)
                }
                else -> today
            }
        }
    }
}

/** Count-up: days since you stopped (or started) something. */
class StreakWidget : DotWidget() {
    override val layout = R.layout.widget_streak
    override val premium = true
    override fun tapIntent(context: Context, appWidgetId: Int) = Intent(context, StreakConfigActivity::class.java)

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val app = DotWidgetsApp.instance
        val label = app.getWidgetString(appWidgetId, KEY_LABEL) ?: "STREAK"
        val note = app.getWidgetString(appWidgetId, KEY_NOTE) ?: ""
        val epochDay = app.getWidgetLong(appWidgetId, KEY_EPOCH_DAY)
        val icon = ICONS[app.getWidgetString(appWidgetId, KEY_ICON) ?: "cigarette"]
        if (icon == null) views.setViewVisibility(R.id.icon, android.view.View.GONE)
        else { views.setViewVisibility(R.id.icon, android.view.View.VISIBLE); views.setImageViewResource(R.id.icon, icon) }
        views.inkLabel(context, R.id.label, label)
        views.inkLabel(context, R.id.note, note)
        views.setViewVisibility(R.id.note, if (note.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)

        if (epochDay == 0L) {
            views.ink(context, R.id.days, "—", DotInk.Kind.HERO)
            views.inkLabel(context, R.id.unit, "TAP TO SET", R.color.dw_red)
            return
        }
        val days = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(epochDay), LocalDate.now()).coerceAtLeast(0)
        views.ink(context, R.id.days, days.toString(), DotInk.Kind.HERO)
        views.inkLabel(context, R.id.unit, if (days == 1L) "DAY" else "DAYS", R.color.dw_red)
        if (isMilestone(days)) {
            // Celebration frame for the day: firework, red note. Widgets can't animate; the Glyph Toy does the show.
            views.setViewVisibility(R.id.icon, android.view.View.VISIBLE)
            views.setImageViewResource(R.id.icon, R.drawable.ic_dot_firework)
            views.inkLabel(context, R.id.note, "MILESTONE · WELL DONE", R.color.dw_red)
            views.setViewVisibility(R.id.note, android.view.View.VISIBLE)
        }
    }

    companion object {
        const val KEY_LABEL = "label"
        const val KEY_NOTE = "note"
        const val KEY_ICON = "icon"
        const val KEY_EPOCH_DAY = "epochDay"

        /** Days worth a celebration frame. */
        fun isMilestone(d: Long): Boolean =
            d in setOf(1L, 7L, 14L, 30L, 50L, 100L, 180L, 200L, 365L, 500L, 730L, 1000L) || (d > 730 && d % 365 == 0L)

        /** Dot-matrix pictograms the user can pick; "none" hides it. */
        val ICONS: Map<String, Int?> = linkedMapOf(
            "cigarette" to R.drawable.ic_dot_cigarette,
            "drink" to R.drawable.ic_dot_drink,
            "heart" to R.drawable.ic_dot_heart,
            "star" to R.drawable.ic_dot_star,
            "none" to null,
        )
    }
}

class LabelWidget : DotWidget() {
    override val layout = R.layout.widget_label
    override val premium = true
    override fun tapIntent(context: Context, appWidgetId: Int) = Intent(context, LabelConfigActivity::class.java)

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val text = DotWidgetsApp.instance.getWidgetString(appWidgetId, KEY_TEXT) ?: "TAP TO EDIT"
        views.ink(context, R.id.text, text, DotInk.Kind.TITLE, maxWidthDp = 300)
    }

    companion object {
        const val KEY_TEXT = "text"
    }
}
