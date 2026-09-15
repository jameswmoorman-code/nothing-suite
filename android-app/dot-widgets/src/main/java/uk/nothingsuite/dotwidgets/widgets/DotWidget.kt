package uk.nothingsuite.dotwidgets.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import uk.nothingsuite.dotwidgets.DotWidgetsApp
import uk.nothingsuite.dotwidgets.MainActivity
import uk.nothingsuite.dotwidgets.R

/**
 * Base class for every widget. Subclasses declare a layout, whether they're
 * part of the free set, and how to fill the views. Everything else — the
 * paywall state, tap-to-open, refresh plumbing — lives here once.
 *
 * Plain RemoteViews rather than Glance: the dot-matrix font is applied in
 * the layout XML (android:fontFamily), so no bitmap rendering is needed and
 * TextClock ticks by itself with zero battery cost.
 */
abstract class DotWidget : AppWidgetProvider() {

    abstract val layout: Int
    abstract val premium: Boolean

    /** Fill the views. Only called when the widget is unlocked (or free). */
    abstract fun render(context: Context, views: RemoteViews, appWidgetId: Int)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Battery / date broadcasts arrive here too — refresh everything of this type.
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) refreshAll(context, javaClass)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { DotWidgetsApp.instance.clearWidget(it) }
    }

    fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, layout)
        val unlocked = !premium || DotWidgetsApp.instance.license.tier.value.isPremium

        if (unlocked) {
            views.setViewVisibility(R.id.locked, View.GONE)
            render(context, views, id)
        } else {
            views.setViewVisibility(R.id.locked, View.VISIBLE)
        }

        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.root, open)
        manager.updateAppWidget(id, views)
    }

    companion object {
        /** Re-render every placed instance of one widget class. */
        fun refreshAll(context: Context, cls: Class<out DotWidget>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        /** After a purchase or restore: unlock everything on screen. */
        fun refreshEverything(context: Context) {
            ALL.forEach { refreshAll(context, it) }
        }

        val ALL: List<Class<out DotWidget>> = listOf(
            ClockWidget::class.java, DateWidget::class.java, BatteryWidget::class.java,
            ProgressWidget::class.java, CountdownWidget::class.java, LabelWidget::class.java,
        )

        /** "●●●●●○○○○○" — the suite's progress bar. */
        fun dots(fraction: Double, count: Int = 20): String {
            val on = (fraction.coerceIn(0.0, 1.0) * count).toInt()
            return "●".repeat(on) + "○".repeat(count - on)
        }
    }
}
