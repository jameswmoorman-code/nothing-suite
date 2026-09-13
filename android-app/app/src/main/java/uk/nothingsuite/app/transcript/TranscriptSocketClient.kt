package uk.nothingsuite.app.transcript

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** One JSON frame from backend-server/src/app/appSocket.js */
@Serializable
data class TranscriptFrame(
    val type: String,                       // hello | call_started | delta | final | call_ended | error
    val callSid: String? = null,
    val from: String? = null,
    val text: String? = null,
    val ts: Long = 0,
)

/**
 * Thin OkHttp WebSocket wrapper exposed as a cold Flow. The URL and token
 * come from SecureSettings; nothing else about the user is transmitted.
 */
class TranscriptSocketClient(
    private val backendWsUrl: String,   // wss://host/app
    private val sharedSecret: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived
        .build()

    fun frames(): Flow<TranscriptFrame> = callbackFlow {
        val url = "$backendWsUrl?token=$sharedSecret"
        val request = Request.Builder().url(url).build()

        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { json.decodeFromString<TranscriptFrame>(text) }
                    .onSuccess { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(TranscriptFrame(type = "error", text = t.message ?: "connection failed"))
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })

        awaitClose { socket.close(1000, "activity closed") }
    }
}
