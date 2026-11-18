package com.mirkwood.rutinify.ui.newroutine

import com.mirkwood.rutinify.testutil.es
import com.mirkwood.rutinify.domain.importer.PlanLink
import com.mirkwood.rutinify.domain.usecase.DecodePlanLinkUseCase
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import com.mirkwood.rutinify.testutil.FakeDiagnosticsRecorder
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.data.draft.InMemoryDraftStore
import com.mirkwood.rutinify.data.share.InMemorySharedTextInbox
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.Severity
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.model.TemplateScenario
import com.mirkwood.rutinify.domain.repository.DraftEntry
import com.mirkwood.rutinify.testutil.FakeClipboard
import com.mirkwood.rutinify.testutil.FakeRoutineImporter
import com.mirkwood.rutinify.testutil.FakeTemplateScenarioStore
import com.mirkwood.rutinify.testutil.FakeTextSharer
import com.mirkwood.rutinify.testutil.MainDispatcherRule
import com.mirkwood.rutinify.ui.common.ErrorMessages
import com.mirkwood.rutinify.ui.common.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** T-21, T-40–T-42: New routine screen logic (R-1.1, R-1.2, R-1.4, R-2.1–R-2.5, R-3.19, R-14.2–R-14.8, R-16.2, R-16.3). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class NewRoutineViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val template = "Instrucciones…\n[PEGA AQUÍ TU RUTINA]"
    private val draft = RoutineDraft("draft-1", "Cachorro", null, null, emptyList(), DraftOrigin.IMPORT)
    private val warning = ValidationIssue(itemIndex = 1, kind = IssueKind.TITLE_TRUNCATED, severity = Severity.WARNING)

    private val clipboard = FakeClipboard()
    private val importer = FakeRoutineImporter(ImportResult.Success(draft, listOf(warning)))
    private val draftStore = InMemoryDraftStore()
    private val savedState = SavedStateHandle()
    private val inbox = InMemorySharedTextInbox()
    private val sharer = FakeTextSharer()

    private val scenarios = FakeTemplateScenarioStore()
    private val diagnostics = FakeDiagnosticsRecorder()

    private fun viewModel(handle: SavedStateHandle = savedState) =
        NewRoutineViewModel(handle, importer, { template }, clipboard, draftStore, inbox, sharer, scenarios, diagnostics, DecodePlanLinkUseCase())

    /** Each scenario has its own template text. */
    private fun scenarioViewModel() =
        NewRoutineViewModel(savedState, importer, { "Plantilla ${it.name}" }, clipboard, draftStore, inbox, sharer, scenarios, diagnostics, DecodePlanLinkUseCase())

    /** Arguments of `NewRoutineDestination(fromShare = true)`. */
    private fun sharedHandle() = SavedStateHandle(mapOf("fromShare" to true))

    /** Keeps `uiState` subscribed (WhileSubscribed) for the whole test. */
    private fun TestScope.subscribed(vm: NewRoutineViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    @Test
    fun `empieza vacio con Validar deshabilitado`() = runTest { // R-2.1, R-2.4
        val vm = subscribed(viewModel())

        assertThat(vm.uiState.value.text).isEmpty()
        assertThat(vm.uiState.value.canValidate).isFalse()
    }

    @Test
    fun `con texto se puede validar y con solo espacios no`() = runTest { // R-2.4
        val vm = subscribed(viewModel())

        vm.onAction(NewRoutineAction.TextChanged("   "))
        assertThat(vm.uiState.value.canValidate).isFalse()

        vm.onAction(NewRoutineAction.TextChanged("{}"))
        assertThat(vm.uiState.value.canValidate).isTrue()
    }

    @Test
    fun `carga la plantilla para poder verla y empieza plegada`() = runTest { // R-1.3, R-1.4
        val vm = subscribed(viewModel())

        assertThat(vm.uiState.value.template).isEqualTo(template)
        assertThat(vm.uiState.value.templateExpanded).isFalse()

        vm.onAction(NewRoutineAction.ToggleTemplate)
        assertThat(vm.uiState.value.templateExpanded).isTrue()
        vm.onAction(NewRoutineAction.ToggleTemplate)
        assertThat(vm.uiState.value.templateExpanded).isFalse()
    }

    @Test
    fun `copiar plantilla la deja en el portapapeles y emite el efecto de confirmacion`() = runTest { // R-1.2
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(NewRoutineAction.CopyTemplateClicked)

            assertThat(awaitItem()).isEqualTo(NewRoutineEffect.ShowMessage(UiText.Res(R.string.new_routine_template_copied)))
        }
        assertThat(clipboard.text).isEqualTo(template)
    }

    @Test
    fun `pegar reemplaza el texto por el del portapapeles`() = runTest { // R-2.2
        val vm = subscribed(viewModel())
        vm.onAction(NewRoutineAction.TextChanged("viejo"))
        clipboard.text = "{\"routine\":{}}"

        vm.onAction(NewRoutineAction.PasteClicked)

        assertThat(vm.uiState.value.text).isEqualTo("{\"routine\":{}}")
    }

    @Test
    fun `con el portapapeles vacio avisa y no cambia el texto`() = runTest { // R-2.3
        val vm = subscribed(viewModel())
        vm.onAction(NewRoutineAction.TextChanged("mi texto"))
        clipboard.text = null

        vm.effects.test {
            vm.onAction(NewRoutineAction.PasteClicked)

            assertThat(awaitItem()).isEqualTo(NewRoutineEffect.ShowMessage(UiText.Res(R.string.new_routine_clipboard_empty)))
        }
        assertThat(vm.uiState.value.text).isEqualTo("mi texto")
    }

    @Test
    fun `el texto sobrevive a la muerte del proceso`() = runTest { // R-2.5
        val first = subscribed(viewModel())
        first.onAction(NewRoutineAction.TextChanged("{\"a\":1}"))

        val restored = subscribed(viewModel(SavedStateHandle(mapOf(NewRoutineViewModel.KEY_TEXT to savedState.get<String>(NewRoutineViewModel.KEY_TEXT)))))

        assertThat(restored.uiState.value.text).isEqualTo("{\"a\":1}")
    }

    @Test
    fun `validar con errores muestra los mensajes y no navega`() = runTest { // R-3.16
        val issues = listOf(
            ValidationIssue(field = "routine.name", kind = IssueKind.MISSING_FIELD),
            ValidationIssue(itemIndex = 2, itemTime = "25:00", field = "time", kind = IssueKind.INVALID_TIME, received = "25:00"),
        )
        importer.result = ImportResult.Failure(issues)
        val vm = subscribed(viewModel())
        vm.onAction(NewRoutineAction.TextChanged("{}"))

        vm.effects.test {
            vm.onAction(NewRoutineAction.ValidateClicked)
            expectNoEvents()
        }
        assertThat(vm.uiState.value.issues).isEqualTo(ErrorMessages.list(issues))
        assertThat(vm.uiState.value.isValidating).isFalse()
    }

    @Test
    fun `validar bien guarda el borrador con sus avisos y navega con su id`() = runTest { // R-3.19
        val vm = subscribed(viewModel())
        vm.onAction(NewRoutineAction.TextChanged("{}"))

        vm.effects.test {
            vm.onAction(NewRoutineAction.ValidateClicked)

            assertThat(awaitItem()).isEqualTo(NewRoutineEffect.NavigateToReview("draft-1"))
        }
        assertThat(draftStore.get("draft-1")).isEqualTo(DraftEntry(draft, listOf(warning)))
        assertThat(importer.received).containsExactly("{}")
    }

    @Test
    fun `mientras valida muestra el progreso y no deja validar otra vez`() = runTest { // R-3.19
        val gate = CompletableDeferred<Unit>()
        importer.gate = gate
        val vm = subscribed(viewModel())
        vm.onAction(NewRoutineAction.TextChanged("{}"))

        vm.onAction(NewRoutineAction.ValidateClicked)
        assertThat(vm.uiState.value.isValidating).isTrue()
        assertThat(vm.uiState.value.canValidate).isFalse()
        vm.onAction(NewRoutineAction.ValidateClicked)

        gate.complete(Unit)
        assertThat(vm.uiState.value.isValidating).isFalse()
        assertThat(importer.received).hasSize(1)
    }

    @Test
    fun `editar el texto borra los errores anteriores`() = runTest { // R-3.16
        importer.result = ImportResult.Failure(listOf(ValidationIssue(kind = IssueKind.NO_JSON_FOUND)))
        val vm = subscribed(viewModel())
        vm.onAction(NewRoutineAction.TextChanged("hola"))
        vm.onAction(NewRoutineAction.ValidateClicked)
        assertThat(vm.uiState.value.issues).isNotEmpty()

        vm.onAction(NewRoutineAction.TextChanged("hola!"))

        assertThat(vm.uiState.value.issues).isEmpty()
    }

    // ---- Shared text (T-40) ----

    @Test
    fun `un texto compartido valido se pega y abre la revision`() = runTest { // R-14.2, R-14.3
        inbox.put("Aquí tienes: {\"schemaVersion\": 1}")
        val vm = viewModel(sharedHandle())

        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(NewRoutineEffect.NavigateToReview("draft-1"))
        }
        assertThat(subscribed(vm).uiState.value.text).isEqualTo("Aquí tienes: {\"schemaVersion\": 1}")
        assertThat(importer.received).containsExactly("Aquí tienes: {\"schemaVersion\": 1}")
        assertThat(draftStore.get("draft-1")).isNotNull()
    }

    @Test
    fun `un texto compartido con errores los muestra con el texto en el campo`() = runTest { // R-14.3
        val issue = ValidationIssue(kind = IssueKind.NO_JSON_FOUND)
        importer.result = ImportResult.Failure(listOf(issue))
        inbox.put("hola")

        val vm = subscribed(viewModel(sharedHandle()))

        assertThat(vm.uiState.value.text).isEqualTo("hola")
        assertThat(vm.uiState.value.issues).isEqualTo(ErrorMessages.list(listOf(issue)))
    }

    @Test
    fun `un envio sin texto avisa y no cambia el campo`() = runTest { // R-14.4
        val handle = sharedHandle().apply { set(NewRoutineViewModel.KEY_TEXT, "lo que había") }
        inbox.put("   ")
        val vm = viewModel(handle)

        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(NewRoutineEffect.ShowMessage(UiText.Res(R.string.new_routine_shared_empty)))
        }
        assertThat(subscribed(vm).uiState.value.text).isEqualTo("lo que había")
        assertThat(importer.received).isEmpty()
    }

    @Test
    fun `un texto compartido descarta el borrador que estaba en revision`() = runTest { // R-14.5
        draftStore.put(DraftEntry(draft.copy(id = "old"), emptyList()))
        inbox.put("{}")

        subscribed(viewModel(sharedHandle()))

        assertThat(draftStore.get("old")).isNull()
    }

    @Test
    fun `al recrear la pantalla no vuelve a importar el texto compartido`() = runTest { // R-14.2
        inbox.put("{}")
        val handle = sharedHandle()
        subscribed(viewModel(handle))
        importer.received.clear()

        inbox.put("{}")
        subscribed(viewModel(handle))

        assertThat(importer.received).isEmpty()
    }

    @Test
    fun `sin venir de compartir no lee el buzon`() = runTest { // R-14.2
        inbox.put("{}")

        val vm = subscribed(viewModel())

        assertThat(vm.uiState.value.text).isEmpty()
        assertThat(inbox.take()).isEqualTo("{}")
    }

    // ---- "Abrir en IA" (T-41) ----

    @Test
    fun `abrir en IA envia la plantilla al selector de apps`() = runTest { // R-14.7
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(NewRoutineAction.OpenInAiClicked)

            assertThat(awaitItem()).isEqualTo(NewRoutineEffect.ShareTemplate(template))
        }
        assertThat(clipboard.text).isNull()
    }

    @Test
    fun `sin apps que reciban texto copia la plantilla y lo explica`() = runTest { // R-14.8
        sharer.available = false
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(NewRoutineAction.OpenInAiClicked)

            assertThat(awaitItem()).isEqualTo(NewRoutineEffect.ShowMessage(UiText.Res(R.string.new_routine_no_share_apps)))
        }
        assertThat(clipboard.text).isEqualTo(template)
    }

    // ---- Templates per scenario (T-42) ----

    @Test
    fun `la primera vez la plantilla elegida es la generica`() = runTest { // R-16.2
        val vm = subscribed(scenarioViewModel())

        assertThat(vm.uiState.value.scenario).isEqualTo(TemplateScenario.GENERIC)
        assertThat(vm.uiState.value.template).isEqualTo("Plantilla GENERIC")
    }

    @Test
    fun `recuerda el ultimo escenario elegido`() = runTest { // R-16.2
        scenarios.saved = TemplateScenario.MEDICATION

        val vm = subscribed(scenarioViewModel())

        assertThat(vm.uiState.value.scenario).isEqualTo(TemplateScenario.MEDICATION)
        assertThat(vm.uiState.value.template).isEqualTo("Plantilla MEDICATION")
    }

    @Test
    fun `elegir un escenario cambia la plantilla y lo guarda`() = runTest { // R-16.2, R-16.3
        val vm = subscribed(scenarioViewModel())

        vm.onAction(NewRoutineAction.ScenarioSelected(TemplateScenario.BABY))

        assertThat(vm.uiState.value.scenario).isEqualTo(TemplateScenario.BABY)
        assertThat(vm.uiState.value.template).isEqualTo("Plantilla BABY")
        assertThat(scenarios.saved).isEqualTo(TemplateScenario.BABY)
    }

    @Test
    fun `copiar y abrir en IA usan la plantilla del escenario elegido`() = runTest { // R-16.3
        val vm = subscribed(scenarioViewModel())
        vm.onAction(NewRoutineAction.ScenarioSelected(TemplateScenario.STUDY))

        vm.effects.test {
            vm.onAction(NewRoutineAction.CopyTemplateClicked)
            awaitItem()
            vm.onAction(NewRoutineAction.OpenInAiClicked)

            assertThat(awaitItem()).isEqualTo(NewRoutineEffect.ShareTemplate("Plantilla STUDY"))
        }
        assertThat(clipboard.text).isEqualTo("Plantilla STUDY")
    }

    @Test
    fun `cuenta abrir la pantalla validar y cada tipo de error una vez`() = runTest { // R-17.2, D-72
        importer.result = ImportResult.Failure(
            listOf(
                ValidationIssue(field = "routine.name", kind = IssueKind.MISSING_FIELD),
                ValidationIssue(itemIndex = 1, field = "time", kind = IssueKind.MISSING_FIELD),
                ValidationIssue(itemIndex = 2, field = "time", kind = IssueKind.INVALID_TIME, received = "25:00"),
                ValidationIssue(itemIndex = 3, kind = IssueKind.TITLE_TRUNCATED, severity = Severity.WARNING),
            ),
        )
        val vm = subscribed(viewModel())
        vm.onAction(NewRoutineAction.TextChanged("{}"))

        vm.onAction(NewRoutineAction.ValidateClicked)

        assertThat(diagnostics.events).containsExactly(
            DiagnosticEvent.Step.NEW_ROUTINE,
            DiagnosticEvent.Step.VALIDATE,
            DiagnosticEvent.ValidationFailed(IssueKind.MISSING_FIELD),
            DiagnosticEvent.ValidationFailed(IssueKind.INVALID_TIME),
        ).inOrder()
    }

    @Test
    fun `un enlace de plan recibido se decodifica y se valida`() = runTest { // R-19.7
        val json = """{"schemaVersion":1,"routine":{"name":"Gato","date":null,"notes":null},"items":[]}"""
        inbox.put((PlanLink.encode(json) as PlanLink.Encoded.Ok).link)
        val vm = subscribed(viewModel(sharedHandle()))

        assertThat(vm.uiState.value.text).isEqualTo(json)
        assertThat(importer.received).containsExactly(json)
    }

    @Test
    fun `un enlace daniado avisa que no contiene un plan`() = runTest { // R-19.8
        inbox.put("rutinify://import?v=1&d=roto")

        viewModel(sharedHandle()).effects.test {
            assertThat((awaitItem() as NewRoutineEffect.ShowMessage).message.es()).isEqualTo("Este código no contiene un plan de Rutinify")
        }
    }

    @Test
    fun `Escanear QR abre el escaner`() = runTest { // R-19.6
        val vm = viewModel()

        vm.effects.test {
            vm.onAction(NewRoutineAction.ScanQrClicked)
            assertThat(awaitItem()).isEqualTo(NewRoutineEffect.StartQrScan)
        }
    }

    @Test
    fun `un QR de Rutinify se decodifica y se valida`() = runTest { // R-19.7
        val json = """{"schemaVersion":1,"routine":{"name":"Gato","date":null,"notes":null},"items":[]}"""
        val vm = subscribed(viewModel())

        vm.onAction(NewRoutineAction.QrScanned((PlanLink.encode(json) as PlanLink.Encoded.Ok).link))

        assertThat(vm.uiState.value.text).isEqualTo(json)
        assertThat(importer.received).containsExactly(json)
    }

    @Test
    fun `un QR ajeno dice que no contiene un plan`() = runTest { // R-19.8
        val vm = viewModel()

        vm.effects.test {
            vm.onAction(NewRoutineAction.QrScanned("https://example.com"))
            assertThat((awaitItem() as NewRoutineEffect.ShowMessage).message.es()).isEqualTo("Este código no contiene un plan de Rutinify")
        }
        assertThat(importer.received).isEmpty()
    }
}
