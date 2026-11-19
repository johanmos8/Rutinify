package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.Recurrence
import com.mirkwood.rutinify.domain.model.RoutineActivity
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** T-68: the 7-day window of recurring plans (R-23.7, R-23.12, D-81). */
class MaterializeRecurringUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val monday = LocalDate.of(2026, 11, 16)

    /** Monday 2026-11-16, 12:00 in Bogotá. */
    private var clock = Clock.fixed(Instant.parse("2026-11-16T17:00:00Z"), bogota)
    private val orders = FakeOrderRepository()
    private val alarms = FakeAlarmRepository(clock) { id -> orders.activeDate(id) }
    private val scheduler = FakeAlarmScheduler()
    private val publisher = FakeNotificationPublisher()
    private val categories = FakeCategorySettingsRepository()
    private val calculator = TriggerTimeCalculator()

    private fun materializer(at: Clock = clock) =
        MaterializeRecurringUseCase(orders, alarms, scheduler, publisher, categories, calculator, { bogota }, at)

    private fun plan(
        days: Set<DayOfWeek>,
        until: LocalDate? = null,
        start: LocalDate = monday,
        status: OrderStatus = OrderStatus.ACTIVE,
        vararg activities: Pair<String, Int> = arrayOf("07:30" to 0, "21:00" to 0),
    ) {
        orders.add(testOrder(id = 1, date = start, status = status).copy(recurrence = Recurrence(days, until)))
        orders.activities[1] = activities.mapIndexed { i, (time, offset) ->
            RoutineActivity(100L + i, 1, i, LocalTime.parse(time), offset, "Paseo $i", null, null, "paseo", null)
        }
    }

    private fun dates() = alarms.alarms.values.map { orders.orders.getValue(1).date.plusDays(it.dayOffset.toLong()) to it.time }.sortedWith(compareBy({ it.first }, { it.second }))

    @Test
    fun `crea los dias de la semana que tocan en los proximos 7 dias y no los ya pasados`() = runTest { // R-23.7
        plan(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))

        val created = materializer().materialize(null)

        // Monday 07:30 is already past at 12:00; next Monday (day 7) is outside the window
        assertThat(dates()).containsExactly(
            monday to LocalTime.of(21, 0),
            monday.plusDays(2) to LocalTime.of(7, 30),
            monday.plusDays(2) to LocalTime.of(21, 0),
            monday.plusDays(4) to LocalTime.of(7, 30),
            monday.plusDays(4) to LocalTime.of(21, 0),
        ).inOrder()
        assertThat(created).isEqualTo(5)
        assertThat(scheduler.scheduled).hasSize(5)
        assertThat(alarms.alarms.values.map { it.activityId }.toSet()).containsExactly(100L, 101L)
    }

    @Test
    fun `volver a ejecutarlo no duplica nada`() = runTest { // R-23.7, R-12.6
        plan(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        materializer().materialize(null)
        val before = alarms.alarms.size

        val again = materializer().materialize(null)

        assertThat(again).isEqualTo(0)
        assertThat(alarms.alarms).hasSize(before)
    }

    @Test
    fun `al dia siguiente entra el nuevo dia en la ventana`() = runTest { // R-23.7
        plan(setOf(DayOfWeek.MONDAY))
        materializer().materialize(null)
        assertThat(alarms.alarms).hasSize(1) // only today's 21:00

        val nextMonday = Clock.fixed(Instant.parse("2026-11-17T17:00:00Z"), bogota)
        materializer(nextMonday).materialize(null)

        assertThat(dates().map { it.first }).containsExactly(monday, monday.plusDays(7), monday.plusDays(7))
    }

    @Test
    fun `respeta until y la fecha de inicio`() = runTest { // R-23.2
        plan(DayOfWeek.entries.toSet(), until = monday.plusDays(2), start = monday.plusDays(1))

        materializer().materialize(null)

        assertThat(dates().map { it.first }.distinct()).containsExactly(monday.plusDays(1), monday.plusDays(2)).inOrder()
    }

    @Test
    fun `un plan pausado no crea alarmas`() = runTest { // R-23.12
        plan(DayOfWeek.entries.toSet(), status = OrderStatus.PAUSED)

        assertThat(materializer().materialize(null)).isEqualTo(0)
        assertThat(alarms.alarms).isEmpty()
    }

    @Test
    fun `la madrugada cuenta como el dia siguiente`() = runTest { // R-23.6
        plan(setOf(DayOfWeek.TUESDAY), activities = arrayOf("02:00" to 1))

        materializer().materialize(null)

        assertThat(dates()).containsExactly(monday.plusDays(2) to LocalTime.of(2, 0))
    }

    @Test
    fun `una categoria critica se programa como despertador`() = runTest { // R-15.3
        plan(setOf(DayOfWeek.WEDNESDAY))
        categories.setCritical("paseo", true)

        materializer().materialize(null)

        assertThat(scheduler.critical.values.toSet()).containsExactly(CriticalAlarm(orderId = 1))
        assertThat(publisher.channels).contains("paseo")
    }
}
