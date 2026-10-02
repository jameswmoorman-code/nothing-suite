package uk.nothingsuite.app.transcript

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
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
 * The live screening screen: what the caller says, what the concierge says,
 * and the choices — take the call, ask, call back, take a message, hang up.
 * Pops up by itself (ConciergeService) when a call reaches the concierge.
 */
class LiveTranscriptActivity : ComponentActivity() {

    private val vm: TranscriptViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TranscriptViewModel(
                    settings = NothingSuiteApp.instance.settings,
                    expectedCaller = intent.getStringExtra(EXTRA_CALLER),
                    expectedCallSid = intent.getStringExtra(EXTRA_CALL_SID),
                ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // Keep the display (and so the Glyph toy) awake while this screen is up.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(android.app.NotificationManager::class.java).cancel(ConciergeService.CALL_NOTIFICATION_ID)
        CallBanner.hide(this)
        // Auto-close ~8 s after the call ends. Lives here (not in the composable) so a redraw
        // can't cancel it, and closes the whole task so nothing is left behind on the lock screen.
        lifecycleScope.launch {
            var closing = false
            vm.state.collect { st ->
                if (st.status == ScreeningStatus.Ended && !closing) {
                    closing = true
                    android.util.Log.i("LiveScreen", "call ended → closing in 8 s")
                    kotlinx.coroutines.delay(8_000)
                    if (!isFinishing) finishAndRemoveTask()
                }
            }
        }
        setContent {
            NothingTheme {
                val state by vm.state.collectAsState()
                TranscriptScreen(state, vm, onClose = ::finish, onOpenInbox = { sid ->
                    startActivity(android.content.Intent(this, uk.nothingsuite.app.inbox.InboxActivity::class.java).putExtra(uk.nothingsuite.app.inbox.InboxActivity.EXTRA_CALL_SID, sid))
                    finish()
                })
            }
        }
    }

    companion object {
        const val EXTRA_CALLER = "caller"
        const val EXTRA_CALL_SID = "callSid"
    }
}

@Composable
private fun TranscriptScreen(state: TranscriptUiState, vm: TranscriptViewModel, onClose: () -> Unit, onOpenInbox: (String?) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.lines.size, state.lines.lastOrNull()?.text?.length) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }
    val live = state.status == ScreeningStatus.Live
    // The call's over: give a moment to read the last line, then get out of the way.

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        DotMatrixText(
            text = when (state.status) {
                ScreeningStatus.Connecting -> "CONNECTING TO CONCIERGE"
                ScreeningStatus.Waiting -> "ON DUTY · WAITING FOR A CALL"
                ScreeningStatus.Live -> if (state.connecting) "RINGING YOU…" else if (state.held) "ON HOLD · MUSIC PLAYING" else "LIVE"
                ScreeningStatus.Ended -> "CALL ENDED"
                ScreeningStatus.Error -> "ERROR"
            },
            size = 14,
            color = if (live) colors.accent else colors.onBackgroundMuted,
        )
        Spacer(Modifier.height(4.dp))
        DotMatrixText(text = state.caller ?: "UNKNOWN", size = 24)
        Spacer(Modifier.height(12.dp))
        if (state.risk != Risk.NONE) { RiskBanner(state.risk, state.riskReasons); Spacer(Modifier.height(12.dp)) }

        if (state.status == ScreeningStatus.Connecting || state.status == ScreeningStatus.Waiting) {
            NothingLoader(); Spacer(Modifier.height(12.dp))
        }

        NothingCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                itemsIndexed(state.lines) { _, line ->
                    val concierge = line.speaker == Speaker.CONCIERGE
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = if (concierge) Alignment.CenterEnd else Alignment.CenterStart) {
                        Column(horizontalAlignment = if (concierge) Alignment.End else Alignment.Start) {
                            Text(if (concierge) "CONCIERGE" else "CALLER", style = typography.caption, color = if (concierge) colors.accent else colors.onBackgroundMuted)
                            Text(
                                text = line.text,
                                style = typography.body,
                                fontStyle = if (concierge) FontStyle.Italic else FontStyle.Normal,
                                color = if (line.final) colors.onBackground else colors.onBackgroundMuted,
                            )
                        }
                    }
                }
            }
        }

        state.message?.let { Spacer(Modifier.height(8.dp)); Text(it, style = typography.caption, color = colors.accent) }
        Spacer(Modifier.height(12.dp))

        if (live) {
            NothingButton("TAKE THE CALL", if (state.risk == Risk.SCAM) NothingButtonStyle.Outline else NothingButtonStyle.Accent, Modifier.fillMaxWidth(), enabled = !state.connecting) { vm.takeTheCall() }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NothingButton("ASK WHY", NothingButtonStyle.Outline, Modifier.weight(1f)) { vm.askReason() }
                NothingButton("TAKE MESSAGE", NothingButtonStyle.Outline, Modifier.weight(1f)) { vm.takeMessage() }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NothingButton(if (state.held) "RESUME" else "HOLD", if (state.held) NothingButtonStyle.Solid else NothingButtonStyle.Outline, Modifier.weight(1f)) { if (state.held) vm.resume() else vm.hold() }
                NothingButton("CALL BACK", NothingButtonStyle.Outline, Modifier.weight(1f)) { vm.callBack() }
            }
            Spacer(Modifier.height(8.dp))
            NothingButton("HANG UP", if (state.risk == Risk.SCAM) NothingButtonStyle.Accent else NothingButtonStyle.Solid, Modifier.fillMaxWidth()) { vm.hangUp() }
        } else {
            if (state.status == ScreeningStatus.Ended) {
                NothingButton("OPEN IN INBOX", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) { onOpenInbox(state.callSid) }
                Spacer(Modifier.height(8.dp))
            }
            NothingButton(if (state.status == ScreeningStatus.Ended) "DONE" else "CLOSE", NothingButtonStyle.Outline, Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}
