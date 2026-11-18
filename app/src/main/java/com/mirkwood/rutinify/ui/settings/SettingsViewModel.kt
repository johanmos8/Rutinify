package com.mirkwood.rutinify.ui.settings

import com.mirkwood.rutinify.ui.common.UiText
import com.mirkwood.rutinify.domain.usecase.ManageDiagnosticsUseCase
import com.mirkwood.rutinify.domain.usecase.ExportDiagnosticsUseCase
import com.mirkwood.rutinify.domain.usecase.DiagnosticsReport
import com.mirkwood.rutinify.R
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
    /** "Guardar diagnóstico en este teléfono" (R-17.1). */
    val diagnosticsEnabled: Boolean = false,
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

    data class DiagnosticsToggled(val enabled: Boolean) : SettingsAction

    data object ExportDiagnostics : SettingsAction

    data object ClearDiagnostics : SettingsAction
}

sealed interface SettingsEffect {
    data object OpenExactAlarmSettings : SettingsEffect

    /** Opens the Android chooser with the report as text (R-17.5). */
    data class ShareDiagnostics(val report: DiagnosticsReport) : SettingsEffect

    data class ShowMessage(val message: UiText) : SettingsEffect
}

/** Settings › "Alarmas críticas" (R-15.1, R-15.2, R-15.4, R-15.5; D-66). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeCategorySettings: ObserveCategorySettingsUseCase,
    private val setCategoryCritical: SetCategoryCriticalUseCase,
    private val getPermissionStatus: GetPermissionStatusUseCase,
    private val rescheduleAll: RescheduleAllUseCase,
    private val manageDiagnostics: ManageDiagnosticsUseCase,
    private val exportDiagnostics: ExportDiagnosticsUseCase,
) : ViewModel() {

    private val exactAlarms = MutableStateFlow(getPermissionStatus().exactAlarms)
    private val exactAlarmsDialog = MutableStateFlow(false)
    private val diagnosticsEnabled = MutableStateFlow(manageDiagnostics.isEnabled())

    val uiState: StateFlow<SettingsUiState> =
        combine(observeCategorySettings(), exactAlarms, exactAlarmsDialog, diagnosticsEnabled) { settings, exact, dialog, diagnostics ->
            SettingsUiState(
                isLoading = false,
                categories = settings.map(::toRow),
                exactAlarmsGranted = exact,
                showExactAlarmsDialog = dialog,
                diagnosticsEnabled = diagnostics,
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
            is SettingsAction.DiagnosticsToggled -> {
                diagnosticsEnabled.value = action.enabled
                viewModelScope.launch { manageDiagnostics.setEnabled(action.enabled) } // R-17.1, R-17.7
            }
            SettingsAction.ExportDiagnostics -> viewModelScope.launch {
                effectChannel.send(SettingsEffect.ShareDiagnostics(exportDiagnostics())) // R-17.5
            }
            SettingsAction.ClearDiagnostics -> viewModelScope.launch {
                manageDiagnostics.clear() // R-17.6
                effectChannel.send(SettingsEffect.ShowMessage(UiText.Res(R.string.settings_diagnostics_cleared)))
            }
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
