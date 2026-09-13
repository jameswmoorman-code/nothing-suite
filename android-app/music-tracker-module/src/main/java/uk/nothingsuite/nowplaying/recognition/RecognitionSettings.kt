package uk.nothingsuite.nowplaying.recognition

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * BYOK for the recognition service. ACRCloud is the default because its
 * identify endpoint accepts a raw audio sample over plain HTTPS with an HMAC
 * signature — no proprietary SDK required. Keys are encrypted at rest.
 */
class RecognitionSettings(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "now_playing_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** e.g. identify-eu-west-1.acrcloud.com — shown in your ACRCloud project. */
    var acrHost: String
        get() = prefs.getString("acr_host", "identify-eu-west-1.acrcloud.com") ?: ""
        set(v) = prefs.edit { putString("acr_host", v.trim()) }

    var acrAccessKey: String
        get() = prefs.getString("acr_key", "") ?: ""
        set(v) = prefs.edit { putString("acr_key", v.trim()) }

    var acrAccessSecret: String
        get() = prefs.getString("acr_secret", "") ?: ""
        set(v) = prefs.edit { putString("acr_secret", v.trim()) }

    /** Minutes between samples. 4–5 as specified; users can widen it to save quota and battery. */
    var intervalMinutes: Int
        get() = prefs.getInt("interval_min", 5)
        set(v) = prefs.edit { putInt("interval_min", v.coerceIn(2, 30)) }

    /** Skip the network call when the room is quieter than this (RMS on int16 scale). */
    var loudnessGate: Int
        get() = prefs.getInt("loudness_gate", 400)
        set(v) = prefs.edit { putInt("loudness_gate", v.coerceIn(50, 5000)) }

    var listeningEnabled: Boolean
        get() = prefs.getBoolean("listening", false)
        set(v) = prefs.edit { putBoolean("listening", v) }

    val isConfigured: Boolean get() = acrHost.isNotBlank() && acrAccessKey.isNotBlank() && acrAccessSecret.isNotBlank()
}
