package com.mirkwood.rutinify.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.common.BackTopAppBar
import com.mirkwood.rutinify.ui.permissions.openExactAlarmSettings
import com.mirkwood.rutinify.ui.preview.PreviewData
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Stateful Settings: collects the VM state and opens the system exact-alarm page on request. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // R-11.5: the warning follows changes made in system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(SettingsAction.Resumed) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.OpenExactAlarmSettings -> openExactAlarmSettings(context)
            }
        }
    }
    SettingsScreen(state = state, onAction = viewModel::onAction, onBack = onBack, modifier = modifier)
}

/** Stateless Settings: the "Alarmas críticas" section, one switch per category (R-15.1, D-66). */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(titleRes = R.string.settings_title, onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item(key = "header") { CriticalHeader() }
            if (state.showExactAlarmsWarning) {
                item(key = "warning") {
                    ExactAlarmsWarning(
                        onOpenSettings = { onAction(SettingsAction.ExactAlarmsOpenSettings) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (state.showEmptyState) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.settings_critical_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            items(state.categories, key = { it.name }) { row ->
                CategorySwitchRow(row = row, onToggle = { onAction(SettingsAction.CriticalToggled(row.name, it)) })
            }
        }
    }
    if (state.showExactAlarmsDialog) ExactAlarmsDialog(onAction)
}

@Composable
private fun CriticalHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_critical_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        // K-8: setAlarmClock shows the alarm icon and "next alarm" in the system
        Text(
            text = stringResource(R.string.settings_critical_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** R-15.4 */
@Composable
private fun ExactAlarmsWarning(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = stringResource(R.string.settings_critical_exact_warning),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_open_settings))
            }
        }
    }
}

/** The whole row toggles, so the touch target is the full width (CLAUDE.md §7). */
@Composable
private fun CategorySwitchRow(row: CategoryRowUi, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = row.critical, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = row.critical, onCheckedChange = null)
    }
}

/** R-15.5, worded like R-11.2. */
@Composable
private fun ExactAlarmsDialog(onAction: (SettingsAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(SettingsAction.ExactAlarmsDialogDismissed) },
        title = { Text(stringResource(R.string.settings_exact_dialog_title)) },
        text = { Text(stringResource(R.string.settings_exact_dialog_body)) },
        confirmButton = {
            TextButton(onClick = { onAction(SettingsAction.ExactAlarmsOpenSettings) }) {
                Text(stringResource(R.string.action_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(SettingsAction.ExactAlarmsDialogDismissed) }) {
                Text(stringResource(R.string.action_not_now))
            }
        },
    )
}

@ThemePreviews
@Composable
private fun SettingsScreenPreview() {
    RutinifyTheme { SettingsScreen(state = PreviewData.settingsState, onAction = {}, onBack = {}) }
}

@ThemePreviews
@Composable
private fun SettingsScreenWarningPreview() {
    RutinifyTheme {
        SettingsScreen(state = PreviewData.settingsState.copy(exactAlarmsGranted = false), onAction = {}, onBack = {})
    }
}

@ThemePreviews
@Composable
private fun SettingsScreenEmptyPreview() {
    RutinifyTheme { SettingsScreen(state = SettingsUiState(isLoading = false), onAction = {}, onBack = {}) }
}

@ThemePreviews
@Composable
private fun SettingsScreenDialogPreview() {
    RutinifyTheme {
        SettingsScreen(
            state = PreviewData.settingsState.copy(exactAlarmsGranted = false, showExactAlarmsDialog = true),
            onAction = {},
            onBack = {},
        )
    }
}
