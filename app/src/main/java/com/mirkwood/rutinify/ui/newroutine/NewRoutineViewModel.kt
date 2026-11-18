package com.mirkwood.rutinify.ui.newroutine

import kotlinx.coroutines.Job
import com.mirkwood.rutinify.domain.usecase.ConvertResult
import com.mirkwood.rutinify.domain.usecase.ConvertOnDeviceUseCase
import com.mirkwood.rutinify.domain.usecase.DecodePlanLinkUseCase
import com.mirkwood.rutinify.domain.importer.Severity
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRecorder
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.PromptTemplateSource
import com.mirkwood.rutinify.domain.importer.RoutineImporter
import com.mirkwood.rutinify.domain.importer.TemplateScenarioStore
import com.mirkwood.rutinify.domain.model.TemplateScenario
import com.mirkwood.rutinify.domain.repository.DraftEntry
import com.mirkwood.rutinify.domain.repository.DraftStore
import com.mirkwood.rutinify.domain.repository.SharedTextInbox
import com.mirkwood.rutinify.domain.system.Clipboard
import com.mirkwood.rutinify.domain.system.TextSharer
import com.mirkwood.rutinify.ui.common.ErrorMessages
import com.mirkwood.rutinify.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewRoutineUiState(
    val text: String = "",
    /** Selected template scenario; "Genérica" the first time (R-16.2). */
    val scenario: TemplateScenario = TemplateScenario.GENERIC,
    /** Template of [scenario]; null until loaded from `res/raw` (R-1.3, R-16.3). */
    val template: String? = null,
    val templateExpanded: Boolean = false,
    val isValidating: Boolean = false,
    /** Already-worded validation errors, at most 20 plus "y {k} errores más" (R-3.16). */
    val issues: List<UiText> = emptyList(),
    /** "Convertir en el teléfono" is offered only when the phone's model is ready (R-22.1, R-22.4). */
    val onDeviceAvailable: Boolean = false,
    val onDeviceText: String = "",
    /** "Convirtiendo…" with "Cancelar" (R-22.3). */
    val isConverting: Boolean = false,
) {
    val canConvert: Boolean get() = onDeviceText.isNotBlank() && !isConverting && !isValidating

    /** R-2.4: disabled while the field is empty (or only blanks) and while validating. */
    val canValidate: Boolean get() = text.isNotBlank() && !isValidating
}

sealed interface NewRoutineAction {
    data class TextChanged(val text: String) : NewRoutineAction
    data object PasteClicked : NewRoutineAction

    /** Free text for the phone's model (R-22.1). */
    data class OnDeviceTextChanged(val text: String) : NewRoutineAction

    /** "Convertir" (R-22.2). */
    data object ConvertClicked : NewRoutineAction

    /** "Cancelar" while converting (R-22.3). */
    data object CancelConvertClicked : NewRoutineAction

    /** "Escanear QR" (R-19.6). */
    data object ScanQrClicked : NewRoutineAction

    /** What the scanner read (R-19.7). */
    data class QrScanned(val text: String) : NewRoutineAction

    /** The scanner could not open (no Google Play services, for example). */
    data object QrScanFailed : NewRoutineAction
    data object CopyTemplateClicked : NewRoutineAction

    /** Template selector (R-16.2). */
    data class ScenarioSelected(val scenario: TemplateScenario) : NewRoutineAction

    /** "Abrir en IA" (R-14.7). */
    data object OpenInAiClicked : NewRoutineAction
    data object ToggleTemplate : NewRoutineAction
    data object ValidateClicked : NewRoutineAction
}

sealed interface NewRoutineEffect {
    /** Snackbar text ("Plantilla copiada", "El portapapeles está vacío"). */
    data class ShowMessage(val message: UiText) : NewRoutineEffect
    data class NavigateToReview(val draftId: String) : NewRoutineEffect

    /** Android's app chooser with the template, titled "Enviar la plantilla a…" (R-14.7, D-65). */
    data class ShareTemplate(val template: String) : NewRoutineEffect

    /** Opens the Google code scanner (R-19.6, D-75). */
    data object StartQrScan : NewRoutineEffect
}

