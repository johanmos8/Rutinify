package com.mirkwood.rutinify.ui.permissions

import com.mirkwood.rutinify.domain.scheduler.AutoStartStatus
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.domain.scheduler.PermissionStatus
import com.mirkwood.rutinify.domain.usecase.GetPermissionStatusUseCase
import com.mirkwood.rutinify.domain.usecase.RescheduleAllUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** R-11.4: one row per permission, each with its "Corregir" button while missing; [fullScreenIntent] is R-15.11. */
data class PermissionsUiState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreenIntent: Boolean = true,
    /** R-11.10: a row only on Xiaomi phones. */
    val autoStart: AutoStartStatus = AutoStartStatus.NOT_APPLICABLE,
) {
    val allGranted: Boolean get() = notifications && exactAlarms && fullScreenIntent && autoStart != AutoStartStatus.DENIED
}

sealed interface PermissionsAction {
    /** Back from system settings or the permission dialog (R-11.5). */
    data object Resumed : PermissionsAction
}

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val getPermissionStatus: GetPermissionStatusUseCase,
    private val rescheduleAll: RescheduleAllUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(getPermissionStatus().toUi())
    val uiState: StateFlow<PermissionsUiState> = state.asStateFlow()

    fun onAction(action: PermissionsAction) {
        when (action) {
            PermissionsAction.Resumed -> refresh()
        }
    }

    private fun refresh() {
        val before = state.value
        val now = getPermissionStatus().toUi()
        state.value = now
        // R-11.6: windowed alarms become exact at once; RescheduleReceiver also gets the system
        // broadcast, and running both is harmless because rescheduling is idempotent (R-12.6)
        if (!before.exactAlarms && now.exactAlarms) {
            viewModelScope.launch { rescheduleAll() }
        }
    }

    private fun PermissionStatus.toUi() =
        PermissionsUiState(notifications = notifications, exactAlarms = exactAlarms, fullScreenIntent = fullScreenIntent, autoStart = autoStart)
}
