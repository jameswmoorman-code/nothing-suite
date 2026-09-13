package uk.nothingsuite.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * BYOK storage. The phone never holds Twilio or OpenAI keys — only how to
 * reach *your* backend. Everything is AES-256 encrypted at rest via the
 * Android Keystore. No backup (allowBackup=false in the manifest).
 */
class SecureSettings(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nothing_suite_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** e.g. wss://example.ngrok-free.app/app */
    var backendWsUrl: String
        get() = prefs.getString(KEY_BACKEND, "") ?: ""
        set(v) = prefs.edit { putString(KEY_BACKEND, v.trim()) }

    var sharedSecret: String
        get() = prefs.getString(KEY_SECRET, "") ?: ""
        set(v) = prefs.edit { putString(KEY_SECRET, v.trim()) }

    /** Your Twilio number in E.164, used to pre-fill the *67* forwarding code. */
    var twilioNumber: String
        get() = prefs.getString(KEY_TWILIO, "") ?: ""
        set(v) = prefs.edit { putString(KEY_TWILIO, v.trim()) }

    var glyphTrackerEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLYPH, true)
        set(v) = prefs.edit { putBoolean(KEY_GLYPH, v) }

    val isConfigured: Boolean get() = backendWsUrl.startsWith("wss://") && sharedSecret.isNotBlank()

    /**
     * GSM "forward when no answer" registration, 5-second timer (the minimum).
     * This is the rule screening relies on: the app silences the call, the
     * network diverts it after 5 s. Ready for ACTION_DIAL.
     */
    fun forwardNoAnswerCode(): String = "*61*${twilioNumber}**5#"

    /** "Forward when busy" — belt and braces for calls the user declines outright. */
    fun forwardWhenBusyCode(): String = "*67*${twilioNumber}#"

    /** Cancels both rules. */
    fun cancelForwardingCodes(): List<String> = listOf("#61#", "#67#")

    private companion object {
        const val KEY_BACKEND = "backend_ws_url"
        const val KEY_SECRET = "shared_secret"
        const val KEY_TWILIO = "twilio_number"
        const val KEY_GLYPH = "glyph_tracker"
    }
}
