package com.mirkwood.rutinify.ui.review

import com.mirkwood.rutinify.ui.permissions.openExactAlarmSettings
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.common.BackTopAppBar
import com.mirkwood.rutinify.ui.common.RoutineDatePickerDialog
import com.mirkwood.rutinify.ui.common.UiText
import com.mirkwood.rutinify.ui.common.asString
import com.mirkwood.rutinify.ui.preview.PreviewData
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Stateful Review screen: collects the VM state and turns effects into snackbars or navigation. */
@Composable
fun ReviewRoute(
    onBack: () -> Unit,
    onNavigateToOrder: (orderId: Long, scheduledCount: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val undoLabel = stringResource(R.string.action_undo)
    val retryLabel = stringResource(R.string.action_retry)
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onAction(ReviewAction.NotificationPermissionResult)
    }

    // Back from the exact-alarm settings page (R-11.2)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(ReviewAction.Resumed) }

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is ReviewEffect.ShowMessage ->
                        snackbarHostState.showSnackbar(effect.message.resolve(resources), duration = SnackbarDuration.Long)
                    is ReviewEffect.ShowUndo -> {
                        val result = snackbarHostState.showSnackbar(effect.message.resolve(resources), actionLabel = undoLabel)
                        if (result == SnackbarResult.ActionPerformed) viewModel.onAction(ReviewAction.UndoRemove)
                    }
                    is ReviewEffect.ShowRetry -> {
                        val result = snackbarHostState.showSnackbar(
                            effect.message.resolve(resources),
                            actionLabel = retryLabel,
                            duration = SnackbarDuration.Indefinite,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.onAction(ReviewAction.RetrySchedule)
                    }
                    is ReviewEffect.NavigateToOrder -> onNavigateToOrder(effect.orderId, effect.scheduledCount)
                    ReviewEffect.RequestNotificationPermission ->
                        // The effect is only sent on Android 13+ (SchedulingPermissionsUseCase)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.onAction(ReviewAction.NotificationPermissionResult)
                        }
                    ReviewEffect.OpenExactAlarmSettings -> openExactAlarmSettings(context)
                }
            }
        }
    }

    ReviewScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Stateless Review screen (R-4.1–R-4.12, R-6.1). */
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onAction: (ReviewAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(titleRes = R.string.review_title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (!state.draftLost) ScheduleBar(state, onSchedule = { onAction(ReviewAction.ScheduleClicked) }) },
    ) { padding ->
        if (state.draftLost) {
            DraftLost(onBack = onBack, modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Header(state, onChangeDate = { showDatePicker = true }, onUseTomorrow = { onAction(ReviewAction.UseTomorrow) })
            }
            items(state.items, key = { it.key }) { item ->
                ReviewItemRow(item = item, onAction = onAction)
            }
        }
    }

    if (showDatePicker) {
        RoutineDatePickerDialog(
            initialMillis = state.dateMillisUtc,
            onConfirm = { millis ->
                showDatePicker = false
                onAction(ReviewAction.DateSelected(millis))
            },
            onDismiss = { showDatePicker = false },
        )
    }
    state.editing?.let { EditItemDialog(form = it, onAction = onAction) }
    when (val dialog = state.dialog) {
        ReviewDialog.NotificationsRationale -> NotificationsRationaleDialog(onAction)
        ReviewDialog.ExactAlarms -> ExactAlarmsDialog(onAction)
        is ReviewDialog.Conflict -> ConflictDialog(dialog, onAction)
        null -> Unit
    }
}

@Composable
private fun Header(state: ReviewUiState, onChangeDate: () -> Unit, onUseTomorrow: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.name, style = MaterialTheme.typography.headlineSmall)
        state.notes?.let { notes ->
            InfoCard(title = stringResource(R.string.review_notes_title), lines = listOf(notes))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.review_date_label), style = MaterialTheme.typography.labelMedium)
                Text(state.dateLabel, style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onChangeDate) { Text(stringResource(R.string.action_change_date)) }
        }
        state.dateNotice?.let { Text(it.asString(), style = MaterialTheme.typography.bodyMedium) }
        if (state.suggestTomorrow) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.review_suggest_tomorrow), modifier = Modifier.weight(1f))
                    TextButton(onClick = onUseTomorrow) { Text(stringResource(R.string.action_use_tomorrow)) }
                }
            }
        }
        if (state.warnings.isNotEmpty()) {
            InfoCard(title = stringResource(R.string.review_warnings_title), lines = state.warnings.map { it.asString() })
        }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ReviewItemRow(item: ReviewItemUi, onAction: (ReviewAction) -> Unit, modifier: Modifier = Modifier) {
    val includeLabel = stringResource(R.string.review_include_item, item.title)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (item.isPast) 0.5f else 1f)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.isIncluded,
                onCheckedChange = { onAction(ReviewAction.ToggleIncluded(item.key)) },
                enabled = !item.isPast,
                modifier = Modifier.semanticsLabel(includeLabel),
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val time = if (item.nextDay) stringResource(R.string.review_time_next_day, item.time) else item.time
                    Text(time, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    CategoryChip(item.category)
                    if (item.isPast) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.review_past),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                item.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { onAction(ReviewAction.EditItem(item.key)) }) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.review_edit_item, item.title))
            }
            IconButton(onClick = { onAction(ReviewAction.RemoveItem(item.key)) }) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.review_remove_item, item.title))
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun CategoryChip(category: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(category, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun ScheduleBar(state: ReviewUiState, onSchedule: () -> Unit, modifier: Modifier = Modifier) {
    Surface(tonalElevation = 3.dp, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.summary?.asString() ?: stringResource(R.string.review_nothing_to_schedule),
                style = MaterialTheme.typography.titleSmall,
            )
            Button(onClick = onSchedule, enabled = state.canSchedule, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.isScheduling) R.string.review_scheduling else R.string.action_schedule))
            }
        }
    }
}

