package uk.nothingsuite.nowplaying

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import uk.nothingsuite.nowplaying.recognition.RecognitionSettings
import uk.nothingsuite.nowplaying.widget.NowPlayingState

class NowPlayingApp : Application() {

    lateinit var settings: RecognitionSettings private set
    lateinit var state: NowPlayingState private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = RecognitionSettings(this)
        state = NowPlayingState(this)

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENING, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CHANNEL_LISTENING = "listening"
        lateinit var instance: NowPlayingApp private set
    }
}
