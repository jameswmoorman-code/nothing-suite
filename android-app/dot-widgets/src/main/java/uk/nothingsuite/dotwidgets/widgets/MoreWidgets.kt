package uk.nothingsuite.dotwidgets.widgets

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.CalendarContract
import android.widget.RemoteViews
import org.json.JSONObject
import uk.nothingsuite.dotwidgets.DotWidgetsApp
import uk.nothingsuite.dotwidgets.R
import uk.nothingsuite.dotwidgets.config.NextUpConfigActivity
import uk.nothingsuite.dotwidgets.config.QuoteConfigActivity
import uk.nothingsuite.dotwidgets.config.StepsConfigActivity
import uk.nothingsuite.dotwidgets.config.WeatherConfigActivity
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Widgets that need to go and fetch something (sensor, calendar, network)
 * before they can draw. They override onUpdate, do the fetch off the main
 * thread with goAsync(), cache the result in prefs, then draw from the cache
 * so render() itself stays instant and never blocks.
 */
abstract class FetchingWidget : DotWidget() {

    /** Fetch fresh data into prefs. Runs on a background thread; must be quick (< 8 s). */
    abstract fun fetch(context: Context)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try { fetch(context) } catch (_: Exception) { }
            ids.forEach { update(context, manager, it) }
            pending.finish()
        }.start()
    }

    protected fun has(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

// ------------------------------------------------------------------ STEPS ----

/** Today's steps from the phone's own step counter. Free on Android; no account, no cloud. */
class StepsWidget : FetchingWidget() {
    override val layout = R.layout.widget_steps
    override val premium = true
    override fun tapIntent(context: Context, appWidgetId: Int) = Intent(context, StepsConfigActivity::class.java)

    override fun fetch(context: Context) {
        if (!has(context, Manifest.permission.ACTIVITY_RECOGNITION)) return
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val latch = CountDownLatch(1)
        var sinceBoot = -1f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { sinceBoot = e.values[0]; latch.countDown() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        Handler(Looper.getMainLooper()).post { sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
        latch.await(4, TimeUnit.SECONDS)
        Handler(Looper.getMainLooper()).post { sm.unregisterListener(listener) }
        if (sinceBoot < 0) return

        // The counter runs since boot; remember where it stood at the start of today.
        val app = DotWidgetsApp.instance
        val today = LocalDate.now().toEpochDay()
        val baseDay = app.prefs.getLong("steps:baseDay", 0L)
        var base = app.prefs.getFloat("steps:base", -1f)
        if (baseDay != today || base < 0 || base > sinceBoot) { // new day, or phone rebooted
            base = sinceBoot
            app.prefs.edit().putLong("steps:baseDay", today).putFloat("steps:base", base).apply()
        }
        app.prefs.edit().putInt("steps:today", (sinceBoot - base).toInt()).apply()
    }

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val app = DotWidgetsApp.instance
        val goal = app.getWidgetLong(appWidgetId, KEY_GOAL).takeIf { it > 0 } ?: 8000L
        views.inkLabel(context, R.id.heading, "STEPS")
        if (!has(context, Manifest.permission.ACTIVITY_RECOGNITION)) {
            views.ink(context, R.id.steps, "—", DotInk.Kind.HERO)
            views.inkLabel(context, R.id.goal, "TAP TO ALLOW", R.color.dw_red)
            views.inkDots(context, R.id.dots, 0.0)
            return
        }
        val today = app.prefs.getInt("steps:today", 0)
        views.ink(context, R.id.steps, "%,d".format(today), DotInk.Kind.HERO)
        views.inkLabel(context, R.id.goal, if (today >= goal) "GOAL DONE" else "OF %,d".format(goal))
        views.inkDots(context, R.id.dots, today.toDouble() / goal, 10, if (today >= goal) R.color.dw_red else R.color.dw_white)
    }

    companion object { const val KEY_GOAL = "goal" }
}

// ---------------------------------------------------------------- NEXT UP ----

/** The next thing in your calendar within 24 hours. */
class NextUpWidget : FetchingWidget() {
    override val layout = R.layout.widget_nextup
    override val premium = true
    override fun tapIntent(context: Context, appWidgetId: Int) = Intent(context, NextUpConfigActivity::class.java)

    override fun fetch(context: Context) {
        if (!has(context, Manifest.permission.READ_CALENDAR)) return
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            ContentUris.appendId(it, now); ContentUris.appendId(it, now + 24 * 3600_000L); it.build()
        }
        val proj = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY)
        context.contentResolver.query(uri, proj, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(2) == 1
                val begin = c.getLong(1)
                if (!allDay && begin < now - 5 * 60_000L) continue // already started a while ago
                DotWidgetsApp.instance.prefs.edit()
                    .putString("nextup:title", c.getString(0) ?: "(NO TITLE)")
                    .putLong("nextup:begin", begin).putBoolean("nextup:allDay", allDay).apply()
                return
            }
        }
        DotWidgetsApp.instance.prefs.edit().remove("nextup:title").apply()
    }

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val p = DotWidgetsApp.instance.prefs
        if (!has(context, Manifest.permission.READ_CALENDAR)) {
            views.inkLabel(context, R.id.when_, "NEXT UP"); views.ink(context, R.id.title, "TAP TO ALLOW", DotInk.Kind.TITLE); views.inkLabel(context, R.id.in_, " "); return
        }
        val title = p.getString("nextup:title", null)
        if (title == null) {
            views.inkLabel(context, R.id.when_, "NEXT 24 HRS"); views.ink(context, R.id.title, "NOTHING ON", DotInk.Kind.TITLE); views.inkLabel(context, R.id.in_, " "); return
        }
        val begin = p.getLong("nextup:begin", 0L)
        val allDay = p.getBoolean("nextup:allDay", false)
        val start = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault())
        val mins = (begin - System.currentTimeMillis()) / 60_000L
        views.inkLabel(context, R.id.when_, if (allDay) "ALL DAY" else start.format(DateTimeFormatter.ofPattern("EEE HH:mm")))
        views.ink(context, R.id.title, title.take(40), DotInk.Kind.BODY, maxWidthDp = 300)
        views.inkLabel(
            context, R.id.in_,
            when {
                allDay -> " "
                mins <= 0 -> "NOW"
                mins < 60 -> "IN $mins MIN"
                mins < 24 * 60 -> "IN ${mins / 60} HR${if (mins / 60 == 1L) "" else "S"}"
                else -> "TOMORROW"
            },
            R.color.dw_red,
        )
    }
}