@Composable
private fun DraftLost(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.review_draft_lost), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }
    }
}

@Composable
private fun EditItemDialog(form: EditItemUi, onAction: (ReviewAction) -> Unit) {
    fun changed(time: String = form.time, title: String = form.title, category: String = form.category) =
        onAction(ReviewAction.EditChanged(time, title, category))

    AlertDialog(
        onDismissRequest = { onAction(ReviewAction.DismissEdit) },
        title = { Text(stringResource(R.string.review_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(form.time, { changed(time = it) }, R.string.review_field_time, form.timeError)
                FormField(form.title, { changed(title = it) }, R.string.review_field_title, form.titleError)
                FormField(form.category, { changed(category = it) }, R.string.review_field_category, form.categoryError)
            }
        },
        confirmButton = { TextButton(onClick = { onAction(ReviewAction.SaveEdit) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { onAction(ReviewAction.DismissEdit) }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FormField(value: String, onValueChange: (String) -> Unit, label: Int, error: UiText?, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(it.asString()) } },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConflictDialog(dialog: ReviewDialog.Conflict, onAction: (ReviewAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(ReviewAction.DismissDialog) },
        title = {
            Text(stringResource(if (dialog.showsRanges) R.string.review_conflict_title_range else R.string.review_conflict_title))
        },
        text = {
            if (dialog.showsRanges) {
                val orders = dialog.orders.map { stringResource(R.string.review_conflict_order_range, it.name, it.range.asString()) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.review_conflict_body_range, orders.joinToString(", ")))
                    Text(stringResource(R.string.review_conflict_replace_whole))
                }
            } else {
                Text(stringResource(R.string.review_conflict_body, dialog.orders.joinToString(", ") { it.name }))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onAction(ReviewAction.ConflictReplace) }) { Text(stringResource(R.string.action_replace)) }
                TextButton(onClick = { onAction(ReviewAction.ConflictKeepBoth) }) { Text(stringResource(R.string.action_keep_both)) }
                TextButton(onClick = { onAction(ReviewAction.DismissDialog) }) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** R-11.1: shown once, before Android's own dialog. */
@Composable
private fun NotificationsRationaleDialog(onAction: (ReviewAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(ReviewAction.DismissDialog) },
        title = { Text(stringResource(R.string.review_notifications_title)) },
        text = { Text(stringResource(R.string.review_notifications_body)) },
        confirmButton = {
            TextButton(onClick = { onAction(ReviewAction.NotificationsRationaleAccepted) }) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ReviewAction.NotificationsRationaleSkipped) }) {
                Text(stringResource(R.string.action_not_now))
            }
        },
    )
}

/** R-11.2: every time exact alarms are not allowed. */
@Composable
private fun ExactAlarmsDialog(onAction: (ReviewAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(ReviewAction.DismissDialog) },
        title = { Text(stringResource(R.string.review_exact_title)) },
        text = { Text(stringResource(R.string.review_exact_body)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onAction(ReviewAction.ExactAlarmsOpenSettings) }) {
                    Text(stringResource(R.string.action_open_settings))
                }
                TextButton(onClick = { onAction(ReviewAction.ExactAlarmsContinueWithout) }) {
                    Text(stringResource(R.string.action_continue_without_exact))
                }
            }
        },
    )
}

private fun Modifier.semanticsLabel(label: String): Modifier =
    this.then(Modifier.semantics { contentDescription = label })

@ThemePreviews
@Composable
private fun ReviewScreenPreview() {
    RutinifyTheme { ReviewScreen(state = PreviewData.reviewState, onAction = {}, onBack = {}) }
}

@ThemePreviews
@Composable
private fun ReviewScreenNotificationsDialogPreview() {
    RutinifyTheme {
        ReviewScreen(state = PreviewData.reviewState.copy(dialog = ReviewDialog.NotificationsRationale), onAction = {}, onBack = {})
    }
}

@ThemePreviews
@Composable
private fun ReviewScreenExactDialogPreview() {
    RutinifyTheme { ReviewScreen(state = PreviewData.reviewState.copy(dialog = ReviewDialog.ExactAlarms), onAction = {}, onBack = {}) }
}

@ThemePreviews
@Composable
private fun ReviewScreenNothingToSchedulePreview() {
    RutinifyTheme {
        ReviewScreen(
            state = PreviewData.reviewState.copy(
                items = PreviewData.reviewState.items.map { it.copy(isPast = true, isIncluded = false) },
                summary = null,
                canSchedule = false,
                suggestTomorrow = true,
                dateNotice = null,
            ),
            onAction = {},
            onBack = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ReviewScreenConflictPreview() {
    RutinifyTheme {
        ReviewScreen(
            state = PreviewData.reviewState.copy(dialog = PreviewData.conflictDialog),
            onAction = {},
            onBack = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ReviewScreenConflictRangesPreview() {
    RutinifyTheme {
        ReviewScreen(
            state = PreviewData.reviewState.copy(dialog = PreviewData.conflictDialogWithRanges),
            onAction = {},
            onBack = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ReviewScreenEditPreview() {
    RutinifyTheme {
        ReviewScreen(
            state = PreviewData.reviewState.copy(
                editing = EditItemUi(1, "25:00", "Paseo", "paseo", timeError = UiText.Res(R.string.review_error_time)),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
