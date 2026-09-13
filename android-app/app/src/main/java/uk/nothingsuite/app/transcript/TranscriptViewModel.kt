package uk.nothingsuite.app.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.nothingsuite.app.settings.SecureSettings

enum class ScreeningStatus { Connecting, Waiting, Live, Ended, Error }

data class TranscriptLine(val text: String, val final: Boolean)

data class TranscriptUiState(
    val status: ScreeningStatus = ScreeningStatus.Connecting,
    val caller: String? = null,
    val lines: List<TranscriptLine> = emptyList(),
    val message: String? = null,
)

/**
 * Consumes the socket, builds a rolling transcript:
 *   delta  → append to / replace the trailing non-final line
 *   final  → freeze the trailing line
 * Reconnects with back-off while the call is live.
 */
class TranscriptViewModel(
    private val settings: SecureSettings,
    private val expectedCaller: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(TranscriptUiState(caller = expectedCaller))
    val state: StateFlow<TranscriptUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { connect() }

    private fun connect() {
        job?.cancel()
        job = viewModelScope.launch {
            var attempt = 0
            while (_state.value.status != ScreeningStatus.Ended) {
                val client = TranscriptSocketClient(settings.backendWsUrl, settings.sharedSecret)
                client.frames()
                    .catch { emit(TranscriptFrame(type = "error", text = it.message)) }
                    .collect { frame -> handle(frame) }
                if (_state.value.status == ScreeningStatus.Ended) break
                attempt++
                delay(minOf(1000L * attempt, 8000L))
            }
        }
    }

    private fun handle(frame: TranscriptFrame) {
        // Only show frames for the caller we just rejected, if we know it.
        val matches = expectedCaller == null || frame.from == null ||
            normalise(frame.from) == normalise(expectedCaller)

        when (frame.type) {
            "hello" -> _state.update { it.copy(status = ScreeningStatus.Waiting) }
            "call_started" -> if (matches) _state.update {
                it.copy(status = ScreeningStatus.Live, caller = frame.from ?: it.caller)
            }
            "delta" -> if (matches) appendDelta(frame.text.orEmpty())
            "final" -> if (matches) finaliseLine(frame.text.orEmpty())
            "call_ended" -> if (matches) _state.update { it.copy(status = ScreeningStatus.Ended) }
            "error" -> _state.update { it.copy(status = ScreeningStatus.Error, message = frame.text) }
        }
    }

    private fun appendDelta(delta: String) = _state.update { s ->
        val lines = s.lines.toMutableList()
        val last = lines.lastOrNull()
        if (last != null && !last.final) {
            lines[lines.lastIndex] = last.copy(text = last.text + delta)
        } else {
            lines += TranscriptLine(delta, final = false)
        }
        s.copy(status = ScreeningStatus.Live, lines = lines)
    }

    private fun finaliseLine(full: String) = _state.update { s ->
        val lines = s.lines.toMutableList()
        val last = lines.lastOrNull()
        if (last != null && !last.final) {
            lines[lines.lastIndex] = TranscriptLine(full.ifBlank { last.text }, final = true)
        } else if (full.isNotBlank()) {
            lines += TranscriptLine(full, final = true)
        }
        s.copy(lines = lines)
    }

    /** Strip everything but digits so +44 7700… matches 07700… */
    private fun normalise(n: String) = n.filter(Char::isDigit).takeLast(10)
}
