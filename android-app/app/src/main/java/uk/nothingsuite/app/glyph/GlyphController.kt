package uk.nothingsuite.app.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin, device-aware wrapper over Nothing's Glyph Developer Kit.
 *
 * Progress lives on the segmented strip that every Glyph phone has
 * (C1 segments on Phone (1)/(2); C on (2a)/(3a)). We hide that difference
 * behind [showProgress] so callers only speak in percent.
 *
 * Lifecycle: init() lazily on first use, session opened on connect, closed
 * in [release]. Everything is best-effort — a Glyph failure must never
 * crash a call or a notification listener.
 */
class GlyphController(private val context: Context) {

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var manager: GlyphManager? = null
    private var sessionOpen = false

    private val callback = object : GlyphManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            val gm = manager ?: return
            val ok = when {
                Common.is20111() -> gm.register(Common.DEVICE_20111)   // Phone (1)
                Common.is22111() -> gm.register(Common.DEVICE_22111)   // Phone (2)
                Common.is23111() -> gm.register(Common.DEVICE_23111)   // Phone (2a)
                Common.is23113() -> gm.register(Common.DEVICE_23113)   // Phone (2a) Plus
                Common.is24111() -> gm.register(Common.DEVICE_24111)   // Phone (3a) / (3a) Pro
                else -> false
            }
            if (ok) {
                runCatching { gm.openSession() }.onSuccess { sessionOpen = true }
            }
            _connected.value = ok && sessionOpen
            Log.i(TAG, "Glyph service connected, registered=$ok model=${Build.MODEL}")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sessionOpen = false
            _connected.value = false
        }
    }

    val isSupported: Boolean
        get() = Build.MANUFACTURER.equals("Nothing", ignoreCase = true)

    fun connect() {
        if (!isSupported || manager != null) return
        manager = GlyphManager.getInstance(context.applicationContext).also { it.init(callback) }
    }

    /** Light the progress strip to [percent] (0–100). */
    fun showProgress(percent: Int, reverse: Boolean = false) = safely {
        val gm = manager ?: return@safely
        val frame = progressFrame(gm)
        gm.displayProgress(frame, percent.coerceIn(0, 100), reverse)
    }

    /** Pick the strip channel per device. */
    private fun progressFrame(gm: GlyphManager): GlyphFrame {
        val b = gm.glyphFrameBuilder
        return when {
            Common.is20111() || Common.is22111() -> b.buildChannelC().build()   // 16-segment C1 strip
            Common.is23111() || Common.is23113() -> b.buildChannelC().build()   // 24-segment C
            else -> b.buildChannelC().build()
        }
    }

    /** Short flash of the whole strip — used for "screening started". */
    fun pulse(cycles: Int = 2) = safely {
        val gm = manager ?: return@safely
        val frame = gm.glyphFrameBuilder
            .buildChannelC()
            .buildPeriod(600)
            .buildCycles(cycles)
            .buildInterval(200)
            .build()
        gm.animate(frame)
    }

    fun clear() = safely { manager?.turnOff() }

    fun release() = safely {
        clear()
        if (sessionOpen) manager?.closeSession()
        manager?.unInit()
        manager = null
        sessionOpen = false
        _connected.value = false
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: GlyphException) {
            Log.w(TAG, "Glyph op failed: ${e.message}")
        } catch (e: Throwable) {
            Log.w(TAG, "Glyph op failed", e)
        }
    }

    private companion object {
        const val TAG = "GlyphController"
    }
}
