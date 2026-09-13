package uk.nothingsuite.nowplaying

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.design.components.NothingCard
import uk.nothingsuite.nowplaying.audio.AmbientListenerService

/**
 * Setup + the prominent disclosure Google Play requires for microphone use.
 * "Start listening" must be tapped here (foreground) — Android 14+ won't let
 * a microphone service start from the background.
 */
class MainActivity : ComponentActivity() {

    private var micGranted by mutableStateOf(false)
    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        micGranted = it[Manifest.permission.RECORD_AUDIO] == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = NowPlayingApp.instance
        micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        setContent {
            NothingTheme {
                var host by remember { mutableStateOf(app.settings.acrHost) }
                var key by remember { mutableStateOf(app.settings.acrAccessKey) }
                var secret by remember { mutableStateOf(app.settings.acrAccessSecret) }
                var interval by remember { mutableStateOf(app.settings.intervalMinutes.toString()) }
                var listening by remember { mutableStateOf(app.settings.listeningEnabled) }

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    DotMatrixText("NOW PLAYING", size = 28)
                    Text("Ambient music recognition on the home screen", style = typography.caption, color = colors.onBackgroundMuted)
                    Spacer(Modifier.height(24.dp))

                    // Prominent disclosure — keep this wording; Play reviewers look for it.
                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("HOW IT LISTENS", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Every few minutes the app records 5 seconds of sound and sends it to the recognition service you've set up with your own key. Nothing is recorded in between, nothing is stored, and you'll see the microphone indicator each time it listens. Pause any time from the notification.",
                                style = typography.body,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("1 · YOUR ACRCLOUD KEY", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text("Host", style = typography.caption)
                            OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth())
                            Text("Access key", style = typography.caption)
                            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth())
                            Text("Access secret", style = typography.caption)
                            OutlinedTextField(secret, { secret = it }, Modifier.fillMaxWidth())
                            Text("Minutes between samples (2–30)", style = typography.caption)
                            OutlinedTextField(interval, { interval = it.filter(Char::isDigit) }, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            NothingButton("SAVE", NothingButtonStyle.Solid, Modifier.fillMaxWidth()) {
                                app.settings.acrHost = host
                                app.settings.acrAccessKey = key
                                app.settings.acrAccessSecret = secret
                                app.settings.intervalMinutes = interval.toIntOrNull() ?: 5
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("2 · MICROPHONE", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text(if (micGranted) "Allowed" else "Needed to hear the room", style = typography.body)
                            Spacer(Modifier.height(12.dp))
                            NothingButton(
                                if (micGranted) "ALLOWED" else "ALLOW MICROPHONE",
                                if (micGranted) NothingButtonStyle.Outline else NothingButtonStyle.Accent,
                                Modifier.fillMaxWidth(), enabled = !micGranted,
                            ) {
                                micLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    NothingButton(
                        if (listening) "STOP LISTENING" else "START LISTENING",
                        if (listening) NothingButtonStyle.Outline else NothingButtonStyle.Accent,
                        Modifier.fillMaxWidth(), enabled = micGranted,
                    ) {
                        if (listening) AmbientListenerService.stop(this@MainActivity) else AmbientListenerService.start(this@MainActivity)
                        listening = !listening
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Then long-press your home screen → Widgets → Now Playing.", style = typography.caption, color = colors.onBackgroundMuted)
                }
            }
        }
    }
}
