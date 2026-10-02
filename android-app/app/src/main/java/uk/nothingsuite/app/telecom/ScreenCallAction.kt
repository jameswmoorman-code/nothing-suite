package uk.nothingsuite.app.telecom

import android.content.Context
import android.content.Intent
import android.telecom.Call
import uk.nothingsuite.app.NothingSuiteApp
import uk.nothingsuite.app.transcript.LiveTranscriptActivity

/**
 * The "Screen Call" tap, end to end.
 *
 *  1. Remember the caller's number so the transcript screen can filter frames.
 *  2. Hand the call to the network. Default: REJECT it, which the network reports
 *     as "busy" and diverts at once under the *67* rule — the assistant picks up in
 *     about a second. Fallback (Settings → "Keep ringing instead"): SILENCE it and
 *     let the *61* no-answer rule (5 s minimum) divert it, for carriers that don't
 *     forward on busy. Both rules are set from the Setup screen, so either works.
 *  3. Open the live transcript immediately so the socket is warm before the
 *     forwarded leg reaches the screener ~5 s later.
 *
 * The call object stays RINGING until the network diverts it, at which point
 * Telecom disconnects it on our side and CallRepository clears it.
 * See docs/carrier-forwarding.md.
 */
object ScreenCallAction {

    fun execute(context: Context, info: CallRepository.CallInfo) {
        CallRepository.markScreening(info.number)

        if (NothingSuiteApp.instance.settings.rejectToScreen) reject(info.call) else silence(context)

        val intent = Intent(context, LiveTranscriptActivity::class.java)
            .putExtra(LiveTranscriptActivity.EXTRA_CALLER, info.number)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }

    /** Decline → network sees busy → *67* forwards straight to the screener. */
    private fun reject(call: Call) {
        runCatching { call.reject(Call.REJECT_REASON_DECLINED) }
    }

    /** Stops the ringtone/vibration but keeps the call alive for the network's no-answer timer. */
    private fun silence(context: Context) {
        // The ringer belongs to Telecom, not the Call. As the default dialer we may
        // silence it; the caller still hears ringing on their end.
        runCatching { context.getSystemService(android.telecom.TelecomManager::class.java).silenceRinger() }
    }
}
