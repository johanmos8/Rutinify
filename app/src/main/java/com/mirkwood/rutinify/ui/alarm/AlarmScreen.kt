package com.mirkwood.rutinify.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.preview.PreviewData
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Stateful alarm screen: [onClose] runs once the alarm is answered, silenced or no longer ringing. */
@Composable
fun AlarmRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    AlarmScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/** Stateless alarm screen (R-15.7): time, title, description, plan and the three buttons. */
@Composable
fun AlarmScreen(
    state: AlarmUiState,
    onAction: (AlarmAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (state.isLoading || state.closed) return@Surface
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(text = state.timeLabel, style = MaterialTheme.typography.displayLarge)
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            state.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                text = state.planName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Spacer(Modifier.weight(1f))
            AlarmButtons(onAction)
        }
    }
}

@Composable
private fun AlarmButtons(onAction: (AlarmAction) -> Unit, modifier: Modifier = Modifier) {
    val buttonModifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onAction(AlarmAction.Done) }, modifier = buttonModifier) {
            Text(stringResource(R.string.notification_action_done))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { onAction(AlarmAction.Snooze) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.notification_action_snooze))
            }
            OutlinedButton(onClick = { onAction(AlarmAction.Silence) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.notification_action_silence))
            }
        }
    }
}

@ThemePreviews
@Composable
private fun AlarmScreenPreview() {
    RutinifyTheme { AlarmScreen(state = PreviewData.alarmState, onAction = {}) }
}

@ThemePreviews
@Composable
private fun AlarmScreenNoDescriptionPreview() {
    RutinifyTheme { AlarmScreen(state = PreviewData.alarmState.copy(description = null), onAction = {}) }
}
