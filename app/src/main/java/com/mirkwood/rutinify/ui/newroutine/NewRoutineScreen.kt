package com.mirkwood.rutinify.ui.newroutine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.mirkwood.rutinify.R
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

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is NewRoutineEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message.resolve(resources))
                    is NewRoutineEffect.NavigateToReview -> onNavigateToReview(effect.draftId)
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
                template = state.template,
                expanded = state.templateExpanded,
                onCopy = { onAction(NewRoutineAction.CopyTemplateClicked) },
                onToggle = { onAction(NewRoutineAction.ToggleTemplate) },
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onAction(NewRoutineAction.PasteClicked) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_paste)) }
                Button(
                    onClick = { onAction(NewRoutineAction.ValidateClicked) },
                    enabled = state.canValidate,
                    modifier = Modifier.weight(1f),
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
            if (state.issues.isNotEmpty()) IssuesCard(state)
        }
    }
}

@Composable
private fun TemplateSection(
    template: String?,
    expanded: Boolean,
    onCopy: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.new_routine_template_hint), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCopy) { Text(stringResource(R.string.action_copy_template)) }
                TextButton(onClick = onToggle, enabled = template != null) {
                    Text(stringResource(if (expanded) R.string.action_hide_template else R.string.action_show_template))
                }
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
            state = NewRoutineUiState(template = PreviewData.TEMPLATE, templateExpanded = true),
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
