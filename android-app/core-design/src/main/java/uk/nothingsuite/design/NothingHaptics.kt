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
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .compose()
            )
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(12, 180))
        }
    }

    /** Scroll detent / list item snap. */
    fun tick() {
        if (supports(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                    .compose()
            )
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(8, 120))
        }
    }

    /** Success / "screening started". Double click, second one lighter. */
    fun confirm() {
        if (supports(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 60)
                    .compose()
            )
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 14, 60, 10), -1))
        }
    }

    /** Error / destructive. Long-short. */
    fun reject() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 15), -1))
    }
}

val LocalNothingHaptics = staticCompositionLocalOf<NothingHaptics> {
    error("NothingHaptics not provided — wrap your UI in NothingTheme { }")
}
