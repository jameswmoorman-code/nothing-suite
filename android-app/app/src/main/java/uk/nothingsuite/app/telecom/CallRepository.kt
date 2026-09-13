package uk.nothingsuite.app.telecom

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the call Telecom has handed us.
 * The InCallService writes; activities read. Kept as an object because
 * Telecom only ever binds one InCallService per process.
 */
object CallRepository {

    data class CallInfo(
        val call: Call,
        val number: String?,          // E.164 where possible, e.g. +447700900123
        val state: Int,               // Call.STATE_*
        val isIncoming: Boolean,
    )

    private val _current = MutableStateFlow<CallInfo?>(null)
    val current: StateFlow<CallInfo?> = _current.asStateFlow()

    /** Number of the caller we most recently chose to screen — used to filter transcript frames. */
    private val _screeningNumber = MutableStateFlow<String?>(null)
    val screeningNumber: StateFlow<String?> = _screeningNumber.asStateFlow()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _current.value = _current.value?.takeIf { it.call == call }?.copy(state = state)
            if (state == Call.STATE_DISCONNECTED) _current.value = null
        }
    }

    fun onCallAdded(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart
        val incoming = call.details?.callDirection == Call.Details.DIRECTION_INCOMING ||
            call.state == Call.STATE_RINGING
        _current.value = CallInfo(call, number, call.state, incoming)
        call.registerCallback(callback)
    }

    fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        if (_current.value?.call == call) _current.value = null
    }

    fun markScreening(number: String?) {
        _screeningNumber.value = number
    }

    fun clearScreening() {
        _screeningNumber.value = null
    }
}
