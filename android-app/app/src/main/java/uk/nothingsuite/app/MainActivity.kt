package uk.nothingsuite.app

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
import uk.nothingsuite.billing.Sku
import uk.nothingsuite.app.settings.SettingsActivity
import uk.nothingsuite.app.telecom.DialerRoleManager
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.design.components.NothingCard

/**
 * Home + onboarding checklist. Also the ACTION_DIAL target (see the
 * .DialActivity alias in the manifest) — a keypad can be added here later.
 */
class MainActivity : ComponentActivity() {

    private val roleManager by lazy { DialerRoleManager(this) }
    private var isDefault by mutableStateOf(false)
    private var configured by mutableStateOf(false)
    private var canPopUp by mutableStateOf(true)
    private var canPopUpLocked by mutableStateOf(true)
    private val roleLauncher = roleManager.register(this) { isDefault = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = NothingSuiteApp.instance

        setContent {
            NothingTheme {
                val tier by app.license.tier.collectAsState()

                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    DotMatrixText("NOTHING SUITE", size = 28)
                    Text(
                        if (tier.isPremium) "PREMIUM" else "FREE",
                        style = typography.caption,
                        color = colors.accent,
                    )
                    Spacer(Modifier.height(24.dp))

                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("1 · DEFAULT PHONE APP", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text(if (isDefault) "Done" else "Required to intercept calls", style = typography.body)
                            Spacer(Modifier.height(12.dp))
                            NothingButton(
                                text = if (isDefault) "SET" else "SET AS DEFAULT",
                                style = if (isDefault) NothingButtonStyle.Outline else NothingButtonStyle.Accent,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDefault,
                            ) { roleLauncher.launch(roleManager.requestIntent()) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("2 · YOUR KEYS", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text(if (configured) "Backend connected" else "Point the app at your server", style = typography.body)
                            Spacer(Modifier.height(12.dp))
                            NothingButton("OPEN SETUP", NothingButtonStyle.Solid, Modifier.fillMaxWidth()) {
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (!canPopUp || !canPopUpLocked) {
                        NothingCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                DotMatrixText("ALLOW POP-UP", size = 14)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (!canPopUp) "So the live screen opens by itself while you're using the phone, allow \"Display over other apps\" and come back."
                                    else "Allow full-screen notifications so the live screen opens on the lock screen.",
                                    style = typography.body,
                                )
                                Spacer(Modifier.height(12.dp))
                                NothingButton("ALLOW", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                                    val svc = uk.nothingsuite.app.transcript.ConciergeService
                                    runCatching { startActivity(if (!canPopUp) svc.popUpSettingsIntent(this@MainActivity) else svc.fullScreenSettingsIntent(this@MainActivity)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("3 · LIVE VIEW", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text(if (configured) "On duty. When a call reaches the concierge this screen pops up by itself; open it any time to watch." else "Finish Setup first.", style = typography.body)
                            Spacer(Modifier.height(12.dp))
                            NothingButton("OPEN LIVE VIEW", NothingButtonStyle.Outline, Modifier.fillMaxWidth(), enabled = configured) {
                                startActivity(Intent(this@MainActivity, uk.nothingsuite.app.transcript.LiveTranscriptActivity::class.java))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // Premium unlock — Google Play handles the payment sheet.
                    if (!tier.isPremium) {
                        NothingCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                DotMatrixText("GLYPH VISUALISERS", size = 14)
                                Spacer(Modifier.height(8.dp))
                                Text("Sweep and milestone animations on the Glyph strip. One-time purchase.", style = typography.body)
                                Spacer(Modifier.height(12.dp))
                                NothingButton("UNLOCK PLUS · ${app.license.catalogue.plus?.price}", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                                    app.license.play.buy(this@MainActivity, Sku.PLUS)
                                }
                                Spacer(Modifier.height(8.dp))
                                NothingButton("UNLOCK PRO · ${app.license.catalogue.pro?.price}", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                                    app.license.play.buy(this@MainActivity, Sku.PRO)
                                }
                                Spacer(Modifier.height(8.dp))
                                NothingButton("RESTORE PURCHASE", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                                    app.license.play.restore(); app.license.refresh()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isDefault = roleManager.isDefaultDialer
        configured = NothingSuiteApp.instance.settings.isConfigured
        canPopUp = uk.nothingsuite.app.transcript.ConciergeService.canPopUp(this)
        canPopUpLocked = uk.nothingsuite.app.transcript.ConciergeService.canPopUpWhenLocked(this)
        if (configured) uk.nothingsuite.app.transcript.ConciergeService.ensureRunning(this)
    }
}
