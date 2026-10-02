package uk.nothingsuite.app.hold

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.nothingsuite.app.NothingSuiteApp
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.design.components.NothingCard
import uk.nothingsuite.design.components.NothingLoader

/**
 * Hold For Me: ring a company through the concierge; when you're stuck in the queue,
 * hand the wait to the concierge and get rung back when a person answers.
 */
class HoldForMeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HoldForMe.init()
        setContent { NothingTheme { HoldScreen(onClose = ::finish) } }
    }
}

private val RECENTS_KEY = "hold_recents"

@Composable
private fun HoldScreen(onClose: () -> Unit) {
    val st by HoldForMe.state.collectAsState()
    val prefs = NothingSuiteApp.instance.getSharedPreferences("hold", 0)
    var number by remember { mutableStateOf("") }
    var recents by remember { mutableStateOf(prefs.getString(RECENTS_KEY, "")!!.split('\n').filter { it.isNotBlank() }) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.active) { while (st.active) { delay(1000); now = System.currentTimeMillis() } }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        DotMatrixText(
            when (st.state) {
                "idle" -> "HOLD FOR ME"
                "dialing_you" -> "RINGING YOU…"
                "connecting" -> "CALLING THEM…"
                "talking" -> "YOU'RE CONNECTED"
                "holding" -> "CONCIERGE IS HOLDING"
                "human" -> "SOMEONE ANSWERED · PICK UP"
                "joined" -> "YOU'RE THROUGH"
                else -> "FINISHED"
            },
            size = 14, color = if (st.state == "human") colors.accent else colors.onBackgroundMuted,
        )
        Spacer(Modifier.height(4.dp))
        DotMatrixText(st.to ?: "", size = 24)
        if (st.onHold && st.holdSince > 0) Text("ON HOLD FOR ${duration(now - st.holdSince)}", style = typography.caption, color = colors.accent)
        Spacer(Modifier.height(12.dp))

        if (!st.active) {
            // ---- set up a call -------------------------------------------------------
            NothingCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("The concierge rings you, then the company. When you hit a queue, tap HOLD FOR ME: your phone hangs up, the concierge waits, and it rings you back the moment a person answers.", style = typography.body, color = colors.onBackground)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = number, onValueChange = { number = it }, singleLine = true,
                        label = { Text("NUMBER TO CALL") }, placeholder = { Text("0300 200 3300") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    NothingButton("CALL VIA CONCIERGE", NothingButtonStyle.Accent, Modifier.fillMaxWidth(), enabled = number.filter(Char::isDigit).length >= 6) {
                        val n = number.trim()
                        recents = (listOf(n) + recents.filter { it != n }).take(6)
                        prefs.edit().putString(RECENTS_KEY, recents.joinToString("\n")).apply()
                        HoldForMe.start(n)
                    }
                    st.error?.let { Spacer(Modifier.height(8.dp)); Text(it, style = typography.caption, color = colors.accent) }
                    if (st.state == "ended" && st.message != null) { Spacer(Modifier.height(8.dp)); Text("Last call: ${st.message}", style = typography.caption, color = colors.onBackgroundMuted) }
                }
            }
            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("RECENT", style = typography.caption, color = colors.onBackgroundMuted)
                Spacer(Modifier.height(6.dp))
                recents.forEach { r ->
                    Text(r, style = typography.body, color = colors.onBackground, modifier = Modifier.fillMaxWidth().clickable { number = r }.padding(vertical = 8.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            NothingButton("CLOSE", NothingButtonStyle.Outline, Modifier.fillMaxWidth(), onClick = onClose)
        } else {
            // ---- live -------------------------------------------------------------------
            if (st.state in setOf("dialing_you", "connecting", "holding")) { NothingLoader(); Spacer(Modifier.height(12.dp)) }
            st.message?.let { Text(it, style = typography.body, color = colors.onBackground); Spacer(Modifier.height(12.dp)) }

            NothingCard(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                    if (st.heard.isEmpty()) item {
                        Text(
                            when (st.state) {
                                "talking" -> "Talk to them as normal. If you get put in a queue, tap HOLD FOR ME and put the phone down."
                                "holding" -> "Listening… the music and the recorded announcements are being ignored. The moment a person speaks you'll get a call."
                                "human" -> "A person has answered! The concierge has asked them to hold. Answer your phone."
                                else -> ""
                            },
                            style = typography.body, color = colors.onBackgroundMuted,
                        )
                    }
                    items(st.heard) { (line, recorded) ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(if (recorded) "RECORDING" else "HEARD", style = typography.caption, color = if (recorded) colors.onBackgroundMuted else colors.accent)
                            Text(line, style = typography.body, fontStyle = if (recorded) FontStyle.Italic else FontStyle.Normal, color = if (recorded) colors.onBackgroundMuted else colors.onBackground)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (st.canHold) NothingButton("HOLD FOR ME", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) { HoldForMe.holdNow() }
            if (st.canHold) Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NothingButton("CANCEL", NothingButtonStyle.Outline, Modifier.weight(1f)) { HoldForMe.cancel() }
                NothingButton("HIDE", NothingButtonStyle.Outline, Modifier.weight(1f), onClick = onClose)
            }
        }
    }
}

private fun duration(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s" }
