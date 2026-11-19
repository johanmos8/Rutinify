package com.mirkwood.rutinify.ui.review

import com.mirkwood.rutinify.ui.common.RecurrenceLabels
import com.mirkwood.rutinify.domain.model.Recurrence
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import com.mirkwood.rutinify.testutil.FakeDiagnosticsRecorder
import com.mirkwood.rutinify.testutil.es
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
import com.mirkwood.rutinify.domain.usecase.HandleAlarmFiredUseCase
import com.mirkwood.rutinify.domain.usecase.RescheduleAllUseCase
import com.mirkwood.rutinify.domain.usecase.SchedulingPermissionsUseCase
import com.mirkwood.rutinify.domain.usecase.ScheduleOrderUseCase
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.FakePermissionChecker
import com.mirkwood.rutinify.testutil.FakePermissionPromptStore
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
    private val categories = FakeCategorySettingsRepository()
    private val diagnostics = FakeDiagnosticsRecorder()
    private val publisher = FakeNotificationPublisher()
    private val lock = AlarmMutationLock()
    private val buildPreview = BuildSchedulePreviewUseCase(calculator)
    private val draftStore = InMemoryDraftStore()
    private val checker = FakePermissionChecker()
    private val prompts = FakePermissionPromptStore()

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
            scheduleOrder = ScheduleOrderUseCase(orders, alarms, scheduler, categories, publisher, buildPreview, calculator, { bogota }, clock, lock, diagnostics, { 0 }),
            permissions = SchedulingPermissionsUseCase(checker, prompts),
            rescheduleAll = RescheduleAllUseCase(
                alarms = alarms,
                scheduler = scheduler,
                categories = categories,
                handleFired = HandleAlarmFiredUseCase(alarms, orders, publisher, categories, scheduler, clock, calculator, { bogota }, lock),
                materializer = { 0 },
                calculator = calculator,
                zoneProvider = { bogota },
                clock = clock,
                lock = lock,
            ),
            clock = clock,
            zoneProvider = { bogota },
            diagnostics = diagnostics,
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

        assertThat(vm.state.dateLabel.es()).isEqualTo("jueves 5 de noviembre de 2026")
        assertThat(vm.state.dateMillisUtc).isEqualTo(utcMillis(today))
        assertThat(vm.state.dateNotice).isNull()
    }

    @Test
    fun `sin fecha en el JSON elige una y lo avisa`() = runTest { // R-4.5
        val vm = subscribed(viewModel(draft.copy(date = null)))

        assertThat(vm.state.dateLabel.es()).isEqualTo("jueves 5 de noviembre de 2026")
        assertThat(vm.state.dateNotice?.es()).isEqualTo("No venía fecha; elegí jueves 5 de noviembre de 2026")
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

    // --- Multi-day routines (T-35) ----------------------------------------------------------------

    /** Antibiotic 06:00 / 14:00 / 22:00 for 3 days from [today] (Thursday). Keys 0..8; 06:00 of day 1 is past. */
    private val treatment = draft.copy(
        name = "Antibiótico",
        notes = null,
        items = (0..2).flatMap { day -> listOf("06:00", "14:00", "22:00").map { day to it } }.mapIndexed { i, (day, t) ->
            DraftItem(i, LocalTime.parse(t), day, "Dosis", null, null, "salud", null)
        },
    )

    private fun dayHeader(day: Int, date: String) = "Día $day · $date"

    @Test
    fun `una rutina de un dia no tiene cabeceras y conserva el sufijo del dia siguiente`() = runTest { // R-4.13
        val vm = subscribed(viewModel())

        assertThat(vm.state.days).isEmpty()
        assertThat(vm.state.items.map { it.nextDay }).containsExactly(false, false, false, false, true).inOrder()
    }

    @Test
    fun `una rutina de varios dias agrupa por dia sin sufijo en las horas`() = runTest { // R-4.13
        val vm = subscribed(viewModel(treatment))

        assertThat(vm.state.days.map { it.title.es() }).containsExactly(
            dayHeader(1, "jueves 5 de noviembre"),
            dayHeader(2, "viernes 6 de noviembre"),
            dayHeader(3, "sábado 7 de noviembre"),
        ).inOrder()
        assertThat(vm.state.days.map { day -> day.items.map { it.key } })
            .containsExactly(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8)).inOrder()
        assertThat(vm.state.days.flatMap { it.items }.none { it.nextDay }).isTrue()
        assertThat(vm.state.days.first().items.first().isPast).isTrue()
    }

    @Test
    fun `el resumen de varios dias da alarmas dias y rango con dia de la semana`() = runTest { // R-4.14
        val vm = subscribed(viewModel(treatment))

        assertThat(vm.state.summary?.es()).isEqualTo("8 alarmas en 3 días, del jueves 5 al sábado 7 de noviembre")
    }

    @Test
    fun `si solo queda un dia con alarmas el resumen nombra ese dia`() = runTest { // R-4.14, D-59
        val vm = subscribed(viewModel(treatment))

        listOf(1, 2, 3, 4, 5).forEach { vm.onAction(ReviewAction.ToggleIncluded(it)) }

        assertThat(vm.state.summary?.es()).isEqualTo("3 alarmas en 1 día, el sábado 7 de noviembre")
    }

    @Test
    fun `cambiar la fecha desplaza las cabeceras y el resumen`() = runTest { // R-4.15
        val vm = subscribed(viewModel(treatment))

        vm.onAction(ReviewAction.DateSelected(utcMillis(LocalDate.of(2026, 11, 30))))

        assertThat(vm.state.days.map { it.title.es() }).containsExactly(
            dayHeader(1, "lunes 30 de noviembre"),
            dayHeader(2, "martes 1 de diciembre"),
            dayHeader(3, "miércoles 2 de diciembre"),
        ).inOrder()
        assertThat(vm.state.summary?.es()).isEqualTo("9 alarmas en 3 días, del lunes 30 de noviembre al miércoles 2 de diciembre")
    }

    @Test
    fun `quitar o excluir la toma de un dia no cambia las de otros dias`() = runTest { // R-4.16
        val vm = subscribed(viewModel(treatment))

        vm.onAction(ReviewAction.RemoveItem(4))
        vm.onAction(ReviewAction.ToggleIncluded(7))

        assertThat(vm.state.days.map { day -> day.items.map { it.key } })
            .containsExactly(listOf(0, 1, 2), listOf(3, 5), listOf(6, 7, 8)).inOrder()
        assertThat(vm.state.days[2].items.map { it.isIncluded }).containsExactly(true, false, true).inOrder()
        assertThat(vm.state.days[1].items.all { it.isIncluded }).isTrue()
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

        assertThat(vm.state.dateLabel.es()).isEqualTo("viernes 6 de noviembre de 2026")
        assertThat(vm.state.items.none { it.isPast }).isTrue()
        assertThat(vm.state.dateNotice).isNull()
    }

    @Test
    fun `con fecha pasada y todo pasado sugiere mañana y la aplica`() = runTest { // R-4.11
        val vm = subscribed(viewModel(draft.copy(date = LocalDate.of(2026, 11, 1))))
        assertThat(vm.state.suggestTomorrow).isTrue()

        vm.onAction(ReviewAction.UseTomorrow)

        assertThat(vm.state.dateLabel.es()).isEqualTo("viernes 6 de noviembre de 2026")
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

    /** A single-day order on [today] as the conflict dialog shows it. */
    private fun sameDay(name: String) = ConflictOrderUi(name, UiText.Date(today, R.string.date_pattern_short))

    @Test
    fun `un tratamiento que sigue en esa fecha muestra su rango y el aviso de reemplazo`() = runTest { // R-6.6, R-6.7
        orders.add(testOrder(id = 1L, name = "Antibiótico", date = today.minusDays(2)))
        alarms.add(testAlarm(id = 10L, orderId = 1L, dayOffset = 6))
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ScheduleClicked)

        assertThat(vm.state.dialog).isEqualTo(
            ReviewDialog.Conflict(
                listOf(
                    ConflictOrderUi(
                        "Antibiótico",
                        UiText.Res(
                            R.string.date_range,
                            listOf(
                                UiText.Date(today.minusDays(2), R.string.date_pattern_start_same_month),
                                UiText.Date(today.plusDays(4), R.string.date_pattern_end_same_month),
                            ),
                        ),
                    ),
                ),
                showsRanges = true,
            ),
        )
    }

    @Test
    fun `un borrador de varios dias muestra rangos aunque la otra orden sea de un dia`() = runTest { // R-6.7
        orders.add(testOrder(id = 1L, name = "Gato", date = today.plusDays(3)))
        val treatment = draft.copy(items = draft.items + draft.items.first().copy(key = 9, dayOffset = 6))
        val vm = subscribed(viewModel(treatment))

        vm.onAction(ReviewAction.ScheduleClicked)

        assertThat(vm.state.dialog).isEqualTo(
            ReviewDialog.Conflict(listOf(ConflictOrderUi("Gato", UiText.Date(today.plusDays(3), R.string.date_pattern_short))), showsRanges = true),
        )
    }

    @Test
    fun `con una orden el mismo dia pregunta antes de programar`() = runTest { // R-6.1, R-6.5
        orders.add(testOrder(id = 1L, name = "Gato", date = today), testOrder(id = 2L, name = "Perro", date = today, status = OrderStatus.PAUSED))
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ScheduleClicked)

        assertThat(vm.state.dialog).isEqualTo(ReviewDialog.Conflict(listOf(sameDay("Gato"), sameDay("Perro"))))
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

    // --- Permission steps before scheduling (T-30) ----------------------------------------------

    @Test
    fun `la primera vez explica las notificaciones antes de programar`() = runTest { // R-11.1
        checker.notificationsRequestable = true
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ScheduleClicked)

        assertThat(vm.state.dialog).isEqualTo(ReviewDialog.NotificationsRationale)
        assertThat(orders.createCalls).isEqualTo(0)
    }

    @Test
    fun `aceptar la explicacion pide el permiso y al responder sigue programando`() = runTest { // R-11.1
        checker.notificationsRequestable = true
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.ScheduleClicked)

        vm.effects.test {
            vm.onAction(ReviewAction.NotificationsRationaleAccepted)
            assertThat(awaitItem()).isEqualTo(ReviewEffect.RequestNotificationPermission)
            assertThat(vm.state.dialog).isNull()
            assertThat(prompts.explained).isTrue()

            vm.onAction(ReviewAction.NotificationPermissionResult)
            assertThat(awaitItem()).isInstanceOf(ReviewEffect.NavigateToOrder::class.java)
        }
    }

    @Test
    fun `ahora no salta el permiso de notificaciones y programa igual`() = runTest { // R-11.1, R-7.8
        checker.notificationsRequestable = true
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.ScheduleClicked)

        vm.effects.test {
            vm.onAction(ReviewAction.NotificationsRationaleSkipped)

            assertThat(awaitItem()).isInstanceOf(ReviewEffect.NavigateToOrder::class.java)
        }
        assertThat(prompts.explained).isTrue()
    }

    @Test
    fun `ya explicadas no vuelve a preguntar por las notificaciones`() = runTest { // R-11.1
        checker.notificationsRequestable = true
        prompts.explained = true
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(ReviewAction.ScheduleClicked)

            assertThat(awaitItem()).isInstanceOf(ReviewEffect.NavigateToOrder::class.java)
        }
    }

    @Test
    fun `sin alarmas exactas lo explica antes de programar`() = runTest { // R-11.2
        checker.exactAlarms = false
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ScheduleClicked)

        assertThat(vm.state.dialog).isEqualTo(ReviewDialog.ExactAlarms)
        assertThat(orders.createCalls).isEqualTo(0)
    }

    @Test
    fun `continuar sin exactas programa igual`() = runTest { // R-11.2
        checker.exactAlarms = false
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.ScheduleClicked)

        vm.effects.test {
            vm.onAction(ReviewAction.ExactAlarmsContinueWithout)

            assertThat(awaitItem()).isInstanceOf(ReviewEffect.NavigateToOrder::class.java)
        }
    }

    @Test
    fun `abrir ajustes espera a volver y entonces sigue`() = runTest { // R-11.2
        checker.exactAlarms = false
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.ScheduleClicked)

        vm.effects.test {
            vm.onAction(ReviewAction.ExactAlarmsOpenSettings)
            assertThat(awaitItem()).isEqualTo(ReviewEffect.OpenExactAlarmSettings)
            assertThat(vm.state.dialog).isNull()
            assertThat(orders.createCalls).isEqualTo(0)

            checker.exactAlarms = true
            vm.onAction(ReviewAction.Resumed)

            assertThat(awaitItem()).isInstanceOf(ReviewEffect.NavigateToOrder::class.java)
        }
    }

    @Test
    fun `volver con el permiso exacto concedido reprograma las demas ordenes`() = runTest { // R-11.6
        orders.add(testOrder(id = 7, date = today.plusDays(3)))
        alarms.add(testAlarm(id = 70, orderId = 7, time = LocalTime.of(9, 0)))
        checker.exactAlarms = false
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.ScheduleClicked)
        vm.onAction(ReviewAction.ExactAlarmsOpenSettings)

        checker.exactAlarms = true
        vm.onAction(ReviewAction.Resumed)

        assertThat(scheduler.scheduled).containsKey(70L)
    }

    @Test
    fun `volver a primer plano sin haber abierto ajustes no programa`() = runTest { // R-11.2
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.Resumed)

        assertThat(orders.createCalls).isEqualTo(0)
    }

    @Test
    fun `cerrar el dialogo de permisos cancela la programacion`() = runTest { // R-11.2
        checker.exactAlarms = false
        val vm = subscribed(viewModel())
        vm.onAction(ReviewAction.ScheduleClicked)

        vm.onAction(ReviewAction.DismissDialog)

        assertThat(vm.state.dialog).isNull()
        assertThat(orders.createCalls).isEqualTo(0)
    }

    @Test
    fun `los pasos van en orden notificaciones exactas conflicto y programar`() = runTest { // R-11.1, R-11.2, R-6.1
        checker.notificationsRequestable = true
        checker.exactAlarms = false
        orders.add(testOrder(id = 7, date = today))
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ScheduleClicked)
        assertThat(vm.state.dialog).isEqualTo(ReviewDialog.NotificationsRationale)

        vm.onAction(ReviewAction.NotificationsRationaleSkipped)
        assertThat(vm.state.dialog).isEqualTo(ReviewDialog.ExactAlarms)

        vm.onAction(ReviewAction.ExactAlarmsContinueWithout)
        assertThat(vm.state.dialog).isEqualTo(ReviewDialog.Conflict(listOf(sameDay("Cachorro"))))

        vm.effects.test {
            vm.onAction(ReviewAction.ConflictKeepBoth)
            assertThat(awaitItem()).isInstanceOf(ReviewEffect.NavigateToOrder::class.java)
        }
    }

    @Test
    fun `cuenta abrir Revision y pulsar Programar alarmas`() = runTest { // R-17.2, D-72
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ScheduleClicked)

        assertThat(diagnostics.events.take(2)).containsExactly(DiagnosticEvent.Step.REVIEW, DiagnosticEvent.Step.SCHEDULE_TAP).inOrder()
    }

    @Test
    fun `sin borrador no cuenta Revision`() = runTest { // R-17.2
        subscribed(viewModel(routine = null))

        assertThat(diagnostics.events).isEmpty()
    }

    // ---- Filter by person (T-60) ----

    private val shared = draft.copy(
        items = listOf("13:00" to "Ana", "14:00" to "Luis", "15:00" to null, "16:00" to "Ana").mapIndexed { i, (t, who) ->
            DraftItem(i, LocalTime.parse(t), 0, "A$i", null, null, "general", who)
        },
    )

    @Test
    fun `sin responsables no hay filtro`() = runTest { // R-19.9
        assertThat(subscribed(viewModel()).state.assignees).isEmpty()
    }

    @Test
    fun `ofrece cada responsable distinto y Todas`() = runTest { // R-19.9
        val vm = subscribed(viewModel(shared))

        assertThat(vm.state.assignees).containsExactly("Ana", "Luis").inOrder()
        assertThat(vm.state.assigneeFilter).isNull()
        assertThat(vm.state.items.map { it.key }).containsExactly(0, 1, 2, 3)
    }

    @Test
    fun `elegir a alguien quita las de otros y deja las de todos`() = runTest { // R-19.9, D-76
        val vm = subscribed(viewModel(shared))

        vm.onAction(ReviewAction.AssigneeFilterChanged("Ana"))

        assertThat(vm.state.items.map { it.key }).containsExactly(0, 2, 3).inOrder()
        assertThat(vm.state.assigneeFilter).isEqualTo("Ana")
    }

    @Test
    fun `Todas vuelve a mostrarlas`() = runTest { // R-19.9
        val vm = subscribed(viewModel(shared))
        vm.onAction(ReviewAction.AssigneeFilterChanged("Luis"))

        vm.onAction(ReviewAction.AssigneeFilterChanged(null))

        assertThat(vm.state.items.map { it.key }).containsExactly(0, 1, 2, 3)
    }

    @Test
    fun `al programar solo entran las tareas elegidas, no como omitidas`() = runTest { // R-19.9, D-76
        val vm = subscribed(viewModel(shared))
        vm.onAction(ReviewAction.AssigneeFilterChanged("Luis"))

        vm.onAction(ReviewAction.ScheduleClicked)

        assertThat(alarms.alarms.values.map { it.title }).containsExactly("A1", "A2")
    }

    // ---- Recurring plans (T-68) ----

    @Test
    fun `un plan recurrente muestra el dia tipo la recurrencia y alarmas por semana`() = runTest { // R-23.14
        val weekly = draft.copy(recurrence = Recurrence(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY), until = null))
        val vm = subscribed(viewModel(weekly))

        assertThat(vm.state.items.none { it.isPast }).isTrue()
        assertThat(vm.state.days).isEmpty()
        assertThat(vm.state.summary?.es()).isEqualTo("10 alarmas por semana")
        assertThat(vm.state.recurrenceLabel?.es()).isEqualTo("Se repite lunes y jueves, desde el 5 de noviembre")
        assertThat(vm.state.canSchedule).isTrue()

        vm.onAction(ReviewAction.ToggleIncluded(0))
        assertThat(vm.state.summary?.es()).isEqualTo("8 alarmas por semana")
    }

    @Test
    fun `un plan recurrente en conflicto se describe con sus dias de la semana`() = runTest { // R-23.9, R-23.10, T-69
        val thursdays = Recurrence(setOf(today.dayOfWeek), until = null)
        orders.add(testOrder(id = 1L, name = "Gimnasio", date = today.minusDays(7), recurrence = thursdays))
        val vm = subscribed(viewModel())

        vm.onAction(ReviewAction.ScheduleClicked)

        val dialog = vm.state.dialog as ReviewDialog.Conflict
        assertThat(dialog.showsRanges).isTrue()
        assertThat(dialog.orders.single().name).isEqualTo("Gimnasio")
        assertThat(dialog.orders.single().range).isEqualTo(RecurrenceLabels.label(thursdays, today.minusDays(7)))
        assertThat(orders.createCalls).isEqualTo(0)
    }
}
