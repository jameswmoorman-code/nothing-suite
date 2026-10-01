package uk.nothingsuite.glyphtracker

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.edit
import uk.nothingsuite.billing.Catalogue
import uk.nothingsuite.billing.LicenseManager
import uk.nothingsuite.glyphtracker.glyph.GlyphOutput

class GlyphTrackerApp : Application() {

    lateinit var license: LicenseManager private set
    lateinit var glyph: GlyphOutput private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        license = LicenseManager(this, Catalogue.single(PRODUCT_ALL, "£1.99"))
        glyph = GlyphOutput.forThisPhone(this)
    }

    val prefs get() = getSharedPreferences("tracker", Context.MODE_PRIVATE)

    /** Master switch for the listener (notification access is granted separately, by the system). */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) = prefs.edit { putBoolean("enabled", v) }

    /** "plain", "sweep" or "milestone" — see [uk.nothingsuite.glyphtracker.glyph.Animations]. */
    var animation: String
        get() = prefs.getString("animation", "sweep") ?: "sweep"
        set(v) = prefs.edit { putString("animation", v) }

    /** Everything unlocked: bought, or a debug build on the developer's own phone. */
    val unlocked: Boolean
        get() = license.tier.value.isPremium || (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        const val PRODUCT_ALL = "glyph_tracker_all"
        lateinit var instance: GlyphTrackerApp private set
    }
}
