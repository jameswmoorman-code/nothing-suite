package uk.nothingsuite.shadeguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.design.components.NothingCard

/** Setup: enable the accessibility service, toggle the guard, see the last event. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NothingTheme {
                var enabled by remember { mutableStateOf(GuardState.isEnabled(this)) }
                val last by GuardState.lastEvent.collectAsState()

                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    DotMatrixText("SHADE GUARD", size = 28)
                    Text("Fingerprint before Quick Settings while locked", style = typography.caption, color = colors.onBackgroundMuted)
                    Spacer(Modifier.height(24.dp))

                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("1 · TURN ON THE SERVICE", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Android asks you to switch Shade Guard on under Accessibility → Downloaded apps. It only watches for the Quick Settings panel; it cannot read your screen.",
                                style = typography.body,
                            )
                            Spacer(Modifier.height(12.dp))
                            NothingButton("OPEN ACCESSIBILITY SETTINGS", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("2 · GUARD", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text(if (enabled) "On — shade is gated while locked" else "Off", style = typography.body)
                            Spacer(Modifier.height(12.dp))
                            NothingButton(
                                if (enabled) "SWITCH OFF" else "SWITCH ON",
                                if (enabled) NothingButtonStyle.Outline else NothingButtonStyle.Solid,
                                Modifier.fillMaxWidth(),
                            ) {
                                enabled = !enabled
                                GuardState.setEnabled(this@MainActivity, enabled)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("LAST EVENT", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text(last.ifBlank { "Nothing yet — lock the phone and pull the shade down" }, style = typography.body)
                        }
                    }
                }
            }
        }
    }
}
