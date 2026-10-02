package uk.nothingsuite.app.inbox

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.nothingsuite.app.transcript.TranscriptBuilder
import uk.nothingsuite.app.transcript.TranscriptFrame
import uk.nothingsuite.app.transcript.TranscriptLine
import uk.nothingsuite.app.transcript.Risk
import java.io.File

/** One call the concierge handled, kept on the phone. */
@Serializable
data class ScreenedCall(
    val callSid: String,
    val from: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** How it finished, in the server's words: "ended by you", "connected to you", "caller hung up"… */
    val outcome: String? = null,
    val lines: List<TranscriptLine> = emptyList(),
    val read: Boolean = false,
    val risk: Risk = Risk.NONE,
    val riskReasons: List<String> = emptyList(),
) {
    val gist: String get() = TranscriptBuilder.gist(lines)
    val live: Boolean get() = endedAt == null
}

/**
 * The concierge's message book. Fed every frame by ConciergeService (so calls are
 * kept even when the live screen never opened) and saved as one JSON file in the
 * app's private storage. Nothing leaves the phone.
 */
object CallInbox {
    private const val TAG = "CallInbox"
    private const val FILE = "inbox.json"
    private const val KEEP = 200
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var file: File
    private val lock = Any()
    private val _calls = MutableStateFlow<List<ScreenedCall>>(emptyList())
    /** Newest first. */
    val calls: StateFlow<List<ScreenedCall>> = _calls.asStateFlow()
    val unread get() = calls.map { list -> list.count { !it.read && !it.live } }

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, FILE)
        io.launch {
            val loaded = runCatching { if (file.exists()) json.decodeFromString<List<ScreenedCall>>(file.readText()) else emptyList() }
                .onFailure { Log.w(TAG, "could not read inbox: ${it.message}") }
                .getOrDefault(emptyList())
            // Anything still "live" from before a restart is over now.
            synchronized(lock) { _calls.value = loaded.map { if (it.live) it.copy(endedAt = it.startedAt, outcome = it.outcome ?: "ended") else it } }
        }
    }

    /** Every frame from the server goes through here. */
    fun record(frame: TranscriptFrame) {
        val sid = frame.callSid ?: return
        synchronized(lock) {
            val list = _calls.value.toMutableList()
            val i = list.indexOfFirst { it.callSid == sid }
            when (frame.type) {
                "call_started" -> if (i < 0) list.add(0, ScreenedCall(callSid = sid, from = frame.from ?: "unknown", startedAt = frame.ts.takeIf { it > 0 } ?: System.currentTimeMillis()))
                "assistant", "delta", "final" -> {
                    val idx = if (i >= 0) i else { list.add(0, ScreenedCall(sid, frame.from ?: "unknown", System.currentTimeMillis())); 0 }
                    list[idx] = list[idx].copy(lines = TranscriptBuilder.apply(list[idx].lines, frame))
                }
                "alert" -> if (i >= 0) list[i] = list[i].copy(risk = maxOf(list[i].risk, Risk.parse(frame.level)), riskReasons = frame.reasons)
                "call_ended" -> if (i >= 0) list[i] = list[i].copy(endedAt = System.currentTimeMillis(), outcome = frame.text ?: "ended",
                    lines = list[i].lines.map { if (it.final) it else it.copy(final = true) })
                else -> return
            }
            _calls.value = list.take(KEEP)
        }
        if (frame.type != "delta") persist()   // deltas arrive many times a second; finals are enough to save
    }

    fun markRead(callSid: String) = update { if (it.callSid == callSid) it.copy(read = true) else it }
    fun markAllRead() = update { it.copy(read = true) }
    fun delete(callSid: String) { synchronized(lock) { _calls.value = _calls.value.filterNot { it.callSid == callSid } }; persist() }
    fun clear() { synchronized(lock) { _calls.value = emptyList() }; persist() }

    private fun update(f: (ScreenedCall) -> ScreenedCall) { synchronized(lock) { _calls.value = _calls.value.map(f) }; persist() }

    private fun persist() {
        if (!::file.isInitialized) return
        val snapshot = _calls.value
        io.launch {
            runCatching { file.writeText(json.encodeToString(snapshot)) }
                .onFailure { Log.w(TAG, "could not save inbox: ${it.message}") }
        }
    }
}
