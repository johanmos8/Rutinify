package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmEventType
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** T-27: "Reanudar" (R-10.6). */
class ResumeOrderUseCaseTest {

    private val zone = ZoneId.of("America/Bogota")

    // 2026-11-05 07:00 in Bogotá
    private val now = Instant.parse("2026-11-05T12:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val alarms = FakeAlarmRepository(clock)
    private val orders = FakeOrderRepository(alarms)
    private val scheduler = FakeAlarmScheduler()
    private val categories = FakeCategorySettingsRepository()
    private val resume = ResumeOrderUseCase(orders, alarms, scheduler, categories, TriggerTimeCalculator(), { zone }, clock, AlarmMutationLock())

    private val date = LocalDate.of(2026, 11, 5)

    @Test
    fun `reanudar omite las pasadas y programa las futuras`() = runTest { // R-10.6
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.PAUSED))
        alarms.add(
            testAlarm(id = 10, time = LocalTime.of(6, 0)),
            testAlarm(id = 11, time = LocalTime.of(9, 0)),
            testAlarm(id = 12, time = LocalTime.of(1, 0), dayOffset = 1),
        )

        val result = resume(1)

        assertThat(result).isEqualTo(ResumeResult(scheduled = 2, skipped = 1))
        assertThat(alarms.alarms.getValue(10).status).isEqualTo(AlarmStatus.SKIPPED)
        assertThat(scheduler.scheduled).containsExactly(
            11L, Instant.parse("2026-11-05T14:00:00Z"),
            12L, Instant.parse("2026-11-06T06:00:00Z"),
        ).inOrder()
        assertThat(orders.orders.getValue(1).status).isEqualTo(OrderStatus.ACTIVE)
    }

    @Test
    fun `una pasada hace un minuto tambien se omite sin tolerancia`() = runTest { // R-10.6, D-49
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.PAUSED))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(6, 59)), testAlarm(id = 11, time = LocalTime.of(7, 0)))

        resume(1)

        assertThat(alarms.alarms.getValue(10).status).isEqualTo(AlarmStatus.SKIPPED)
        // Exactly now is not ahead anymore either
        assertThat(alarms.alarms.getValue(11).status).isEqualTo(AlarmStatus.SKIPPED)
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `una pospuesta se programa a su hora pospuesta si aun no llega`() = runTest { // R-10.6, D-12
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.PAUSED))
        val until = Instant.parse("2026-11-05T12:05:00Z")
        alarms.add(testAlarm(id = 10, time = LocalTime.of(6, 0), snoozedUntil = until))

        resume(1)

        assertThat(scheduler.scheduled).containsExactly(10L, until)
    }

    @Test
    fun `las alarmas disparadas hechas o canceladas no cambian`() = runTest { // R-10.6: solo pendientes
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.PAUSED))
        val fired = testAlarm(id = 10, time = LocalTime.of(6, 0), status = AlarmStatus.FIRED)
        val done = testAlarm(id = 11, time = LocalTime.of(9, 0), status = AlarmStatus.DONE)
        alarms.add(fired, done)

        val result = resume(1)

        assertThat(result).isEqualTo(ResumeResult(scheduled = 0, skipped = 0))
        assertThat(alarms.alarms.values).containsExactly(fired, done)
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `solo se reanuda una orden pausada`() = runTest { // R-10.6
        orders.add(testOrder(id = 1, date = date), testOrder(id = 2, date = date, status = OrderStatus.CANCELLED))
        alarms.add(testAlarm(id = 10, orderId = 1, time = LocalTime.of(9, 0)))

        assertThat(resume(1)).isNull()
        assertThat(resume(2)).isNull()
        assertThat(resume(3)).isNull()
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(orders.orders.getValue(2).status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun `pausar y reanudar antes de la hora deja la alarma igual que antes`() = runTest { // R-10.5, R-10.6
        orders.add(testOrder(id = 1, date = date))
        val alarm = testAlarm(id = 10, time = LocalTime.of(9, 0))
        alarms.add(alarm)
        scheduler.schedule(10, Instant.parse("2026-11-05T14:00:00Z"))

        PauseOrderUseCase(orders, alarms, scheduler, AlarmMutationLock())(1)
        resume(1)

        assertThat(alarms.alarms.getValue(10)).isEqualTo(alarm)
        assertThat(scheduler.scheduled).containsExactly(10L, Instant.parse("2026-11-05T14:00:00Z"))
    }

    @Test
    fun `al reanudar las alarmas criticas se programan como despertador`() = runTest { // R-10.6, R-15.3
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.PAUSED))
        alarms.add(
            testAlarm(id = 11, time = LocalTime.of(9, 0), category = "medicación"),
            testAlarm(id = 12, time = LocalTime.of(10, 0)),
        )
        categories.setCritical("medicación", true)

        resume(1)

        assertThat(scheduler.critical).containsExactly(11L, CriticalAlarm(orderId = 1L))
        assertThat(scheduler.scheduled.keys).containsExactly(11L, 12L)
    }

    @Test
    fun `las omitidas al reanudar quedan en el historial`() = runTest { // R-20.1, R-10.6
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.PAUSED))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(6, 0)), testAlarm(id = 11, time = LocalTime.of(9, 0)))

        resume(1)

        assertThat(alarms.events.map { it.alarmId to it.type }).containsExactly(10L to AlarmEventType.SKIPPED)
    }
}
