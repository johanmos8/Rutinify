package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
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
import java.time.ZoneOffset

/** T-19: boot, time/zone change, update and app start reconciliation (design §7.2, R-11.6, R-11.7, R-12.*). */
class RescheduleAllUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val date = LocalDate.of(2026, 11, 5)
    private val now = Instant.parse("2026-11-05T17:00:00Z") // 12:00 in Bogotá
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private var zone: ZoneId = bogota

    private val orders = FakeOrderRepository()
    private val alarms = FakeAlarmRepository(clock) { id -> orders.orders[id]?.takeIf { it.status == OrderStatus.ACTIVE }?.date }
    private val scheduler = FakeAlarmScheduler()
    private val categories = FakeCategorySettingsRepository()
    private val publisher = FakeNotificationPublisher()
    private val lock = AlarmMutationLock()
    private val calculator = TriggerTimeCalculator()
    private val reschedule = RescheduleAllUseCase(
        alarms = alarms,
        scheduler = scheduler,
        categories = categories,
        handleFired = HandleAlarmFiredUseCase(alarms, orders, publisher, categories, scheduler, clock, calculator, { zone }, lock),
        calculator = calculator,
        zoneProvider = { zone },
        clock = clock,
        lock = lock,
    )

    /** Bogotá wall-clock instant on [date]. */
    private fun at(time: String): Instant = calculator.triggerAt(date, LocalTime.parse(time), 0, bogota)

    @Test
    fun `programa las alarmas futuras en su hora`() = runTest { // R-12.1
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, time = LocalTime.of(13, 0)), testAlarm(id = 11L, time = LocalTime.of(2, 0), dayOffset = 1))

        val result = reschedule()

        assertThat(scheduler.scheduled).containsExactly(
            10L, at("13:00"),
            11L, calculator.triggerAt(date, LocalTime.of(2, 0), 1, bogota),
        )
        assertThat(result).isEqualTo(RescheduleResult(scheduled = 2, firedNow = 0, skipped = 0))
    }

    @Test
    fun `ejecutarlo dos veces deja las mismas alarmas`() = runTest { // R-12.6
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, time = LocalTime.of(13, 0)), testAlarm(id = 11L, time = LocalTime.of(18, 0)))

        reschedule()
        val first = scheduler.scheduled.toMap()
        reschedule()

        assertThat(scheduler.scheduled).isEqualTo(first)
        assertThat(alarms.alarms.values.map { it.status }).containsExactly(AlarmStatus.PENDING, AlarmStatus.PENDING)
    }

    @Test
    fun `una alarma pospuesta se programa en su hora pospuesta`() = runTest { // R-12.7, D-12
        val snoozedUntil = now.plusSeconds(180)
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, time = LocalTime.of(11, 0), snoozedUntil = snoozedUntil))

        reschedule()

        assertThat(scheduler.scheduled).containsExactly(10L, snoozedUntil)
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.PENDING)
    }

    @Test
    fun `las alarmas de ordenes pausadas o canceladas se ignoran`() = runTest { // R-10.5, R-12.1
        orders.add(testOrder(id = 1L, date = date, status = OrderStatus.PAUSED), testOrder(id = 2L, date = date, status = OrderStatus.CANCELLED))
        alarms.add(
            testAlarm(id = 10L, orderId = 1L, time = LocalTime.of(13, 0)),
            testAlarm(id = 11L, orderId = 1L, time = LocalTime.of(9, 0)),
            testAlarm(id = 20L, orderId = 2L, time = LocalTime.of(13, 0)),
        )

        val result = reschedule()

        assertThat(scheduler.scheduled).isEmpty()
        assertThat(alarms.alarms.values.map { it.status }.toSet()).containsExactly(AlarmStatus.PENDING)
        assertThat(result).isEqualTo(RescheduleResult(0, 0, 0))
    }

    @Test
    fun `solo toca alarmas pendientes`() = runTest { // R-12.1
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(
            testAlarm(id = 10L, time = LocalTime.of(13, 0), status = AlarmStatus.DONE),
            testAlarm(id = 11L, time = LocalTime.of(9, 0), status = AlarmStatus.FIRED),
        )

        reschedule()

        assertThat(scheduler.scheduled).isEmpty()
        assertThat(publisher.shown).isEmpty()
        assertThat(alarms.alarms.getValue(11L).status).isEqualTo(AlarmStatus.FIRED)
    }

    @Test
    fun `una alarma pasada hace 15 minutos o menos se dispara ya`() = runTest { // R-12.4
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, time = LocalTime.of(11, 45)), testAlarm(id = 11L, time = LocalTime.of(11, 59)))

        val result = reschedule()

        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.FIRED)
        assertThat(alarms.alarms.getValue(11L).status).isEqualTo(AlarmStatus.FIRED)
        assertThat(publisher.shown.keys).containsExactly(10L, 11L)
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(result).isEqualTo(RescheduleResult(scheduled = 0, firedNow = 2, skipped = 0))
    }

    @Test
    fun `una alarma justo a la hora actual se dispara ya`() = runTest { // R-12.4 (t == now is not future)
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, time = LocalTime.NOON))

        reschedule()

        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.FIRED)
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `una alarma pasada hace mas de 15 minutos se omite sin notificar`() = runTest { // R-12.5
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, time = LocalTime.of(11, 44, 59)), testAlarm(id = 11L, time = LocalTime.of(6, 45)))

        val result = reschedule()

        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.SKIPPED)
        assertThat(alarms.alarms.getValue(11L).status).isEqualTo(AlarmStatus.SKIPPED)
        assertThat(publisher.shown).isEmpty()
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(result).isEqualTo(RescheduleResult(scheduled = 0, firedNow = 0, skipped = 2))
    }

    @Test
    fun `las omitidas se cancelan en el sistema`() = runTest { // R-12.5: a clock moved forward must not fire them
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, time = LocalTime.of(6, 45)))

        reschedule()

        assertThat(scheduler.cancelled).containsExactly(10L)
    }

    @Test
    fun `un cambio de zona recalcula la hora de pared en la zona nueva`() = runTest { // R-12.2
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, time = LocalTime.of(20, 0)))
        val madrid = ZoneId.of("Europe/Madrid")

        reschedule()
        zone = madrid
        reschedule()

        // 20:00 in Madrid (19:00Z) instead of 20:00 in Bogotá (01:00Z next day)
        assertThat(scheduler.scheduled).containsExactly(10L, calculator.triggerAt(date, LocalTime.of(20, 0), 0, madrid))
    }

    @Test
    fun `sin permiso de alarmas exactas reprograma igualmente`() = runTest { // R-11.7
        scheduler.exact = false
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, time = LocalTime.of(13, 0)))

        reschedule()

        assertThat(scheduler.scheduled.keys).containsExactly(10L)
    }

    @Test
    fun `mezcla futuras disparadas ya y omitidas en una sola pasada`() = runTest { // design §7.2
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(
            testAlarm(id = 10L, time = LocalTime.of(6, 45)),
            testAlarm(id = 11L, time = LocalTime.of(11, 50)),
            testAlarm(id = 12L, time = LocalTime.of(13, 0)),
        )

        val result = reschedule()

        assertThat(result).isEqualTo(RescheduleResult(scheduled = 1, firedNow = 1, skipped = 1))
    }

    @Test
    fun `las alarmas de categorias criticas se vuelven a programar como despertador`() = runTest { // R-15.13, R-15.14
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(
            testAlarm(id = 10L, time = LocalTime.of(13, 0), category = "medicación"),
            testAlarm(id = 11L, time = LocalTime.of(14, 0), category = "paseo"),
        )
        categories.setCritical("medicación", true)

        reschedule()

        assertThat(scheduler.critical).containsExactly(10L, CriticalAlarm(orderId = 1L))
        assertThat(scheduler.scheduled.keys).containsExactly(10L, 11L)
    }
}
