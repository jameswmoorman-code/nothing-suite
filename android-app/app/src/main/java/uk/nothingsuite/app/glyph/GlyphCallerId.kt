package uk.nothingsuite.app.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.provider.ContactsContract
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import uk.nothingsuite.app.inbox.CallInbox
import uk.nothingsuite.app.transcript.Risk
import uk.nothingsuite.app.transcript.TranscriptFrame
import uk.nothingsuite.design.glyph.Matrix

/**
 * Glyph caller ID — the back of the phone during a screened call.
 *
 *   top row     who is calling (contact name if we know them, else the number), scrolling
 *   bottom row  the latest thing the caller said, scrolling once as it arrives
 *   SCAM        the divider flashes and SCAM LIKELY scrolls through
 *   idle        a ring with the number of new calls in the inbox
 *
 * Nothing only lets an ordinary app draw on the matrix while the phone is unlocked,
 * which is the opposite of when you need this. So the same frames go out two ways:
 *   • [ConciergeToy] — a Glyph Toy. Pick "Concierge" with the Glyph button once and
 *     it draws locked or unlocked, face down on the desk.
 *   • on demand (setAppMatrixFrame) from the background service — works when unlocked.
 */
object GlyphCallerId {
    private const val TAG = "GlyphCallerId"
    private const val N = 25
    private val handler = Handler(Looper.getMainLooper())

    /** Where frames go. The toy registers while it's the selected toy; the service's on-demand sink otherwise. */
    @Volatile private var toySink: ((IntArray) -> Unit)? = null
    @Volatile private var appSink: ((IntArray) -> Unit)? = null
    @Volatile private var appSinkClose: (() -> Unit)? = null

    val isMatrixPhone get() = Common.is23112() || Common.is25111p()

    // call state
    private var live = false
    private var who = ""
    private var whoX = 0
    private val queue = ArrayDeque<String>()
    private var line: String? = null
    private var lineX = 0
    private var risk = Risk.NONE
    private var tickCount = 0

    // ---- input ------------------------------------------------------------------

    fun onFrame(context: Context, frame: TranscriptFrame) {
        when (frame.type) {
            "call_started" -> start(context, frame.from)
            "final" -> if (live && frame.text?.isNotBlank() == true) enqueue(frame.text)
            "alert" -> if (live) { val r = Risk.parse(frame.level); if (r > risk) { risk = r; if (r == Risk.SCAM) queue.addFirst("SCAM LIKELY") } }
            "call_ended" -> stop()
        }
    }

    private fun start(context: Context, number: String?) {
        if (!isMatrixPhone) return
        live = true; risk = Risk.NONE; queue.clear(); line = null
        who = displayName(context, number)
        whoX = N
        handler.removeCallbacks(tick); handler.post(tick)
        Log.i(TAG, "call from $who → toy=${toySink != null} app=${appSink != null}")
    }

    private fun stop() {
        if (!live) return
        live = false
        handler.removeCallbacks(tick)
        val m = Matrix(); m.textCentered("END", 9); push(m)
        handler.postDelayed({ showIdle(); appSinkClose?.invoke() }, 1500)
    }

    private fun enqueue(text: String) {
        val clean = text.trim().replace(Regex("[^A-Za-z0-9 %:!.,/-]"), "").replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return
        if (queue.size >= 4) queue.removeFirst()           // keep up with a fast talker: drop the oldest
        queue.addLast(clean)
    }

    // ---- frames -------------------------------------------------------------------

    private val tick = object : Runnable {
        override fun run() {
            if (!live) return
            tickCount++
            val m = Matrix()

            val whoW = m.textWidth(who)
            if (whoW <= N) m.textCentered(who, 2)
            else { m.text(who, whoX, 2); if (tickCount % 2 == 0) { whoX--; if (whoX < -whoW) whoX = N } }

            val divV = when (risk) { Risk.SCAM -> if (tickCount / 5 % 2 == 0) 255 else 40; Risk.CAUTION -> 120; else -> 50 }
            for (x in 0 until N step 2) m.set(x, 11, divV)

            if (line == null && queue.isNotEmpty()) { line = queue.removeFirst(); lineX = N }
            line?.let { s ->
                val w = m.textWidth(s)
                m.text(s, lineX, 15)
                lineX--
                if (lineX < -w) line = null
            }
            push(m)
            handler.postDelayed(this, 70)
        }
    }

