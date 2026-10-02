package uk.nothingsuite.app.settings

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
import uk.nothingsuite.app.transcript.VoiceOption

data class PrefsState(
    val voice: String? = null,
    val greeting: String? = null,
    val voices: List<VoiceOption> = emptyList(),
    val message: String? = null,
) { val loaded get() = voice != null }

/** The concierge's voice and greeting live on the server; this mirrors them and sends changes. */
object ConciergePrefs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PrefsState())
    val state: StateFlow<PrefsState> = _state.asStateFlow()
    private var started = false

    fun init() {
        refresh()
        if (started) return
        started = true
        scope.launch {
            ConciergeLink.frames.collect { f ->
                when (f.type) {
                    "prefs" -> _state.update { it.copy(voice = f.voice ?: it.voice, greeting = f.greeting ?: it.greeting, voices = f.voices.ifEmpty { it.voices }, message = null) }
                    "error" -> if (f.callSid == null && (f.text?.contains("voice", true) == true || f.text?.contains("greeting", true) == true || f.text?.contains("MY NUMBER") == true)) _state.update { it.copy(message = f.text) }
                }
            }
        }
    }

    /** Ask the server for the current voice/greeting and the list (the connect-time copy is easy to miss). */
    fun refresh() {
        scope.launch {
            var tries = 0
            while (!_state.value.loaded && tries < 10) {
                ConciergeLink.sendRaw { put("type", "prefs_get") }
                kotlinx.coroutines.delay(1500); tries++
            }
            if (!_state.value.loaded) _state.update { it.copy(message = "No reply from the server. Is it running the latest code? (Ctrl+C, then npm start.)") }
        }
    }

    fun setVoice(id: String) {
        _state.update { it.copy(voice = id) }
        if (!ConciergeLink.sendRaw { put("type", "prefs"); put("voice", id) }) _state.update { it.copy(message = "Not connected to the concierge.") }
    }
    fun setGreeting(text: String) {
        if (!ConciergeLink.sendRaw { put("type", "prefs"); put("greeting", text) }) _state.update { it.copy(message = "Not connected to the concierge.") }
        else _state.update { it.copy(message = "Greeting saved.") }
    }
    /** voice = an id to hear that one, "all" for the tour, null for the current voice. Rings MY NUMBER. */
    fun preview(voice: String? = null) {
        val ok = ConciergeLink.sendRaw { put("type", "prefs_preview"); if (voice != null) put("voice", voice) }
        _state.update { it.copy(message = if (ok) (if (voice == "all") "Ringing your phone — all ten voices, about two minutes." else "Ringing your phone with a sample…") else "Not connected to the concierge.") }
    }
}
