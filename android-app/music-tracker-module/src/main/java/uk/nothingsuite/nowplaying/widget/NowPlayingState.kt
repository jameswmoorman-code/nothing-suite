package uk.nothingsuite.nowplaying.widget

import android.content.Context
import androidx.core.content.edit
import uk.nothingsuite.nowplaying.recognition.Track

/**
 * What the widget shows. Plain SharedPreferences (nothing sensitive here)
 * so the Glance receiver can read it from any process state.
 */
class NowPlayingState(context: Context) {
    private val prefs = context.getSharedPreferences("now_playing_state", Context.MODE_PRIVATE)

    val title: String get() = prefs.getString("title", "") ?: ""
    val artist: String get() = prefs.getString("artist", "") ?: ""
    val status: String get() = prefs.getString("status", "OFF") ?: "OFF"
    val updatedAt: Long get() = prefs.getLong("updated", 0L)

    fun setTrack(t: Track) = prefs.edit {
        putString("title", t.title); putString("artist", t.artist)
        putString("status", "PLAYING"); putLong("updated", System.currentTimeMillis())
    }

    /** Keeps the last track on screen; status line changes underneath it. */
    fun setStatus(s: String) = prefs.edit { putString("status", s); putLong("updated", System.currentTimeMillis()) }
}
