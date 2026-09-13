package uk.nothingsuite.shadeguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import uk.nothingsuite.design.NothingHaptics

/**
 * Translucent activity shown over the lock screen purely to host
 * BiometricPrompt. Success → 45 s grace window during which the shade
 * opens normally. Anything else → finish, shade stays blocked.
 *
 * Uses AppCompatActivity because BiometricPrompt requires a FragmentActivity.
 */
class BiometricGateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val haptics = NothingHaptics(this)
        val canAuth = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canAuth) {
            GuardState.log("No biometrics enrolled — guard cannot gate")
            done()
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    haptics.confirm()
                    GuardState.grantGrace()
                    GuardState.log("Fingerprint OK — shade allowed for 45 s")
                    done()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    haptics.reject()
                    GuardState.log("Auth error: $errString")
                    done()
                }
                override fun onAuthenticationFailed() {
                    haptics.reject()
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        )
    }

    private fun done() {
        GuardState.promptShowing = false
        finish()
    }
}
