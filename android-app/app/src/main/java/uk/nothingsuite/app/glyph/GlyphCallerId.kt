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
import kotlinx.coroutines.launch
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
    private const val BUSY_WORD = "CONCIERGE ON"   // slides across once a minute while face down
    private val handler = Handler(Looper.getMainLooper())

    /** Where frames go. The toy registers while it's the selected toy; the service's on-demand sink otherwise. */
    @Volatile private var toySink: ((IntArray) -> Unit)? = null
    /** When Nothing last had the Concierge toy awake (0 = never since the app started). */
    @Volatile var toyLastSeen: Long = 0L; private set
    /** Has Nothing bound the toy recently? Asleep-but-selected toys wake once a minute, so 3 min is a safe window. */
    val toySelected: Boolean get() = toySink != null || System.currentTimeMillis() - toyLastSeen < 3 * 60_000L
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
        appContext = context.applicationContext
        live = true; risk = Risk.NONE; queue.clear(); line = null
        who = displayName(context, number)
        whoX = N
        handler.removeCallbacks(tick); handler.post(tick)
        Log.i(TAG, "call from $who → toy=${toySink != null} app=${appSink != null}")
        if (toySink == null) wakeForToy(context)
    }

    /**
     * Nothing puts the selected toy to sleep when the phone has been locked a while, and
     * only brings it back when the screen wakes. A lock-screen app frame is ignored. So if
     * the toy is asleep when a call lands, wake the screen briefly — like a ringing call.
     */
    @Suppress("DEPRECATION")
    private fun wakeForToy(context: Context, ms: Long = 15_000) {
        runCatching {
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            val wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or android.os.PowerManager.ON_AFTER_RELEASE,
                "nothingsuite:glyph-callerid",
            )
            wl.acquire(ms)
            Log.i(TAG, "toy asleep → woke the screen so Nothing re-binds the toy")
        }.onFailure { Log.w(TAG, "wake failed: ${it.message}") }
    }

    private fun stop() {
        if (!live) return
        live = false
        handler.removeCallbacks(tick)
        Log.i(TAG, "call ended → END (toy=${toySink != null}, screenOn=$screenOn)")
        val m = Matrix(); m.textCentered("END", 9); push(m)
        // Keep the screen (and so the Glyph) awake long enough to show END, then the count.
        appContext?.let { wakeForToy(it, 9_000) }
        // Only close the on-demand channel if that's what drew the call — closing it while the
        // toy is showing blanks the whole matrix.
        handler.postDelayed({ showIdle(); if (toySink == null) appSinkClose?.invoke() }, 1500)
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
            if (whoW <= 21) m.textCentered(who, 4)
            else { m.text(who, whoX, 4); whoX--; if (whoX < -whoW) whoX = N }

            val divV = when (risk) { Risk.SCAM -> if (tickCount / 5 % 2 == 0) 255 else 40; Risk.CAUTION -> 120; else -> 50 }
            for (x in 2 until N - 2 step 2) m.set(x, 12, divV)

            if (line == null && queue.isNotEmpty()) { line = queue.removeFirst(); lineX = N }
            line?.let { s ->
                val w = m.textWidth(s)
                m.text(s, lineX, 14)
                lineX--
                if (lineX < -w) line = null
            }
            push(m)
            handler.postDelayed(this, 45)
        }
    }

    /**
     * Standby face for the toy — the desk-mode face:
     *   time on top, the number of new screened calls underneath (big when there are some),
     *   and a bright frame when the phone is face down with desk mode on ("I'm busy").
     */
    private var idleCount = 0
    private var busyWordX = Int.MIN_VALUE      // > MIN when the BUSY word is sliding through

    private var appContext: Context? = null
    private val screenOn: Boolean get() = appContext?.getSystemService(android.os.PowerManager::class.java)?.isInteractive ?: true

    fun idleFrame(): Matrix {
        val m = Matrix()
        val unread = CallInbox.calls.value.count { !it.read && !it.live }
        val busy = uk.nothingsuite.app.desk.DeskMode.faceDown.value && uk.nothingsuite.app.NothingSuiteApp.instance.settings.deskMode
        val t = java.time.LocalTime.now()
        // Compact 3×5 time: "12:14" is 19 dots wide, fits with margins. (The 5×7 font would be 29.)
        MiniFont.textCentered(m, "%d:%02d".format(t.hour, t.minute), 6, 140)

        // Middle band (rows 8–14): a dotted line normally; when busy it breathes, and once a
        // minute the word BUSY slides across it.
        if (busy && busyWordX > Int.MIN_VALUE) {
            val w = m.textWidth(BUSY_WORD)
            m.text(BUSY_WORD, busyWordX, 9, 1, 255)
            busyWordX -= 2
            if (busyWordX < -w) busyWordX = Int.MIN_VALUE
        } else {
            // Screen on: the line breathes while busy. Screen off (one frame a minute): solid when busy.
            val pulse = if (!busy) 40 else if (!screenOn) 200 else (90 + 165 * ((Math.sin(idleCount * 0.35) + 1) / 2)).toInt()
            for (x in 2 until N - 2 step (if (busy && !screenOn) 1 else 2)) m.set(x, 12, pulse)
        }

        // The thing you glance at: how many calls the concierge has taken for you.
        if (unread > 0) m.textCentered(unread.coerceAtMost(99).toString(), 14, 1, 255)
        else { m.set(11, 17, 70); m.set(13, 17, 70) }
        return m
    }

    private val idleTick = object : Runnable {
        override fun run() {
            if (live || toySink == null) return
            idleCount++
            val busy = uk.nothingsuite.app.desk.DeskMode.faceDown.value && uk.nothingsuite.app.NothingSuiteApp.instance.settings.deskMode
            // Our own minute timer (160 ms × 375 ≈ 60 s) so the word shows even without the AOD tick.
            if (busy && idleCount % 375 == 0 && busyWordX == Int.MIN_VALUE) busyWordX = N
            push(idleFrame(), toyOnly = true)
            // Screen on: breathe at ~6 fps while busy, lazy refresh otherwise. Screen off: one frame a minute.
            val sliding = busyWordX > Int.MIN_VALUE
            handler.postDelayed(this, if (!screenOn) 60_000 else if (sliding) 90 else if (busy) 160 else 15_000)
        }
    }
    /** Called on the phone's always-on tick (once a minute): slide BUSY through if we're busy. */
    fun minuteTick() { if (uk.nothingsuite.app.desk.DeskMode.faceDown.value) busyWordX = N; showIdle() }
    /** Redraw now — called when the toy is (re)selected, on the AOD tick, and when face-down flips. */
    fun showIdle() { handler.removeCallbacks(idleTick); if (toySink != null) handler.post(idleTick) }

    private var deskWatcher: kotlinx.coroutines.Job? = null
    private var screenReceiver: android.content.BroadcastReceiver? = null
    private fun watchDesk() {
        // Screen on/off: redraw at once (and slide the word through if we're face down).
        if (screenReceiver == null) appContext?.let { ctx ->
            screenReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    Log.i(TAG, "screen event ${i?.action?.substringAfterLast('.')} (toy=${toySink != null}, live=$live)")
                    if (i?.action == Intent.ACTION_SCREEN_ON && uk.nothingsuite.app.desk.DeskMode.faceDown.value) busyWordX = N
                    idleCount = 0; showIdle()
                }
            }
            runCatching { ctx.registerReceiver(screenReceiver, android.content.IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }) }
        }
        if (deskWatcher != null) return
        deskWatcher = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            uk.nothingsuite.app.desk.DeskMode.faceDown.collect { down ->
                if (down) busyWordX = N
                idleCount = 0; showIdle()
                // Face down but the toy's asleep (phone locked)? Wake it briefly so CONCIERGE ON can slide through.
                if (down && toySink == null) appContext?.let { wakeForToy(it, 7_000) }
            }
        }
    }

    private fun push(m: Matrix, toyOnly: Boolean = false) {
        val f = IntArray(m.px.size) { m.px[it].coerceIn(0, 255) * 16 }
        toySink?.invoke(f)
        if (!toyOnly && toySink == null) appSink?.invoke(f)
    }

    // ---- sinks ---------------------------------------------------------------------

    fun attachToy(context: Context, sink: (IntArray) -> Unit) { Log.i(TAG, "toy awake (live=$live, screenOn=$screenOn)"); toyLastSeen = System.currentTimeMillis(); appContext = context.applicationContext; toySink = sink; watchDesk(); if (uk.nothingsuite.app.desk.DeskMode.faceDown.value && busyWordX == Int.MIN_VALUE) busyWordX = N; if (live) { handler.removeCallbacks(tick); handler.post(tick) } else showIdle() }
    fun detachToy() { Log.i(TAG, "toy asleep"); toyLastSeen = System.currentTimeMillis(); toySink = null; handler.removeCallbacks(idleTick) }

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
                GlyphToy.EVENT_CHANGE -> manager?.let { gmm -> GlyphCallerId.attachToy(this@ConciergeToy) { f -> runCatching { gmm.setMatrixFrame(f) } } }
                GlyphToy.EVENT_AOD -> { manager?.let { gmm -> GlyphCallerId.attachToy(this@ConciergeToy) { f -> runCatching { gmm.setMatrixFrame(f) } } }; GlyphCallerId.minuteTick() }
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
                    GlyphCallerId.attachToy(this@ConciergeToy) { f -> runCatching { gmm.setMatrixFrame(f) } }
                }
                override fun onServiceDisconnected(name: ComponentName?) { GlyphCallerId.detachToy() }
            })
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Nothing unbinds us when it puts the toy to sleep (always-on) or the user picks another toy.
        // Leave the last frame alone — turning the matrix off here is what made it go dark.
        GlyphCallerId.detachToy()
        manager?.let { runCatching { it.unInit() } }; manager = null
        return false
    }
}

