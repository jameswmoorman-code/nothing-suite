package uk.nothingsuite.app.inbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import uk.nothingsuite.app.transcript.Speaker
import uk.nothingsuite.app.transcript.Risk
import uk.nothingsuite.app.transcript.RiskBanner
import uk.nothingsuite.app.transcript.label
import uk.nothingsuite.app.transcript.tint
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.design.components.NothingCard

/** Every call the concierge took: who, when, how it ended, what they said. */
class InboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NothingTheme {
                val calls by CallInbox.calls.collectAsState()
                var openSid by remember { mutableStateOf(intent.getStringExtra(EXTRA_CALL_SID)) }
                val open = calls.firstOrNull { it.callSid == openSid }
                if (open != null) {
                    BackHandler { openSid = null }
                    LaunchedEffect(open.callSid) { CallInbox.markRead(open.callSid) }
                    CallDetail(open, onBack = { openSid = null }, onCallBack = { dial(open.from) }, onDelete = { CallInbox.delete(open.callSid); openSid = null })
                } else {
                    InboxList(calls, onOpen = { openSid = it.callSid }, onClose = ::finish)
                }
            }
        }
    }

    private fun dial(number: String) {
        runCatching { startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))) }
            .onFailure { runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) } }
    }

    companion object { const val EXTRA_CALL_SID = "callSid" }
}

@Composable
private fun InboxList(calls: List<ScreenedCall>, onOpen: (ScreenedCall) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        DotMatrixText("INBOX", size = 28)
        val unread = calls.count { !it.read && !it.live }
        Text(
            when {
                calls.isEmpty() -> "NO CALLS YET"
                unread == 0 -> "${calls.size} CALL${if (calls.size == 1) "" else "S"}"
                else -> "$unread NEW"
            },
            style = typography.caption, color = if (unread > 0) colors.accent else colors.onBackgroundMuted,
        )
        Spacer(Modifier.height(16.dp))

        if (calls.isEmpty()) {
            NothingCard(Modifier.fillMaxWidth()) {
                Text(
                    "When the concierge answers a call it lands here with what the caller said, even if you never looked at the live screen.",
                    Modifier.padding(16.dp), style = typography.body, color = colors.onBackgroundMuted,
                )
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(calls, key = { it.callSid }) { call ->
                NothingCard(Modifier.fillMaxWidth().clickable { onOpen(call) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!call.read && !call.live) {
                                Box(Modifier.size(8.dp).background(colors.accent, CircleShape)); Spacer(Modifier.width(8.dp))
                            }
                            DotMatrixText(call.from, size = 16, modifier = Modifier.weight(1f))
                            if (call.risk != Risk.NONE) { Text(call.risk.label(), style = typography.caption, color = call.risk.tint()); Spacer(Modifier.width(10.dp)) }
                            Text(if (call.live) "LIVE" else ago(call.startedAt), style = typography.caption, color = if (call.live) colors.accent else colors.onBackgroundMuted)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(call.gist, style = typography.body, color = colors.onBackground)
                        call.outcome?.let { Spacer(Modifier.height(6.dp)); Text(it.uppercase(), style = typography.caption, color = colors.onBackgroundMuted) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (calls.any { !it.read }) NothingButton("MARK ALL READ", NothingButtonStyle.Outline, Modifier.weight(1f)) { CallInbox.markAllRead() }
            NothingButton("CLOSE", NothingButtonStyle.Outline, Modifier.weight(1f), onClick = onClose)
        }
    }
}

@Composable
private fun CallDetail(call: ScreenedCall, onBack: () -> Unit, onCallBack: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        DotMatrixText(if (call.live) "LIVE" else (call.outcome ?: "CALL").uppercase(), size = 14, color = if (call.live) colors.accent else colors.onBackgroundMuted)
        Spacer(Modifier.height(4.dp))
        DotMatrixText(call.from, size = 24)
        val ctx = LocalContext.current
        Text(
            DateUtils.formatDateTime(ctx, call.startedAt, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL) +
                (call.endedAt?.let { "  ·  ${duration(it - call.startedAt)}" } ?: ""),
            style = typography.caption, color = colors.onBackgroundMuted,
        )
        Spacer(Modifier.height(12.dp))
        if (call.risk != Risk.NONE) { RiskBanner(call.risk, call.riskReasons); Spacer(Modifier.height(12.dp)) }

        NothingCard(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                if (call.lines.isEmpty()) item { Text("The caller hung up without saying anything.", style = typography.body, color = colors.onBackgroundMuted) }
                items(call.lines) { line ->
                    val concierge = line.speaker == Speaker.CONCIERGE
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = if (concierge) Alignment.CenterEnd else Alignment.CenterStart) {
                        Column(horizontalAlignment = if (concierge) Alignment.End else Alignment.Start) {
                            Text(if (concierge) "CONCIERGE" else "CALLER", style = typography.caption, color = if (concierge) colors.accent else colors.onBackgroundMuted)
                            Text(line.text, style = typography.body, fontStyle = if (concierge) FontStyle.Italic else FontStyle.Normal, color = colors.onBackground)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        NothingButton("CALL BACK", NothingButtonStyle.Accent, Modifier.fillMaxWidth(), onClick = onCallBack)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NothingButton("DELETE", NothingButtonStyle.Outline, Modifier.weight(1f), onClick = onDelete)
            NothingButton("BACK", NothingButtonStyle.Outline, Modifier.weight(1f), onClick = onBack)
        }
    }
}

private fun ago(t: Long): String = DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString().uppercase()
private fun duration(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s" }
