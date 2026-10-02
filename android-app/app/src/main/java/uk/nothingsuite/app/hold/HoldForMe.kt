package uk.nothingsuite.app.hold

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import uk.nothingsuite.app.transcript.ConciergeLink
import uk.nothingsuite.app.transcript.TranscriptFrame

/** Where a Hold For Me call is, mirrored from the server's `hold` frames. */
data class HoldState(
    val session: String? = null,
    val state: String = "idle",        // idle | dialing_you | connecting | talking | holding | human | joined | ended
    val to: String? = null,
    val message: String? = null,       // the server's last status line
    val heard: List<Pair<String, Boolean>> = emptyList(),   // (line, recorded?) while holding
    val startedAt: Long = 0,
    val holdSince: Long = 0,
    val error: String? = null,
) {
    val active get() = state !in setOf("idle", "ended")
    val canHold get() = state == "talking"
    val onHold get() = state == "holding"
}

/**
 * Hold For Me — the concierge waits in the queue for you.
 * Listens to the one server link for `hold` frames and sends the three requests.
 */
object HoldForMe {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(HoldState())
    val state: StateFlow<HoldState> = _state.asStateFlow()
    private var started = false

    fun init() {
        if (started) return
        started = true
        scope.launch { ConciergeLink.frames.collect(::onFrame) }
    }

    private fun onFrame(f: TranscriptFrame) {
        when (f.type) {
            "hold" -> _state.update { s ->
                val fresh = f.session != null && f.session != s.session
                val base = if (fresh) HoldState(session = f.session, to = f.to, startedAt = System.currentTimeMillis()) else s
                val st = f.state ?: base.state
                if (f.heard && f.text != null) base.copy(state = st, heard = (base.heard + (f.text to f.recorded)).takeLast(12))
                else base.copy(
                    state = st, to = f.to ?: base.to, message = f.text ?: base.message, error = null,
                    holdSince = if (st == "holding" && base.holdSince == 0L) System.currentTimeMillis() else base.holdSince,
                )
            }
            "error" -> if (f.callSid == null) _state.update {
                // A server-side refusal (bad number, no USER_NUMBER, Twilio error): back to the form.
                if (it.session == null) HoldState(error = f.text, to = it.to) else it.copy(error = f.text)
            }
        }
    }

    fun start(number: String) {
        _state.value = HoldState(state = "dialing_you", to = number, startedAt = System.currentTimeMillis(), message = "Asking the concierge to ring you…")
        if (!ConciergeLink.sendRaw { put("type", "hold_start"); put("to", number) }) {
            _state.update { it.copy(state = "idle", error = "Not connected to the concierge — try again in a moment.") }
            return
        }
        scope.launch {
            kotlinx.coroutines.delay(12_000)
            _state.update { if (it.session == null && it.state == "dialing_you") HoldState(to = number, error = "The server didn't answer. Is it running the latest code? (Restart it: Ctrl+C, then npm start.)") else it }
        }
    }

    fun holdNow() { _state.value.session?.let { sid -> ConciergeLink.sendRaw { put("type", "hold_now"); put("session", sid) } } }
    fun cancel() { _state.value.session?.let { sid -> ConciergeLink.sendRaw { put("type", "hold_cancel"); put("session", sid) } }; _state.update { it.copy(state = "ended", message = "Cancelled") } }
    fun reset() { _state.value = HoldState() }
}
