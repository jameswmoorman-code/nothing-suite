package uk.nothingsuite.glyphtracker.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager
import com.nothing.ketchum.GlyphMatrixManager
import uk.nothingsuite.design.glyph.Fireworks
import uk.nothingsuite.design.glyph.Matrix
import uk.nothingsuite.glyphtracker.GlyphTrackerApp
import uk.nothingsuite.glyphtracker.tracker.ProgressState
import uk.nothingsuite.glyphtracker.tracker.Trackers

/** The three looks. Free = PLAIN; the unlock adds SWEEP and MILESTONE. */
enum class Animations(val id: String, val label: String, val premium: Boolean) {
    PLAIN("plain", "PLAIN", false),        // jump straight to the value
    SWEEP("sweep", "SWEEP", true),         // fill up from the last value
    MILESTONE("milestone", "MILESTONE", true); // sweep, plus a flash at 80 % and fireworks at 100 %

    companion object {
        fun current(app: GlyphTrackerApp): Animations {
            val wanted = entries.firstOrNull { it.id == app.animation } ?: SWEEP
            return if (wanted.premium && !app.unlocked) PLAIN else wanted
        }
    }
}

/**
 * Draws progress on whichever Glyph this phone has. Everything is best-effort:
 * a Glyph failure must never crash the notification listener.
 */
abstract class GlyphOutput(protected val context: Context) {
    protected val handler = Handler(Looper.getMainLooper())
    /** Tags the animation callbacks so cancelling them leaves the demo's own schedule alone. */
    protected val anim = Any()
    protected val app get() = GlyphTrackerApp.instance
    protected var shown = 0          // last percent drawn, for sweeps
    val isSupported: Boolean get() = Build.MANUFACTURER.equals("Nothing", ignoreCase = true)

    abstract fun connect()
    abstract fun release()
    /** Draw [percent] right now, no animation. */
    protected abstract fun draw(percent: Int, label: String)
    protected abstract fun flash()
    protected abstract fun off()

    fun show(state: ProgressState) {
        connect()
        handler.removeCallbacksAndMessages(anim)
        val look = Animations.current(app)
        val target = state.percent.coerceIn(0, 100)
        val from = if (look == Animations.PLAIN) target else shown.coerceAtMost(target)
        var p = from
        val step = object : Runnable {
            override fun run() {
                draw(p, state.label); shown = p
                if (p < target) { p = (p + 4).coerceAtMost(target); handler.postDelayed(this, anim, 30) }
                else finished(target, look)
            }
        }
        handler.postDelayed(step, anim, 0)
    }

    private fun finished(target: Int, anim: Animations) {
        if (anim == Animations.MILESTONE && target == 80) flash()
        if (target >= 100) {
            if (anim == Animations.MILESTONE) celebrate()
            // A finished tracker shouldn't stay lit forever.
            handler.postDelayed({ Trackers.clearCompleted(); if (Trackers.active.value == null) clear() }, anim, COMPLETE_HOLD_MS)
        } else keepAlive()
    }

    protected open fun celebrate() = flash()
    /** The strip stays lit by itself; the matrix needs nudging. Subclasses override. */
    protected open fun keepAlive() {}

    fun clear() { handler.removeCallbacksAndMessages(anim); shown = 0; safely { off() } }

    /** DEMO button: 0 → 100 over a few seconds. */
    fun demo() {
        Trackers.clear()
        val steps = listOf(10, 40, 60, 80, 100)
        steps.forEachIndexed { i, pct -> handler.postDelayed({ show(ProgressState(pct, "DEMO", "demo")) }, 1400L * i) }
    }

    protected inline fun safely(block: () -> Unit) { try { block() } catch (e: Throwable) { Log.w("GlyphTracker", "Glyph op failed", e) } }

    companion object {
        const val COMPLETE_HOLD_MS = 8_000L
        fun forThisPhone(context: Context): GlyphOutput =
            if (Common.is23112() || Common.is25111p()) MatrixOutput(context) else StripOutput(context)
    }
}

/** Phone (1), (2), (2a), (2a) Plus, (3a): the segmented light strip, channel C. */
class StripOutput(context: Context) : GlyphOutput(context) {
    private var gm: GlyphManager? = null
    private var ready = false

