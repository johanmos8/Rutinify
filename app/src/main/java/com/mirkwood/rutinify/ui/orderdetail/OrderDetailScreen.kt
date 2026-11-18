package com.mirkwood.rutinify.ui.orderdetail

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import com.mirkwood.rutinify.ui.common.asString
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.ui.common.BackTopAppBar
import com.mirkwood.rutinify.ui.common.RoutineDatePickerDialog
import com.mirkwood.rutinify.ui.common.OrderStatusLabel
import com.mirkwood.rutinify.ui.common.UiText
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.ui.preview.PreviewData
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme
import kotlinx.coroutines.flow.first

/** Stateful order detail: collects the VM state, shows its messages and re-checks permissions on resume. */
@Composable
fun OrderDetailRoute(
    onBack: () -> Unit,
    onNavigateToReview: (draftId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val undoLabel = stringResource(R.string.action_undo)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(OrderDetailAction.Resumed) }

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    OrderDetailEffect.NavigateBack -> onBack()
                    is OrderDetailEffect.NavigateToReview -> onNavigateToReview(effect.draftId)
                    is OrderDetailEffect.ShowMessage ->
                        snackbarHostState.showSnackbar(effect.message.resolve(resources), duration = SnackbarDuration.Short)
                    is OrderDetailEffect.ShowUndo -> {
                        // A newer deletion replaces the previous offer instead of queueing behind it
                        snackbarHostState.currentSnackbarData?.dismiss()
                        launch {
                            val result = snackbarHostState.showSnackbar(effect.message.resolve(resources), actionLabel = undoLabel)
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.onAction(OrderDetailAction.UndoDelete(effect.alarmId))
                            }
                        }
                    }
                }
            }
        }
    }

    OrderDetailScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Stateless order detail: header, windowed-mode notice and timeline with per-alarm actions (R-9.*, R-7.6, R-5.4). */
@Composable
fun OrderDetailScreen(
    state: OrderDetailUiState,
    onAction: (OrderDetailAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.name.isEmpty()) {
                BackTopAppBar(titleRes = R.string.order_detail_title, onBack = onBack)
            } else {
                BackTopAppBar(title = state.name, onBack = onBack, actions = { OverflowMenu(state.canDuplicate, onAction) })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.notFound -> NotFound(Modifier.padding(padding))
            else -> Timeline(state, onAction, Modifier.padding(padding))
        }
    }
    state.dialog?.let { ConfirmDialog(it, onAction) }
    state.shiftDialog?.let { ShiftDialog(it, onAction) }
    state.duplicateDateMillis?.let { millis ->
        RoutineDatePickerDialog(
            initialMillis = millis,
            onConfirm = { onAction(OrderDetailAction.DuplicateDateSelected(it)) },
            onDismiss = { onAction(OrderDetailAction.DismissDuplicate) },
            confirmLabel = R.string.action_duplicate,
        )
    }
}

/** "Duplicar para otra fecha" and "Eliminar plan" live in the menu; both stay in every status (R-10.8). */
@Composable
private fun OverflowMenu(canDuplicate: Boolean, onAction: (OrderDetailAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canDuplicate) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_duplicate_order)) },
                    onClick = {
                        expanded = false
                        onAction(OrderDetailAction.DuplicateClicked)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete_order)) },
                onClick = {
                    expanded = false
                    onAction(OrderDetailAction.DeleteOrderClicked)
                },
            )
        }
    }
}

