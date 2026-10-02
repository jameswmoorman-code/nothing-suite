package uk.nothingsuite.app.transcript

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A floating "call in progress" strip drawn over whatever the user is doing.
 *
 * Newer Android will not let a background service open an Activity, even with
 * "display over other apps" granted — but that permission does let us draw this
 * banner, and a tap on our own visible window is allowed to open the live screen.
 * Plain Views (no Compose) so it needs no lifecycle owner.
 */
object CallBanner {
    private const val TAG = "CallBanner"
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var autoHide: Runnable? = null

    fun show(context: Context, caller: String?, callSid: String?) {
        if (!Settings.canDrawOverlays(context)) return
        main.post {
            hideNow(context)
            val app = context.applicationContext
            val wm = app.getSystemService(WindowManager::class.java)
            val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, app.resources.displayMetrics) }

            val open = Intent(app, LiveTranscriptActivity::class.java)
                .putExtra(LiveTranscriptActivity.EXTRA_CALLER, caller)
                .putExtra(LiveTranscriptActivity.EXTRA_CALL_SID, callSid)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            val row = LinearLayout(app).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f).toInt(), dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt())
                background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(18f); setStroke(dp(1f).toInt(), Color.argb(80, 255, 255, 255)) }
                elevation = dp(8f)
                setOnClickListener { openLive(app, open) }
            }
            val text = LinearLayout(app).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(app).apply {
                    setText("CONCIERGE · TAKING A CALL"); setTextColor(Color.rgb(0xD7, 0x1A, 0x21))
                    textSize = 11f; typeface = Typeface.MONOSPACE; letterSpacing = 0.1f
                })
                addView(TextView(app).apply {
                    setText(caller ?: "Unknown caller"); setTextColor(Color.WHITE); textSize = 16f
                })
            }
            val button = TextView(app).apply {
                setText("OPEN"); setTextColor(Color.BLACK); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(16f).toInt(), dp(8f).toInt(), dp(16f).toInt(), dp(8f).toInt())
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14f) }
                setOnClickListener { openLive(app, open) }
            }
            row.addView(text); row.addView(button)

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(48f).toInt()
                horizontalMargin = 0f
            }
            val wrapper = LinearLayout(app).apply {
                setPadding(dp(12f).toInt(), 0, dp(12f).toInt(), 0)
                addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            runCatching { wm.addView(wrapper, lp); view = wrapper }
                .onFailure { Log.w(TAG, "could not show banner: ${it.message}") }

            // Don't leave it hanging around if the call ends without us hearing.
            autoHide = Runnable { hideNow(app) }.also { main.postDelayed(it, 3 * 60_000L) }
        }
    }

    private fun openLive(app: Context, intent: Intent) {
        runCatching { app.startActivity(intent) }.onFailure { Log.w(TAG, "open live failed: ${it.message}") }
        hideNow(app)
    }

    fun hide(context: Context) { main.post { hideNow(context) } }

    private fun hideNow(context: Context) {
        autoHide?.let { main.removeCallbacks(it) }; autoHide = null
        val v = view ?: return
        view = null
        runCatching { context.applicationContext.getSystemService(WindowManager::class.java).removeView(v) }
    }
}
