package uk.nothingsuite.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import uk.nothingsuite.app.glyph.GlyphController
import uk.nothingsuite.billing.Catalogue
import uk.nothingsuite.billing.LicenseManager
import uk.nothingsuite.billing.Product
import uk.nothingsuite.billing.Sku
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
        uk.nothingsuite.app.inbox.CallInbox.init(this)
        instance = this
        settings = SecureSettings(this)
        license = LicenseManager(
            this,
            Catalogue(
                listOf(
                    Product(Sku.PLUS, "nothing_suite_plus", "£2.99"),
                    Product(Sku.PRO, "nothing_suite_pro", "£4.99"),
                )
            ),
        )
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
        // Audible nudge when a call reaches the concierge: chime + buzz (respects Do Not Disturb).
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALL_ALERT, "Concierge taking a call", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Chime and vibration when the concierge starts screening a call"
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250, 150, 400)
            }
        )
    }

    companion object {
        const val CHANNEL_CALLS = "calls"
        const val CHANNEL_SCREENING = "screening"
        const val CHANNEL_CALL_ALERT = "call_alert"
        lateinit var instance: NothingSuiteApp private set
    }
}
