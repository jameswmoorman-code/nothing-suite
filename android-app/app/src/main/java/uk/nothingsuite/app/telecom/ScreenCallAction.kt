package uk.nothingsuite.app.telecom

import android.content.Context
import android.content.Intent
import android.telecom.Call
import uk.nothingsuite.app.transcript.LiveTranscriptActivity

/**
 * The "Screen Call" tap, end to end.
 *
 *  1. Remember the caller's number so the transcript screen can filter frames.
 *  2. SILENCE the call and let it ring out. We deliberately do NOT reject it:
 *     rejecting is reported to the network as "busy", and not every carrier
 *     forwards on busy. Letting it ring triggers "forward when no answer"
 *     (*61*, set to the 5-second minimum), which every UK network honours
 *     because it's the same mechanism that sends calls to voicemail.
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

        silence(info.call)

        val intent = Intent(context, LiveTranscriptActivity::class.java)
            .putExtra(LiveTranscriptActivity.EXTRA_CALLER, info.number)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }

    /** Stops the ringtone/vibration but keeps the call alive for the network's no-answer timer. */
    private fun silence(call: Call) {
        // Call.silence() exists from API 23. Telecom silences the ringer for
        // this call only; the caller still hears ringing on their end.
        runCatching { call.silence() }
    }
}