@HiltViewModel
class NewRoutineViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val importer: RoutineImporter,
    private val templateSource: PromptTemplateSource,
    private val clipboard: Clipboard,
    private val draftStore: DraftStore,
    private val sharedTextInbox: SharedTextInbox,
    private val textSharer: TextSharer,
    private val scenarioStore: TemplateScenarioStore,
    private val diagnostics: DiagnosticsRecorder,
    private val decodePlanLink: DecodePlanLinkUseCase,
    private val convertOnDevice: ConvertOnDeviceUseCase,
) : ViewModel() {

    private var convertJob: Job? = null

    /** Everything except the text, which lives in [savedStateHandle] (R-2.5). */
    private val local = MutableStateFlow(NewRoutineUiState())
    private val effectChannel = Channel<NewRoutineEffect>(Channel.BUFFERED)
    val effects: Flow<NewRoutineEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<NewRoutineUiState> =
        combine(savedStateHandle.getStateFlow(KEY_TEXT, ""), local) { text, state -> state.copy(text = text) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewRoutineUiState(text = currentText()))

    init {
        viewModelScope.launch { diagnostics.record(DiagnosticEvent.Step.NEW_ROUTINE) } // R-17.2
        showScenario(scenarioStore.last() ?: TemplateScenario.GENERIC)
        // R-22.4: without a ready model the screen stays as it was
        viewModelScope.launch { local.update { it.copy(onDeviceAvailable = convertOnDevice.isAvailable()) } }
        receiveSharedText()
    }

    fun onAction(action: NewRoutineAction) {
        when (action) {
            is NewRoutineAction.TextChanged -> setText(action.text)
            NewRoutineAction.PasteClicked -> paste()
            is NewRoutineAction.OnDeviceTextChanged -> local.update { it.copy(onDeviceText = action.text) }
            NewRoutineAction.ConvertClicked -> convert()
            NewRoutineAction.CancelConvertClicked -> {
                convertJob?.cancel()
                local.update { it.copy(isConverting = false) }
            }
            NewRoutineAction.ScanQrClicked -> effectChannel.trySend(NewRoutineEffect.StartQrScan)
            is NewRoutineAction.QrScanned -> {
                draftStore.clear()
                // R-19.8: a QR code that is not a Rutinify link is not a plan either
                if (decodePlanLink.isLink(action.text)) {
                    importText(action.text)
                } else {
                    effectChannel.trySend(NewRoutineEffect.ShowMessage(UiText.Res(R.string.import_link_invalid)))
                }
            }
            NewRoutineAction.QrScanFailed ->
                effectChannel.trySend(NewRoutineEffect.ShowMessage(UiText.Res(R.string.new_routine_scan_failed)))
            NewRoutineAction.CopyTemplateClicked -> copyTemplate()
            is NewRoutineAction.ScenarioSelected -> {
                scenarioStore.save(action.scenario)
                showScenario(action.scenario)
            }
            NewRoutineAction.OpenInAiClicked -> openInAi()
            NewRoutineAction.ToggleTemplate -> local.update { it.copy(templateExpanded = !it.templateExpanded) }
            NewRoutineAction.ValidateClicked -> validate()
        }
    }

    /**
     * Opened by a share (`NewRoutineDestination.fromShare`): the text replaces the field and is validated at once,
     * as if pasted and validated (R-14.2, D-64). Only once: a recreated screen keeps the text but does not import again.
     */
    private fun receiveSharedText() {
        if (savedStateHandle.get<Boolean>(KEY_FROM_SHARE) != true || savedStateHandle.get<Boolean>(KEY_SHARE_HANDLED) == true) return
        savedStateHandle[KEY_SHARE_HANDLED] = true
        // The draft under review, if any, is replaced by this one (R-14.5)
        draftStore.clear()
        val text = sharedTextInbox.take()
        if (text.isNullOrBlank()) {
            effectChannel.trySend(NewRoutineEffect.ShowMessage(UiText.Res(R.string.new_routine_shared_empty)))
            return
        }
        importText(text)
    }

    /** Shared text, or a plan link from a QR code or another app (R-14.2, R-19.7, R-19.8), then validated. */
    private fun importText(text: String) {
        val json = if (decodePlanLink.isLink(text)) {
            decodePlanLink(text) ?: run {
                effectChannel.trySend(NewRoutineEffect.ShowMessage(UiText.Res(R.string.import_link_invalid)))
                return
            }
        } else {
            text
        }
        setText(json)
        validate()
    }

    /** R-22.2: the model writes the JSON; a valid one opens Review, an invalid one shows it with its errors. */
    private fun convert() {
        val state = local.value
        if (!state.canConvert) return
        local.update { it.copy(isConverting = true, issues = emptyList()) }
        convertJob = viewModelScope.launch {
            val result = convertOnDevice(state.onDeviceText, state.scenario)
            local.update { it.copy(isConverting = false) }
            when (result) {
                is ConvertResult.Converted -> when (val imported = result.result) {
                    is ImportResult.Success -> {
                        draftStore.put(DraftEntry(imported.draft, imported.warnings))
                        effectChannel.send(NewRoutineEffect.NavigateToReview(imported.draft.id))
                    }
                    is ImportResult.Failure -> {
                        setText(result.json)
                        local.update { it.copy(issues = ErrorMessages.list(imported.issues)) }
                    }
                }
                ConvertResult.TooLong -> effectChannel.send(NewRoutineEffect.ShowMessage(UiText.Res(R.string.on_device_too_long)))
                ConvertResult.Failed -> effectChannel.send(NewRoutineEffect.ShowMessage(UiText.Res(R.string.on_device_failed)))
            }
        }
    }

    private fun currentText(): String = savedStateHandle[KEY_TEXT] ?: ""

    private fun setText(text: String) {
        savedStateHandle[KEY_TEXT] = text
        // Errors refer to the previous text
        local.update { it.copy(issues = emptyList()) }
    }

    private fun paste() {
        val text = clipboard.readText()
        if (text == null) {
            effectChannel.trySend(NewRoutineEffect.ShowMessage(UiText.Res(R.string.new_routine_clipboard_empty)))
            return
        }
        setText(text)
    }

    /** Selects [scenario] and loads its template; a slower earlier load never overwrites a newer choice. */
    private fun showScenario(scenario: TemplateScenario) {
        local.update { it.copy(scenario = scenario, template = null) }
        viewModelScope.launch {
            val template = templateSource.load(scenario)
            local.update { if (it.scenario == scenario) it.copy(template = template) else it }
        }
    }

    private suspend fun currentTemplate(): String = local.value.template ?: templateSource.load(local.value.scenario)

    private fun copyTemplate() {
        viewModelScope.launch {
            val template = currentTemplate()
            clipboard.copyText(template)
            effectChannel.send(NewRoutineEffect.ShowMessage(UiText.Res(R.string.new_routine_template_copied)))
        }
    }

    /** With no app able to receive text, the template is copied instead (R-14.8). */
    private fun openInAi() {
        viewModelScope.launch {
            val template = currentTemplate()
            if (textSharer.canShareText()) {
                effectChannel.send(NewRoutineEffect.ShareTemplate(template))
            } else {
                clipboard.copyText(template)
                effectChannel.send(NewRoutineEffect.ShowMessage(UiText.Res(R.string.new_routine_no_share_apps)))
            }
        }
    }

    private fun validate() {
        val text = currentText()
        if (text.isBlank() || local.value.isValidating) return
        local.update { it.copy(isValidating = true, issues = emptyList()) }
        viewModelScope.launch {
            diagnostics.record(DiagnosticEvent.Step.VALIDATE)
            when (val result = importer.import(text)) {
                is ImportResult.Success -> {
                    draftStore.put(DraftEntry(result.draft, result.warnings))
                    local.update { it.copy(isValidating = false) }
                    effectChannel.send(NewRoutineEffect.NavigateToReview(result.draft.id))
                }
                is ImportResult.Failure -> {
                    local.update { it.copy(isValidating = false, issues = ErrorMessages.list(result.issues)) }
                    // R-17.2: once per kind of error in this validation
                    result.issues.filter { it.severity == Severity.ERROR }.map { it.kind }.distinct()
                        .forEach { diagnostics.record(DiagnosticEvent.ValidationFailed(it)) }
                }
            }
        }
    }

    companion object {
        const val KEY_TEXT = "text"

        /** Property name of `NewRoutineDestination.fromShare` (type-safe navigation stores it under that key). */
        const val KEY_FROM_SHARE = "fromShare"
        private const val KEY_SHARE_HANDLED = "shareHandled"
    }
}
