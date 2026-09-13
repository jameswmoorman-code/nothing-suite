package uk.nothingsuite.app.telecom

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import uk.nothingsuite.app.NothingSuiteApp

/**
 * Telecom binds this service while we hold ROLE_DIALER. Every incoming and
 * outgoing call arrives via [onCallAdded]; we never poll telephony state.
 *
 * Responsibilities are intentionally thin:
 *   1. push the Call into [CallRepository]
 *   2. show the full-screen incoming UI (via full-screen-intent notification,
 *      which is the only reliable way to launch an activity from the background
 *      on Android 10+ — a direct startActivity() is silently dropped)
 */
class ScreenerInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallRepository.onCallAdded(call)

        if (call.state == Call.STATE_RINGING) {
            showIncomingCallUi(call)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallRepository.onCallRemoved(call)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun showIncomingCallUi(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"

        val fullScreen = Intent(this, IncomingCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullScreenPi = PendingIntent.getActivity(
            this, 0, fullScreen, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, NothingSuiteApp.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(getString(uk.nothingsuite.app.R.string.incoming_call))
            .setContentText(number)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .build()

        // Foreground with phoneCall type keeps us alive while ringing.
        startForeground(NOTIFICATION_ID, notification)
        // Also try a direct launch — succeeds when the screen is already on / app is foreground.
        runCatching { startActivity(fullScreen) }
    }

    private companion object {
        const val NOTIFICATION_ID = 0xCA11
    }
}