// ---------------------------------------------------------------- WEATHER ----

/**
 * Temperature and a one-word sky for a town you type in. Uses Open-Meteo,
 * which is free and needs no key or account, so nothing to sign up for.
 * No location permission: the town is geocoded once in the setup screen.
 */
class WeatherWidget : FetchingWidget() {
    override val layout = R.layout.widget_weather
    override val premium = true
    override fun tapIntent(context: Context, appWidgetId: Int) = Intent(context, WeatherConfigActivity::class.java)

    override fun fetch(context: Context) {
        val p = DotWidgetsApp.instance.prefs
        val lat = p.getString("weather:lat", null) ?: return
        val lon = p.getString("weather:lon", null) ?: return
        val json = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto") ?: return
        val cur = JSONObject(json).getJSONObject("current")
        p.edit().putInt("weather:temp", Math.round(cur.getDouble("temperature_2m")).toInt())
            .putInt("weather:code", cur.getInt("weather_code"))
            .putLong("weather:at", System.currentTimeMillis()).apply()
    }

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val p = DotWidgetsApp.instance.prefs
        val town = p.getString("weather:town", null)
        views.setViewVisibility(R.id.icon, android.view.View.GONE)
        if (town == null) { views.ink(context, R.id.temp, "—", DotInk.Kind.HERO); views.inkLabel(context, R.id.sky, "TAP TO SET TOWN", R.color.dw_red); views.inkLabel(context, R.id.town, " "); return }
        val at = p.getLong("weather:at", 0L)
        if (at == 0L) { views.ink(context, R.id.temp, "…", DotInk.Kind.HERO); views.inkLabel(context, R.id.sky, "FETCHING"); views.inkLabel(context, R.id.town, town, R.color.dw_white); return }
        val code = p.getInt("weather:code", 0)
        views.setViewVisibility(R.id.icon, android.view.View.VISIBLE)
        views.setImageViewResource(R.id.icon, icon(code))
        views.ink(context, R.id.temp, "${p.getInt("weather:temp", 0)}°", DotInk.Kind.HERO)
        views.inkLabel(context, R.id.sky, sky(code))
        views.inkLabel(context, R.id.town, town, R.color.dw_white)
    }

    companion object {
        /** WMO weather code → dot pictogram. */
        fun icon(code: Int) = when (code) {
            0, 1 -> R.drawable.ic_dot_sun
            2, 3, 45, 48 -> R.drawable.ic_dot_cloud
            in 51..67, in 80..82 -> R.drawable.ic_dot_rain
            in 71..77, 85, 86 -> R.drawable.ic_dot_snow
            in 95..99 -> R.drawable.ic_dot_thunder
            else -> R.drawable.ic_dot_cloud
        }

        /** WMO weather code → one word. */
        fun sky(code: Int) = when (code) {
            0 -> "CLEAR"; 1 -> "MOSTLY CLEAR"; 2 -> "PARTLY CLOUDY"; 3 -> "OVERCAST"
            45, 48 -> "FOG"; in 51..57 -> "DRIZZLE"; in 61..67 -> "RAIN"; in 71..77 -> "SNOW"
            in 80..82 -> "SHOWERS"; 85, 86 -> "SNOW SHOWERS"; in 95..99 -> "THUNDER"; else -> "—"
        }

        fun get(url: String): String? {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 6000
            return try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
        }

        /** Open-Meteo geocoding: town name → (lat, lon, pretty name). */
        fun geocode(town: String): Triple<String, String, String>? {
            val json = get("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(town, "UTF-8")}&count=1&language=en") ?: return null
            val r = JSONObject(json).optJSONArray("results")?.optJSONObject(0) ?: return null
            return Triple(r.getDouble("latitude").toString(), r.getDouble("longitude").toString(), r.getString("name"))
        }
    }
}

