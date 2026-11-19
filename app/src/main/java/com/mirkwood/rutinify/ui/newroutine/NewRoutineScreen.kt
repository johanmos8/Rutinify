package com.mirkwood.rutinify.ui.newroutine

import androidx.compose.ui.Alignment
import android.content.ComponentName
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import com.mirkwood.rutinify.ui.common.ActionButtonRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mirkwood.rutinify.MainActivity
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.TemplateScenario
import com.mirkwood.rutinify.ui.common.BackTopAppBar
import com.mirkwood.rutinify.ui.common.asString
import com.mirkwood.rutinify.ui.preview.PreviewData
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Stateful New routine screen: collects the VM state and one-off effects. */
@Composable
fun NewRoutineRoute(
    onBack: () -> Unit,
    onNavigateToReview: (draftId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewRoutineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.new_routine_share_template_title)

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is NewRoutineEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message.resolve(resources))
                    is NewRoutineEffect.NavigateToReview -> onNavigateToReview(effect.draftId)
                    NewRoutineEffect.StartQrScan -> startQrScan(
                        context,
                        onScanned = { viewModel.onAction(NewRoutineAction.QrScanned(it)) },
                        onFailed = { viewModel.onAction(NewRoutineAction.QrScanFailed) },
                    )
                    is NewRoutineEffect.ShareTemplate -> {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, effect.template)
                        val chooser = Intent.createChooser(send, chooserTitle)
                            // Rutinify also receives text (R-14.1): sending the template to itself would import it
                            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
                        context.startActivity(chooser)
                    }
                }
            }
        }
    }

    NewRoutineScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Stateless New routine screen (R-1.1, R-1.4, R-2.1–R-2.4). */
@Composable
fun NewRoutineScreen(
    state: NewRoutineUiState,
    onAction: (NewRoutineAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(titleRes = R.string.new_routine_title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TemplateSection(
                scenario = state.scenario,
                onScenarioSelected = { onAction(NewRoutineAction.ScenarioSelected(it)) },
                template = state.template,
                expanded = state.templateExpanded,
                onCopy = { onAction(NewRoutineAction.CopyTemplateClicked) },
                onOpenInAi = { onAction(NewRoutineAction.OpenInAiClicked) },
                onToggle = { onAction(NewRoutineAction.ToggleTemplate) },
            )
            if (state.onDeviceAvailable) OnDeviceSection(state, onAction)
            OutlinedTextField(
                value = state.text,
                onValueChange = { onAction(NewRoutineAction.TextChanged(it)) },
                label = { Text(stringResource(R.string.new_routine_json_label)) },
                placeholder = { Text(stringResource(R.string.new_routine_json_placeholder)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                maxLines = 14,
                isError = state.issues.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            // Labels never wrap: the buttons stack when they do not fit side by side
            ActionButtonRow {
                OutlinedButton(onClick = { onAction(NewRoutineAction.PasteClicked) }) { Text(stringResource(R.string.action_paste)) }
                Button(
                    onClick = { onAction(NewRoutineAction.ValidateClicked) },
                    enabled = state.canValidate,
                ) {
                    if (state.isValidating) {
                        val validating = stringResource(R.string.new_routine_validating)
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp).semantics { contentDescription = validating },
                        )
                    } else {
                        Text(stringResource(R.string.action_validate))
                    }
                }
            }
            // R-19.6: a plan from another phone's QR code
            TextButton(onClick = { onAction(NewRoutineAction.ScanQrClicked) }) { Text(stringResource(R.string.action_scan_qr)) }
            if (state.issues.isNotEmpty()) IssuesCard(state)
        }
    }
}

@Composable
private fun TemplateSection(
    scenario: TemplateScenario,
    onScenarioSelected: (TemplateScenario) -> Unit,
    template: String?,
    expanded: Boolean,
    onCopy: () -> Unit,
    onOpenInAi: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.new_routine_template_hint), style = MaterialTheme.typography.bodyMedium)
            ScenarioSelector(selected = scenario, onSelected = onScenarioSelected)
            ActionButtonRow {
                Button(onClick = onCopy) { Text(stringResource(R.string.action_copy_template)) }
                // R-14.6: next to "Copiar plantilla"
                OutlinedButton(onClick = onOpenInAi, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_open_in_ai))
                }
            }
            TextButton(onClick = onToggle, enabled = template != null) {
                Text(stringResource(if (expanded) R.string.action_hide_template else R.string.action_show_template))
            }
            AnimatedVisibility(visible = expanded && template != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = template.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** R-16.2: one chip per scenario, wrapping so the selected one is always visible. */
@Composable
private fun ScenarioSelector(selected: TemplateScenario, onSelected: (TemplateScenario) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.new_routine_scenario_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TemplateScenario.entries.forEach { scenario ->
                FilterChip(
                    selected = scenario == selected,
                    onClick = { onSelected(scenario) },
                    label = { Text(stringResource(scenario.labelRes())) },
                )
            }
        }
    }
}

