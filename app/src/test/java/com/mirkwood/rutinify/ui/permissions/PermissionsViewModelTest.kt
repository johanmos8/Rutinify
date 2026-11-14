package com.mirkwood.rutinify.ui.permissions

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.usecase.AlarmMutationLock
import com.mirkwood.rutinify.domain.usecase.GetPermissionStatusUseCase
import com.mirkwood.rutinify.domain.usecase.HandleAlarmFiredUseCase
import com.mirkwood.rutinify.domain.usecase.RescheduleAllUseCase
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.FakePermissionChecker
import com.mirkwood.rutinify.testutil.MainDispatcherRule
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** T-29: Permissions screen (R-11.4, R-11.5, R-11.6). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class PermissionsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val zone = ZoneId.of("America/Bogota")
    // 2026-11-05 07:00 in Bogotá
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), zone)
    private val orders = FakeOrderRepository()
    private val alarms = FakeAlarmRepository(clock) { id -> orders.activeDate(id) }
    private val scheduler = FakeAlarmScheduler()
    private val checker = FakePermissionChecker(notifications = false, exactAlarms = false)
    private val lock = AlarmMutationLock()

    private fun viewModel() = PermissionsViewModel(
        getPermissionStatus = GetPermissionStatusUseCase(checker),
        rescheduleAll = RescheduleAllUseCase(
            alarms = alarms,
            scheduler = scheduler,
            handleFired = HandleAlarmFiredUseCase(alarms, orders, FakeNotificationPublisher(), TriggerTimeCalculator(), { zone }, lock),
            calculator = TriggerTimeCalculator(),
            zoneProvider = { zone },
            clock = clock,
            lock = lock,
        ),
    )

    @Test
    fun `muestra el estado de cada permiso`() { // R-11.4
        checker.notifications = true

        val state = viewModel().uiState.value

        assertThat(state).isEqualTo(PermissionsUiState(notifications = true, exactAlarms = false))
    }

    @Test
    fun `al volver a primer plano vuelve a comprobar los permisos`() { // R-11.5
        val vm = viewModel()
        checker.notifications = true
        checker.exactAlarms = true

        vm.onAction(PermissionsAction.Resumed)

        assertThat(vm.uiState.value).isEqualTo(PermissionsUiState(notifications = true, exactAlarms = true))
        assertThat(vm.uiState.value.allGranted).isTrue()
    }

    @Test
    fun `al conceder alarmas exactas reprograma las pendientes de ordenes activas`() = runTest { // R-11.6
        orders.add(testOrder(id = 1), testOrder(id = 2, status = OrderStatus.PAUSED))
        alarms.add(testAlarm(id = 10, orderId = 1, time = LocalTime.of(9, 0)), testAlarm(id = 20, orderId = 2, time = LocalTime.of(9, 0)))
        val vm = viewModel()

        checker.exactAlarms = true
        vm.onAction(PermissionsAction.Resumed)

        assertThat(scheduler.scheduled.keys).containsExactly(10L)
    }

    @Test
    fun `si el permiso exacto no cambia no reprograma`() = runTest { // R-11.6: only on the false → true change
        orders.add(testOrder(id = 1))
        alarms.add(testAlarm(id = 10, orderId = 1, time = LocalTime.of(9, 0)))
        val vm = viewModel()

        checker.notifications = true
        vm.onAction(PermissionsAction.Resumed)

        assertThat(scheduler.scheduled).isEmpty()
    }
}
