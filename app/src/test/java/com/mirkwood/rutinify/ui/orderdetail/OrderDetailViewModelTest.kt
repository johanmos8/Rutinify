package com.mirkwood.rutinify.ui.orderdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.usecase.CanScheduleExactAlarmsUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveOrderDetailUseCase
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
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
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** T-24: order detail logic (R-9.1, R-9.2, R-7.6, R-5.4, R-5.6). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class OrderDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val zone = ZoneId.of("America/Bogota")
    private val orders = FakeOrderRepository()
    private val scheduler = FakeAlarmScheduler(exact = true)

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
        zoneProvider = { zone },
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
        assertThat(state.dateLabel).isEqualTo("jueves 5 de noviembre de 2026")
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
}
