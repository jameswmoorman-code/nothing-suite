package uk.nothingsuite.app.telecom

import android.os.Bundle
import android.telecom.Call
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.design.components.NothingLoader

/**
 * Full-screen incoming call UI. Three actions: Answer, Decline, Screen.
 * Finishes itself the moment the call leaves RINGING for any reason.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            NothingTheme {
                val info by CallRepository.current.collectAsState()

                LaunchedEffect(info?.state) {
                    if (info == null || info?.state != Call.STATE_RINGING) finish()
                }

                IncomingCallScreen(
                    number = info?.number ?: "UNKNOWN",
                    onAnswer = { info?.call?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY) },
                    onDecline = {
                        info?.call?.reject(Call.REJECT_REASON_DECLINED)
                        finish()
                    },
                    onScreen = {
                        info?.let { ScreenCallAction.execute(this@IncomingCallActivity, it) }
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun IncomingCallScreen(
    number: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(64.dp))
            DotMatrixText(text = "INCOMING", size = 14)
            Spacer(Modifier.height(16.dp))
            DotMatrixText(text = number, size = 32)
            Spacer(Modifier.height(24.dp))
            NothingLoader()
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Primary action: Screen — the feature this suite exists for.
            NothingButton(
                text = "SCREEN CALL",
                style = NothingButtonStyle.Accent,
                modifier = Modifier.fillMaxWidth(),
                onClick = onScreen,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                NothingButton(
                    text = "DECLINE",
                    style = NothingButtonStyle.Outline,
                    modifier = Modifier.weight(1f),
                    onClick = onDecline,
                )
                Spacer(Modifier.width(12.dp))
                NothingButton(
                    text = "ANSWER",
                    style = NothingButtonStyle.Solid,
                    modifier = Modifier.weight(1f),
                    onClick = onAnswer,
                )
            }
        }
    }
}
