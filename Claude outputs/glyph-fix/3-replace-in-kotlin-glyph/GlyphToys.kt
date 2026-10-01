package uk.nothingsuite.dotwidgets.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import uk.nothingsuite.dotwidgets.DotWidgetsApp
import uk.nothingsuite.dotwidgets.widgets.CountdownWidget
import uk.nothingsuite.dotwidgets.widgets.StepsWidget
import uk.nothingsuite.dotwidgets.widgets.StreakWidget
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** True on a phone with the 25×25 Glyph Matrix (Phone (3), model A024). */
fun hasGlyphMatrix(): Boolean = Build.MODEL.contains("A024", ignoreCase = true) || Build.DEVICE.contains("23112")

/**
 * A "show": a sequence of frames the toy plays. Each step returns the frame
 * to display and how long to hold it. Shows are plain Kotlin so the same
 * code drives both a Glyph Toy (button-selected) and on-demand playback.
 */
abstract class Show {
    val m = Matrix()
    /** Called every [interval] ms with the elapsed ms; return false when finished (the last frame stays lit). */
    abstract fun frame(t: Long): Boolean
    open val interval = 50L
}

// ------------------------------------------------------------------ SHOWS ----

/** Icon spins → GOOD JOB! scrolls → day count → fireworks → settle on count. */
class StreakShow(private val label: String, private val days: Long, iconKey: String?) : Show() {
    private val icon = Icons.forKey(iconKey)
    private val fw = Fireworks()
    private var fwDone = false

    override fun frame(t: Long): Boolean {
        val base = Matrix()
        when {
            t < 2200 -> {                                   // spin
                if (icon != null) { base.icon(icon, 1, 1, 2); m.copyFrom(base.rotated(t / 2200.0 * 720)) }
                else { m.clear(); m.textCentered(label.take(4), 9, 1) }
            }
            t < 5200 -> {                                   // scroll GOOD JOB!
                m.clear(); val s = "GOOD JOB!"; val w = m.textWidth(s, 2)
                val x = 25 - ((t - 2200) / 3000.0 * (w + 30)).toInt()
                m.text(s, x, 5, 2)
            }
            t < 7200 -> countFrame()                        // hold the number
            !fwDone -> { fwDone = !fw.step(m) }             // fireworks over black
            else -> { countFrame(); return false }          // rest on the number
        }
        return true
    }

    private fun countFrame() {
        m.clear()
        val s = days.toString()
        when (s.length) { 1, 2 -> m.textCentered(s, 2, 3); 3 -> m.textCentered(s, 5, 2); else -> m.textCentered(s, 9, 1) }
        if (s.length <= 2) m.textCentered("DAYS", 23 - 7 + 1, 1, 160) else if (s.length == 3) m.textCentered("DAYS", 20, 1, 160)
    }
}

/** Label scrolls once, then "N" big with DAYS under it. */
class CountdownShow(private val label: String, private val days: Long) : Show() {
    override fun frame(t: Long): Boolean {
        m.clear()
        val w = m.textWidth(label, 1)
        val scrollMs = (w + 30) * 60L
        if (t < scrollMs) {
            m.text(label, 25 - (t / 60).toInt(), 9, 1); return true
        }
        val s = days.toString()
        when (s.length) { 1, 2 -> { m.textCentered(s, 2, 3); m.textCentered(if (days == 1L) "DAY" else "DAYS", 17, 1, 160) }
            else -> { m.textCentered(s, 5, 2); m.textCentered("DAYS", 20, 1, 160) } }
        return false
    }
}

/** Ring fills to today's fraction of the goal; count in the middle; burst at goal. */
class StepsShow(private val steps: Int, private val goal: Long) : Show() {
    private val target = (steps.toDouble() / goal).coerceIn(0.0, 1.0)
    private val fw = Fireworks()
    private var fwDone = target < 1.0
    override fun frame(t: Long): Boolean {
        val f = (t / 1500.0).coerceAtMost(1.0) * target
        m.clear(); m.ring(f)
        val s = if (steps >= 1000) "${steps / 1000}K" else steps.toString()
        m.textCentered(s, 9, 1)
        if (f >= target && !fwDone) { val fm = Matrix(); fwDone = !fw.step(fm); for (i in m.px.indices) m.px[i] = maxOf(m.px[i], fm.px[i]); return true }
        return f < target
    }
}

/** Percentage big, a bar underneath that fills up, lightning if charging. */
class BatteryShow(private val level: Int, private val charging: Boolean) : Show() {
    override fun frame(t: Long): Boolean {
        m.clear()
        m.textCentered("$level%", 3, if (level == 100) 1 else 2)
        val fill = ((t / 1200.0).coerceAtMost(1.0) * level / 100.0 * 21).toInt()
        for (x in 2..22) for (y in 19..21) m.set(x, y, if (x - 2 < fill) 255 else 40)
        if (charging && (t / 500) % 2 == 0L) m.icon(Icons.BOLT, 7, 20, 1)
        return t < 1200 || charging
    }
}

/** Hours over minutes, big. Ticks while selected. */
class ClockShow : Show() {
    override val interval = 1000L
    override fun frame(t: Long): Boolean {
        m.clear(); val now = LocalTime.now()
        m.textCentered("%02d".format(now.hour), 1, 2); m.textCentered("%02d".format(now.minute), 13, 2)
        if (now.second % 2 == 0) m.set(12, 12, 90)
        return true
    }
}

