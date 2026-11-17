package com.mirkwood.rutinify.ui.alarm

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.usecase.AlarmMutationLock
import com.mirkwood.rutinify.domain.usecase.MarkAlarmDoneUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveOrderDetailUseCase
import com.mirkwood.rutinify.domain.usecase.SilenceAlarmUseCase
import com.mirkwood.rutinify.domain.usecase.SnoozeAlarmUseCase
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.FakeSilencedAlarms
import com.mirkwood.rutinify.testutil.MainDispatcherRule
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** T-46: alarm screen (R-15.7–R-15.10). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class AlarmViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val zone = ZoneId.of("America/Bogota")
    private val clock = Clock.fixed(Instant.parse("2026-11-05T19:00:00Z"), zone)
    private val alarms = FakeAlarmRepository(clock)
    private val orders = FakeOrderRepository(alarms)
    private val scheduler = FakeAlarmScheduler()
    private val publisher = FakeNotificationPublisher()
    private val silenced = FakeSilencedAlarms()
    private val lock = AlarmMutationLock()
    private val calculator = TriggerTimeCalculator()

    private val ringing = testAlarm(
        id = 10L,
        time = LocalTime.of(14, 0),
        title = "Amoxicilina",
        description = "500 mg",
        category = "medicación",
        status = AlarmStatus.FIRED,
    )

    private fun viewModel() = AlarmViewModel(
        savedStateHandle = SavedStateHandle(mapOf(AlarmActivity.EXTRA_ALARM_ID to 10L, AlarmActivity.EXTRA_ORDER_ID to 1L)),
        observeOrderDetail = ObserveOrderDetailUseCase(orders, calculator) { zone },
        silencedAlarms = silenced,
        markDone = MarkAlarmDoneUseCase(alarms, scheduler, publisher, lock),
        snooze = SnoozeAlarmUseCase(alarms, orders, scheduler, FakeCategorySettingsRepository(), publisher, clock, lock),
        silence = SilenceAlarmUseCase(alarms, orders, publisher, scheduler, silenced, calculator, { zone }, lock),
        appScope = CoroutineScope(mainRule.testDispatcher),
    )

    private fun showing(vararg items: com.mirkwood.rutinify.domain.model.AlarmItem) {
        val order = testOrder(id = 1L, name = "Antibiótico de Luna")
        orders.add(order)
        alarms.add(*items)
        orders.detail(1).value = OrderWithAlarms(order, items.toList())
    }

    /** Keeps `uiState` subscribed (WhileSubscribed) for the whole test. */
    private fun TestScope.subscribed(vm: AlarmViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    @Test
    fun `muestra la hora el titulo la descripcion y el plan`() = runTest { // R-15.7
        showing(ringing)

        val state = subscribed(viewModel()).uiState.value

        assertThat(state).isEqualTo(
            AlarmUiState(
                isLoading = false,
                title = "Amoxicilina",
                description = "500 mg",
                timeLabel = "14:00",
                planName = "Antibiótico de Luna",
            ),
        )
    }

    @Test
    fun `Hecho marca la alarma y cierra la pantalla`() = runTest { // R-15.8, R-7.4
        showing(ringing)
        val vm = subscribed(viewModel())

        vm.onAction(AlarmAction.Done)

        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.DONE)
        assertThat(vm.uiState.value.closed).isTrue()
    }

    @Test
    fun `mas 5 min pospone y cierra la pantalla`() = runTest { // R-15.8, R-7.5
        showing(ringing)
        val vm = subscribed(viewModel())

        vm.onAction(AlarmAction.Snooze)

        assertThat(alarms.alarms.getValue(10L).snoozedUntil).isEqualTo(clock.instant().plusSeconds(300))
        assertThat(vm.uiState.value.closed).isTrue()
    }

    @Test
    fun `Silenciar deja la alarma disparada y cierra la pantalla`() = runTest { // R-15.9
        showing(ringing)
        val vm = subscribed(viewModel())

        vm.onAction(AlarmAction.Silence)

        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.FIRED)
        assertThat(publisher.shown.getValue(10L).silenced).isTrue()
        assertThat(vm.uiState.value.closed).isTrue()
    }

    @Test
    fun `un segundo toque no hace nada mas`() = runTest {
        showing(ringing)
        val vm = subscribed(viewModel())

        vm.onAction(AlarmAction.Silence)
        vm.onAction(AlarmAction.Snooze)

        assertThat(alarms.alarms.getValue(10L).snoozedUntil).isNull()
    }

    @Test
    fun `se cierra si la alarma se silencia desde la notificacion o por el corte`() = runTest { // R-15.10
        showing(ringing)
        val vm = subscribed(viewModel())

        silenced.add(10L)

        assertThat(vm.uiState.value.closed).isTrue()
    }

    @Test
    fun `se cierra si la alarma ya no esta disparada`() = runTest { // R-15.8: "Hecho" from the notification
        showing(ringing.copy(status = AlarmStatus.DONE))

        assertThat(subscribed(viewModel()).uiState.value.closed).isTrue()
    }

    @Test
    fun `se cierra si el plan ya no existe`() = runTest {
        assertThat(subscribed(viewModel()).uiState.value.closed).isTrue()
    }
}