@Composable
private fun IssuesCard(state: NewRoutineUiState, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.new_routine_issues_title), style = MaterialTheme.typography.titleSmall)
            // At most 21 short lines (R-3.16): a plain Column inside the screen scroll is enough
            state.issues.forEach { issue ->
                Text(stringResource(R.string.new_routine_issue_bullet, issue.asString()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@ThemePreviews
@Composable
private fun NewRoutineScreenEmptyPreview() {
    RutinifyTheme { NewRoutineScreen(state = NewRoutineUiState(template = PreviewData.TEMPLATE), onAction = {}, onBack = {}) }
}

@ThemePreviews
@Composable
private fun NewRoutineScreenTemplatePreview() {
    RutinifyTheme {
        NewRoutineScreen(
            state = NewRoutineUiState(scenario = TemplateScenario.MEDICATION, template = PreviewData.TEMPLATE, templateExpanded = true),
            onAction = {},
            onBack = {},
        )
    }
}

@ThemePreviews
@Composable
private fun NewRoutineScreenErrorsPreview() {
    RutinifyTheme {
        NewRoutineScreen(
            state = NewRoutineUiState(text = PreviewData.INVALID_JSON, template = PreviewData.TEMPLATE, issues = PreviewData.issues),
            onAction = {},
            onBack = {},
        )
    }
}

@ThemePreviews
@Composable
private fun NewRoutineScreenValidatingPreview() {
    RutinifyTheme {
        NewRoutineScreen(
            state = NewRoutineUiState(text = PreviewData.INVALID_JSON, template = PreviewData.TEMPLATE, isValidating = true),
            onAction = {},
            onBack = {},
        )
    }
}

/** R-22.1–R-22.3: only when the phone's model is ready; the template flow below stays the same. */
@Composable
private fun OnDeviceSection(state: NewRoutineUiState, onAction: (NewRoutineAction) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.on_device_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.onDeviceText,
                onValueChange = { onAction(NewRoutineAction.OnDeviceTextChanged(it)) },
                label = { Text(stringResource(R.string.on_device_hint)) },
                minLines = 3,
                maxLines = 8,
                enabled = !state.isConverting,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isConverting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.on_device_converting), modifier = Modifier.weight(1f))
                    TextButton(onClick = { onAction(NewRoutineAction.CancelConvertClicked) }) { Text(stringResource(R.string.action_cancel)) }
                }
            } else {
                Button(onClick = { onAction(NewRoutineAction.ConvertClicked) }, enabled = state.canConvert) {
                    Text(stringResource(R.string.action_convert))
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun NewRoutineOnDevicePreview() {
    RutinifyTheme {
        NewRoutineScreen(
            state = NewRoutineUiState(onDeviceAvailable = true, onDeviceText = "Amoxicilina cada 8 horas durante 7 días, empezando a las 6"),
            onAction = {},
            onBack = {},
        )
    }
}

@ThemePreviews
@Composable
private fun NewRoutineConvertingPreview() {
    RutinifyTheme {
        NewRoutineScreen(
            state = NewRoutineUiState(onDeviceAvailable = true, onDeviceText = "Paseos a las 7, 13 y 21", isConverting = true),
            onAction = {},
            onBack = {},
        )
    }
}
