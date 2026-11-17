package com.mirkwood.rutinify.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.domain.model.CategorySetting
import com.mirkwood.rutinify.domain.model.categoryDisplayName
import com.mirkwood.rutinify.domain.usecase.GetPermissionStatusUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveCategorySettingsUseCase
import com.mirkwood.rutinify.domain.usecase.RescheduleAllUseCase
import com.mirkwood.rutinify.domain.usecase.SetCategoryCriticalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One switch of "Alarmas críticas": [name] is the stored category, [label] its readable form. */
data class CategoryRowUi(val name: String, val label: String, val critical: Boolean)

data class SettingsUiState(
    /** True until Room answers, so the empty text does not flash. */
    val isLoading: Boolean = true,
    val categories: List<CategoryRowUi> = emptyList(),
    val exactAlarmsGranted: Boolean = true,
    /** R-15.5: explanation shown after marking the first critical category without exact alarms. */
    val showExactAlarmsDialog: Boolean = false,
) {
    val showEmptyState: Boolean get() = !isLoading && categories.isEmpty()

    /** R-15.4: critical alarms would ring in a window. */
    val showExactAlarmsWarning: Boolean get() = !exactAlarmsGranted && categories.any { it.critical }
}

sealed interface SettingsAction {
    data class CriticalToggled(val name: String, val critical: Boolean) : SettingsAction

    /** Back in the foreground: the exact-alarm permission may have changed (R-11.5). */
    data object Resumed : SettingsAction

    /** "Abrir ajustes" in the dialog or the warning (R-15.5, R-15.4). */
    data object ExactAlarmsOpenSettings : SettingsAction

    /** "Ahora no" or closing the dialog: the category stays critical (R-15.4). */
    data object ExactAlarmsDialogDismissed : SettingsAction
}

sealed interface SettingsEffect {
    data object OpenExactAlarmSettings : SettingsEffect
}

/** Settings › "Alarmas críticas" (R-15.1, R-15.2, R-15.4, R-15.5; D-66). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeCategorySettings: ObserveCategorySettingsUseCase,
    private val setCategoryCritical: SetCategoryCriticalUseCase,
    private val getPermissionStatus: GetPermissionStatusUseCase,
    private val rescheduleAll: RescheduleAllUseCase,
) : ViewModel() {

    private val exactAlarms = MutableStateFlow(getPermissionStatus().exactAlarms)
    private val exactAlarmsDialog = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(observeCategorySettings(), exactAlarms, exactAlarmsDialog) { settings, exact, dialog ->
            SettingsUiState(
                isLoading = false,
                categories = settings.map(::toRow),
                exactAlarmsGranted = exact,
                showExactAlarmsDialog = dialog,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.CriticalToggled -> toggle(action.name, action.critical)
            SettingsAction.Resumed -> refresh()
            SettingsAction.ExactAlarmsOpenSettings -> {
                exactAlarmsDialog.value = false
                effectChannel.trySend(SettingsEffect.OpenExactAlarmSettings)
            }
            SettingsAction.ExactAlarmsDialogDismissed -> exactAlarmsDialog.value = false
        }
    }

    private fun toggle(name: String, critical: Boolean) {
        // "La primera" among the listed categories: the switches the user can see (R-15.5)
        val firstCritical = critical && uiState.value.categories.none { it.critical }
        viewModelScope.launch {
            setCategoryCritical(name, critical)
            if (firstCritical && !getPermissionStatus().exactAlarms) {
                exactAlarms.value = false
                exactAlarmsDialog.value = true
            }
        }
    }

    private fun refresh() {
        val before = exactAlarms.value
        val now = getPermissionStatus().exactAlarms
        exactAlarms.value = now
        // R-11.6: windowed alarms become exact at once; idempotent with RescheduleReceiver (D-51)
        if (!before && now) viewModelScope.launch { rescheduleAll() }
    }

    private fun toRow(setting: CategorySetting) =
        CategoryRowUi(name = setting.name, label = categoryDisplayName(setting.name), critical = setting.critical)
}
