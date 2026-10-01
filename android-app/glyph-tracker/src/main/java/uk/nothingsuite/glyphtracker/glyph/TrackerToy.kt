package uk.nothingsuite.glyphtracker.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import uk.nothingsuite.glyphtracker.tracker.Trackers

/**
 * Phone (3) Glyph Toy: while selected on the Glyph button it shows the live
 * tracker ring, or scrolls NOTHING TRACKED. Long-press replays the fill.
 */
class TrackerToy : Service() {
    private var manager: GlyphMatrixManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var start = 0L
    private var sweepFrom = 0

    private val tick = object : Runnable {
        override fun run() {
            val m = manager ?: return
            val t = System.currentTimeMillis() - start
            val active = Trackers.active.value
            val frame = if (active == null) Frames.idle(t) else {
                val target = active.percent
                val p = if (t < 900) sweepFrom + ((target - sweepFrom) * t / 900.0).toInt() else target
                Frames.progress(p)
            }
            try { m.setMatrixFrame(IntArray(frame.px.size) { frame.px[it].coerceIn(0, 255) * 16 }) } catch (_: Exception) {}
            handler.postDelayed(this, if (active == null || t < 900) 50 else 1000)
        }
    }

    private fun replay() { start = System.currentTimeMillis(); sweepFrom = 0; handler.removeCallbacks(tick); handler.post(tick) }

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE, GlyphToy.EVENT_ACTION_DOWN, GlyphToy.EVENT_AOD -> replay()
            }
        }
    })

    override fun onBind(intent: Intent?): IBinder {
        GlyphMatrixManager.getInstance(applicationContext)?.let { gmm ->
            manager = gmm
            gmm.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    gmm.register(if (Common.is25111p()) Glyph.DEVICE_25111p else Glyph.DEVICE_23112)
                    try { gmm.setGlyphMatrixTimeout(false) } catch (_: Exception) {}
                    replay()
                }
                override fun onServiceDisconnected(name: ComponentName?) {}
            })
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(tick)
        manager?.let { it.turnOff(); it.unInit() }; manager = null
        return false
    }
}
