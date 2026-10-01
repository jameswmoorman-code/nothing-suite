package uk.nothingsuite.glyphtracker.tracker

import android.app.Notification
import android.service.notification.StatusBarNotification

/** One thing being tracked: how far along, what to call it, which app it came from. */
data class ProgressState(val percent: Int, val label: String, val source: String)

/** An app we know how to read. [free] ones work without the unlock. */
data class SupportedApp(val pkg: String, val name: String, val free: Boolean, val phrases: List<Pair<String, Int>>)

/**
 * Pure functions: notification in → progress out. Two strategies:
 *
 *  1. Native progress bars — any app that sets EXTRA_PROGRESS/EXTRA_PROGRESS_MAX
 *     (downloads, file transfers, Play Store installs, some couriers). Always free.
 *  2. Phrase maps — apps that describe state in words. Each entry is
 *     `substring (case-insensitive) → percent`, most specific first.
 *
 * Adding an app = adding an entry to [apps]. Community PRs welcome.
 */
object ProgressExtractors {

    val apps: List<SupportedApp> = listOf(
        // Parcels
        SupportedApp("com.amazon.mShop.android.shopping", "Amazon", free = true, phrases = listOf(
            "delivered" to 100, "stops away" to 90, "out for delivery" to 80, "arriving today" to 60,
            "shipped" to 40, "dispatched" to 40, "ordered" to 10,
        )),
        SupportedApp("uk.co.royalmail.consumer", "Royal Mail", free = true, phrases = listOf(
            "delivered" to 100, "out for delivery" to 80, "at your local delivery office" to 60,
            "in transit" to 40, "we've got it" to 20,
        )),
        SupportedApp("com.dpd.uk", "DPD", free = false, phrases = listOf(
            "delivered" to 100, "stops away" to 90, "out for delivery" to 80, "at depot" to 50,
        )),
        SupportedApp("com.evri.app", "Evri", free = false, phrases = listOf(
            "delivered" to 100, "out for delivery" to 80, "at local depot" to 55, "on its way" to 35,
        )),
        // Food
        SupportedApp("com.deliveroo.orderapp", "Deliveroo", free = true, phrases = listOf(
            "delivered" to 100, "arriving" to 95, "on the way" to 75, "on their way" to 75,
            "being prepared" to 40, "preparing" to 40, "accepted" to 20, "confirmed" to 15,
        )),
        SupportedApp("com.ubercab.eats", "Uber Eats", free = false, phrases = listOf(
            "delivered" to 100, "arriving" to 95, "heading your way" to 75, "picked up" to 65,
            "preparing" to 40, "confirmed" to 15,
        )),
        SupportedApp("com.justeat.app", "Just Eat", free = false, phrases = listOf(
            "delivered" to 100, "nearly there" to 90, "on its way" to 70, "being prepared" to 40,
            "accepted" to 20,
        )),
        // Rides
        SupportedApp("com.ubercab", "Uber", free = false, phrases = listOf(
            "arrived" to 100, "arriving now" to 95, "minute away" to 85, "minutes away" to 60,
            "on the way" to 40, "confirmed" to 15, "finding" to 5,
        )),
        SupportedApp("com.bolt.client", "Bolt", free = false, phrases = listOf(
            "arrived" to 100, "arriving" to 90, "on the way" to 45, "confirmed" to 15,
        )),
        // Trains
        SupportedApp("com.thetrainline", "Trainline", free = false, phrases = listOf(
            "arrived" to 100, "arriving" to 90, "departed" to 30, "boarding" to 20, "on time" to 10,
        )),
    )

    private val byPackage = apps.associateBy { it.pkg }

    fun appFor(pkg: String): SupportedApp? = byPackage[pkg]

    fun extract(sbn: StatusBarNotification): ProgressState? {
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val haystack = "$title $text $big"
        val label = title.ifBlank { text }

        // Strategy 1: native progress bar
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        if (max > 0 && !indeterminate) {
            val p = extras.getInt(Notification.EXTRA_PROGRESS, 0)
            return ProgressState((p * 100) / max, label, sbn.packageName)
        }

        // Strategy 2: phrase map for known apps
        val app = byPackage[sbn.packageName] ?: return genericPercent(haystack, sbn.packageName, label)
        for ((needle, pct) in app.phrases) {
            if (haystack.contains(needle, ignoreCase = true)) return ProgressState(pct, label, sbn.packageName)
        }
        return null
    }

    /** Last resort: any app that literally writes "42%" in its notification. */
    private val percentRegex = Regex("""(\d{1,3})\s?%""")
    private fun genericPercent(haystack: String, pkg: String, label: String): ProgressState? {
        val pct = percentRegex.find(haystack)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (pct !in 0..100) return null
        return ProgressState(pct, label, pkg)
    }
}
