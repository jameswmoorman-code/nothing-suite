package uk.nothingsuite.app.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nothingsuite.app.NothingSuiteApp
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle

/** BYOK entry: backend URL, shared secret, Twilio number, and the one-tap forwarding dialer. */
class SettingsActivity : ComponentActivity() {
    /** Opens the native dialer with the code pre-filled. The user presses call — by design. */
    private fun dial(code: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(code)}")))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = NothingSuiteApp.instance.settings

        setContent {
            NothingTheme {
                var url by remember { mutableStateOf(settings.backendWsUrl) }
                var secret by remember { mutableStateOf(settings.sharedSecret) }
                var twilio by remember { mutableStateOf(settings.twilioNumber) }

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    DotMatrixText("SETUP", size = 28)
                    Spacer(Modifier.height(24.dp))

                    Text("Your backend (wss://…/app)", style = typography.caption)
                    OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))

                    Text("Shared secret (APP_SHARED_SECRET)", style = typography.caption)
                    OutlinedTextField(value = secret, onValueChange = { secret = it }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))

                    Text("Your Twilio number (+44…)", style = typography.caption)
                    OutlinedTextField(value = twilio, onValueChange = { twilio = it }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))

                    NothingButton("SAVE", NothingButtonStyle.Solid, Modifier.fillMaxWidth()) {
                        settings.backendWsUrl = url
                        settings.sharedSecret = secret
                        settings.twilioNumber = twilio
                        finish()
                    }
                    Spacer(Modifier.height(12.dp))

                    // Step 1 of 2: no-answer forwarding, 5 s — the rule screening depends on.
                    Text("Call forwarding: dial each code once, press call, wait for the confirmation.", style = typography.caption)
                    Spacer(Modifier.height(8.dp))
                    NothingButton("1 · FORWARD IF NO ANSWER", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                        settings.twilioNumber = twilio
                        dial(settings.forwardNoAnswerCode())
                    }
                    Spacer(Modifier.height(8.dp))
                    // Step 2 of 2: busy forwarding, so a call the user declines is screened too.
                    NothingButton("2 · FORWARD IF BUSY", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                        settings.twilioNumber = twilio
                        dial(settings.forwardWhenBusyCode())
                    }
                    Spacer(Modifier.height(8.dp))
                    NothingButton("SWITCH FORWARDING OFF", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                        dial(settings.cancelForwardingCodes().first())
                    }
                }
            }
        }
    }
}
