package uk.nothingsuite.glyphtracker.tracker

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.nothingsuite.glyphtracker.GlyphTrackerApp

/**
 * What's being tracked right now, shared by the listener, the Glyph output,
 * the toy and the app screen. Newest update wins when several are live.
 */
object Trackers {
    private data class Tracked(val state: ProgressState, val updatedAt: Long)
    private val map = LinkedHashMap<String, Tracked>()

    private val _active = MutableStateFlow<ProgressState?>(null)
    val active: StateFlow<ProgressState?> = _active

    @Synchronized fun put(key: String, state: ProgressState) { map[key] = Tracked(state, System.currentTimeMillis()); recompute() }
    @Synchronized fun remove(key: String) { if (map.remove(key) != null) recompute() }
    @Synchronized fun clearCompleted() { map.entries.removeAll { it.value.state.percent >= 100 }; recompute() }
    @Synchronized fun clear() { map.clear(); recompute() }

    private fun recompute() { _active.value = map.values.maxByOrNull { it.updatedAt }?.state }

    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, TrackerListener::class.java)
        return enabled.split(":").any { ComponentName.unflattenFromString(it) == me }
    }
}

/**
 * notification posted ─► extract() ─► Trackers ─► GlyphOutput.show()
 * notification removed ─► drop it ─► show the next one, or turn the Glyph off.
 *
 * Runs only while the user has granted Notification Access. Content never leaves the phone.
 */
class TrackerListener : NotificationListenerService() {

    private val app get() = GlyphTrackerApp.instance

    override fun onListenerConnected() {
        app.glyph.connect()
        activeNotifications?.forEach { handle(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { handle(sbn) }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Trackers.remove(key(sbn))
        render()
    }

    private fun handle(sbn: StatusBarNotification) {
        if (!app.enabled || sbn.packageName == packageName) return
        val state = ProgressExtractors.extract(sbn) ?: return
        val known = ProgressExtractors.appFor(sbn.packageName)
        if (known != null && !known.free && !app.unlocked) return      // paid app, not unlocked
        Trackers.put(key(sbn), state)
        Log.d(TAG, "tracking ${state.source} → ${state.percent}% (${state.label})")
        render()
    }

    private fun render() {
        val active = Trackers.active.value
        if (active == null) app.glyph.clear() else app.glyph.show(active)
    }

    private fun key(sbn: StatusBarNotification) = "${sbn.packageName}:${sbn.id}"

    override fun onListenerDisconnected() {
        app.glyph.clear()
        Trackers.clear()
    }

    private companion object { const val TAG = "GlyphTracker" }
}
