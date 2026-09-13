package uk.nothingsuite.nowplaying.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nothingsuite.nowplaying.MainActivity
import uk.nothingsuite.nowplaying.NowPlayingApp
import uk.nothingsuite.nowplaying.R
import uk.nothingsuite.nowplaying.recognition.AcrCloudClient
import uk.nothingsuite.nowplaying.widget.NowPlayingWidget

/**
 * The sampling loop.
 *
 *   every N minutes:
 *     skip if a call is in progress or the user is playing audio through headphones
 *     record 5 s  →  quieter than the gate? skip (saves quota + battery)
 *     otherwise   →  Recognizer.identify(wav)  →  update widget state
 *
 * Foreground service with type "microphone": Android shows a persistent
 * notification and the green mic indicator whenever we record. That is the
 * honest, visible way to do this and the only one the OS allows.
 */
class AmbientListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app get() = NowPlayingApp.instance

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            app.settings.listeningEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        app.settings.listeningEnabled = true
        loop()
        return START_STICKY
    }

    private fun loop() {
        scope.launch {
            val recognizer = AcrCloudClient(app.settings)
            val audioManager = getSystemService(AudioManager::class.java)

            while (true) {
                val settings = app.settings
                if (!settings.isConfigured) {
                    app.state.setStatus("ADD YOUR ACRCLOUD KEY")
                    delay(60_000); continue
                }

                val busy = audioManager.mode == AudioManager.MODE_IN_CALL ||
                    audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                    audioManager.isBluetoothA2dpOn || audioManager.isWiredHeadsetOn
                if (!busy) sampleOnce(recognizer, settings.loudnessGate)

                delay(settings.intervalMinutes * 60_000L)
            }
        }
    }

    private suspend fun sampleOnce(recognizer: AcrCloudClient, gate: Int) {
        app.state.setStatus("LISTENING")
        NowPlayingWidget.updateAll(this)

        val clip = withContext(Dispatchers.IO) { AudioSampler.record(5) }
        if (clip == null) {
            app.state.setStatus("MIC UNAVAILABLE"); NowPlayingWidget.updateAll(this); return
        }
        if (clip.rms < gate) {
            Log.d(TAG, "quiet room (rms=${clip.rms.toInt()} < $gate) — skipping")
            app.state.setStatus("QUIET"); NowPlayingWidget.updateAll(this); return
        }

        val track = withContext(Dispatchers.IO) { runCatching { recognizer.identify(clip.toWav()) }.getOrNull() }
        if (track != null) {
            app.state.setTrack(track)
        } else {
            app.state.setStatus("NO MATCH")
        }
        NowPlayingWidget.updateAll(this)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AmbientListenerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, NowPlayingApp.CHANNEL_LISTENING)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "PAUSE", stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AmbientListener"
        private const val NOTIFICATION_ID = 0x4E50
        const val ACTION_STOP = "uk.nothingsuite.nowplaying.STOP"

        /** Must be called while the app is in the foreground on Android 14+. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, AmbientListenerService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AmbientListenerService::class.java).setAction(ACTION_STOP))
        }
    }
}
