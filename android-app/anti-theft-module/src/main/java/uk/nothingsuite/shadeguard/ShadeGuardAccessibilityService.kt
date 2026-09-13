package uk.nothingsuite.shadeguard

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * The guard loop.
 *
 *   System UI opens the shade ──► accessibility event (package com.android.systemui)
 *          │
 *          ├─ phone unlocked?            → ignore
 *          ├─ inside fingerprint grace?  → ignore
 *          └─ locked, no grace           → DISMISS_NOTIFICATION_SHADE immediately,
 *                                          then show BiometricGateActivity.
 *
 * Detection is heuristic: we look at the window class name System UI reports
 * for the shade / Quick Settings. Nothing OS is close to stock so the stock
 * names hold; add OEM variants to [SHADE_MARKERS] if a device misses.
 *
 * Honest limits (see docs/shade-guard.md): there is a ~100 ms window in which
 * a fast thief could hit a tile before we close the shade; power-off from
 * the lock screen is not covered; and Google Play restricts accessibility
 * services heavily — expect to justify this one carefully.
 */
class ShadeGuardAccessibilityService : AccessibilityService() {

    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }

    override fun onServiceConnected() {
        GuardState.log("Guard active")
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!GuardState.isEnabled(this)) return
        if (event.packageName != "com.android.systemui") return
        if (!keyguard.isKeyguardLocked) return
        if (GuardState.inGrace() || GuardState.promptShowing) return

        val cls = event.className?.toString().orEmpty()
        val looksLikeShade = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> SHADE_MARKERS.any { cls.contains(it, ignoreCase = true) }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> shadeWindowPresent()
            else -> false
        }
        if (!looksLikeShade) return

        Log.i(TAG, "shade opened while locked ($cls) — closing and gating")
        GuardState.log("Blocked shade at ${System.currentTimeMillis()}")
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)

        GuardState.promptShowing = true
        startActivity(
            Intent(this, BiometricGateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    /** Cheap check across the interactive windows for a System UI window with shade-ish title. */
    private fun shadeWindowPresent(): Boolean = windows.any { w ->
        val title = w.title?.toString().orEmpty()
        w.root?.packageName == "com.android.systemui" &&
            SHADE_MARKERS.any { title.contains(it, ignoreCase = true) }
    }

    override fun onInterrupt() {}

    private companion object {
        const val TAG = "ShadeGuard"
        val SHADE_MARKERS = listOf(
            "NotificationShade",      // stock window title (API 31+)
            "QuickSettings",
            "QSPanel",
            "NotificationPanelView",
            "StatusBarWindowView",
        )
    }
}
