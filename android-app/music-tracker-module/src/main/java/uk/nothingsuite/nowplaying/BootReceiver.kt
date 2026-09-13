package uk.nothingsuite.nowplaying

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uk.nothingsuite.nowplaying.audio.AmbientListenerService

/**
 * Best-effort restart after reboot. Android 14+ may refuse to start a
 * microphone foreground service from a boot broadcast; when that happens
 * the user simply taps "Start listening" in the app once.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!NowPlayingApp.instance.settings.listeningEnabled) return
        runCatching { AmbientListenerService.start(context) }
    }
}
