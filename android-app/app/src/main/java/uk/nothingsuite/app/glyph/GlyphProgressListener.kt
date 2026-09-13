package uk.nothingsuite.app.glyph

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nothingsuite.app.NothingSuiteApp

/**
 * "Universal Glyph Progress Tracker" — the background routing loop.
 *
 *   notification posted ─► extract() ─► keep newest per (package,id)
 *                                              │
 *                          pick the *active* tracker (most recently updated)
 *                                              │
 *                          GlyphController.showProgress(percent)
 *   notification removed ─► drop it ─► show next tracker, or clear()
 *
 * Runs only while the user has granted Notification Access. Notification
 * content never leaves the device — this class holds it in memory only.
 */
class GlyphProgressListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app get() = NothingSuiteApp.instance
    private val glyph get() = app.glyph

    /** key = "$packageName:$id" */
    private val trackers = LinkedHashMap<String, Tracked>()
    private var renderJob: Job? = null

    private data class Tracked(val state: ProgressState, val updatedAt: Long)

    override fun onListenerConnected() {
        glyph.connect()
        // Seed from anything already in the shade.
        activeNotifications?.forEach { handle(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!app.settings.glyphTrackerEnabled) return
        handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (trackers.remove(key(sbn)) != null) render()
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return           // ignore ourselves
        val state = ProgressExtractors.extract(sbn) ?: return
        trackers[key(sbn)] = Tracked(state, System.currentTimeMillis())
        Log.d(TAG, "tracking ${state.source} → ${state.percent}% (${state.label})")
        render()
    }

    private fun render() {
        renderJob?.cancel()
        renderJob = scope.launch {
            val active = trackers.values.maxByOrNull { it.updatedAt }
            if (active == null) {
                glyph.clear()
                return@launch
            }

            val anim = GlyphAnimations.forTier(app.license.tier.value)
            anim.render(glyph, active.state.percent)

            // Completed (100 %) trackers auto-expire so the strip doesn't stay lit forever.
            if (active.state.percent >= 100) {
                delay(COMPLETE_HOLD_MS)
                trackers.entries.removeAll { it.value.state.percent >= 100 }
                render()
            }
        }
    }

    private fun key(sbn: StatusBarNotification) = "${sbn.packageName}:${sbn.id}"

    override fun onDestroy() {
        scope.cancel()
        glyph.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "GlyphProgress"
        const val COMPLETE_HOLD_MS = 8_000L
    }
}
