package uk.nothingsuite.app.glyph

import android.app.Notification
import android.service.notification.StatusBarNotification

data class ProgressState(val percent: Int, val label: String, val source: String)

/**
 * Pure functions: notification in → progress out. Two strategies:
 *
 *  1. Native progress bars — any app that sets EXTRA_PROGRESS/EXTRA_PROGRESS_MAX
 *     (downloads, file transfers, Android Auto nav, some couriers).
 *  2. Phrase maps — apps that describe state in words. Each entry is
 *     `substring (case-insensitive) → percent`. First match wins, so order
 *     from most- to least-specific.
 *
 * Adding an app = adding a map entry. Community PRs welcome.
 */
object ProgressExtractors {

    private val phraseMaps: Map<String, List<Pair<String, Int>>> = mapOf(
        // Food delivery
        "com.deliveroo.orderapp" to listOf(
            "delivered" to 100, "arriving" to 95, "on the way" to 75, "on their way" to 75,
            "being prepared" to 40, "preparing" to 40, "accepted" to 20, "confirmed" to 15,
        ),
        "com.ubercab.eats" to listOf(
            "delivered" to 100, "arriving" to 95, "heading your way" to 75, "picked up" to 65,
            "preparing" to 40, "confirmed" to 15,
        ),
        "com.justeat.app" to listOf(
            "delivered" to 100, "nearly there" to 90, "on its way" to 70, "being prepared" to 40,
            "accepted" to 20,
        ),
        // Parcels
        "com.amazon.mShop.android.shopping" to listOf(
            "delivered" to 100, "stops away" to 90, "out for delivery" to 80, "arriving today" to 60,
            "shipped" to 40, "dispatched" to 40, "ordered" to 10,
        ),
        "uk.co.royalmail.consumer" to listOf(
            "delivered" to 100, "out for delivery" to 80, "at your local delivery office" to 60,
            "in transit" to 40, "we've got it" to 20,
        ),
        "com.dpd.uk" to listOf(
            "delivered" to 100, "stops away" to 90, "out for delivery" to 80, "at depot" to 50,
        ),
        "com.evri.app" to listOf(
            "delivered" to 100, "out for delivery" to 80, "at local depot" to 55, "on its way" to 35,
        ),
        // Ride hailing
        "com.ubercab" to listOf(
            "arrived" to 100, "arriving now" to 95, "minute away" to 85, "minutes away" to 60,
            "on the way" to 40, "confirmed" to 15, "finding" to 5,
        ),
        "com.bolt.client" to listOf(
            "arrived" to 100, "arriving" to 90, "on the way" to 45, "confirmed" to 15,
        ),
        // Transit (Trainline / National Rail style)
        "com.thetrainline" to listOf(
            "arrived" to 100, "arriving" to 90, "departed" to 30, "boarding" to 20, "on time" to 10,
        ),
    )

    fun extract(sbn: StatusBarNotification): ProgressState? {
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val haystack = "$title $text $big"

        // Strategy 1: native progress bar
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        if (max > 0 && !indeterminate) {
            val p = extras.getInt(Notification.EXTRA_PROGRESS, 0)
            return ProgressState((p * 100) / max, title.ifBlank { text }, sbn.packageName)
        }

        // Strategy 2: phrase map for known apps
        val phrases = phraseMaps[sbn.packageName] ?: return genericPercent(haystack, sbn.packageName, title)
        for ((needle, pct) in phrases) {
            if (haystack.contains(needle, ignoreCase = true)) {
                return ProgressState(pct, title.ifBlank { text }, sbn.packageName)
            }
        }
        return null
    }

    /** Last resort: any app that literally writes "42%" in its notification. */
    private val percentRegex = Regex("""(\d{1,3})\s?%""")
    private fun genericPercent(haystack: String, pkg: String, title: String): ProgressState? {
        val pct = percentRegex.find(haystack)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (pct !in 0..100) return null
        return ProgressState(pct, title, pkg)
    }

    val supportedPackages: Set<String> get() = phraseMaps.keys
}
