package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
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

/** T-55: "Voy tarde" in the domain (R-18.3–R-18.8, D-73). */
class ShiftOrderAlarmsUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val date = LocalDate.of(2026, 11, 5)
    private val clock = Clock.fixed(Instant.parse("2026-11-05T17:00:00Z"), bogota) // 12:00 in Bogotá
    private val orders = FakeOrderRepository()
    private val alarms = FakeAlarmRepository(clock) { id -> orders.activeDate(id) }
    private val scheduler = FakeAlarmScheduler()
    private val categories = FakeCategorySettingsRepository()
    private val calculator = TriggerTimeCalculator()

    private val shift = ShiftOrderAlarmsUseCase(orders, alarms, scheduler, categories, calculator, { bogota }, clock, AlarmMutationLock())

    private fun at(day: LocalDate, time: String): Instant = calculator.triggerAt(day, LocalTime.parse(time), 0, bogota)

    @Test
    fun `retrasa cada pendiente y la reprograma`() = runTest { // R-18.3
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)), testAlarm(id = 11, time = LocalTime.of(18, 30)))

        val result = shift(1, 30)

        assertThat(result).isEqualTo(ShiftResult.Ok(2, LocalTime.of(14, 0), LocalTime.of(14, 30)))
        assertThat(alarms.alarms.getValue(10).time).isEqualTo(LocalTime.of(14, 30))
        assertThat(alarms.alarms.getValue(11).time).isEqualTo(LocalTime.of(19, 0))
        assertThat(scheduler.scheduled).containsExactly(10L, at(date, "14:30"), 11L, at(date, "19:00"))
    }

    @Test
    fun `retrasar cruzando la medianoche pasa al dia siguiente`() = runTest { // R-18.3
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(23, 30)))

        shift(1, 60)

        val moved = alarms.alarms.getValue(10)
        assertThat(moved.time).isEqualTo(LocalTime.of(0, 30))
        assertThat(moved.dayOffset).isEqualTo(1)
    }

    @Test
    fun `adelantar cruzando la medianoche vuelve al dia anterior`() = runTest { // R-18.3
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(0, 30), dayOffset = 2))

        shift(1, -60)

        val moved = alarms.alarms.getValue(10)
        assertThat(moved.time).isEqualTo(LocalTime.of(23, 30))
        assertThat(moved.dayOffset).isEqualTo(1)
    }

    @Test
    fun `una pospuesta mueve tambien su hora pospuesta`() = runTest { // R-18.4
        orders.add(testOrder(id = 1, date = date))
        val snoozed = Instant.parse("2026-11-05T17:04:00Z")
        alarms.add(testAlarm(id = 10, time = LocalTime.of(11, 59), snoozedUntil = snoozed))

        val result = shift(1, 15)

        assertThat(alarms.alarms.getValue(10).snoozedUntil).isEqualTo(snoozed.plusSeconds(15 * 60))
        assertThat(scheduler.scheduled).containsExactly(10L, snoozed.plusSeconds(15 * 60))
        assertThat(result).isEqualTo(ShiftResult.Ok(1, LocalTime.of(12, 4), LocalTime.of(12, 19)))
    }

    @Test
    fun `adelantar sin que quede ninguna en el pasado funciona`() = runTest { // R-18.5
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(13, 0)))

        assertThat(shift(1, -59)).isInstanceOf(ShiftResult.Ok::class.java)
        assertThat(alarms.alarms.getValue(10).time).isEqualTo(LocalTime.of(12, 1))
    }

    @Test
    fun `adelantar hasta ahora o antes no cambia nada`() = runTest { // R-18.5
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(13, 0)), testAlarm(id = 11, time = LocalTime.of(12, 30)))

        val result = shift(1, -60)

        assertThat(result).isEqualTo(ShiftResult.InPast(2))
        assertThat(alarms.alarms.getValue(10).time).isEqualTo(LocalTime.of(13, 0))
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `fuera del dia 1 al dia 31 no cambia nada`() = runTest { // R-18.6
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(23, 0), dayOffset = 30), testAlarm(id = 11, time = LocalTime.of(14, 0)))

        assertThat(shift(1, 120)).isEqualTo(ShiftResult.OutOfRange)
        assertThat(alarms.alarms.getValue(11).time).isEqualTo(LocalTime.of(14, 0))

        orders.add(testOrder(id = 2, date = date.plusDays(1)))
        alarms.add(testAlarm(id = 20, orderId = 2, time = LocalTime.of(0, 30)))
        assertThat(shift(2, -60)).isEqualTo(ShiftResult.OutOfRange)
    }

    @Test
    fun `con el plan pausado guarda las horas sin programarlas`() = runTest { // R-18.7
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.PAUSED))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)))

        val result = shift(1, 30)

        assertThat(result).isInstanceOf(ShiftResult.Ok::class.java)
        assertThat(alarms.alarms.getValue(10).time).isEqualTo(LocalTime.of(14, 30))
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `las disparadas hechas omitidas y canceladas no cambian`() = runTest { // R-18.3
        orders.add(testOrder(id = 1, date = date))
        val others = listOf(AlarmStatus.FIRED, AlarmStatus.DONE, AlarmStatus.SKIPPED, AlarmStatus.CANCELLED)
            .mapIndexed { i, status -> testAlarm(id = 20L + i, time = LocalTime.of(9, 0), status = status) }
        alarms.add(*others.toTypedArray(), testAlarm(id = 10, time = LocalTime.of(14, 0)))

        val result = shift(1, 30)

        assertThat((result as ShiftResult.Ok).count).isEqualTo(1)
        others.forEach { assertThat(alarms.alarms.getValue(it.id)).isEqualTo(it) }
    }

    @Test
    fun `un plan cancelado o inexistente no se mueve`() = runTest { // R-18.1
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.CANCELLED))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)))

        assertThat(shift(1, 30)).isEqualTo(ShiftResult.NotAllowed)
        assertThat(shift(99, 30)).isEqualTo(ShiftResult.NotAllowed)
    }

    @Test
    fun `la vista previa no cambia nada`() = runTest { // R-18.2
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)), testAlarm(id = 11, time = LocalTime.of(9, 0), dayOffset = 1))

        val preview = shift.preview(1, 45)

        assertThat(preview).isEqualTo(ShiftResult.Ok(2, LocalTime.of(14, 0), LocalTime.of(14, 45)))
        assertThat(alarms.alarms.getValue(10).time).isEqualTo(LocalTime.of(14, 0))
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `mover y volver deja las mismas alarmas programadas`() = runTest { // D-73, R-12.6
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0), category = "medicación"))
        categories.setCritical("medicación", true)

        shift(1, 90)
        shift(1, -90)

        assertThat(scheduler.scheduled).containsExactly(10L, at(date, "14:00"))
        assertThat(scheduler.critical).containsExactly(10L, CriticalAlarm(orderId = 1))
    }
}
