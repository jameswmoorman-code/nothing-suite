package uk.nothingsuite.app.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nothingsuite.app.NothingSuiteApp
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle

/** BYOK entry: backend URL, shared secret, Twilio number, and the one-tap forwarding dialer. */
class SettingsActivity : ComponentActivity() {
    /** Opens the native dialer with the code pre-filled. The user presses call — by design. */
    /** Network codes (*67*…#) are handled by the phone framework, so place them directly rather than
     *  via ACTION_DIAL — which, now we are the default dialer, would just bounce back into this app. */
    private fun dial(code: String) {
        val uri = Uri.parse("tel:${Uri.encode(code)}")
        val ok = runCatching { startActivity(Intent(Intent.ACTION_CALL, uri)); true }.getOrDefault(false)
        if (!ok) runCatching { startActivity(Intent(Intent.ACTION_DIAL, uri).setPackage("com.nothing.dialer")) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uk.nothingsuite.app.settings.ConciergePrefs.init()
        val settings = NothingSuiteApp.instance.settings

        setContent {
            NothingTheme {
                var url by remember { mutableStateOf(settings.backendWsUrl) }
                var secret by remember { mutableStateOf(settings.sharedSecret) }
                var twilio by remember { mutableStateOf(settings.twilioNumber) }
                var mine by remember { mutableStateOf(settings.myNumber) }
                var reject by remember { mutableStateOf(settings.rejectToScreen) }
                var autoUnknown by remember { mutableStateOf(settings.autoScreenUnknown) }
                var glyphId by remember { mutableStateOf(settings.glyphCallerId) }
                var desk by remember { mutableStateOf(settings.deskMode) }
                var alert by remember { mutableStateOf(settings.alertSound) }

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    DotMatrixText("SETUP", size = 28)
                    Spacer(Modifier.height(24.dp))

                    Text("Your backend (wss://…/app)", style = typography.caption)
                    OutlinedTextField(value = url, onValueChange = { url = it; settings.backendWsUrl = it }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("wss://…ngrok-free.dev/app") })
                    Spacer(Modifier.height(12.dp))

                    Text("Shared secret (APP_SHARED_SECRET)", style = typography.caption)
                    OutlinedTextField(value = secret, onValueChange = { secret = it; settings.sharedSecret = it }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("from the server's .env") })
                    Spacer(Modifier.height(12.dp))

                    Text("Your Twilio number (+44…)", style = typography.caption)
                    OutlinedTextField(value = twilio, onValueChange = { twilio = it; settings.twilioNumber = it }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("+44…") })
                    Spacer(Modifier.height(12.dp))

                    Text("Your own mobile (+44…) — TAKE THE CALL rings this", style = typography.caption)
                    OutlinedTextField(value = mine, onValueChange = { mine = it; settings.myNumber = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("+447…") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                    Spacer(Modifier.height(24.dp))

                    DotMatrixText("VOICE", size = 14)
                    Spacer(Modifier.height(8.dp))
                    val prefs by uk.nothingsuite.app.settings.ConciergePrefs.state.collectAsState()
                    var greetingDraft by remember(prefs.greeting) { mutableStateOf(prefs.greeting ?: "") }
                    if (!prefs.loaded) {
                        Text(prefs.message ?: "Fetching the voice list from the concierge…", style = typography.caption, color = if (prefs.message != null) colors.accent else colors.onBackgroundMuted)
                    } else {
                        prefs.voices.forEach { v ->
                            val chosen = v.id == prefs.voice
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NothingButton(
                                    (if (chosen) "● " else "") + v.label.uppercase() + "  ·  " + v.desc.uppercase(),
                                    if (chosen) NothingButtonStyle.Solid else NothingButtonStyle.Outline, Modifier.weight(1f),
                                ) { uk.nothingsuite.app.settings.ConciergePrefs.setVoice(v.id) }
                                NothingButton("HEAR", NothingButtonStyle.Outline, Modifier.width(84.dp)) { uk.nothingsuite.app.settings.ConciergePrefs.preview(v.id) }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        NothingButton("HEAR ALL TEN (ONE CALL, ~2 MIN)", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) { uk.nothingsuite.app.settings.ConciergePrefs.preview("all") }
                        Spacer(Modifier.height(6.dp))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = greetingDraft, onValueChange = { greetingDraft = it },
                            label = { Text("WHAT CALLERS HEAR FIRST") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NothingButton("SAVE GREETING", NothingButtonStyle.Outline, Modifier.weight(1f), enabled = greetingDraft.trim() != (prefs.greeting ?: "") && greetingDraft.trim().length >= 5) {
                                uk.nothingsuite.app.settings.ConciergePrefs.setGreeting(greetingDraft.trim())
                            }
                            NothingButton("HEAR GREETING", NothingButtonStyle.Accent, Modifier.weight(1f)) { uk.nothingsuite.app.settings.ConciergePrefs.preview() }
                        }
                        Text("HEAR rings MY NUMBER and reads a sample in that voice without changing your choice (about a penny a go). The better \"neural\" voices cost roughly 0.3p per greeting instead of 0.08p.", style = typography.caption)
                        prefs.message?.let { Spacer(Modifier.height(4.dp)); Text(it, style = typography.caption, color = colors.accent) }
                    }
                    Spacer(Modifier.height(24.dp))

                    DotMatrixText("SCREENING", size = 14)
                    Spacer(Modifier.height(8.dp))
                    NothingButton(if (reject) "HAND OVER INSTANTLY (REJECT)" else "KEEP RINGING (5 S NO-ANSWER)", if (reject) NothingButtonStyle.Solid else NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                        reject = !reject; settings.rejectToScreen = reject
                    }
                    Text(
                        if (reject) "Screen declines the call; the network's \"forward if busy\" rule sends it to the assistant in about a second."
                        else "Screen silences the call; the \"forward if no answer\" rule sends it on after 5 seconds. Use this if your network ignores busy forwarding.",
                        style = typography.caption,
                    )
                    Spacer(Modifier.height(12.dp))
                    NothingButton(if (autoUnknown) "AUTO-SCREEN UNKNOWN NUMBERS: ON" else "AUTO-SCREEN UNKNOWN NUMBERS: OFF", if (autoUnknown) NothingButtonStyle.Solid else NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                        autoUnknown = !autoUnknown; settings.autoScreenUnknown = autoUnknown
                    }
                    Text("Numbers not in your contacts go straight to the assistant without ringing. Needs the Contacts permission.", style = typography.caption)
                    Spacer(Modifier.height(12.dp))
                    NothingButton(if (alert) "ALERT SOUND: ON" else "ALERT SOUND: OFF", if (alert) NothingButtonStyle.Solid else NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                        alert = !alert; settings.alertSound = alert
                    }
                    Text("Chime and buzz when a call reaches the concierge, so you notice the live screen. Follows Do Not Disturb.", style = typography.caption)
                    Spacer(Modifier.height(12.dp))
                    NothingButton(if (desk) "DESK MODE: ON" else "DESK MODE: OFF", if (desk) NothingButtonStyle.Solid else NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                        desk = !desk; settings.deskMode = desk
                    }
                    Text("Phone face down on the desk = busy. Every call goes to the concierge without ringing until you pick it up. The Concierge Glyph face shows the time and how many calls it took.", style = typography.caption)
                    Spacer(Modifier.height(12.dp))
                    NothingButton(if (glyphId) "GLYPH CALLER ID: ON" else "GLYPH CALLER ID: OFF", if (glyphId) NothingButtonStyle.Solid else NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                        glyphId = !glyphId; settings.glyphCallerId = glyphId
                    }
                    Text("Phone (3): while the concierge is on a call, the Glyph Matrix shows who's calling on top and what they're saying underneath — handy with the phone face down. Flashes on a scam alert. For it to work while the phone is locked, add the Concierge Glyph Toy and leave it selected.", style = typography.caption)
                    Spacer(Modifier.height(8.dp))
                    NothingButton("ADD CONCIERGE GLYPH TOY", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                        runCatching {
                            startActivity(Intent().setComponent(android.content.ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")))
                        }.onFailure { runCatching { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } }
                    }
                    Spacer(Modifier.height(24.dp))

                    NothingButton("DONE", NothingButtonStyle.Solid, Modifier.fillMaxWidth()) {
                        uk.nothingsuite.app.transcript.ConciergeLink.restart(settings)
                        uk.nothingsuite.app.transcript.ConciergeService.ensureRunning(this@SettingsActivity)
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