    override fun connect() {
        if (!isSupported || gm != null) return
        gm = GlyphManager.getInstance(context.applicationContext).also { m ->
            m.init(object : GlyphManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    val device = when {
                        Common.is20111() -> Glyph.DEVICE_20111
                        Common.is22111() -> Glyph.DEVICE_22111
                        Common.is23111() -> Glyph.DEVICE_23111
                        Common.is23113() -> Glyph.DEVICE_23113
                        Common.is24111() -> Glyph.DEVICE_24111
                        Common.is25111() -> Glyph.DEVICE_25111
                        else -> null
                    } ?: return
                    ready = m.register(device)
                    safely { m.openSession() }
                }
                override fun onServiceDisconnected(name: ComponentName?) { ready = false }
            })
        }
    }

    override fun draw(percent: Int, label: String) = safely {
        val m = gm ?: return; if (!ready) return
        m.displayProgress(m.glyphFrameBuilder.buildChannelC().build(), percent, false)
    }

    override fun flash() = safely {
        val m = gm ?: return; if (!ready) return
        m.animate(m.glyphFrameBuilder.buildChannelC().buildPeriod(500).buildCycles(2).buildInterval(150).build())
        handler.postDelayed({ draw(shown, "") }, anim, 1500)
    }

    override fun off() { gm?.turnOff() }

    override fun release() { safely { off(); gm?.closeSession(); gm?.unInit() }; gm = null; ready = false }
}

/** Phone (3) and later matrix phones: a ring that fills clockwise, the number in the middle. */
class MatrixOutput(context: Context) : GlyphOutput(context) {
    private var gmm: GlyphMatrixManager? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    override fun connect() {
        if (!isSupported || gmm != null) return
        gmm = GlyphMatrixManager.getInstance(context.applicationContext).also { m ->
            m.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    ready = m.register(if (Common.is25111p()) Glyph.DEVICE_25111p else Glyph.DEVICE_23112)
                    safely { m.setGlyphMatrixTimeout(false) }
                    pending?.invoke(); pending = null
                }
                override fun onServiceDisconnected(name: ComponentName?) { ready = false }
            })
        }
    }

    private fun push(m: Matrix): Unit = safely {
        val g = gmm ?: return
        if (!ready) { pending = { push(m) }; return }
        g.setAppMatrixFrame(IntArray(m.px.size) { m.px[it].coerceIn(0, 255) * 16 })
    }

    override fun draw(percent: Int, label: String) { push(Frames.progress(percent)) }

    override fun flash() {
        val m = Matrix(); m.ring(1.0, v = 255); m.textCentered(shown.toString(), 9, 1); push(m)
        handler.postDelayed({ draw(shown, "") }, anim, 400)
        handler.postDelayed({ push(m) }, anim, 800)
        handler.postDelayed({ draw(shown, "") }, anim, 1200)
    }

    override fun celebrate() {
        val fw = Fireworks(); val m = Matrix()
        val tick = object : Runnable {
            override fun run() { if (fw.step(m)) { push(m); handler.postDelayed(this, anim, 50) } else draw(100, "") }
        }
        handler.postDelayed(tick, anim, 0)
    }

    /** The system may drop an app frame after a while; redraw every 20 s while something is live. */
    override fun keepAlive() {
        handler.postDelayed({ Trackers.active.value?.let { draw(it.percent, it.label); keepAlive() } }, anim, 20_000)
    }

    override fun off() { gmm?.closeAppMatrix(); gmm?.turnOff() }

    override fun release() { safely { off(); gmm?.unInit() }; gmm = null; ready = false }
}

/** Frame recipes shared by the live output and the toy. */
object Frames {
    fun progress(percent: Int): Matrix {
        val m = Matrix()
        m.ring(percent / 100.0, r = 11.0, thickness = 1.8, v = 255, dim = 30)
        val s = percent.coerceIn(0, 100).toString()
        if (s.length <= 2) m.textCentered(s, 5, 2) else m.textCentered(s, 9, 1)
        return m
    }
    fun idle(t: Long): Matrix {
        val m = Matrix(); val s = "NOTHING TRACKED"; val w = m.textWidth(s, 1)
        m.text(s, 25 - ((t / 60) % (w + 30)).toInt(), 9, 1, 180)
        return m
    }
}
