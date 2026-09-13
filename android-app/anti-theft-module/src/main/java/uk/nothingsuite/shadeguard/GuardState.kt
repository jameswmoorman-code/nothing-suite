package uk.nothingsuite.shadeguard

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tiny shared state between the accessibility service and the biometric
 * gate. After a successful fingerprint we open a short grace window during
 * which the shade is allowed; otherwise every open-while-locked is closed.
 */
object GuardState {
    private const val PREFS = "shade_guard"
    private const val KEY_ENABLED = "enabled"
    private const val GRACE_MS = 45_000L

    @Volatile var graceUntil: Long = 0L
    @Volatile var promptShowing: Boolean = false

    private val _lastEvent = MutableStateFlow("")
    val lastEvent: StateFlow<String> = _lastEvent

    fun isEnabled(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, v) }

    fun inGrace() = System.currentTimeMillis() < graceUntil

    fun grantGrace() {
        graceUntil = System.currentTimeMillis() + GRACE_MS
    }

    fun log(msg: String) {
        _lastEvent.value = msg
    }
}