/** 3×5 digits (and a colon) for when the 5×7 font won't fit — a time, a date. */
object MiniFont {
    private val glyphs = mapOf(
        '0' to listOf("###", "#.#", "#.#", "#.#", "###"),
        '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
        '2' to listOf("###", "..#", "###", "#..", "###"),
        '3' to listOf("###", "..#", "###", "..#", "###"),
        '4' to listOf("#.#", "#.#", "###", "..#", "..#"),
        '5' to listOf("###", "#..", "###", "..#", "###"),
        '6' to listOf("###", "#..", "###", "#.#", "###"),
        '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
        '8' to listOf("###", "#.#", "###", "#.#", "###"),
        '9' to listOf("###", "#.#", "###", "..#", "###"),
        ':' to listOf(".", "#", ".", "#", "."),
        ' ' to listOf(".", ".", ".", ".", "."),
    )
    private fun width(c: Char) = glyphs[c]?.get(0)?.length ?: 3
    fun width(s: String) = s.sumOf { width(it) + 1 } - 1
    fun text(m: Matrix, s: String, ox: Int, oy: Int, v: Int = 255) {
        var x = ox
        for (c in s) {
            val g = glyphs[c] ?: glyphs[' ']!!
            g.forEachIndexed { r, row -> row.forEachIndexed { cx, ch -> if (ch == '#') m.set(x + cx, oy + r, v) } }
            x += width(c) + 1
        }
    }
    fun textCentered(m: Matrix, s: String, oy: Int, v: Int = 255) = text(m, s, (m.n - width(s)) / 2, oy, v)
}
