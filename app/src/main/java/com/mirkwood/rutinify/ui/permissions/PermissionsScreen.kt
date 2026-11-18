package com.mirkwood.rutinify.ui.permissions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.common.BackTopAppBar
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

@Composable
fun PermissionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fixNotifications = rememberNotificationPermissionFixer { viewModel.onAction(PermissionsAction.Resumed) }

    // R-11.5: back from system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(PermissionsAction.Resumed) }

    PermissionsScreen(
        state = state,
        onBack = onBack,
        onFixNotifications = fixNotifications,
        onFixExactAlarms = { openExactAlarmSettings(context) },
        onFixFullScreen = { openFullScreenIntentSettings(context) },
        modifier = modifier,
    )
}

/** Stateless Permissions screen: status of each permission and a button to fix it (R-11.4). */
@Composable
fun PermissionsScreen(
    state: PermissionsUiState,
    onBack: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixExactAlarms: () -> Unit,
    onFixFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(titleRes = R.string.permissions_title, onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(if (state.allGranted) R.string.permissions_all_granted else R.string.permissions_intro),
                style = MaterialTheme.typography.bodyLarge,
            )
            PermissionCard(
                titleRes = R.string.permissions_notifications_title,
                descriptionRes = R.string.permissions_notifications_description,
                granted = state.notifications,
                onFix = onFixNotifications,
            )
            PermissionCard(
                titleRes = R.string.permissions_exact_title,
                descriptionRes = R.string.permissions_exact_description,
                granted = state.exactAlarms,
                onFix = onFixExactAlarms,
            )
            // R-15.11: "Alarmas a pantalla completa: desactivado"
            PermissionCard(
                titleRes = R.string.permissions_full_screen_title,
                descriptionRes = R.string.permissions_full_screen_description,
                granted = state.fullScreenIntent,
                onFix = onFixFullScreen,
                grantedLabelRes = R.string.permissions_enabled,
                missingLabelRes = R.string.permissions_disabled,
            )
            // Design K-2: informative only, no link (the app has no INTERNET permission, R-13.6)
            Text(
                text = stringResource(R.string.permissions_battery_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    granted: Boolean,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes grantedLabelRes: Int = R.string.permissions_granted,
    @StringRes missingLabelRes: Int = R.string.permissions_missing,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(if (granted) grantedLabelRes else missingLabelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(stringResource(descriptionRes), style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Button(onClick = onFix) { Text(stringResource(R.string.action_fix)) }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun PermissionsScreenMissingPreview() {
    RutinifyTheme {
        PermissionsScreen(
            state = PermissionsUiState(notifications = true, exactAlarms = false, fullScreenIntent = false),
            onBack = {},
            onFixNotifications = {},
            onFixExactAlarms = {},
            onFixFullScreen = {},
        )
    }
}

@ThemePreviews
@Composable
private fun PermissionsScreenGrantedPreview() {
    RutinifyTheme {
        PermissionsScreen(
            state = PermissionsUiState(notifications = true, exactAlarms = true),
            onBack = {},
            onFixNotifications = {},
            onFixExactAlarms = {},
            onFixFullScreen = {},
        )
    }
}