    /**
     * Standby face for the toy — the desk-mode face:
     *   time on top, the number of new screened calls underneath (big when there are some),
     *   and a bright frame when the phone is face down with desk mode on ("I'm busy").
     */
    fun idleFrame(): Matrix {
        val m = Matrix()
        val unread = CallInbox.calls.value.count { !it.read && !it.live }
        val busy = uk.nothingsuite.app.desk.DeskMode.faceDown.value && uk.nothingsuite.app.NothingSuiteApp.instance.settings.deskMode
        val t = java.time.LocalTime.now()
        m.textCentered("%d:%02d".format(t.hour, t.minute), 3, 1, if (busy) 255 else 120)
        for (x in 0 until N step 2) m.set(x, 12, if (busy) 110 else 40)
        if (unread > 0) m.textCentered(unread.coerceAtMost(99).toString(), 15, 1, 255)
        else { m.set(11, 18, 70); m.set(13, 18, 70) }
        if (busy) for (i in 0 until N) { m.set(i, 0, 60); m.set(i, N - 1, 60); m.set(0, i, 60); m.set(N - 1, i, 60) }
        return m
    }

    private val idleTick = object : Runnable { override fun run() { if (!live && toySink != null) { push(idleFrame(), toyOnly = true); handler.postDelayed(this, 15_000) } } }
    private fun showIdle() { handler.removeCallbacks(idleTick); if (toySink != null) handler.post(idleTick) }

    private fun push(m: Matrix, toyOnly: Boolean = false) {
        val f = IntArray(m.px.size) { m.px[it].coerceIn(0, 255) * 16 }
        toySink?.invoke(f)
        if (!toyOnly && toySink == null) appSink?.invoke(f)
    }

    // ---- sinks ---------------------------------------------------------------------

    fun attachToy(sink: (IntArray) -> Unit) { toySink = sink; if (live) { handler.removeCallbacks(tick); handler.post(tick) } else showIdle() }
    fun detachToy() { toySink = null; handler.removeCallbacks(idleTick) }

    /** The service's fallback path: connect on demand; frames only show while the phone is unlocked. */
    fun attachAppSink(context: Context) {
        if (appSink != null || !isMatrixPhone) return
        val gmm = GlyphMatrixManager.getInstance(context.applicationContext) ?: return
        var ready = false
        gmm.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                ready = runCatching { gmm.register(if (Common.is25111p()) Glyph.DEVICE_25111p else Glyph.DEVICE_23112) }.getOrDefault(false)
                runCatching { gmm.setGlyphMatrixTimeout(false) }
                Log.i(TAG, "on-demand matrix ready=$ready")
            }
            override fun onServiceDisconnected(name: ComponentName?) { ready = false }
        })
        appSink = { f -> if (ready) runCatching { gmm.setAppMatrixFrame(f) }.onFailure { Log.w(TAG, "frame failed: ${it.message}") } }
        appSinkClose = { runCatching { gmm.closeAppMatrix() } }
    }

    fun releaseAppSink() { appSinkClose?.invoke(); appSink = null; appSinkClose = null }

    /** Contact name for the number, else the number with the country code trimmed. */
    private fun displayName(context: Context, number: String?): String {
        if (number.isNullOrBlank() || number == "unknown") return "WITHHELD"
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) return it.getString(0).uppercase()
                }
            }
        }
        return number.replace("+44", "0").replace(" ", "")
    }
}

/**
 * The "Concierge" Glyph Toy. Nothing binds this while it's the selected toy; we hand
 * the matrix to [GlyphCallerId], which draws the standby ring, then the live caller ID
 * when a call comes in — locked, unlocked, face down, whatever.
 */
class ConciergeToy : Service() {
    private var manager: GlyphMatrixManager? = null

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                // Hold the button while idle: open the inbox on the screen.
                GlyphToy.EVENT_ACTION_DOWN -> runCatching {
                    startActivity(Intent(this@ConciergeToy, uk.nothingsuite.app.inbox.InboxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                GlyphToy.EVENT_CHANGE, GlyphToy.EVENT_AOD -> manager?.let { gmm -> GlyphCallerId.attachToy { f -> runCatching { gmm.setMatrixFrame(f) } } }
            }
        }
    })

    override fun onBind(intent: Intent?): IBinder {
        GlyphMatrixManager.getInstance(applicationContext)?.let { gmm ->
            manager = gmm
            gmm.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    runCatching { gmm.register(if (Common.is25111p()) Glyph.DEVICE_25111p else Glyph.DEVICE_23112) }
                    runCatching { gmm.setGlyphMatrixTimeout(false) }
                    GlyphCallerId.attachToy { f -> runCatching { gmm.setMatrixFrame(f) } }
                }
                override fun onServiceDisconnected(name: ComponentName?) { GlyphCallerId.detachToy() }
            })
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        GlyphCallerId.detachToy()
        manager?.let { runCatching { it.turnOff() }; runCatching { it.unInit() } }; manager = null
        return false
    }
}