// ------------------------------------------------------------------ QUOTE ----

/** One short line a day. Built-in list, or your own lines from the setup screen. */
class QuoteWidget : DotWidget() {
    override val layout = R.layout.widget_quote
    override val premium = true
    override fun tapIntent(context: Context, appWidgetId: Int) = Intent(context, QuoteConfigActivity::class.java)

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val custom = DotWidgetsApp.instance.getWidgetString(appWidgetId, KEY_LINES)
            ?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val pool = custom.ifEmpty { BUILT_IN }
        val line = pool[(LocalDate.now().toEpochDay() % pool.size).toInt()]
        val (text, by) = line.split("—", "--").let { it[0].trim() to (it.getOrNull(1)?.trim() ?: "") }
        views.ink(context, R.id.text, text, DotInk.Kind.BODY, maxWidthDp = 300)
        views.inkLabel(context, R.id.by, by.ifBlank { " " })
    }

    companion object {
        const val KEY_LINES = "lines"
        val BUILT_IN = listOf(
            "Do it badly, but do it — Anon",
            "Less, but better — Dieter Rams",
            "Start where you are — Arthur Ashe",
            "Slow is smooth, smooth is fast",
            "Make it simple, but significant — Don Draper",
            "One day at a time",
            "Done is better than perfect",
            "The obstacle is the way — Marcus Aurelius",
            "Stay curious",
            "What gets measured gets done",
            "Small steps every day",
            "Simplicity is the ultimate sophistication",
            "You can't pour from an empty cup",
            "Begin anywhere — John Cage",
            "Nothing is impossible",
            "Be where your feet are",
            "Progress, not perfection",
            "The best time to plant a tree was 20 years ago. The second best is now",
            "Attention is the rarest form of generosity — Simone Weil",
            "Eat the frog first",
            "Compare less. Create more",
            "If in doubt, walk",
            "Amateurs sit and wait for inspiration — Stephen King",
            "Make something people want — Paul Graham",
            "Well begun is half done — Aristotle",
            "Do fewer things, better",
            "Hard choices, easy life — Jerzy Gregorek",
            "Say no by default",
            "Ship it",
            "Rest is productive",
        )
    }
}

// ---------------------------------------------------------------- STORAGE ----

/** How full the phone is. No permission needed. */
class StorageWidget : DotWidget() {
    override val layout = R.layout.widget_storage
    override val premium = true

    override fun render(context: Context, views: RemoteViews, appWidgetId: Int) {
        val fs = StatFs(context.filesDir.absolutePath)
        val total = fs.totalBytes.toDouble(); val free = fs.availableBytes.toDouble()
        val gb = 1_000_000_000.0
        val used = 1 - free / total
        views.ink(context, R.id.free, "${(free / gb).toInt()} GB", DotInk.Kind.HERO)
        views.inkLabel(context, R.id.of, "FREE OF ${(total / gb).toInt()}")
        views.inkDots(context, R.id.dots, used, 10, if (used > 0.9) R.color.dw_red else R.color.dw_white)
    }
}
