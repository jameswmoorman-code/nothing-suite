package uk.nothingsuite.design

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Nothing OS haptics are crisp and short — a single sharp "tick" rather than
 * Material's soft thud. We use composition primitives where the device
 * supports them and fall back to a hand-tuned 12 ms pulse elsewhere.
 */
class NothingHaptics(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private fun supports(primitive: Int) =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(primitive)

    /** Button press. */
    fun click() {
        if (supports(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            safe { vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .compose()
            ) }
        } else {
            safe { vibrator.vibrate(VibrationEffect.createOneShot(12, 180)) }
        }
    }

    /** Scroll detent / list item snap. */
    fun tick() {
        if (supports(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            safe { vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                    .compose()
            ) }
        } else {
            safe { vibrator.vibrate(VibrationEffect.createOneShot(8, 120)) }
        }
    }

    /** Success / "screening started". Double click, second one lighter. */
    fun confirm() {
        if (supports(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            safe { vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 60)
                    .compose()
            ) }
        } else {
            safe { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 14, 60, 10), -1)) }
        }
    }

    /** Error / destructive. Long-short. */
    fun reject() {
        safe { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 15), -1)) }
    }

    /** Vibrating without the VIBRATE permission throws; a missing tick is never worth a crash. */
    private inline fun safe(block: () -> Unit) {
        try { block() } catch (_: SecurityException) { }
    }
}

val LocalNothingHaptics = staticCompositionLocalOf<NothingHaptics> {
    error("NothingHaptics not provided — wrap your UI in NothingTheme { }")
}
