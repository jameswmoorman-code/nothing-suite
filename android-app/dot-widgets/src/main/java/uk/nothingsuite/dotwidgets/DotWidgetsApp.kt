package uk.nothingsuite.dotwidgets

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import uk.nothingsuite.billing.Catalogue
import uk.nothingsuite.billing.LicenseManager

class DotWidgetsApp : Application() {

    lateinit var license: LicenseManager private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // One product, one price. Create it in Play Console with exactly this ID.
        license = LicenseManager(this, Catalogue.single(PRODUCT_ALL, "£2.99"))
    }

    /** Per-widget settings (countdown date, label text). Keyed by appWidgetId. */
    val prefs get() = getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun putWidgetString(id: Int, key: String, value: String) = prefs.edit { putString("$id:$key", value) }
    fun getWidgetString(id: Int, key: String): String? = prefs.getString("$id:$key", null)
    fun putWidgetLong(id: Int, key: String, value: Long) = prefs.edit { putLong("$id:$key", value) }
    fun getWidgetLong(id: Int, key: String): Long = prefs.getLong("$id:$key", 0L)
    fun removeWidgetKey(id: Int, key: String) = prefs.edit { remove("$id:$key") }
    /** App-wide look, chosen in the gallery: rounded card, circle, or no background. */
    enum class Style(val background: Int, val label: String) {
        CARD(R.drawable.bg_widget, "CARD"),
        CIRCLE(R.drawable.bg_widget_circle, "CIRCLE"),
        NONE(R.drawable.bg_widget_none, "NONE"),
    }
    var style: Style
        get() = runCatching { Style.valueOf(prefs.getString("style", null) ?: "") }.getOrDefault(Style.CARD)
        set(value) = prefs.edit { putString("style", value.name) }

    /** Per-widget override ("shape" pref); null/blank means "use the app style". */
    fun styleFor(id: Int): Style =
        runCatching { Style.valueOf(getWidgetString(id, KEY_SHAPE) ?: "") }.getOrNull() ?: style

    fun clearWidget(id: Int) = prefs.edit { prefs.all.keys.filter { it.startsWith("$id:") }.forEach { remove(it) } }

    companion object {
        const val PRODUCT_ALL = "dot_widgets_all"
        const val KEY_SHAPE = "shape"
        lateinit var instance: DotWidgetsApp private set
    }
}
