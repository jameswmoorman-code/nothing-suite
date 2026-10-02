package uk.nothingsuite.app.transcript

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uk.nothingsuite.app.NothingSuiteApp
import uk.nothingsuite.app.R

/**
 * Keeps the concierge connection alive in the background and pops the live
 * screen the moment a call reaches your Twilio number — like a ringing call,
 * even if the app is closed and the phone is locked.
 *
 * It is a foreground service (a quiet "Concierge on duty" notification) because
 * Android kills background sockets within minutes otherwise.
 */
class ConciergeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = NothingSuiteApp.instance
        if (!app.settings.isConfigured) { stopSelf(); return START_NOT_STICKY }

        val n = Notification.Builder(this, NothingSuiteApp.CHANNEL_SCREENING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Concierge on duty — calls to your Twilio number will pop up here")
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, uk.nothingsuite.app.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, n)

        ConciergeLink.start(app.settings)
        uk.nothingsuite.app.hold.HoldForMe.init()
        uk.nothingsuite.app.desk.DeskMode.start(this)
        if (watcher == null) watcher = scope.launch {
            ConciergeLink.frames.collect { frame ->
                uk.nothingsuite.app.inbox.CallInbox.record(frame)
                if (NothingSuiteApp.instance.settings.glyphCallerId) runCatching {
                    if (frame.type == "call_started") uk.nothingsuite.app.glyph.GlyphCallerId.attachAppSink(this@ConciergeService)
                    uk.nothingsuite.app.glyph.GlyphCallerId.onFrame(this@ConciergeService, frame)
                }.onFailure { android.util.Log.e("Concierge", "glyph caller id failed", it) }
                when (frame.type) {
                    "hold" -> when (frame.state) {
                        "human" -> popHoldScreen(frame)
                        "joined", "ended" -> getSystemService(android.app.NotificationManager::class.java).cancel(HOLD_NOTIFICATION_ID)
                    }
                    "call_started" -> popLiveScreen(frame)
                    "call_ended" -> { CallBanner.hide(this@ConciergeService); notifyInbox(frame) }
                }
            }
        }
        return START_STICKY
    }

    private fun popLiveScreen(frame: TranscriptFrame) {
        val intent = Intent(this, LiveTranscriptActivity::class.java)
            .putExtra(LiveTranscriptActivity.EXTRA_CALLER, frame.from)
            .putExtra(LiveTranscriptActivity.EXTRA_CALL_SID, frame.callSid)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // A full-screen intent is how incoming calls wake the screen; needs USE_FULL_SCREEN_INTENT (we have it as a dialer).
        val n = Notification.Builder(this, NothingSuiteApp.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Concierge is taking a call")
            .setContentText(frame.from ?: "Unknown caller")
            .setCategory(Notification.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
        getSystemService(android.app.NotificationManager::class.java).notify(CALL_NOTIFICATION_ID, n)
        // The full-screen intent only fires when the phone is locked. While it is unlocked and in
        // use Android shows a heads-up notification instead — unless we may "display over other
        // apps", in which case we can open the screen directly.
        if (canPopUp(this)) {
            runCatching { startActivity(intent) }.onFailure { android.util.Log.w("Concierge", "startActivity blocked: ${it.message}") }
            // Belt and braces: newer Android ignores the above from a service, so also show a
            // floating banner; tapping it opens the live screen.
            CallBanner.show(this, frame.from, frame.callSid)
        }
    }

    /** Hold For Me: a person answered — shout about it (your phone is about to ring). */
    private fun popHoldScreen(frame: TranscriptFrame) {
        val intent = Intent(this, uk.nothingsuite.app.hold.HoldForMeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, NothingSuiteApp.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Someone answered at ${frame.to ?: "the company"}")
            .setContentText("The concierge has asked them to hold — pick up when your phone rings.")
            .setCategory(Notification.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
        getSystemService(android.app.NotificationManager::class.java).notify(HOLD_NOTIFICATION_ID, n)
        if (canPopUp(this)) runCatching { startActivity(intent) }
    }

    /** The call is over: replace the ringing-style notification with a quiet "in your inbox" one. */
    private fun notifyInbox(frame: TranscriptFrame) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(CALL_NOTIFICATION_ID)
        val call = uk.nothingsuite.app.inbox.CallInbox.calls.value.firstOrNull { it.callSid == frame.callSid } ?: return
        if (call.read) return
        val open = Intent(this, uk.nothingsuite.app.inbox.InboxActivity::class.java)
            .putExtra(uk.nothingsuite.app.inbox.InboxActivity.EXTRA_CALL_SID, call.callSid)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, NothingSuiteApp.CHANNEL_SCREENING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle((if (call.risk == Risk.SCAM) "\u26A0 Scam likely \u00B7 " else if (call.risk == Risk.CAUTION) "\u26A0 Caution \u00B7 " else "Screened call from ") + call.from)
            .setContentText(call.gist)
            .setStyle(Notification.BigTextStyle().bigText(call.gist + (call.outcome?.let { "\n${it.replaceFirstChar(Char::uppercase)}" } ?: "")))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(INBOX_NOTIFICATION_BASE + (call.callSid.hashCode() and 0xFFFF), n)
    }

    override fun onDestroy() { scope.cancel(); uk.nothingsuite.app.desk.DeskMode.stop(); runCatching { uk.nothingsuite.app.glyph.GlyphCallerId.releaseAppSink() }; super.onDestroy() }

    companion object {
        private const val NOTIFICATION_ID = 0xC0CE
        const val CALL_NOTIFICATION_ID = 0xC0CF
        const val HOLD_NOTIFICATION_ID = 0xC0D0
        private const val INBOX_NOTIFICATION_BASE = 0x1B000

        /** May we open the live screen from the background while the phone is in use? */
        fun canPopUp(context: Context): Boolean = android.provider.Settings.canDrawOverlays(context)

        /** Can the lock-screen pop-up (full-screen intent) fire? Android 14+ can switch it off per app. */
        fun canPopUpWhenLocked(context: Context): Boolean =
            Build.VERSION.SDK_INT < 34 || context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

        /** Settings page where the user grants "display over other apps". */
        fun popUpSettingsIntent(context: Context): Intent =
            Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))

        fun fullScreenSettingsIntent(context: Context): Intent =
            Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:${context.packageName}"))
        /** Start (or re-arm) the background listener if the app is configured. */
        fun ensureRunning(context: Context) {
            if (!NothingSuiteApp.instance.settings.isConfigured) return
            val i = Intent(context, ConciergeService::class.java)
            runCatching { context.startForegroundService(i) }
        }
    }
}
