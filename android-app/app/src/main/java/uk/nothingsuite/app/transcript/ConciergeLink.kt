package uk.nothingsuite.app.transcript

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import uk.nothingsuite.app.settings.SecureSettings
import java.util.concurrent.TimeUnit

/** One JSON frame from backend-server/src/app/appSocket.js */
@kotlinx.serialization.Serializable
data class TranscriptFrame(
    val type: String,                       // hello | call_started | assistant | delta | final | call_ended | error | link_error (local)
    val callSid: String? = null,
    val from: String? = null,
    val text: String? = null,
    val ts: Long = 0,
    // scam-shield "alert" frames
    val level: String? = null,              // none | caution | scam
    val score: Int = 0,
    val reasons: List<String> = emptyList(),
)

/**
 * The one connection between this phone and the concierge server.
 *
 * Kept open by [ConciergeService] in the background so a call can pop the
 * live screen even when the app is closed; the live screen and anything
 * else just collect [frames] and call [send]. Reconnects with back-off.
 */
object ConciergeLink {
    private const val TAG = "Concierge"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _frames = MutableSharedFlow<TranscriptFrame>(replay = 0, extraBufferCapacity = 64)
    /** Every frame from the server, live. */
    val frames: SharedFlow<TranscriptFrame> = _frames.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** CallSid and caller of the call the concierge is on right now, if any. */
    @Volatile var activeCallSid: String? = null; private set
    @Volatile var activeCaller: String? = null; private set

    /**
     * Everything said on the current call so far (assistant / delta / final frames),
     * so a live screen that opens late — after the greeting was spoken — can catch up.
     */
    private val historyLock = Any()
    private val _history = ArrayList<TranscriptFrame>()
    val history: List<TranscriptFrame> get() = synchronized(historyLock) { _history.toList() }

    private var job: Job? = null
    private var socket: WebSocket? = null

    /** Idempotent: safe to call from the service, the app screen, settings. */
    @Synchronized
    fun start(settings: SecureSettings) {
        if (job?.isActive == true) return
        if (!settings.isConfigured) return
        job = scope.launch { runLoop(settings) }
    }

    @Synchronized
    fun stop() { job?.cancel(); job = null; socket?.close(1000, "stopped"); socket = null; _connected.value = false }

    /** Restart after the backend URL or secret changed. */
    fun restart(settings: SecureSettings) { stop(); start(settings) }

    /** Tell the concierge what to do on the live call. */
    fun send(action: String, text: String? = null, to: String? = null, callSid: String? = activeCallSid): Boolean {
        val sid = callSid ?: return false
        val payload = buildJsonObject {
            put("type", "action"); put("callSid", sid); put("action", action)
            if (text != null) put("text", text)
            if (to != null) put("to", to)
        }
        return socket?.send(json.encodeToString(payload)) ?: false
    }

    private suspend fun runLoop(settings: SecureSettings) {
        var attempt = 0
        while (true) {
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
            val url = "${settings.backendWsUrl}?token=${settings.sharedSecret}"
            socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { _connected.value = true; attempt = 0 }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val frame = runCatching { json.decodeFromString<TranscriptFrame>(text) }.getOrNull() ?: return
                    when (frame.type) {
                        "call_started" -> {
                            activeCallSid = frame.callSid; activeCaller = frame.from
                            synchronized(historyLock) { _history.clear(); _history += frame }
                        }
                        "assistant", "delta", "final", "alert" -> synchronized(historyLock) { if (_history.size < 2000) _history += frame }
                        "call_ended" -> if (frame.callSid == activeCallSid) {
                            activeCallSid = null; activeCaller = null
                            synchronized(historyLock) { _history += frame }
                        }
                    }
                    _frames.tryEmit(frame)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "socket failed: ${t.message}")
                    _connected.value = false
                    _frames.tryEmit(TranscriptFrame(type = "link_error", text = t.message ?: "connection failed"))
                    closed.complete(Unit)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { _connected.value = false; closed.complete(Unit) }
            })
            closed.await()
            attempt++
            delay(minOf(1000L * attempt, 15_000L))
        }
    }
}
