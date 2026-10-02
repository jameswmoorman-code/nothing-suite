package uk.nothingsuite.app.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.nothingsuite.app.settings.SecureSettings

enum class ScreeningStatus { Connecting, Waiting, Live, Ended, Error }
enum class Speaker { CALLER, CONCIERGE }

data class TranscriptLine(val text: String, val final: Boolean, val speaker: Speaker = Speaker.CALLER)

data class TranscriptUiState(
    val status: ScreeningStatus = ScreeningStatus.Connecting,
    val caller: String? = null,
    val callSid: String? = null,
    val lines: List<TranscriptLine> = emptyList(),
    val message: String? = null,
    /** Set after TAKE THE CALL — the concierge is ringing you. */
    val connecting: Boolean = false,
)

/**
 * Builds the rolling two-way transcript from [ConciergeLink] frames:
 *   assistant → a CONCIERGE line (what the caller is hearing)
 *   delta     → append to / replace the trailing non-final CALLER line
 *   final     → freeze the trailing line
 * and sends the user's choices back as actions.
 */
class TranscriptViewModel(
    private val settings: SecureSettings,
    expectedCaller: String?,
    expectedCallSid: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TranscriptUiState(
            caller = expectedCaller ?: ConciergeLink.activeCaller,
            callSid = expectedCallSid ?: ConciergeLink.activeCallSid,
            status = if (ConciergeLink.activeCallSid != null) ScreeningStatus.Live
                     else if (ConciergeLink.connected.value) ScreeningStatus.Waiting else ScreeningStatus.Connecting,
        )
    )
    val state: StateFlow<TranscriptUiState> = _state.asStateFlow()

    init {
        ConciergeLink.start(settings)
        // Catch up on anything said before this screen opened (the greeting, usually).
        ConciergeLink.history.forEach(::handle)
        viewModelScope.launch { ConciergeLink.frames.collect(::handle) }
    }

    /** Is this frame about the call we're showing? Unknown call → adopt it. */
    private fun matches(frame: TranscriptFrame): Boolean {
        val mine = _state.value.callSid
        if (frame.callSid == null) return true
        if (mine == null) { _state.update { it.copy(callSid = frame.callSid, caller = it.caller ?: frame.from) }; return true }
        return frame.callSid == mine
    }

    private fun handle(frame: TranscriptFrame) {
        when (frame.type) {
            "hello" -> _state.update { if (it.status == ScreeningStatus.Connecting) it.copy(status = ScreeningStatus.Waiting) else it }
            "link_error" -> _state.update { if (it.status != ScreeningStatus.Ended) it.copy(message = "Reconnecting… (${frame.text})") else it }
            "call_started" -> if (_state.value.status == ScreeningStatus.Ended || _state.value.callSid == null || frame.callSid == _state.value.callSid) {
                _state.update { it.copy(status = ScreeningStatus.Live, caller = frame.from ?: it.caller, callSid = frame.callSid, lines = if (frame.callSid != it.callSid) emptyList() else it.lines, message = null, connecting = false) }
            }
            "assistant" -> if (matches(frame)) addLine(TranscriptLine(frame.text.orEmpty(), final = true, speaker = Speaker.CONCIERGE))
            "delta" -> if (matches(frame)) appendDelta(frame.text.orEmpty())
            "final" -> if (matches(frame)) finaliseLine(frame.text.orEmpty())
            "call_ended" -> if (matches(frame)) _state.update { it.copy(status = ScreeningStatus.Ended, message = frame.text, connecting = false) }
            "error" -> if (frame.callSid == null || matches(frame)) _state.update { it.copy(message = frame.text, connecting = false) }
        }
    }

    // ---- actions -------------------------------------------------------------

    fun askReason() = act("ask_reason")
    fun callBack() = act("callback")
    fun takeMessage() = act("message")
    fun hangUp() = act("hangup")
    fun takeTheCall() {
        val mine = settings.myNumber
        if (mine.isBlank()) { _state.update { it.copy(message = "Add MY NUMBER in Setup so the concierge can ring you.") }; return }
        _state.update { it.copy(connecting = true, message = null) }
        act("connect", to = mine)
    }
    fun say(text: String) = act("say", text = text)

    private fun act(action: String, text: String? = null, to: String? = null) {
        val ok = ConciergeLink.send(action, text, to, _state.value.callSid)
        if (!ok) _state.update { it.copy(message = "Not connected to the concierge — try again in a moment.") }
    }

    // ---- transcript building -------------------------------------------------

    private fun addLine(line: TranscriptLine) = _state.update { s ->
        val lines = s.lines.toMutableList()
        // Close any half-finished caller line first so the order reads correctly.
        val last = lines.lastOrNull()
        if (last != null && !last.final) lines[lines.lastIndex] = last.copy(final = true)
        lines += line
        s.copy(status = ScreeningStatus.Live, lines = lines)
    }

    private fun appendDelta(delta: String) = _state.update { s ->
        val lines = s.lines.toMutableList()
        val last = lines.lastOrNull()
        if (last != null && !last.final && last.speaker == Speaker.CALLER) lines[lines.lastIndex] = last.copy(text = last.text + delta)
        else lines += TranscriptLine(delta, final = false)
        s.copy(status = ScreeningStatus.Live, lines = lines)
    }

    private fun finaliseLine(full: String) = _state.update { s ->
        val lines = s.lines.toMutableList()
        val last = lines.lastOrNull()
        if (last != null && !last.final && last.speaker == Speaker.CALLER) lines[lines.lastIndex] = TranscriptLine(full.ifBlank { last.text }, final = true)
        else if (full.isNotBlank()) lines += TranscriptLine(full, final = true)
        s.copy(status = ScreeningStatus.Live, lines = lines)
    }
}
