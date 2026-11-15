package com.mirkwood.rutinify.ui.orderdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.data.draft.InMemoryDraftStore
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.usecase.AlarmMutationLock
import com.mirkwood.rutinify.domain.usecase.CanScheduleExactAlarmsUseCase
import com.mirkwood.rutinify.domain.usecase.CancelOrderAlarmsUseCase
import com.mirkwood.rutinify.domain.usecase.DeleteAlarmUseCase
import com.mirkwood.rutinify.domain.usecase.DeleteOrderUseCase
import com.mirkwood.rutinify.domain.usecase.DuplicateOrderUseCase
import com.mirkwood.rutinify.domain.usecase.MarkAlarmDoneUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveOrderDetailUseCase
import com.mirkwood.rutinify.domain.usecase.PauseOrderUseCase
import com.mirkwood.rutinify.domain.usecase.RestoreAlarmUseCase
import com.mirkwood.rutinify.domain.usecase.ResumeOrderUseCase
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.MainDispatcherRule
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
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

/** T-24: order detail logic (R-9.1, R-9.2, R-7.6, R-5.4, R-5.6). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class OrderDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val zone = ZoneId.of("America/Bogota")
    // 2026-11-05 07:00 in Bogotá
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), zone)
    private val alarms = FakeAlarmRepository(clock)
    private val orders = FakeOrderRepository(alarms)
    private val scheduler = FakeAlarmScheduler(exact = true)
    private val publisher = FakeNotificationPublisher()
    private val lock = AlarmMutationLock()
    private val drafts = InMemoryDraftStore()

    private fun handle(highlightAlarmId: Long? = null, scheduledCount: Int? = null) = SavedStateHandle(
        buildMap {
            put("orderId", 1L)
            highlightAlarmId?.let { put("highlightAlarmId", it) }
            scheduledCount?.let { put("scheduledCount", it) }
        },
    )

    private fun viewModel(handle: SavedStateHandle = handle()) = OrderDetailViewModel(
        savedStateHandle = handle,
        observeOrderDetail = ObserveOrderDetailUseCase(orders, TriggerTimeCalculator()) { zone },
        canScheduleExactAlarms = CanScheduleExactAlarmsUseCase(scheduler),
        markAlarmDone = MarkAlarmDoneUseCase(alarms, scheduler, publisher, lock),
        deleteAlarm = DeleteAlarmUseCase(alarms, scheduler, publisher, lock),
        restoreAlarm = RestoreAlarmUseCase(alarms, orders, scheduler, TriggerTimeCalculator(), { zone }, clock, lock),
        cancelOrderAlarms = CancelOrderAlarmsUseCase(orders, scheduler, publisher, lock),
        deleteOrder = DeleteOrderUseCase(orders, alarms, scheduler, publisher, lock),
        pauseOrder = PauseOrderUseCase(orders, alarms, scheduler, lock),
        resumeOrder = ResumeOrderUseCase(orders, alarms, scheduler, TriggerTimeCalculator(), { zone }, clock, lock),
        duplicateOrder = DuplicateOrderUseCase(orders, drafts),
        zoneProvider = { zone },
        clock = clock,
    )

    /** Keeps `uiState` subscribed (WhileSubscribed) for the whole test. */
    private fun TestScope.subscribed(vm: OrderDetailViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    private fun setOrder(status: OrderStatus, vararg alarms: AlarmItem) {
        orders.detail(1).value = OrderWithAlarms(testOrder(id = 1, name = "Cachorro", status = status), alarms.toList())
    }

    private val defaultAlarms = arrayOf(
        testAlarm(id = 1, time = LocalTime.of(6, 45), title = "Desayuno", status = AlarmStatus.DONE),
        testAlarm(id = 2, time = LocalTime.of(13, 0), title = "Paseo", description = null),
        testAlarm(id = 3, time = LocalTime.of(2, 0), dayOffset = 1, title = "Salida nocturna"),
    )

    @Test
    fun `mientras carga no muestra nada`() {
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isTrue()
        assertThat(state.notFound).isFalse()
    }

    @Test
    fun `la cabecera muestra nombre, fecha, estado y pendientes`() = runTest { // R-9.1, R-8.2
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)

        val state = subscribed(viewModel()).uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.name).isEqualTo("Cachorro")
        assertThat(state.dateLabel).isEqualTo(UiText.Plain("jueves 5 de noviembre de 2026"))
        assertThat(state.status).isEqualTo(OrderDisplayStatus.ACTIVE)
        assertThat(state.pending).isEqualTo(2)
        assertThat(state.total).isEqualTo(3)
    }

    @Test
    fun `la linea de tiempo muestra hora, mas un dia y estado de cada alarma`() = runTest { // R-9.1
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)

        val timeline = subscribed(viewModel()).uiState.value.timeline

        assertThat(timeline.map { Triple(it.time, it.nextDay, it.status) }).containsExactly(
            Triple("06:45", false, AlarmStatus.DONE),
            Triple("13:00", false, AlarmStatus.PENDING),
            Triple("02:00", true, AlarmStatus.PENDING),
        ).inOrder()
        assertThat(timeline[0].title).isEqualTo("Desayuno")
        assertThat(timeline[0].description).isEqualTo("En su cama")
        assertThat(timeline[0].category).isEqualTo("siesta")
        assertThat(timeline[1].description).isNull()
    }

    /** Antibiotic at 08:00 and 20:00 for 3 days from Thursday 5 November. */
    private val treatmentAlarms = (0..2).flatMap { day ->
        listOf(8, 20).mapIndexed { i, hour -> testAlarm(id = 100L + day * 2 + i, time = LocalTime.of(hour, 0), dayOffset = day, title = "Dosis") }
    }.toTypedArray()

    @Test
    fun `una orden de un dia no tiene cabeceras de dia`() = runTest { // R-9.6
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)

        assertThat(subscribed(viewModel()).uiState.value.timeline.map { it.dayTitle }).containsExactly(null, null, null)
    }

    @Test
    fun `una orden de varios dias agrupa por dia y muestra su rango`() = runTest { // R-9.6, R-8.2
        setOrder(OrderStatus.ACTIVE, *treatmentAlarms)

        val state = subscribed(viewModel()).uiState.value

        assertThat(state.dateLabel).isEqualTo(UiText.Res(R.string.date_range, listOf("jueves 5", "sábado 7 de noviembre de 2026")))
        assertThat(state.timeline.map { it.dayTitle }).containsExactly(
            UiText.Res(R.string.review_day_header, listOf(1, "jueves 5 de noviembre")),
            null,
            UiText.Res(R.string.review_day_header, listOf(2, "viernes 6 de noviembre")),
            null,
            UiText.Res(R.string.review_day_header, listOf(3, "sábado 7 de noviembre")),
            null,
        ).inOrder()
        assertThat(state.timeline.none { it.nextDay }).isTrue()
    }

    @Test
    fun `una alarma pospuesta muestra su nueva hora`() = runTest { // R-7.5, D-12
        setOrder(OrderStatus.ACTIVE, testAlarm(id = 1, time = LocalTime.of(7, 30), snoozedUntil = Instant.parse("2026-11-05T12:35:00Z")))

        val item = subscribed(viewModel()).uiState.value.timeline.single()

        assertThat(item.time).isEqualTo("07:35")
    }

    @Test
    fun `destaca la proxima alarma pendiente y se desplaza hasta ella`() = runTest { // R-9.2
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)

        val state = subscribed(viewModel()).uiState.value

        assertThat(state.timeline.map { it.isNext }).containsExactly(false, true, false).inOrder()
        assertThat(state.scrollToIndex).isEqualTo(1)
    }

    @Test
    fun `resalta la alarma que llega desde la notificacion y se desplaza hasta ella`() = runTest { // R-7.6
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)

        val state = subscribed(viewModel(handle(highlightAlarmId = 3))).uiState.value

        assertThat(state.timeline.map { it.isHighlighted }).containsExactly(false, false, true).inOrder()
        assertThat(state.scrollToIndex).isEqualTo(2)
    }

    @Test
    fun `ignora una alarma resaltada que ya no existe`() = runTest { // R-7.6: deleted after the notification
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)

        val state = subscribed(viewModel(handle(highlightAlarmId = 99))).uiState.value

        assertThat(state.timeline.none { it.isHighlighted }).isTrue()
        assertThat(state.scrollToIndex).isEqualTo(1)
    }

    @Test
    fun `una orden que no existe muestra el aviso de orden no encontrada`() = runTest {
        val state = subscribed(viewModel()).uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.notFound).isTrue()
    }

    @Test
    fun `sin permiso de alarmas exactas avisa de que pueden retrasarse`() = runTest { // R-5.4
        scheduler.exact = false
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)

        assertThat(subscribed(viewModel()).uiState.value.windowedWarning).isTrue()
    }

    @Test
    fun `con permiso de alarmas exactas no hay aviso`() = runTest { // R-5.3
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)

        assertThat(subscribed(viewModel()).uiState.value.windowedWarning).isFalse()
    }

    @Test
    fun `sin alarmas pendientes por sonar no avisa del modo ventana`() = runTest { // R-5.4: nothing left to delay
        scheduler.exact = false
        setOrder(OrderStatus.PAUSED, *defaultAlarms)

        assertThat(subscribed(viewModel()).uiState.value.windowedWarning).isFalse()
    }

    @Test
    fun `al volver a la pantalla revisa de nuevo el permiso`() = runTest { // R-5.4, R-11.5
        scheduler.exact = false
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)
        val vm = subscribed(viewModel())

        scheduler.exact = true
        vm.onAction(OrderDetailAction.Resumed)

        assertThat(vm.uiState.value.windowedWarning).isFalse()
    }

    @Test
    fun `al llegar desde Revision anuncia las alarmas programadas una sola vez`() = runTest { // R-5.6
        val handle = handle(scheduledCount = 3)

        viewModel(handle).effects.test {
            assertThat(awaitItem()).isEqualTo(
                OrderDetailEffect.ShowMessage(UiText.Plural(R.plurals.order_detail_scheduled, 3, listOf(3))),
            )
        }
        // Recreated with the same SavedStateHandle (rotation, process death): no second snackbar
        viewModel(handle).effects.test { expectNoEvents() }
    }

    @Test
    fun `al abrirla desde Inicio no anuncia nada`() = runTest {
        viewModel().effects.test { expectNoEvents() }
    }

    @Test
    fun `solo ofrece marcar como hecha las pendientes y las disparadas`() = runTest { // R-9.5
        setOrder(
            OrderStatus.ACTIVE,
            testAlarm(id = 1, time = LocalTime.of(6, 0), status = AlarmStatus.PENDING),
            testAlarm(id = 2, time = LocalTime.of(6, 1), status = AlarmStatus.FIRED),
            testAlarm(id = 3, time = LocalTime.of(6, 2), status = AlarmStatus.DONE),
            testAlarm(id = 4, time = LocalTime.of(6, 3), status = AlarmStatus.SKIPPED),
            testAlarm(id = 5, time = LocalTime.of(6, 4), status = AlarmStatus.CANCELLED),
        )

        val offered = subscribed(viewModel()).uiState.value.timeline.map { it.canMarkDone }

        assertThat(offered).containsExactly(true, true, false, false, false).inOrder()
    }

    @Test
    fun `marcar como hecha una pendiente la cancela en el sistema`() = runTest { // R-9.5
        alarms.add(testAlarm(id = 2, time = LocalTime.of(13, 0)))
        scheduler.schedule(2, Instant.parse("2026-11-05T18:00:00Z"))

        viewModel().onAction(OrderDetailAction.MarkDone(2))

        assertThat(alarms.alarms.getValue(2).status).isEqualTo(AlarmStatus.DONE)
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `eliminar borra la alarma y ofrece deshacer`() = runTest { // R-9.3
        alarms.add(testAlarm(id = 2, time = LocalTime.of(13, 0)))
        scheduler.schedule(2, Instant.parse("2026-11-05T18:00:00Z"))
        val vm = viewModel()

        vm.effects.test {
            vm.onAction(OrderDetailAction.Delete(2))

            assertThat(awaitItem()).isEqualTo(
                OrderDetailEffect.ShowUndo(UiText.Res(R.string.order_detail_alarm_deleted), alarmId = 2),
            )
        }
        assertThat(alarms.alarms).isEmpty()
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `deshacer restaura la alarma y la reprograma si es futura`() = runTest { // R-9.4
        orders.add(testOrder(id = 1))
        val alarm = testAlarm(id = 2, time = LocalTime.of(13, 0))
        alarms.add(alarm)
        val vm = viewModel()

        vm.onAction(OrderDetailAction.Delete(2))
        vm.onAction(OrderDetailAction.UndoDelete(2))

        assertThat(alarms.alarms[2L]).isEqualTo(alarm)
        assertThat(scheduler.scheduled[2L]).isEqualTo(Instant.parse("2026-11-05T18:00:00Z"))
    }

    @Test
    fun `deshacer dos veces no hace nada la segunda`() = runTest { // R-9.4
        orders.add(testOrder(id = 1))
        alarms.add(testAlarm(id = 2, time = LocalTime.of(13, 0)))
        val vm = viewModel()
        vm.onAction(OrderDetailAction.Delete(2))
        vm.onAction(OrderDetailAction.UndoDelete(2))
        alarms.alarms.clear()

        vm.onAction(OrderDetailAction.UndoDelete(2))

        assertThat(alarms.alarms).isEmpty()
    }

    @Test
    fun `eliminar todas pide confirmacion diciendo cuantas pendientes se cancelan`() = runTest { // R-10.1
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)
        val vm = subscribed(viewModel())

        vm.onAction(OrderDetailAction.CancelAllClicked)

        assertThat(vm.uiState.value.dialog).isEqualTo(
            OrderDetailDialog.CancelAll(UiText.Plural(R.plurals.cancel_all_body, 2, listOf(2))),
        )
    }

    @Test
    fun `sin pendientes la confirmacion lo dice`() = runTest { // R-10.1
        setOrder(OrderStatus.ACTIVE, testAlarm(id = 1, status = AlarmStatus.DONE))
        val vm = subscribed(viewModel())

        vm.onAction(OrderDetailAction.CancelAllClicked)

        assertThat(vm.uiState.value.dialog).isEqualTo(OrderDetailDialog.CancelAll(UiText.Res(R.string.cancel_all_body_none)))
    }

    @Test
    fun `cancelar el dialogo no toca nada`() = runTest { // R-10.1
        orders.add(testOrder(id = 1))
        alarms.add(testAlarm(id = 2, orderId = 1))
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)
        val vm = subscribed(viewModel())
        vm.onAction(OrderDetailAction.CancelAllClicked)

        vm.onAction(OrderDetailAction.DismissDialog)

        assertThat(vm.uiState.value.dialog).isNull()
        assertThat(alarms.alarms.getValue(2).status).isEqualTo(AlarmStatus.PENDING)
    }

    @Test
    fun `confirmar eliminar todas cancela las pendientes y la orden`() = runTest { // R-10.2
        orders.add(testOrder(id = 1))
        alarms.add(testAlarm(id = 2, orderId = 1), testAlarm(id = 3, orderId = 1, status = AlarmStatus.DONE))
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)
        val vm = subscribed(viewModel())
        vm.onAction(OrderDetailAction.CancelAllClicked)

        vm.onAction(OrderDetailAction.ConfirmDialog)

        assertThat(vm.uiState.value.dialog).isNull()
        assertThat(alarms.alarms.values.map { it.status }).containsExactly(AlarmStatus.CANCELLED, AlarmStatus.DONE).inOrder()
        assertThat(orders.orders.getValue(1).status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun `una orden cancelada oculta eliminar todas`() = runTest { // R-10.8
        setOrder(OrderStatus.CANCELLED, testAlarm(id = 1, status = AlarmStatus.CANCELLED))

        assertThat(subscribed(viewModel()).uiState.value.canCancelAll).isFalse()
    }

    @Test
    fun `una orden activa o pausada ofrece eliminar todas`() = runTest { // R-10.1
        setOrder(OrderStatus.PAUSED, *defaultAlarms)

        assertThat(subscribed(viewModel()).uiState.value.canCancelAll).isTrue()
    }

    @Test
    fun `confirmar eliminar orden la borra y sale sin mostrar orden no encontrada`() = runTest { // R-10.4
        orders.add(testOrder(id = 1))
        alarms.add(testAlarm(id = 2, orderId = 1))
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)
        val vm = subscribed(viewModel())
        vm.onAction(OrderDetailAction.DeleteOrderClicked)
        assertThat(vm.uiState.value.dialog)
            .isEqualTo(OrderDetailDialog.DeleteOrder(UiText.Res(R.string.delete_order_body, listOf("Cachorro"))))

        vm.effects.test {
            vm.onAction(OrderDetailAction.ConfirmDialog)
            orders.detail(1).value = null // what Room emits after the delete

            assertThat(awaitItem()).isEqualTo(OrderDetailEffect.NavigateBack)
        }
        assertThat(orders.orders).isEmpty()
        assertThat(alarms.alarms).isEmpty()
        assertThat(vm.uiState.value.notFound).isFalse()
        assertThat(vm.uiState.value.name).isEqualTo("Cachorro")
    }

    @Test
    fun `una orden activa con pendientes ofrece pausar`() = runTest { // R-10.5
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)

        assertThat(subscribed(viewModel()).uiState.value.pauseAction).isEqualTo(PauseAction.PAUSE)
    }

    @Test
    fun `una orden pausada ofrece reanudar`() = runTest { // R-10.6
        setOrder(OrderStatus.PAUSED, *defaultAlarms)

        assertThat(subscribed(viewModel()).uiState.value.pauseAction).isEqualTo(PauseAction.RESUME)
    }

    @Test
    fun `una orden cancelada o terminada no ofrece pausar ni reanudar`() = runTest { // R-10.8, D-49
        setOrder(OrderStatus.CANCELLED, testAlarm(id = 1, status = AlarmStatus.CANCELLED))
        val vm = subscribed(viewModel())
        assertThat(vm.uiState.value.pauseAction).isNull()

        setOrder(OrderStatus.ACTIVE, testAlarm(id = 1, status = AlarmStatus.DONE))
        assertThat(vm.uiState.value.pauseAction).isNull()
    }

    @Test
    fun `pausar cancela las pendientes en el sistema y pone la orden en pausada`() = runTest { // R-10.5
        orders.add(testOrder(id = 1))
        alarms.add(testAlarm(id = 2, orderId = 1, time = LocalTime.of(13, 0)))
        scheduler.schedule(2, Instant.parse("2026-11-05T18:00:00Z"))
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)
        val vm = subscribed(viewModel())

        vm.onAction(OrderDetailAction.PauseClicked)

        assertThat(scheduler.scheduled).isEmpty()
        assertThat(alarms.alarms.getValue(2).status).isEqualTo(AlarmStatus.PENDING)
        assertThat(orders.orders.getValue(1).status).isEqualTo(OrderStatus.PAUSED)
    }

    @Test
    fun `reanudar avisa cuantas alarmas se omitieron`() = runTest { // R-10.6, D-49
        orders.add(testOrder(id = 1, status = OrderStatus.PAUSED))
        alarms.add(
            testAlarm(id = 2, orderId = 1, time = LocalTime.of(6, 0)),
            testAlarm(id = 3, orderId = 1, time = LocalTime.of(6, 30)),
            testAlarm(id = 4, orderId = 1, time = LocalTime.of(13, 0)),
        )
        setOrder(OrderStatus.PAUSED, *defaultAlarms)
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(OrderDetailAction.ResumeClicked)

            assertThat(awaitItem()).isEqualTo(
                OrderDetailEffect.ShowMessage(UiText.Plural(R.plurals.order_detail_resumed_skipped, 2, listOf(2))),
            )
        }
        assertThat(scheduler.scheduled.keys).containsExactly(4L)
        assertThat(orders.orders.getValue(1).status).isEqualTo(OrderStatus.ACTIVE)
    }

    @Test
    fun `reanudar sin alarmas omitidas no muestra mensaje`() = runTest { // R-10.6
        orders.add(testOrder(id = 1, status = OrderStatus.PAUSED))
        alarms.add(testAlarm(id = 4, orderId = 1, time = LocalTime.of(13, 0)))
        setOrder(OrderStatus.PAUSED, *defaultAlarms)
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(OrderDetailAction.ResumeClicked)

            expectNoEvents()
        }
        assertThat(scheduler.scheduled.keys).containsExactly(4L)
    }

    @Test
    fun `duplicar abre el selector de fecha en manana`() = runTest { // R-10.7, D-50
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)
        val vm = subscribed(viewModel())
        assertThat(vm.uiState.value.canDuplicate).isTrue()

        vm.onAction(OrderDetailAction.DuplicateClicked)

        // 2026-11-06T00:00:00Z: tomorrow in Bogotá, as UTC midnight for the picker
        assertThat(vm.uiState.value.duplicateDateMillis).isEqualTo(Instant.parse("2026-11-06T00:00:00Z").toEpochMilli())
    }

    @Test
    fun `elegir fecha crea el borrador y abre la revision`() = runTest { // R-10.7
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)
        val vm = subscribed(viewModel())
        vm.onAction(OrderDetailAction.DuplicateClicked)

        vm.effects.test {
            vm.onAction(OrderDetailAction.DuplicateDateSelected(Instant.parse("2026-11-20T00:00:00Z").toEpochMilli()))

            val effect = awaitItem() as OrderDetailEffect.NavigateToReview
            val draft = requireNotNull(drafts.get(effect.draftId)).draft
            assertThat(draft.date).isEqualTo(LocalDate.of(2026, 11, 20))
            assertThat(draft.origin).isEqualTo(DraftOrigin.DUPLICATE)
            assertThat(draft.items.map { it.title }).containsExactly("Desayuno", "Paseo", "Salida nocturna").inOrder()
        }
        assertThat(vm.uiState.value.duplicateDateMillis).isNull()
    }

    @Test
    fun `cerrar el selector no duplica`() = runTest { // R-10.7
        setOrder(OrderStatus.ACTIVE, *defaultAlarms)
        val vm = subscribed(viewModel())
        vm.onAction(OrderDetailAction.DuplicateClicked)

        vm.effects.test {
            vm.onAction(OrderDetailAction.DismissDuplicate)

            expectNoEvents()
        }
        assertThat(vm.uiState.value.duplicateDateMillis).isNull()
    }

    @Test
    fun `una orden cancelada se puede duplicar pero una sin alarmas no`() = runTest { // R-10.8, D-50
        setOrder(OrderStatus.CANCELLED, testAlarm(id = 1, status = AlarmStatus.CANCELLED))
        val vm = subscribed(viewModel())
        assertThat(vm.uiState.value.canDuplicate).isTrue()

        setOrder(OrderStatus.ACTIVE)
        assertThat(vm.uiState.value.canDuplicate).isFalse()
    }
}
