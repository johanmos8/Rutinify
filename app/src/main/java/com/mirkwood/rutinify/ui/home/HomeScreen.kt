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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.ui.preview.PreviewData
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Stateful Home: collects the order list from the VM. */
@Composable
fun HomeRoute(
    onNewRoutineClick: () -> Unit,
    onOrderClick: (orderId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onNewRoutineClick = onNewRoutineClick,
        onOrderClick = onOrderClick,
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
        when {
            state.showEmptyState -> EmptyState(Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
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
                StatusLabel(card.status)
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
private fun StatusLabel(status: OrderDisplayStatus, modifier: Modifier = Modifier) {
    val (textRes, container) = statusStyle(status)
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun statusStyle(status: OrderDisplayStatus): Pair<Int, Color> {
    val colors = MaterialTheme.colorScheme
    return when (status) {
        OrderDisplayStatus.ACTIVE -> R.string.order_status_active to colors.primaryContainer
        OrderDisplayStatus.PAUSED -> R.string.order_status_paused to colors.tertiaryContainer
        OrderDisplayStatus.CANCELLED -> R.string.order_status_cancelled to colors.errorContainer
        OrderDisplayStatus.FINISHED -> R.string.order_status_finished to colors.surfaceVariant
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
    RutinifyTheme { HomeScreen(state = PreviewData.homeState, onNewRoutineClick = {}, onOrderClick = {}) }
}

@ThemePreviews
@Composable
private fun HomeScreenEmptyPreview() {
    RutinifyTheme { HomeScreen(state = HomeUiState(isLoading = false), onNewRoutineClick = {}, onOrderClick = {}) }
}
