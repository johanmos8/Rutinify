package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
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

/** T-57: editing one alarm from the plan detail (R-18.9, R-18.10, D-74). */
class UpdateAlarmUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val date = LocalDate.of(2026, 11, 5)
    private val clock = Clock.fixed(Instant.parse("2026-11-05T17:00:00Z"), bogota) // 12:00 in Bogotá
    private val orders = FakeOrderRepository()
    private val alarms = FakeAlarmRepository(clock) { id -> orders.activeDate(id) }
    private val scheduler = FakeAlarmScheduler()
    private val publisher = FakeNotificationPublisher()
    private val categories = FakeCategorySettingsRepository()
    private val calculator = TriggerTimeCalculator()

    private val update = UpdateAlarmUseCase(alarms, orders, scheduler, publisher, categories, calculator, { bogota }, clock, AlarmMutationLock())

    @Test
    fun `guarda hora titulo y categoria normalizada y la reprograma`() = runTest { // R-18.9
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)))

        val result = update(10, "15:30", "  Paseo largo ", "Paseo Largo")

        assertThat(result).isEqualTo(UpdateAlarmResult.Updated(titleTruncated = false))
        val saved = alarms.alarms.getValue(10)
        assertThat(saved.time).isEqualTo(LocalTime.of(15, 30))
        assertThat(saved.title).isEqualTo("Paseo largo")
        assertThat(saved.category).isEqualTo("paseo-largo")
        assertThat(scheduler.scheduled).containsExactly(10L, calculator.triggerAt(date, LocalTime.of(15, 30), 0, bogota))
        assertThat(publisher.channels).contains("paseo-largo")
    }

    @Test
    fun `una hora que ya paso no se guarda`() = runTest { // R-18.10
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)))

        assertThat(update(10, "11:00", "Siesta", "siesta")).isEqualTo(UpdateAlarmResult.PastTime)
        assertThat(update(10, "12:00", "Siesta", "siesta")).isEqualTo(UpdateAlarmResult.PastTime)
        assertThat(alarms.alarms.getValue(10).time).isEqualTo(LocalTime.of(14, 0))
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `mantiene el dia de la alarma`() = runTest { // R-18.9
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(2, 0), dayOffset = 1))

        update(10, "03:00", "Toma", "toma")

        assertThat(alarms.alarms.getValue(10).dayOffset).isEqualTo(1)
        assertThat(scheduler.scheduled).containsExactly(10L, calculator.triggerAt(date, LocalTime.of(3, 0), 1, bogota))
    }

    @Test
    fun `aplica las reglas de Revision a cada campo`() = runTest { // R-4.2
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)))

        assertThat(update(10, "25:00", " ", "x".repeat(31)))
            .isEqualTo(UpdateAlarmResult.Invalid(timeInvalid = true, titleBlank = true, categoryTooLong = true))
    }

    @Test
    fun `no deja pasar de 10 categorias en el plan`() = runTest { // R-3.22
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0), category = "c0"))
        (1..10).forEach { alarms.add(testAlarm(id = 20L + it, time = LocalTime.of(15, 0), category = "c$it")) }

        assertThat(update(10, "14:00", "Siesta", "nueva")).isEqualTo(UpdateAlarmResult.Invalid(tooManyCategories = true))
        assertThat(update(10, "14:00", "Siesta", "c1")).isInstanceOf(UpdateAlarmResult.Updated::class.java)
    }

    @Test
    fun `un titulo largo se recorta y lo dice`() = runTest { // R-3.10
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)))

        assertThat(update(10, "14:00", "t".repeat(90), "siesta")).isEqualTo(UpdateAlarmResult.Updated(titleTruncated = true))
        assertThat(alarms.alarms.getValue(10).title).hasLength(80)
    }

    @Test
    fun `solo se editan pendientes de planes activos o pausados`() = runTest { // R-18.9
        orders.add(testOrder(id = 1, date = date), testOrder(id = 2, date = date, status = OrderStatus.CANCELLED))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0), status = AlarmStatus.FIRED))
        alarms.add(testAlarm(id = 20, orderId = 2, time = LocalTime.of(14, 0)))

        assertThat(update(10, "15:00", "Siesta", "siesta")).isEqualTo(UpdateAlarmResult.NotEditable)
        assertThat(update(20, "15:00", "Siesta", "siesta")).isEqualTo(UpdateAlarmResult.NotEditable)
        assertThat(update(99, "15:00", "Siesta", "siesta")).isEqualTo(UpdateAlarmResult.NotEditable)
    }

    @Test
    fun `con el plan pausado guarda sin programar`() = runTest { // R-18.9, R-10.6
        orders.add(testOrder(id = 1, date = date, status = OrderStatus.PAUSED))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)))

        assertThat(update(10, "15:00", "Siesta", "siesta")).isInstanceOf(UpdateAlarmResult.Updated::class.java)
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `una pospuesta pierde su posposicion y pasar a categoria critica la hace despertador`() = runTest { // R-18.9, R-15.3
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(11, 55), snoozedUntil = Instant.parse("2026-11-05T17:04:00Z")))
        categories.setCritical("medicación", true)

        update(10, "16:00", "Ibuprofeno", "medicación")

        assertThat(alarms.alarms.getValue(10).snoozedUntil).isNull()
        assertThat(scheduler.critical).containsExactly(10L, CriticalAlarm(orderId = 1))
    }
}
