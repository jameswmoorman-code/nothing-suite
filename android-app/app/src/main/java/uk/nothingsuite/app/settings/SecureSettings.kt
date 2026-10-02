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
        set(v) = prefs.edit { putString(KEY_BACKEND, normaliseWs(v)) }

    var sharedSecret: String
        get() = prefs.getString(KEY_SECRET, "") ?: ""
        set(v) = prefs.edit { putString(KEY_SECRET, v.trim()) }

    /** Your Twilio number in E.164, used to pre-fill the *67* forwarding code. */
    var twilioNumber: String
        get() = prefs.getString(KEY_TWILIO, "") ?: ""
        set(v) = prefs.edit { putString(KEY_TWILIO, v.trim()) }

    /** Your own mobile (E.164). "Take the call" tells the concierge to ring this. */
    var myNumber: String
        get() = prefs.getString(KEY_MY_NUMBER, "") ?: ""
        set(v) = prefs.edit { putString(KEY_MY_NUMBER, v.filter { it.isDigit() || it == '+' }) }

    var glyphTrackerEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLYPH, true)
        set(v) = prefs.edit { putBoolean(KEY_GLYPH, v) }

    /**
     * How "Screen" hands the call over. REJECT (default): decline the call so the network's
     * "forward when busy" rule diverts it at once — the assistant answers in about a second.
     * SILENCE: keep it ringing and rely on "forward if no answer" (5 s) — for carriers that
     * don't forward on busy.
     */
    var rejectToScreen: Boolean
        get() = prefs.getBoolean(KEY_REJECT, true)
        set(v) = prefs.edit { putBoolean(KEY_REJECT, v) }

    /** Screen calls from numbers not in Contacts without asking (like Pixel's automatic screening). */
    var autoScreenUnknown: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UNKNOWN, false)
        set(v) = prefs.edit { putBoolean(KEY_AUTO_UNKNOWN, v) }

    /** Show caller + live transcript on the Glyph Matrix during a screened call (Phone (3)). */
    var glyphCallerId: Boolean
        get() = prefs.getBoolean("glyph_caller_id", true)
        set(v) = prefs.edit { putBoolean("glyph_caller_id", v) }

    /** Face down on the desk = every call goes to the concierge without ringing. */
    var deskMode: Boolean
        get() = prefs.getBoolean("desk_mode", true)
        set(v) = prefs.edit { putBoolean("desk_mode", v) }

    val isConfigured: Boolean get() = backendWsUrl.startsWith("wss://") && sharedSecret.isNotBlank()

    /** Phones auto-capitalise and add spaces; accept https:// too and turn it into wss://. */
    private fun normaliseWs(raw: String): String {
        var v = raw.trim().replace(" ", "")
        v = v.replaceFirst(Regex("^(?i)https://"), "wss://").replaceFirst(Regex("^(?i)wss://"), "wss://")
            .replaceFirst(Regex("^(?i)http://"), "wss://").replaceFirst(Regex("^(?i)ws://"), "wss://")   // ngrok only speaks TLS
        if (v.isNotBlank() && !v.contains("://")) v = "wss://$v"
        if (v.startsWith("wss://") && !v.endsWith("/app")) v = v.trimEnd('/') + "/app"
        return v
    }

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
        const val KEY_MY_NUMBER = "my_number"
        const val KEY_GLYPH = "glyph_tracker"
        const val KEY_REJECT = "reject_to_screen"
        const val KEY_AUTO_UNKNOWN = "auto_screen_unknown"
    }
}
