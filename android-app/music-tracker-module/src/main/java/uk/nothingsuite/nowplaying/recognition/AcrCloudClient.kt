package uk.nothingsuite.nowplaying.recognition

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class Track(val title: String, val artist: String, val album: String? = null)

/**
 * ACRCloud "identify" over HTTPS with signature v1.
 *
 *   string_to_sign = "POST\n/v1/identify\n<access_key>\naudio\n1\n<timestamp>"
 *   signature      = base64( HMAC-SHA1(secret, string_to_sign) )
 *
 * Only the 5-second clip and your own key leave the phone. No device IDs,
 * no location, no account. Swap this class for another provider (AudD,
 * Audible Magic…) by implementing [Recognizer].
 */
interface Recognizer {
    suspend fun identify(wav: ByteArray): Track?
}

class AcrCloudClient(private val settings: RecognitionSettings) : Recognizer {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun identify(wav: ByteArray): Track? {
        if (!settings.isConfigured) return null
        val ts = (System.currentTimeMillis() / 1000).toString()
        val toSign = "POST\n/v1/identify\n${settings.acrAccessKey}\naudio\n1\n$ts"
        val sig = hmacSha1(settings.acrAccessSecret, toSign)

        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("access_key", settings.acrAccessKey)
            .addFormDataPart("data_type", "audio")
            .addFormDataPart("signature_version", "1")
            .addFormDataPart("signature", sig)
            .addFormDataPart("sample_bytes", wav.size.toString())
            .addFormDataPart("timestamp", ts)
            .addFormDataPart("sample", "sample.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()

        val req = Request.Builder().url("https://${settings.acrHost}/v1/identify").post(body).build()
        val text = http.newCall(req).execute().use { it.body?.string() } ?: return null
        return parse(text)
    }

    private fun parse(text: String): Track? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val status = root["status"]?.jsonObject
        if (status?.get("code")?.jsonPrimitive?.content != "0") return null   // 1001 = no result
        val music = root["metadata"]?.jsonObject?.get("music")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val title = music["title"]?.jsonPrimitive?.content ?: return null
        val artist = music["artists"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
        val album = music["album"]?.jsonObject?.get("name")?.jsonPrimitive?.content
        Track(title, artist, album)
    }.getOrNull()

    private fun hmacSha1(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA1"))
        return Base64.encodeToString(mac.doFinal(data.toByteArray()), Base64.NO_WRAP)
    }
}
