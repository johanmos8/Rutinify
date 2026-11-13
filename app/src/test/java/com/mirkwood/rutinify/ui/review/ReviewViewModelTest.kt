package com.mirkwood.rutinify.ui.review

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.data.draft.InMemoryDraftStore
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.Severity
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.repository.DraftEntry
import com.mirkwood.rutinify.domain.time.DefaultDatePolicy
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.usecase.AlarmMutationLock
import com.mirkwood.rutinify.domain.usecase.BuildSchedulePreviewUseCase
import com.mirkwood.rutinify.domain.usecase.FindConflictingOrdersUseCase
import com.mirkwood.rutinify.domain.usecase.ScheduleOrderUseCase
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.MainDispatcherRule
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import com.mirkwood.rutinify.ui.common.ErrorMessages
import com.mirkwood.rutinify.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** T-22: Review screen logic (R-4.1–R-4.12, R-5.6, R-5.7, R-5.10, R-6.1–R-6.4). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class ReviewViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val bogota = ZoneId.of("America/Bogota")
    private val today = LocalDate.of(2026, 11, 5)
    private val now = Instant.parse("2026-11-05T17:00:00Z") // Thursday 12:00 in Bogotá
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val calculator = TriggerTimeCalculator()

    private val orders = FakeOrderRepository(now = now)
    private val alarms = FakeAlarmRepository(clock) { orders.activeDate(it) }.also { orders.alarmRepository = it }
    private val scheduler = FakeAlarmScheduler()
    private val publisher = FakeNotificationPublisher()
    private val lock = AlarmMutationLock()
    private val buildPreview = BuildSchedulePreviewUseCase(calculator)
    private val draftStore = InMemoryDraftStore()

    private val warning = ValidationIssue(itemIndex = 2, itemTitle = "Paseo", kind = IssueKind.TITLE_TRUNCATED, severity = Severity.WARNING)

    /** 06:45 (past at 12:00), 13:00 paseo, 18:00, 22:30 and 02:00 (+1 day). Keys 0..4. */
    private val draft = RoutineDraft(
        id = "draft-1",
        name = "Cachorro",
        date = today,
        notes = "Llevar bolsas",
        items = listOf("06:45" to 0, "13:00" to 0, "18:00" to 0, "22:30" to 0, "02:00" to 1).mapIndexed { i, (t, offset) ->
            DraftItem(i, LocalTime.parse(t), offset, "A$i", if (i == 1) "Con correa" else null, null, if (i == 1) "paseo" else "general", null)
        },
        origin = DraftOrigin.IMPORT,
    )

    private fun viewModel(routine: RoutineDraft? = draft, warnings: List<ValidationIssue> = emptyList()): ReviewViewModel {
        routine?.let { draftStore.put(DraftEntry(it, warnings)) }
        return ReviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("draftId" to "draft-1")),
            draftStore = draftStore,
            defaultDatePolicy = DefaultDatePolicy(calculator),
            buildPreview = buildPreview,
            findConflicts = FindConflictingOrdersUseCase(orders),
            scheduleOrder = ScheduleOrderUseCase(orders, alarms, scheduler, publisher, buildPreview, calculator, { bogota }, clock, lock),
            clock = clock,
            zoneProvider = { bogota },
        )
    }

    private fun TestScope.subscribed(vm: ReviewViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    private val ReviewViewModel.state get() = uiState.value

    private fun utcMillis(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    // --- Loading --------------------------------------------------------------------------------

    @Test
    fun `muestra nombre notas y actividades ordenadas por hora de disparo`() = runTest { // R-4.1, R-4.12
        val vm = subscribed(viewModel())

        assertThat(vm.state.name).isEqualTo("Cachorro")
        assertThat(vm.state.notes).isEqualTo("Llevar bolsas")
        assertThat(vm.state.items.map { it.time }).containsExactly("06:45", "13:00", "18:00", "22:30", "02:00").inOrder()
        assertThat(vm.state.items.last().nextDay).isTrue()
        assertThat(vm.state.items[1].description).isEqualTo("Con correa")
        assertThat(vm.state.items[1].category).isEqualTo("paseo")
    }

    @Test
    fun `usa la fecha del JSON sin aviso`() = runTest { // R-4.4
        val vm = subscribed(viewModel())

        assertThat(vm.state.dateLabel).isEqualTo("jueves 5 de noviembre de 2026")
        assertThat(vm.state.dateMillisUtc).isEqualTo(utcMillis(today))
        assertThat(vm.state.dateNotice).isNull()
    }

    @Test
    fun `sin fecha en el JSON elige una y lo avisa`() = runTest { // R-4.5
        val vm = subscribed(viewModel(draft.copy(date = null)))

        assertThat(vm.state.dateLabel).isEqualTo("jueves 5 de noviembre de 2026")
        assertThat(vm.state.dateNotice).isEqualTo(UiText.Res(R.string.review_date_defaulted, listOf("jueves 5 de noviembre de 2026")))
    }

    @Test
    fun `muestra los avisos de la importacion`() = runTest { // R-3.19
        val vm = subscribed(viewModel(warnings = listOf(warning)))

        assertThat(vm.state.warnings).isEqualTo(ErrorMessages.list(listOf(warning)))
    }

    @Test
    fun `si el borrador ya no existe lo indica`() = runTest { // D-10
        val vm = subscribed(viewModel(routine = null))

        assertThat(vm.state.draftLost).isTrue()
    }

    // --- Past, exclusions, summary -------------------------------------------------------------

    @Test
    fun `las actividades pasadas se marcan y no se incluyen`() = runTest { // R-4.7
        val vm = subscribed(viewModel())

        assertThat(vm.state.items.map { it.isPast }).containsExactly(true, false, false, false, false).inOrder()
        assertThat(vm.state.items.map { it.isIncluded }).containsExactly(false, true, true, true, true).inOrder()
    }

    @Test
    fun `el resumen da el numero y el rango con el dia siguiente`() = runTest { // R-4.9
        val vm = subscribed(viewModel())

        assertThat(vm.state.summary).isEqualTo(
            UiText.Plural(
                R.plurals.review_summary,
                4,
                listOf(4, "13:00", UiText.Res(R.string.review_time_next_day, listOf("02:00"))),
            ),
        )
        assertThat(vm.state.canSchedule).isTrue()
    }

    @Test
    fun `excluir una actividad la deja en la lista y actualiza el resumen`() = runTest { // R-4.8
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ToggleIncluded(4))

        assertThat(vm.state.items).hasSize(5)
        assertThat(vm.state.items.last().isIncluded).isFalse()
        assertThat((vm.state.summary as UiText.Plural).quantity).isEqualTo(3)

        vm.onAction(ReviewAction.ToggleIncluded(4))
        assertThat(vm.state.items.last().isIncluded).isTrue()
    }

    @Test
    fun `una actividad pasada no se puede incluir`() = runTest { // R-4.7
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ToggleIncluded(0))

        assertThat(vm.state.items.first().isIncluded).isFalse()
    }

    @Test
    fun `sin actividades futuras incluidas no se puede programar`() = runTest { // R-4.10
        val vm = subscribed(viewModel())

        listOf(1, 2, 3, 4).forEach { vm.onAction(ReviewAction.ToggleIncluded(it)) }

        assertThat(vm.state.canSchedule).isFalse()
        assertThat(vm.state.summary).isNull()
    }

    @Test
    fun `cambiar la fecha recalcula las pasadas y quita el aviso de fecha elegida`() = runTest { // R-4.6
        val vm = subscribed(viewModel(draft.copy(date = null)))

        vm.onAction(ReviewAction.DateSelected(utcMillis(today.plusDays(1))))

        assertThat(vm.state.dateLabel).isEqualTo("viernes 6 de noviembre de 2026")
        assertThat(vm.state.items.none { it.isPast }).isTrue()
        assertThat(vm.state.dateNotice).isNull()
    }

    @Test
    fun `con fecha pasada y todo pasado sugiere mañana y la aplica`() = runTest { // R-4.11
        val vm = subscribed(viewModel(draft.copy(date = LocalDate.of(2026, 11, 1))))
        assertThat(vm.state.suggestTomorrow).isTrue()

        vm.onAction(ReviewAction.UseTomorrow)

        assertThat(vm.state.dateLabel).isEqualTo("viernes 6 de noviembre de 2026")
        assertThat(vm.state.suggestTomorrow).isFalse()
        assertThat(vm.state.canSchedule).isTrue()
    }

    // --- Remove with undo ----------------------------------------------------------------------

    @Test
    fun `quitar una actividad la saca de la lista y ofrece deshacer`() = runTest { // R-4.3
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(ReviewAction.RemoveItem(2))

            assertThat(awaitItem()).isEqualTo(ReviewEffect.ShowUndo(UiText.Res(R.string.review_item_removed)))
        }
        assertThat(vm.state.items.map { it.key }).containsExactly(0, 1, 3, 4).inOrder()

        vm.onAction(ReviewAction.UndoRemove)

        assertThat(vm.state.items.map { it.key }).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    // --- Editing --------------------------------------------------------------------------------

    @Test
    fun `editar abre el formulario con los valores actuales`() = runTest { // R-4.2
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.EditItem(1))

        assertThat(vm.state.editing).isEqualTo(EditItemUi(key = 1, time = "13:00", title = "A1", category = "paseo"))
    }

    @Test
    fun `una hora no valida no se guarda y muestra el error`() = runTest { // R-4.2, R-3.8
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.EditItem(1))

        vm.onAction(ReviewAction.EditChanged(time = "25:00", title = "A1", category = "paseo"))
        vm.onAction(ReviewAction.SaveEdit)

        assertThat(vm.state.editing?.timeError).isEqualTo(UiText.Res(R.string.review_error_time))
        assertThat(vm.state.items[1].time).isEqualTo("13:00")
    }

    @Test
    fun `un titulo vacio no se guarda`() = runTest { // R-4.2, R-3.9
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.EditItem(1))

        vm.onAction(ReviewAction.EditChanged(time = "13:00", title = "  ", category = "paseo"))
        vm.onAction(ReviewAction.SaveEdit)

        assertThat(vm.state.editing?.titleError).isEqualTo(UiText.Res(R.string.review_error_title_blank))
    }

    @Test
    fun `guardar una edicion valida reordena y normaliza la categoria`() = runTest { // R-4.2, R-3.14, R-4.1
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.EditItem(1))

        vm.onAction(ReviewAction.EditChanged(time = "19:00", title = " Paseo largo ", category = " Paseo Largo "))
        vm.onAction(ReviewAction.SaveEdit)

        assertThat(vm.state.editing).isNull()
        assertThat(vm.state.items.map { it.key }).containsExactly(0, 2, 1, 3, 4).inOrder()
        val edited = vm.state.items[2]
        assertThat(edited.time).isEqualTo("19:00")
        assertThat(edited.title).isEqualTo("Paseo largo")
        assertThat(edited.category).isEqualTo("paseo-largo")
    }

    @Test
    fun `un titulo largo se recorta y se avisa`() = runTest { // R-4.2, R-3.10
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.EditItem(1))
        vm.onAction(ReviewAction.EditChanged(time = "13:00", title = "x".repeat(90), category = "paseo"))

        vm.effects.test {
            vm.onAction(ReviewAction.SaveEdit)

            assertThat(awaitItem()).isEqualTo(ReviewEffect.ShowMessage(UiText.Res(R.string.review_title_truncated, listOf(80))))
        }
        assertThat(vm.state.items[1].title).hasLength(80)
    }

    @Test
    fun `una categoria demasiado larga no se guarda y renombrar una categoria unica si`() = runTest { // R-3.21, R-3.22
        val tenCategories = draft.copy(
            items = (0 until 10).map { DraftItem(it, LocalTime.of(13, it), 0, "A$it", null, null, "c$it", null) },
        )
        val vm = subscribed(viewModel(tenCategories))
        vm.onAction(ReviewAction.EditItem(0))

        vm.onAction(ReviewAction.EditChanged(time = "13:00", title = "A0", category = "x".repeat(31)))
        vm.onAction(ReviewAction.SaveEdit)
        assertThat(vm.state.editing?.categoryError).isEqualTo(UiText.Res(R.string.review_error_category_long, listOf(30)))

        // Item 0 is the only "c0": renaming it keeps 10 distinct categories, which is allowed
        vm.onAction(ReviewAction.EditChanged(time = "13:00", title = "A0", category = "nueva"))
        vm.onAction(ReviewAction.SaveEdit)
        assertThat(vm.state.editing).isNull()
    }

    @Test
    fun `una edicion que pasa de 10 categorias se rechaza`() = runTest { // R-3.22
        val tenCategories = draft.copy(
            items = (0 until 11).map { DraftItem(it, LocalTime.of(13, it), 0, "A$it", null, null, "c${minOf(it, 9)}", null) },
        )
        val vm = subscribed(viewModel(tenCategories))
        vm.onAction(ReviewAction.EditItem(10)) // shares "c9" with item 9

        vm.onAction(ReviewAction.EditChanged(time = "13:10", title = "A10", category = "otra"))
        vm.onAction(ReviewAction.SaveEdit)

        assertThat(vm.state.editing?.categoryError).isEqualTo(UiText.Res(R.string.review_error_too_many_categories, listOf(10)))
    }

    @Test
    fun `cerrar el formulario descarta los cambios`() = runTest { // R-4.2
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.EditItem(1))
        vm.onAction(ReviewAction.EditChanged(time = "19:00", title = "Otro", category = "x"))

        vm.onAction(ReviewAction.DismissEdit)

        assertThat(vm.state.editing).isNull()
        assertThat(vm.state.items[1].title).isEqualTo("A1")
    }

    // --- Scheduling -----------------------------------------------------------------------------

    @Test
    fun `sin conflicto programa y navega al detalle con el numero de alarmas`() = runTest { // R-5.1, R-5.6
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(ReviewAction.ScheduleClicked)

            val effect = awaitItem() as ReviewEffect.NavigateToOrder
            assertThat(effect.scheduledCount).isEqualTo(4)
            assertThat(orders.orders.getValue(effect.orderId).name).isEqualTo("Cachorro")
        }
        assertThat(scheduler.scheduled).hasSize(4)
        assertThat(draftStore.get("draft-1")).isNull()
    }

    @Test
    fun `programa el borrador editado con sus exclusiones`() = runTest { // R-4.2, R-4.3, R-4.8
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.RemoveItem(2))
        vm.onAction(ReviewAction.ToggleIncluded(4))

        vm.onAction(ReviewAction.ScheduleClicked)

        val saved = alarms.alarms.values
        assertThat(saved.map { it.position }).containsExactly(0, 1, 3, 4)
        assertThat(saved.filter { it.status == AlarmStatus.PENDING }.map { it.position }).containsExactly(1, 3)
    }

    @Test
    fun `con una orden el mismo dia pregunta antes de programar`() = runTest { // R-6.1, R-6.5
        orders.add(testOrder(id = 1L, name = "Gato", date = today), testOrder(id = 2L, name = "Perro", date = today, status = OrderStatus.PAUSED))
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ScheduleClicked)

        assertThat(vm.state.dialog).isEqualTo(ReviewDialog.Conflict(listOf("Gato", "Perro")))
        assertThat(orders.createCalls).isEqualTo(0)
    }

    @Test
    fun `Reemplazar cancela la anterior y programa la nueva`() = runTest { // R-6.2
        orders.add(testOrder(id = 1L, name = "Gato", date = today))
        alarms.add(testAlarm(id = 10L, orderId = 1L, time = LocalTime.of(15, 0)))
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.ScheduleClicked)

        vm.effects.test {
            vm.onAction(ReviewAction.ConflictReplace)

            assertThat(awaitItem()).isInstanceOf(ReviewEffect.NavigateToOrder::class.java)
        }
        assertThat(orders.orders.getValue(1L).status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(vm.state.dialog).isNull()
    }

    @Test
    fun `Mantener ambas programa sin tocar la anterior`() = runTest { // R-6.3
        orders.add(testOrder(id = 1L, name = "Gato", date = today))
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.ScheduleClicked)

        vm.onAction(ReviewAction.ConflictKeepBoth)

        assertThat(orders.orders.getValue(1L).status).isEqualTo(OrderStatus.ACTIVE)
        assertThat(orders.createCalls).isEqualTo(1)
    }

    @Test
    fun `Cancelar vuelve a la revision sin cambios`() = runTest { // R-6.4
        orders.add(testOrder(id = 1L, name = "Gato", date = today))
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.ScheduleClicked)

        vm.onAction(ReviewAction.DismissDialog)

        assertThat(vm.state.dialog).isNull()
        assertThat(orders.createCalls).isEqualTo(0)
        assertThat(draftStore.get("draft-1")).isNotNull()
    }

    @Test
    fun `si se superarian 500 alarmas lo explica y no navega`() = runTest { // R-5.10
        orders.add(testOrder(id = 1L, date = today.plusDays(3)))
        alarms.add(*(1L..498L).map { testAlarm(id = it, orderId = 1L) }.toTypedArray())
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(ReviewAction.ScheduleClicked)

            assertThat(awaitItem()).isEqualTo(
                ReviewEffect.ShowMessage(UiText.Plural(R.plurals.review_too_many_alarms, 4, listOf(4, 498))),
            )
        }
    }

    @Test
    fun `si falla Room ofrece reintentar y el reintento programa`() = runTest { // R-5.7
        orders.failCreate = true
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(ReviewAction.ScheduleClicked)
            assertThat(awaitItem()).isEqualTo(ReviewEffect.ShowRetry(UiText.Res(R.string.review_storage_failure)))
            assertThat(scheduler.scheduled).isEmpty()

            orders.failCreate = false
            vm.onAction(ReviewAction.RetrySchedule)
            assertThat(awaitItem()).isInstanceOf(ReviewEffect.NavigateToOrder::class.java)
        }
    }
}
