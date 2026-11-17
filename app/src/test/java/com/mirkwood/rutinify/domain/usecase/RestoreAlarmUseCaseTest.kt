package com.mirkwood.rutinify.domain.usecase

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

/** T-25: "Deshacer" after deleting one alarm (R-9.4). */
class RestoreAlarmUseCaseTest {

    private val zone = ZoneId.of("America/Bogota")

    // 2026-11-05 07:00 in Bogotá
    private val now = Instant.parse("2026-11-05T12:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val orders = FakeOrderRepository()
    private val alarms = FakeAlarmRepository(clock)
    private val scheduler = FakeAlarmScheduler()
    private val categories = FakeCategorySettingsRepository()
    private val restore = RestoreAlarmUseCase(alarms, orders, scheduler, categories, TriggerTimeCalculator(), { zone }, clock, AlarmMutationLock())

    private val date = LocalDate.of(2026, 11, 5)

    @Test
    fun `restaura la alarma identica y la reprograma si su hora es futura`() = runTest { // R-9.4
        orders.add(testOrder(id = 1, date = date))
        val alarm = testAlarm(id = 10L, time = LocalTime.of(9, 0))

        val scheduled = restore(alarm)

        assertThat(scheduled).isTrue()
        assertThat(alarms.alarms[10L]).isEqualTo(alarm)
        assertThat(scheduler.scheduled[10L]).isEqualTo(Instant.parse("2026-11-05T14:00:00Z"))
    }

    @Test
    fun `una pospuesta se reprograma a su hora pospuesta`() = runTest { // R-9.4, D-12
        orders.add(testOrder(id = 1, date = date))
        val until = Instant.parse("2026-11-05T12:03:00Z")

        restore(testAlarm(id = 10L, time = LocalTime.of(6, 0), snoozedUntil = until))

        assertThat(scheduler.scheduled[10L]).isEqualTo(until)
    }

    @Test
    fun `si su hora ya paso no la reprograma y queda omitida`() = runTest { // R-9.4, D-47
        orders.add(testOrder(id = 1, date = date))

        val scheduled = restore(testAlarm(id = 10L, time = LocalTime.of(6, 59)))

        assertThat(scheduled).isFalse()
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.SKIPPED)
    }

    @Test
    fun `una alarma ya disparada o hecha se restaura con su estado y sin programar`() = runTest { // R-9.4
        orders.add(testOrder(id = 1, date = date))
        val fired = testAlarm(id = 10L, time = LocalTime.of(9, 0), status = AlarmStatus.FIRED)
        val done = testAlarm(id = 11L, time = LocalTime.of(9, 0), status = AlarmStatus.DONE)

        restore(fired)
        restore(done)

        assertThat(alarms.alarms.values).containsExactly(fired, done)
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `en una orden pausada vuelve como pendiente pero no se programa`() = runTest { // R-10.5
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.PAUSED))
        val alarm = testAlarm(id = 10L, time = LocalTime.of(9, 0))

        val scheduled = restore(alarm)

        assertThat(scheduled).isFalse()
        assertThat(alarms.alarms[10L]).isEqualTo(alarm)
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `si la orden ya no existe no restaura nada`() = runTest {
        val scheduled = restore(testAlarm(id = 10L, time = LocalTime.of(9, 0)))

        assertThat(scheduled).isFalse()
        assertThat(alarms.alarms).isEmpty()
    }

    @Test
    fun `una alarma critica restaurada se programa como despertador`() = runTest { // R-9.4, R-15.3
        orders.add(testOrder(id = 1, date = date))
        categories.setCritical("medicación", true)

        restore(testAlarm(id = 10L, time = LocalTime.of(9, 0), category = "medicación"))

        assertThat(scheduler.critical).containsExactly(10L, CriticalAlarm(orderId = 1L))
    }
}
