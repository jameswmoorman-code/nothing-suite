package uk.nothingsuite.app.transcript

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
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
 * Real-time scrolling transcript of the screened caller. Opens the instant
 * "Screen" is tapped, before the forwarded leg reaches Twilio.
 */
class LiveTranscriptActivity : ComponentActivity() {

    private val vm: TranscriptViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TranscriptViewModel(
                    settings = NothingSuiteApp.instance.settings,
                    expectedCaller = intent.getStringExtra(EXTRA_CALLER),
                ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            NothingTheme {
                val state by vm.state.collectAsState()
                TranscriptScreen(state, onClose = ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_CALLER = "caller"
    }
}

@Composable
private fun TranscriptScreen(state: TranscriptUiState, onClose: () -> Unit) {
    val listState = rememberLazyListState()

    // Auto-scroll: follow the newest text as it streams in.
    LaunchedEffect(state.lines.size, state.lines.lastOrNull()?.text?.length) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        DotMatrixText(
            text = when (state.status) {
                ScreeningStatus.Connecting -> "CONNECTING"
                ScreeningStatus.Waiting -> "WAITING FOR CALLER"
                ScreeningStatus.Live -> "LIVE"
                ScreeningStatus.Ended -> "CALL ENDED"
                ScreeningStatus.Error -> "ERROR"
            },
            size = 14,
            color = if (state.status == ScreeningStatus.Live) colors.accent else colors.onBackgroundMuted,
        )
        Spacer(Modifier.height(4.dp))
        DotMatrixText(text = state.caller ?: "UNKNOWN", size = 24)
        Spacer(Modifier.height(16.dp))

        if (state.status == ScreeningStatus.Connecting || state.status == ScreeningStatus.Waiting) {
            NothingLoader()
            Spacer(Modifier.height(16.dp))
        }

        NothingCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                itemsIndexed(state.lines) { _, line ->
                    Text(
                        text = line.text,
                        style = typography.body,
                        color = if (line.final) colors.onBackground else colors.onBackgroundMuted,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }

        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = typography.caption, color = colors.accent)
        }

        Spacer(Modifier.height(16.dp))
        NothingButton(
            text = if (state.status == ScreeningStatus.Ended) "DONE" else "STOP WATCHING",
            style = NothingButtonStyle.Outline,
            modifier = Modifier.fillMaxWidth(),
            onClick = onClose,
        )
    }
}