/** Builds the show for a widget type from the app's saved settings. */
object Shows {
    fun streak(app: DotWidgetsApp): Show {
        val id = app.prefs.all.keys.firstOrNull { it.endsWith(":" + StreakWidget.KEY_EPOCH_DAY) }?.substringBefore(":")?.toIntOrNull()
        val epoch = id?.let { app.getWidgetLong(it, StreakWidget.KEY_EPOCH_DAY) } ?: 0L
        val days = if (epoch == 0L) 0L else ChronoUnit.DAYS.between(LocalDate.ofEpochDay(epoch), LocalDate.now()).coerceAtLeast(0)
        return StreakShow(id?.let { app.getWidgetString(it, StreakWidget.KEY_LABEL) } ?: "STREAK", days, id?.let { app.getWidgetString(it, StreakWidget.KEY_ICON) })
    }
    fun countdown(app: DotWidgetsApp): Show {
        val id = app.prefs.all.keys.firstOrNull { it.endsWith(":" + CountdownWidget.KEY_LABEL) }?.substringBefore(":")?.toIntOrNull()
        val label = id?.let { app.getWidgetString(it, CountdownWidget.KEY_LABEL) } ?: "COUNTDOWN"
        val repeat = id?.let { app.getWidgetString(it, CountdownWidget.KEY_REPEAT) }
        val epoch = if (repeat != null) CountdownWidget.nextOccurrence(repeat).toEpochDay() else id?.let { app.getWidgetLong(it, CountdownWidget.KEY_EPOCH_DAY) } ?: 0L
        val days = if (epoch == 0L) 0L else ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.ofEpochDay(epoch)).coerceAtLeast(0)
        return CountdownShow(label, days)
    }
    fun steps(app: DotWidgetsApp): Show {
        val id = app.prefs.all.keys.firstOrNull { it.endsWith(":" + StepsWidget.KEY_GOAL) }?.substringBefore(":")?.toIntOrNull()
        val goal = id?.let { app.getWidgetLong(it, StepsWidget.KEY_GOAL) }?.takeIf { it > 0 } ?: 8000L
        return StepsShow(app.prefs.getInt("steps:today", 0), goal)
    }
    fun battery(context: Context): Show {
        val bm = context.getSystemService(BatteryManager::class.java)
        return BatteryShow(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY), bm.isCharging)
    }
}

// ---------------------------------------------------------------- PLAYER ----

/** Runs a Show on the matrix through the SDK: as a toy (setMatrixFrame) or on demand (setAppMatrixFrame). */
class Player(private val manager: GlyphMatrixManager, private val onDemand: Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private var show: Show? = null
    private var start = 0L
    private val tick = object : Runnable {
        override fun run() {
            val s = show ?: return
            val more = try { s.frame(System.currentTimeMillis() - start) } catch (_: Exception) { false }
            push(s)
            if (more) handler.postDelayed(this, s.interval)
            else if (onDemand) handler.postDelayed({ stop(); try { manager.closeAppMatrix() } catch (_: Exception) {} }, 4000)
        }
    }
    private fun push(s: Show) {
        try {
            // The phone's LEDs take 0-4095 (the SDK's own renderer does grey*16); Matrix works in 0-255.
            val f = IntArray(s.m.px.size) { s.m.px[it].coerceIn(0, 255) * 16 }
            if (onDemand) manager.setAppMatrixFrame(f) else manager.setMatrixFrame(f)
        } catch (_: Exception) { }
    }
    fun play(s: Show) { stop(); show = s; start = System.currentTimeMillis(); handler.post(tick) }
    fun stop() { handler.removeCallbacks(tick); show = null }
}

// ------------------------------------------------------------------ TOYS ----

/** Base Glyph Toy service, after Nothing's example: bind → register → play; button hold replays. */
abstract class DotToy : Service() {
    abstract fun show(): Show
    private var manager: GlyphMatrixManager? = null
    private var player: Player? = null

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE, GlyphToy.EVENT_ACTION_DOWN -> player?.play(show())
                GlyphToy.EVENT_AOD -> player?.play(show())
            }
        }
    })

    override fun onBind(intent: Intent?): IBinder {
        GlyphMatrixManager.getInstance(applicationContext)?.let { gmm ->
            manager = gmm
            gmm.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    gmm.register(Glyph.DEVICE_23112)
                    try { gmm.setGlyphMatrixTimeout(false) } catch (_: Exception) {}
                    player = Player(gmm, onDemand = false).also { it.play(show()) }
                }
                override fun onServiceDisconnected(name: ComponentName?) {}
            })
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        player?.stop(); player = null
        manager?.let { it.turnOff(); it.unInit() }; manager = null
        return false
    }
}

class StreakToy : DotToy() { override fun show() = Shows.streak(DotWidgetsApp.instance) }
class CountdownToy : DotToy() { override fun show() = Shows.countdown(DotWidgetsApp.instance) }
class StepsToy : DotToy() { override fun show() = Shows.steps(DotWidgetsApp.instance) }
class BatteryToy : DotToy() { override fun show() = Shows.battery(this) }
class ClockToy : DotToy() { override fun show() = ClockShow() }

// ------------------------------------------------------------ ON DEMAND ----

/** "PLAY ON GLYPH": light the matrix now, from the app, without the button. */
object GlyphNow {
    fun play(context: Context, show: Show) {
        if (!hasGlyphMatrix()) return
        val gmm = GlyphMatrixManager.getInstance(context.applicationContext) ?: return
        gmm.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                gmm.register(Glyph.DEVICE_23112)
                Player(gmm, onDemand = true).play(show)
                Handler(Looper.getMainLooper()).postDelayed({ try { gmm.closeAppMatrix() } catch (_: Exception) {}; gmm.unInit() }, 20_000)
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        })
    }

    /** Nothing's own "Manage Glyph Toys" screen, where the user adds our toys to the button carousel. */
    fun openToyManager(context: Context) {
        try {
            context.startActivity(Intent().setComponent(ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }
}