@Composable
private fun ConfirmDialog(dialog: OrderDetailDialog, onAction: (OrderDetailAction) -> Unit) {
    val (titleRes, message) = when (dialog) {
        is OrderDetailDialog.CancelAll -> R.string.cancel_all_title to dialog.message
        is OrderDetailDialog.DeleteOrder -> R.string.delete_order_title to dialog.message
    }
    AlertDialog(
        onDismissRequest = { onAction(OrderDetailAction.DismissDialog) },
        title = { Text(stringResource(titleRes)) },
        text = { Text(message.asString()) },
        confirmButton = {
            TextButton(onClick = { onAction(OrderDetailAction.ConfirmDialog) }) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(OrderDetailAction.DismissDialog) }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** "Voy tarde" (R-18.2): direction, minutes and a live preview; "Mover" only when the change is possible. */
@Composable
private fun ShiftDialog(dialog: ShiftDialogUi, onAction: (OrderDetailAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(OrderDetailAction.ShiftDismissed) },
        title = { Text(stringResource(R.string.shift_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dialog.delay,
                        onClick = { onAction(OrderDetailAction.ShiftDirectionChanged(true)) },
                        label = { Text(stringResource(R.string.shift_delay)) },
                    )
                    FilterChip(
                        selected = !dialog.delay,
                        onClick = { onAction(OrderDetailAction.ShiftDirectionChanged(false)) },
                        label = { Text(stringResource(R.string.shift_advance)) },
                    )
                }
                OutlinedTextField(
                    value = dialog.minutes,
                    onValueChange = { onAction(OrderDetailAction.ShiftMinutesChanged(it)) },
                    label = { Text(stringResource(R.string.shift_minutes_label)) },
                    singleLine = true,
                    isError = dialog.isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                dialog.message?.let {
                    Text(
                        text = it.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dialog.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(OrderDetailAction.ShiftConfirmed) }, enabled = dialog.canConfirm) {
                Text(stringResource(R.string.action_move))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(OrderDetailAction.ShiftDismissed) }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Timeline(state: OrderDetailUiState, onAction: (OrderDetailAction) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // Items above the timeline: header, plus the notice when shown
    val headerCount = if (state.windowedWarning) 2 else 1
    var scrolled by rememberSaveable { mutableStateOf(false) }

    // Bring the highlighted (or next) alarm into view once, keeping the item above it as context
    LaunchedEffect(state.scrollToIndex) {
        val index = state.scrollToIndex ?: return@LaunchedEffect
        if (scrolled) return@LaunchedEffect
        scrolled = true
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 } // wait for the first layout
        val target = headerCount + index
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any { it.index == target && it.offset + it.size <= listState.layoutInfo.viewportEndOffset }
        if (!fullyVisible) listState.scrollToItem((target - 1).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Header(state, onAction) }
        if (state.windowedWarning) {
            item(key = "windowed") { WindowedWarning() }
        }
        items(state.timeline, key = { it.id }) { item ->
            // The day header (R-9.6) shares its alarm's list item, so scrollToIndex keeps pointing at alarms
            Column(modifier = Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.dayTitle?.let { DayHeader(it) }
                TimelineCard(
                    item = item,
                    onMarkDone = { onAction(OrderDetailAction.MarkDone(item.id)) },
                    onDelete = { onAction(OrderDetailAction.Delete(item.id)) },
                )
            }
        }
    }
}

@Composable
private fun Header(state: OrderDetailUiState, onAction: (OrderDetailAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.dateLabel.asString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OrderStatusLabel(state.status)
        }
        Text(
            text = stringResource(R.string.home_order_pending, state.pending, state.total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.notes?.let {
            Text(stringResource(R.string.order_detail_notes, it), style = MaterialTheme.typography.bodyMedium)
        }
        if (state.pauseAction != null || state.canCancelAll || state.canShift) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                when (state.pauseAction) {
                    PauseAction.PAUSE -> OutlinedButton(onClick = { onAction(OrderDetailAction.PauseClicked) }) {
                        Text(stringResource(R.string.action_pause))
                    }
                    PauseAction.RESUME -> Button(onClick = { onAction(OrderDetailAction.ResumeClicked) }) {
                        Text(stringResource(R.string.action_resume))
                    }
                    null -> Unit
                }
                if (state.canShift) {
                    OutlinedButton(onClick = { onAction(OrderDetailAction.ShiftClicked) }) {
                        Text(stringResource(R.string.action_shift))
                    }
                }
                if (state.canCancelAll) {
                    OutlinedButton(onClick = { onAction(OrderDetailAction.CancelAllClicked) }) {
                        Text(stringResource(R.string.action_cancel_all), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowedWarning(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.order_detail_windowed_warning),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun DayHeader(title: UiText, modifier: Modifier = Modifier) {
    Text(
        text = title.asString(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun TimelineCard(
    item: TimelineItemUi,
    onMarkDone: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (item.isNext) CardDefaults.cardColors(containerColor = colors.primaryContainer) else CardDefaults.cardColors(),
        border = if (item.isHighlighted) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.width(64.dp)) {
                Text(item.time, style = MaterialTheme.typography.titleMedium)
                if (item.nextDay) {
                    Text(stringResource(R.string.order_detail_next_day), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                item.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    AlarmStatusText(item.status)
                    if (item.isNext) {
                        Text(
                            text = stringResource(R.string.order_detail_next),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                        )
                    }
                }
            }
            Row {
                if (item.canMarkDone) {
                    IconButton(onClick = onMarkDone) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.order_detail_mark_done, item.title))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.order_detail_delete_alarm, item.title))
                }
            }
        }
    }
}

@Composable
private fun AlarmStatusText(status: AlarmStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (textRes, color: Color) = when (status) {
        AlarmStatus.PENDING -> R.string.alarm_status_pending to colors.primary
        AlarmStatus.FIRED -> R.string.alarm_status_fired to colors.tertiary
        AlarmStatus.DONE -> R.string.alarm_status_done to colors.secondary
        AlarmStatus.SKIPPED -> R.string.alarm_status_skipped to colors.onSurfaceVariant
        AlarmStatus.CANCELLED -> R.string.alarm_status_cancelled to colors.error
    }
    Text(stringResource(textRes), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

@Composable
private fun NotFound(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.order_detail_not_found), style = MaterialTheme.typography.bodyLarge)
    }
}

@ThemePreviews
@Composable
private fun OrderDetailScreenPreview() {
    RutinifyTheme { OrderDetailScreen(state = PreviewData.orderDetailState, onAction = {}, onBack = {}) }
}

@ThemePreviews
@Composable
private fun OrderDetailScreenPausedPreview() {
    val state = PreviewData.orderDetailState.copy(
        status = OrderDisplayStatus.PAUSED,
        pauseAction = PauseAction.RESUME,
        windowedWarning = false,
        timeline = PreviewData.orderDetailState.timeline.map { it.copy(isNext = false) },
    )
    RutinifyTheme { OrderDetailScreen(state = state, onAction = {}, onBack = {}) }
}

@ThemePreviews
@Composable
private fun OrderDetailScreenMultiDayPreview() {
    RutinifyTheme { OrderDetailScreen(state = PreviewData.orderDetailStateMultiDay, onAction = {}, onBack = {}) }
}

@ThemePreviews
@Composable
private fun OrderDetailScreenNotFoundPreview() {
    RutinifyTheme { OrderDetailScreen(state = OrderDetailUiState(isLoading = false, notFound = true), onAction = {}, onBack = {}) }
}

@ThemePreviews
@Composable
private fun OrderDetailShiftDialogPreview() {
    RutinifyTheme {
        OrderDetailScreen(
            state = PreviewData.orderDetailState.copy(
                canShift = true,
                shiftDialog = ShiftDialogUi(
                    delay = true,
                    minutes = "30",
                    message = UiText.Plural(R.plurals.shift_preview, 3, listOf(3, "14:00", "14:30")),
                    canConfirm = true,
                ),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
