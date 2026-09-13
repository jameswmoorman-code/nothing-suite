package uk.nothingsuite.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import uk.nothingsuite.app.glyph.GlyphController
import uk.nothingsuite.app.license.LicenseManager
import uk.nothingsuite.app.settings.SecureSettings
import uk.nothingsuite.app.telecom.CallRepository

/**
 * Process-wide singletons. Deliberately no DI framework: the suite must stay
 * approachable for community contributors and buildable without code-gen.
 */
class NothingSuiteApp : Application() {

    lateinit var settings: SecureSettings private set
    lateinit var license: LicenseManager private set
    lateinit var glyph: GlyphController private set
    val calls: CallRepository = CallRepository

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SecureSettings(this)
        license = LicenseManager(this)
        glyph = GlyphController(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SCREENING, "Call screening", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CHANNEL_CALLS = "calls"
        const val CHANNEL_SCREENING = "screening"
        lateinit var instance: NothingSuiteApp private set
    }
}
