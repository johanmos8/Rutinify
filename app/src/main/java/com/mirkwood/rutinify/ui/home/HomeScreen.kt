package com.mirkwood.rutinify.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mirkwood.rutinify.ui.preview.PreviewData
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Stateful Home: collects the order list from the VM. */
@Composable
fun HomeRoute(
    onNewRoutineClick: () -> Unit,
    onOrderClick: (orderId: Long) -> Unit,
    onPermissionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // R-11.5: the banner follows changes made in system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(HomeAction.Resumed) }
    HomeScreen(
        state = state,
        onNewRoutineClick = onNewRoutineClick,
        onOrderClick = onOrderClick,
        onPermissionsClick = onPermissionsClick,
        modifier = modifier,
    )
}

/** Stateless Home: order list or 3-step empty state, plus the "Nueva rutina" FAB (R-8.1–R-8.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onNewRoutineClick: () -> Unit,
    onOrderClick: (orderId: Long) -> Unit,
    onPermissionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewRoutineClick,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_routine)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.missingPermissions) {
                PermissionsBanner(onClick = onPermissionsClick, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
            }
            HomeContent(state, onOrderClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeContent(state: HomeUiState, onOrderClick: (orderId: Long) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.showEmptyState -> EmptyState(modifier)
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            // Bottom room so the FAB never covers the last card
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.orders, key = { it.id }) { card ->
                OrderCard(card = card, onClick = { onOrderClick(card.id) })
            }
        }
    }
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
            Text(card.dateLabel, style = MaterialTheme.typography.bodyMedium)
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
    RutinifyTheme { HomeScreen(state = PreviewData.homeState, onNewRoutineClick = {}, onOrderClick = {}, onPermissionsClick = {}) }
}

@ThemePreviews
@Composable
private fun HomeScreenEmptyPreview() {
    RutinifyTheme { HomeScreen(state = HomeUiState(isLoading = false), onNewRoutineClick = {}, onOrderClick = {}, onPermissionsClick = {}) }
}

@ThemePreviews
@Composable
private fun HomeScreenMissingPermissionsPreview() {
    RutinifyTheme {
        HomeScreen(
            state = PreviewData.homeState.copy(missingPermissions = true),
            onNewRoutineClick = {},
            onOrderClick = {},
            onPermissionsClick = {},
        )
    }
}
