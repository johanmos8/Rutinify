package com.mirkwood.rutinify.ui.home

import com.mirkwood.rutinify.ui.common.contentSideMargin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.common.OrderStatusLabel
import com.mirkwood.rutinify.ui.common.asString
import com.mirkwood.rutinify.ui.permissions.openExactAlarmSettings
import com.mirkwood.rutinify.ui.preview.PreviewData
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Stateful Home: collects the order list from the VM. */
@Composable
fun HomeRoute(
    onNewRoutineClick: () -> Unit,
    onOrderClick: (orderId: Long) -> Unit,
    onPermissionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // R-11.5: the banner follows changes made in system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(HomeAction.Resumed) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HomeEffect.OpenExactAlarmSettings -> openExactAlarmSettings(context)
            }
        }
    }
    HomeScreen(
        state = state,
        onAction = viewModel::onAction,
        onNewRoutineClick = onNewRoutineClick,
        onOrderClick = onOrderClick,
        onPermissionsClick = onPermissionsClick,
        onSettingsClick = onSettingsClick,
        modifier = modifier,
    )
}

/** Stateless Home: order list or 3-step empty state, plus the "Nueva rutina" FAB (R-8.1–R-8.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onNewRoutineClick: () -> Unit,
    onOrderClick: (orderId: Long) -> Unit,
    onPermissionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // D-66: Settings › "Alarmas críticas"
                    IconButton(onClick = onSettingsClick) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewRoutineClick,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_routine)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.showExactAlarmsCard) {
                ExactAlarmsCard(onAction = onAction, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
            }
            if (state.missingPermissions) {
                PermissionsBanner(onClick = onPermissionsClick, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
            }
            HomeContent(state, onAction, onOrderClick, Modifier.weight(1f))
        }
    }
}

/**
 * R-8.1, R-8.7: the next alarm leads, then the plans still running as cards, then earlier plans as plain rows so they
 * recede (design review, D-99).
 */
@Composable
private fun HomeContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOrderClick: (orderId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = contentSideMargin()
    when {
        state.showEmptyState -> EmptyState(modifier)
        // D-100: one column on phones, two once each card gets 360 dp; never wider than 840 dp
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            modifier = modifier.fillMaxSize(),
            // Bottom room so the FAB never covers the last card
            contentPadding = PaddingValues(start = margin, top = 8.dp, end = margin, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.nextAlarm?.let { next ->
                item(key = "next", span = { GridItemSpan(maxLineSpan) }, contentType = "next") {
                    NextAlarmCard(
                        next = next,
                        onClick = { onOrderClick(next.orderId) },
                        onDone = { onAction(HomeAction.MarkNextDone(next.alarmId)) },
                    )
                }
            }
            if (state.ongoingOrders.isNotEmpty()) {
                item(key = "ongoing_header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { SectionHeader(R.string.home_section_ongoing) }
                items(state.ongoingOrders, key = { it.id }, contentType = { "ongoing" }) { card ->
                    OrderCard(card = card, onClick = { onOrderClick(card.id) })
                }
            }
            if (state.pastOrders.isNotEmpty()) {
                item(key = "past_header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { SectionHeader(R.string.home_section_past) }
                items(state.pastOrders, key = { it.id }, contentType = { "past" }) { card ->
                    PastOrderRow(card = card, onClick = { onOrderClick(card.id) })
                }
            }
        }
    }
}

/** R-8.7: what rings next, with "Hecho" at hand; tapping opens its plan. */
@Composable
private fun NextAlarmCard(next: NextAlarmUi, onClick: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_next_title), style = MaterialTheme.typography.labelLarge)
            Text(next.time.asString(), style = MaterialTheme.typography.displaySmall)
            Text(next.title, style = MaterialTheme.typography.titleLarge)
            Text(next.orderName, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onDone, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                Text(stringResource(R.string.notification_action_done))
            }
        }
    }
}

@Composable
private fun SectionHeader(textRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** A finished or cancelled plan: no container and no pending count, so it does not compete with running plans. */
@Composable
private fun PastOrderRow(card: OrderCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(card.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(card.dateLabel.asString()) },
        trailingContent = { OrderStatusLabel(card.status) },
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    )
}

/** R-11.3: stays while any permission is missing; opens the Permissions screen. */
@Composable
private fun PermissionsBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_permissions_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text(stringResource(R.string.action_review_permissions)) }
        }
    }
}

/** R-11.2, D-89: shown once a plan exists and exact alarms are off, until "Ahora no". */
@Composable
private fun ExactAlarmsCard(onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.home_exact_card_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.home_exact_card_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = { onAction(HomeAction.ExactAlarmsCardDismiss) }) {
                    Text(stringResource(R.string.action_not_now))
                }
                TextButton(onClick = { onAction(HomeAction.ExactAlarmsCardEnable) }) {
                    Text(stringResource(R.string.action_enable))
                }
            }
        }
    }
}

@Composable
private fun OrderCard(card: OrderCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OrderStatusLabel(card.status)
            }
            Text(card.dateLabel.asString(), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.home_order_pending, card.pending, card.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.home_empty_intro), style = MaterialTheme.typography.bodyLarge)
            listOf(R.string.home_empty_step_1, R.string.home_empty_step_2, R.string.home_empty_step_3).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@ThemePreviews
@Composable
private fun HomeScreenOrdersPreview() {
    RutinifyTheme { HomeScreen(state = PreviewData.homeState, onAction = {}, onNewRoutineClick = {}, onOrderClick = {}, onPermissionsClick = {}, onSettingsClick = {}) }
}

@ThemePreviews
@Composable
private fun HomeScreenEmptyPreview() {
    RutinifyTheme { HomeScreen(state = HomeUiState(isLoading = false), onAction = {}, onNewRoutineClick = {}, onOrderClick = {}, onPermissionsClick = {}, onSettingsClick = {}) }
}

@ThemePreviews
@Composable
private fun HomeScreenMissingPermissionsPreview() {
    RutinifyTheme {
        HomeScreen(
            state = PreviewData.homeState.copy(missingPermissions = true),
            onAction = {},
            onNewRoutineClick = {},
            onOrderClick = {},
            onPermissionsClick = {},
            onSettingsClick = {},
        )
    }
}

@ThemePreviews
@Composable
private fun HomeScreenExactAlarmsCardPreview() {
    RutinifyTheme {
        HomeScreen(
            state = PreviewData.homeState.copy(showExactAlarmsCard = true),
            onAction = {},
            onNewRoutineClick = {},
            onOrderClick = {},
            onPermissionsClick = {},
            onSettingsClick = {},
        )
    }
}
