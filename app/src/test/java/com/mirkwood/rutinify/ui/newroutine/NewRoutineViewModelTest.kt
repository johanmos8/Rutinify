package com.mirkwood.rutinify.ui.newroutine

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.data.draft.InMemoryDraftStore
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.Severity
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.repository.DraftEntry
import com.mirkwood.rutinify.testutil.FakeClipboard
import com.mirkwood.rutinify.testutil.FakeRoutineImporter
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

/** T-21: New routine screen logic (R-1.1, R-1.2, R-1.4, R-2.1–R-2.5, R-3.19). */
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

    private fun viewModel(handle: SavedStateHandle = savedState) =
        NewRoutineViewModel(handle, importer, { template }, clipboard, draftStore)

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
}
