package uk.nothingsuite.glyphtracker

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nothing.ketchum.Common
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.design.components.NothingCard
import uk.nothingsuite.glyphtracker.glyph.Animations
import uk.nothingsuite.glyphtracker.tracker.ProgressExtractors
import uk.nothingsuite.glyphtracker.tracker.Trackers

class MainActivity : ComponentActivity() {

    private var access by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = GlyphTrackerApp.instance
        val isMatrix = Common.is23112() || Common.is25111p()

        setContent {
            NothingTheme {
                val tier by app.license.tier.collectAsState()
                val unlocked = app.unlocked
                val active by Trackers.active.collectAsState()
                var enabled by remember { mutableStateOf(app.enabled) }
                var anim by remember { mutableStateOf(app.animation) }

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    DotMatrixText("GLYPH TRACKER", size = 28)
                    Text(
                        if (unlocked) "ALL APPS UNLOCKED" else "3 APPS FREE · UNLOCK ALL FOR ${app.license.catalogue.plus?.price}",
                        style = typography.caption, color = colors.accent,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your delivery, takeaway, ride or train, shown as progress on the Glyph. " +
                            (if (isMatrix) "A ring fills on the back as it gets closer." else "The light strip fills as it gets closer."),
                        style = typography.body,
                    )
                    Spacer(Modifier.height(20.dp))

                    // ---- 1. permission
                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("1 · NOTIFICATION ACCESS", size = 14)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (access) "Granted. Tracker is reading notifications on this phone only; nothing is sent anywhere."
                                else "The tracker reads the progress from delivery and ride notifications. Android asks you to allow this once.",
                                style = typography.caption, color = colors.onBackgroundMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            NothingButton(
                                if (access) "ALLOWED" else "ALLOW NOTIFICATION ACCESS",
                                if (access) NothingButtonStyle.Outline else NothingButtonStyle.Accent,
                                Modifier.fillMaxWidth(),
                            ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // ---- 2. on/off + look
                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    DotMatrixText("2 · TRACKING", size = 14)
                                    Text(if (active == null) "Nothing tracked right now" else "${active!!.percent}% · ${active!!.label}", style = typography.caption, color = colors.onBackgroundMuted)
                                }
                                NothingButton(if (enabled) "ON" else "OFF", if (enabled) NothingButtonStyle.Solid else NothingButtonStyle.Outline) {
                                    enabled = !enabled; app.enabled = enabled; if (!enabled) { Trackers.clear(); app.glyph.clear() }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Look", style = typography.caption, color = colors.onBackgroundMuted)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Animations.entries.forEach { a ->
                                    val locked = a.premium && !unlocked
                                    NothingButton(
                                        if (locked) "${a.label} 🔒" else a.label,
                                        if (anim == a.id) NothingButtonStyle.Solid else NothingButtonStyle.Outline,
                                        Modifier.weight(1f), enabled = !locked,
                                    ) { anim = a.id; app.animation = a.id }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            NothingButton("DEMO ON GLYPH", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) { app.glyph.demo() }
                            Text("Runs 0 to 100 on the back so you can see the look without waiting for a parcel.", style = typography.caption, color = colors.onBackgroundMuted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // ---- 3. apps
                    NothingCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DotMatrixText("3 · APPS IT UNDERSTANDS", size = 14)
                            Spacer(Modifier.height(8.dp))
                            ProgressExtractors.apps.forEach { a ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.name, style = typography.body, modifier = Modifier.weight(1f))
                                    Text(
                                        if (a.free || unlocked) "READY" else "LOCKED",
                                        style = typography.caption, color = if (a.free || unlocked) colors.onBackgroundMuted else colors.accent,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Plus anything with a real progress bar in its notification — downloads, Play Store installs, file transfers — always free.", style = typography.caption, color = colors.onBackgroundMuted)
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    if (!unlocked) {
                        NothingButton("UNLOCK ALL · ${app.license.catalogue.plus?.price}", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                            app.license.play.buy(this@MainActivity, uk.nothingsuite.billing.Sku.PLUS)
                        }
                        Spacer(Modifier.height(8.dp))
                        NothingButton("RESTORE PURCHASE", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                            app.license.play.restore(); app.license.refresh()
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    if (isMatrix) {
                        DotMatrixText("GLYPH TOY", size = 14)
                        Spacer(Modifier.height(6.dp))
                        Text("Add Glyph Tracker to the Glyph button too — it shows the ring whenever you press the button.", style = typography.caption, color = colors.onBackgroundMuted)
                        Spacer(Modifier.height(8.dp))
                        NothingButton("MANAGE GLYPH TOYS", NothingButtonStyle.Solid, Modifier.fillMaxWidth()) { openToyManager() }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text("Notification text stays on this phone. No account, no internet, no analytics.", style = typography.caption, color = colors.onBackgroundMuted)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        access = Trackers.hasNotificationAccess(this)
        GlyphTrackerApp.instance.license.refresh()
    }

    private fun openToyManager() {
        try {
            startActivity(Intent().setComponent(ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")))
        } catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
    }
}
